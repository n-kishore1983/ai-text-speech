package com.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private List<String> blockedWords = new ArrayList<>();

    public List<String> getBlockedWords() {
        return blockedWords;
    }

    public void setBlockedWords(List<String> blockedWords) {
        this.blockedWords = blockedWords;
    }
}

