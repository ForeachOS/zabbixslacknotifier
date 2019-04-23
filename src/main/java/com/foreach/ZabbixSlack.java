package com.foreach;

import allbegray.slack.type.Channel;
import allbegray.slack.type.History;
import allbegray.slack.type.Message;
import com.foreach.search.Messages;
import com.foreach.search.SearchableSlackApiClient;
import com.foreach.search.SearchableSlackApiClientImpl;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ZabbixSlack {
    private static final String TOKEN = "xoxp-XXXXXXXXXX-XXXXXXXXXX-XXXXXXXXXXXX-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    private static final String USERNAME = "Zabbix";
    private static String CHANNEL_ID = StringUtils.EMPTY;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        if (args.length == 2 && StringUtils.equalsIgnoreCase("erase", args[1])) {
            String channel = args[0];
            SearchableSlackApiClient client = new SearchableSlackApiClientImpl(TOKEN);
            CHANNEL_ID = idForChannel(client, channel);


            Messages messages = client.searchMessages("in:" + channel);
            for (Message m : messages.getMatches()) {
                client.deleteMessage(CHANNEL_ID, m.getTs());
            }
        } else if (args.length == 3) {
            String channel = args[0];
            SearchableSlackApiClient client = new SearchableSlackApiClientImpl(TOKEN);
            CHANNEL_ID = idForChannel(client, channel);
            String subject = StringUtils.replace(args[1], "*", "");
            String message = args[2];

            String ZBID = null;
            boolean messageAlreadySent = false;
            if (StringUtils.startsWith(subject, "PROBLEM ")) {
                subject = ":red_circle: " + StringUtils.removeStart(subject, "PROBLEM ");
                // Search if we find another PROBLEM message with the same ID, if so delete it and publish a new message
                // Normally we should find this message instantly (zabbix would never send two PROBLEM events shortly after eachother
                // This usually only happens after 30 minutes, hence there is no retry mechanism
                Messages messages = client.searchMessages(zabbixId(message) + " in:" + channel, "timestamp");
                if (messages.getTotal() > 0) {
                    List<Message> matches = messages.getMatches();
                    for (int i = 0; i < matches.size(); i++) {
                        Message m = matches.get(i);
                        if (i == 0) {
                            String times = StringUtils.substringBetween(m.getText(), ", alerted *", " times*. #ZBID:");
                            if (times != null) {
                                message = StringUtils.replaceOnce(message, " #ZBID:", ", alerted *" + (Integer.valueOf(times) + 1) + " times*. #ZBID:");
                                postMessage(channel, subject, message, client);
                            } else {
                                message = StringUtils.replaceOnce(message, " #ZBID:", ", alerted *" + (matches.size() + 1) + " times*. #ZBID:");
                                postMessage(channel, subject, message, client);
                            }
                        }
                        client.deleteMessage(CHANNEL_ID, m.getTs());
                    }
                    messageAlreadySent = true;
                }
            } else if (StringUtils.startsWith(subject, "OK ")) {
                subject = ":green_circle: " + StringUtils.removeStart(subject, "OK ");
                ZBID = zabbixId(message);
            } else {
                if (StringUtils.isNotBlank(subject)) {
                    subject = ":warning: " + subject;
                }
            }

            if (!messageAlreadySent) {
                // A message might already be sent when it is a repeated PROBLEM alert (above)
                postMessage(channel, subject, message, client);
            }

            if (ZBID != null) {
                cleanup(client, "#ZBID:" + ZBID, channel);
            }
        } else {
            throw new IllegalArgumentException("Needs at least 3 parameters");
        }
        System.out.println("Ran in: " + (System.currentTimeMillis() - start) / 1000.00 + " seconds.");
    }

    private static String idForChannel(SearchableSlackApiClient client, String channel) {
        if (StringUtils.startsWithIgnoreCase(channel, "#zabbix")) {
            List<Channel> channelList = client.getChannelList();
            for (Channel c : channelList) {
                if (StringUtils.containsIgnoreCase(channel, c.getName())) {
                    return c.getId();
                }
            }
            throw new RuntimeException("Could not find channel: [" + channel + "].");
        }
        throw new RuntimeException("Unsupported channel: [" + channel + "] only #zabbix* channels supported");
    }

    private static void postMessage(String channel, String subject, String message, SearchableSlackApiClient client) {
        if (StringUtils.isNotBlank(subject)) {
            subject = "*" + subject + "*: ";
        }
        client.postMessage(channel, subject + message, USERNAME, false, false, null, false, false, null, ":zabbix:");
    }

    private static String zabbixId(String message) {
        return StringUtils.substringAfter(message, "#ZBID:");
    }

    private static void cleanup(SearchableSlackApiClient client, String zabbixEventId, String channel) {
        List<Message> matchingMessages = findMessages(client, zabbixEventId, channel);
        for (Message m : matchingMessages) {
            client.deleteMessage(CHANNEL_ID, m.getTs());
        }
    }

    /***
     * We will use the .channelHistory call because this is usually more performant than search for the Id
     * It might be possible (but shouldn't) that the messages are spread over the 1000 messages
     * If the history call doesn't match at least 2 messages, we will fall back on the slower messages.search call
     * @param zabbixEventId In the form of #ZBID:84738437:R
     * @return the PROBLEM and OK messages (usually two)
     */
    private static List<Message> findMessages(SearchableSlackApiClient client, String zabbixEventId, String channel) {
        List<Message> matchingMessages = new ArrayList<Message>();

        String cleanedZabbixEventId = StringUtils.removeEnd(zabbixEventId, ":R");
        History history = client.getChannelHistory(CHANNEL_ID, 1000);

        for (Message message : history.getMessages()) {
            if (matchingMessages.size() >= 2) {
                // Normally there's only one PROBLEM and one OK, if there are more, fine, break out of everything
                return matchingMessages;
            }
            if (StringUtils.containsIgnoreCase(message.getText(), cleanedZabbixEventId)) {
                matchingMessages.add(message);
            }
        }

        boolean okMessageFound = false;
        for (int i = 0; i < 80; i++) {
            // Retry finding the OK message for up to two minutes (1500 * 80)
            Messages messages = client.searchMessages(zabbixEventId + " in:" + channel);

            if (messages.getMatches().size() > 0) {
                okMessageFound = true;
                break;
            }
            // If we didn't find continue another cycle with searching
            sleep(1500);
        }

        // Just to be sure let's clear this
        matchingMessages.clear();
        if (okMessageFound) {
            Messages messages = client.searchMessages(cleanedZabbixEventId + " in:" + channel);
            matchingMessages.addAll(messages.getMatches());
        }
        return matchingMessages;
    }

    @SneakyThrows
    private static void sleep(long millis) {
        Thread.sleep(millis);
    }
}
