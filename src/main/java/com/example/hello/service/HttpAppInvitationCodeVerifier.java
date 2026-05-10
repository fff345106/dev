package com.example.hello.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.example.hello.config.AppInvitationCodeProperties;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class HttpAppInvitationCodeVerifier implements AppInvitationCodeVerifier {

    private static final String INVALID_CODE_MESSAGE = "邀请码或剪艺码无效";
    private static final String UNAVAILABLE_MESSAGE = "剪艺码校验服务暂不可用";

    private final AppInvitationCodeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpAppInvitationCodeVerifier(AppInvitationCodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .build();
    }

    @Override
    public VerificationResult verifyAndConsume(String code) {
        if (!properties.isEnabled()) {
            return VerificationResult.invalid(INVALID_CODE_MESSAGE);
        }
        if (!hasText(properties.getVerifyConsumeEndpoint())) {
            return VerificationResult.unavailable("剪艺码校验服务配置不完整");
        }

        try {
            HttpRequest request = buildRequest(code);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response, code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.unavailable(UNAVAILABLE_MESSAGE);
        } catch (IOException | IllegalArgumentException e) {
            return VerificationResult.unavailable(UNAVAILABLE_MESSAGE);
        }
    }

    private HttpRequest buildRequest(String code) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getVerifyConsumeEndpoint()))
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .header("Accept", "application/json")
                .GET();

        String authHeaderValue = resolveAuthHeaderValue();
        if (hasText(properties.getAuthHeaderName()) && hasText(authHeaderValue)) {
            builder.header(properties.getAuthHeaderName(), authHeaderValue);
        }
        return builder.build();
    }

    private VerificationResult mapResponse(HttpResponse<String> response, String userCode) {
        String message = extractMessage(response.body());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return resolveSuccessResponse(response.body(), message, userCode);
        }
        if (statusCode == 401) {
            return VerificationResult.unavailable(defaultMessage(message, "剪艺码校验服务鉴权失败"));
        }
        if (statusCode == 403) {
            return VerificationResult.unavailable(defaultMessage(message, UNAVAILABLE_MESSAGE));
        }
        return VerificationResult.unavailable(defaultMessage(message, UNAVAILABLE_MESSAGE));
    }

    private VerificationResult resolveSuccessResponse(String body, String fallbackMessage, String userCode) {
        JsonNode root = readJson(body);
        if (root == null) {
            return VerificationResult.invalid(INVALID_CODE_MESSAGE);
        }

        // API 返回格式: {"invite_code": "JY12345678", "partner_id": "...", "partner_name": "..."}
        String inviteCode = firstText(root, "invite_code", "inviteCode");
        if (!hasText(inviteCode)) {
            return VerificationResult.unavailable("剪艺码校验服务返回数据异常");
        }

        if (inviteCode.equals(userCode)) {
            return VerificationResult.consumed();
        }
        return VerificationResult.invalid(INVALID_CODE_MESSAGE);
    }

    private JsonNode readJson(String body) {
        if (!hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private String extractMessage(String body) {
        JsonNode root = readJson(body);
        if (root == null) {
            return trimToNull(body);
        }
        JsonNode payload = root.path("data").isObject() ? root.path("data") : root;
        String message = firstText(payload, "message", "msg", "detail");
        if (hasText(message)) {
            return message;
        }
        message = firstText(root, "message", "msg", "detail");
        if (hasText(message)) {
            return message;
        }
        JsonNode errorNode = root.path("error");
        if (errorNode.isTextual()) {
            return trimToNull(errorNode.asText());
        }
        if (errorNode.isObject()) {
            return firstText(errorNode, "message", "msg", "detail");
        }
        return null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (valueNode.isTextual()) {
                String value = trimToNull(valueNode.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String resolveAuthHeaderValue() {
        if (!hasText(properties.getAuthToken())) {
            return null;
        }
        if (!hasText(properties.getAuthScheme())) {
            return properties.getAuthToken().trim();
        }
        return properties.getAuthScheme().trim() + " " + properties.getAuthToken().trim();
    }

    private String defaultMessage(String message, String defaultMessage) {
        return hasText(message) ? message : defaultMessage;
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
