package com.example.hello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = false;
    private String cozeBotId;
    private String cozeSecretToken;
    private String cozeEndpoint = "https://open.coze.cn/openapi/v3/chat/completions";
    private int timeoutMillis = 15000;
    private String defaultStyle = "OT";
    private String defaultRegion = "OT";
    private String defaultPeriod = "OT";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCozeBotId() {
        return cozeBotId;
    }

    public void setCozeBotId(String cozeBotId) {
        this.cozeBotId = cozeBotId;
    }

    public String getCozeSecretToken() {
        return cozeSecretToken;
    }

    public void setCozeSecretToken(String cozeSecretToken) {
        this.cozeSecretToken = cozeSecretToken;
    }

    public String getCozeEndpoint() {
        return cozeEndpoint;
    }

    public void setCozeEndpoint(String cozeEndpoint) {
        this.cozeEndpoint = cozeEndpoint;
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
}
