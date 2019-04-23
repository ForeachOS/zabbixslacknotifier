package com.foreach.search;

import allbegray.slack.type.Message;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Messages {
    private int total;
    private List<Message> matches;
}
