package com.foreach.search;

import allbegray.slack.exception.SlackResponseRateLimitException;
import allbegray.slack.rtm.ProxyServerInfo;
import allbegray.slack.webapi.SlackWebApiClient;
import allbegray.slack.webapi.SlackWebApiClientImpl;
import allbegray.slack.webapi.method.SlackMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.InputStream;

public abstract class SearchableSlackApiClient extends SlackWebApiClientImpl implements SlackWebApiClient {
    public SearchableSlackApiClient(String token) {
        super(token);
    }

    public SearchableSlackApiClient(String token, ProxyServerInfo proxyServerInfo) {
        super(token, proxyServerInfo);
    }

    public SearchableSlackApiClient(String token, ObjectMapper mapper) {
        super(token, mapper);
    }

    public SearchableSlackApiClient(String token, ObjectMapper mapper, ProxyServerInfo proxyServerInfo) {
        super(token, mapper, proxyServerInfo);
    }

    public SearchableSlackApiClient(String token, ObjectMapper mapper, int timeout) {
        super(token, mapper, timeout);
    }

    public SearchableSlackApiClient(String token, ObjectMapper mapper, int timeout, ProxyServerInfo proxyServerInfo) {
        super(token, mapper, timeout, proxyServerInfo);
    }

    public abstract Messages searchMessages(String query);

    public abstract Messages searchMessages(String query, String sort);

    @Override
    protected JsonNode call(SlackMethod method, InputStream is) {
        return callWithRetryLimit(method, is, 0);
    }

    @SneakyThrows
    private JsonNode callWithRetryLimit(SlackMethod method, InputStream is, int retryTime) {
        if (retryTime >= 30) {
            // 30 retries, because mostly we need to retry after 2 seconds, giving it 1 minute to complete
            throw new RuntimeException("Failed to retry in a reasonable amount of time");
        }
        try {
            return super.call(method, is);
        } catch (SlackResponseRateLimitException rlEx) {
            Thread.sleep((rlEx.getRetryAfter() * 1000) + 400);
            return this.callWithRetryLimit(method, is, ++retryTime);
        }
    }
}
