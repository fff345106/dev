package com.example.hello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = false;
    private String apiBotId;
    private String apiKey;
    private String apiEndpoint = "https://aiproxy.bja.sealos.run/v1/chat/completions";
    private String model = "moonshot-v1-8k-vision-preview";
    private int timeoutMillis = 15000;
    private String defaultStyle = "OT";
    private String defaultRegion = "OT";
    private String defaultPeriod = "OT";
    private int batchConcurrency = 4;
    private int batchQueueCapacity = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiBotId() {
        return apiBotId;
    }

    public void setApiBotId(String apiBotId) {
        this.apiBotId = apiBotId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String getDefaultStyle() {
        return defaultStyle;
    }

    public void setDefaultStyle(String defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public String getDefaultPeriod() {
        return defaultPeriod;
    }

    public void setDefaultPeriod(String defaultPeriod) {
        this.defaultPeriod = defaultPeriod;
    }

    public int getBatchConcurrency() {
        return batchConcurrency;
    }

    public void setBatchConcurrency(int batchConcurrency) {
        this.batchConcurrency = batchConcurrency;
    }

    public int getBatchQueueCapacity() {
        return batchQueueCapacity;
    }

    public void setBatchQueueCapacity(int batchQueueCapacity) {
        this.batchQueueCapacity = batchQueueCapacity;
    }
}
