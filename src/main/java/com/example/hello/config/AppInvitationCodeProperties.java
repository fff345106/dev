package com.example.hello.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.invitation-code")
public class AppInvitationCodeProperties {

    private boolean enabled = false;
    private String verifyConsumeEndpoint;
    private String authHeaderName = "Authorization";
    private String authScheme = "Bearer";
    private String authToken;
    private String requestCodeField = "code";
    private int timeoutMillis = 5000;
    private String registerCallbackEndpoint;
    private String callbackApiKeyHeader = "X-API-Key";
    private String callbackApiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVerifyConsumeEndpoint() {
        return verifyConsumeEndpoint;
    }

    public void setVerifyConsumeEndpoint(String verifyConsumeEndpoint) {
        this.verifyConsumeEndpoint = verifyConsumeEndpoint;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getRequestCodeField() {
        return requestCodeField;
    }

    public void setRequestCodeField(String requestCodeField) {
        this.requestCodeField = requestCodeField;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String getRegisterCallbackEndpoint() {
        return registerCallbackEndpoint;
    }

    public void setRegisterCallbackEndpoint(String registerCallbackEndpoint) {
        this.registerCallbackEndpoint = registerCallbackEndpoint;
    }

    public String getCallbackApiKeyHeader() {
        return callbackApiKeyHeader;
    }

    public void setCallbackApiKeyHeader(String callbackApiKeyHeader) {
        this.callbackApiKeyHeader = callbackApiKeyHeader;
    }

    public String getCallbackApiKey() {
        return callbackApiKey;
    }

    public void setCallbackApiKey(String callbackApiKey) {
        this.callbackApiKey = callbackApiKey;
    }
}
