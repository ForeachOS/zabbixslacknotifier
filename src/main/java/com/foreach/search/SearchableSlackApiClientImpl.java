package com.foreach.search;

import allbegray.slack.rtm.ProxyServerInfo;
import allbegray.slack.webapi.method.search.SearchMessageMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SearchableSlackApiClientImpl extends SearchableSlackApiClient {
    public SearchableSlackApiClientImpl(String token) {
        super(token);
    }

    public SearchableSlackApiClientImpl(String token, ProxyServerInfo proxyServerInfo) {
        super(token, proxyServerInfo);
    }

    public SearchableSlackApiClientImpl(String token, ObjectMapper mapper) {
        super(token, mapper);
    }

    public SearchableSlackApiClientImpl(String token, ObjectMapper mapper, ProxyServerInfo proxyServerInfo) {
        super(token, mapper, proxyServerInfo);
    }

    public SearchableSlackApiClientImpl(String token, ObjectMapper mapper, int timeout) {
        super(token, mapper, timeout);
    }

    public SearchableSlackApiClientImpl(String token, ObjectMapper mapper, int timeout, ProxyServerInfo proxyServerInfo) {
        super(token, mapper, timeout, proxyServerInfo);
    }

    @Override
    public Messages searchMessages(String query) {
        return searchMessages(query, null);
    }

    @Override
    public Messages searchMessages(String query, String sort) {
        SearchMessageMethod search = new SearchMessageMethod(query);
        search.setSort(sort);
        search.setSort_dir("desc");
        JsonNode retNode = call(new SearchMessageMethod(query));
        return readValue(retNode, "messages", Messages.class);
    }
}
