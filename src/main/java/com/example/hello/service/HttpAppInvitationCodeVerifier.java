package com.example.hello.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.hello.config.AppInvitationCodeProperties;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class HttpAppInvitationCodeVerifier implements AppInvitationCodeVerifier {

    private static final String INVALID_CODE_MESSAGE = "邀请码或剪艺码无效";
    private static final String USED_CODE_MESSAGE = "剪艺码已使用";
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
        if (!hasText(properties.getRequestCodeField())) {
            return VerificationResult.unavailable("剪艺码校验服务配置不完整");
        }

        try {
            HttpRequest request = buildRequest(code);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VerificationResult.unavailable(UNAVAILABLE_MESSAGE);
        } catch (IOException | IllegalArgumentException e) {
            return VerificationResult.unavailable(UNAVAILABLE_MESSAGE);
        }
    }

    private HttpRequest buildRequest(String code) throws IOException {
        String requestBody = objectMapper.createObjectNode()
                .put(properties.getRequestCodeField(), code)
                .toString();

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getVerifyConsumeEndpoint()))
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        String authHeaderValue = resolveAuthHeaderValue();
        if (hasText(properties.getAuthHeaderName()) && hasText(authHeaderValue)) {
            builder.header(properties.getAuthHeaderName(), authHeaderValue);
        }
        return builder.build();
    }

    private VerificationResult mapResponse(HttpResponse<String> response) {
        String message = extractMessage(response.body());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return resolveSuccessResponse(response.body(), message);
        }
        if (statusCode == 409) {
            return VerificationResult.used(defaultMessage(message, USED_CODE_MESSAGE));
        }
        if (statusCode == 400 || statusCode == 404 || statusCode == 422) {
            return VerificationResult.invalid(defaultMessage(message, INVALID_CODE_MESSAGE));
        }
        return VerificationResult.unavailable(defaultMessage(message, UNAVAILABLE_MESSAGE));
    }

    private VerificationResult resolveSuccessResponse(String body, String fallbackMessage) {
        JsonNode root = readJson(body);
        if (root == null) {
            return VerificationResult.consumed();
        }

        JsonNode payload = root.path("data").isObject() ? root.path("data") : root;
        String message = firstText(payload, "message", "msg", "detail");
        if (!hasText(message)) {
            message = fallbackMessage;
        }

        Boolean used = firstBoolean(payload, "used", "alreadyUsed");
        if (Boolean.TRUE.equals(used)) {
            return VerificationResult.used(defaultMessage(message, USED_CODE_MESSAGE));
        }

        Boolean valid = firstBoolean(payload, "valid");
        if (Boolean.FALSE.equals(valid)) {
            return VerificationResult.invalid(defaultMessage(message, INVALID_CODE_MESSAGE));
        }

        Boolean consumed = firstBoolean(payload, "consumed");
        if (Boolean.TRUE.equals(consumed) || Boolean.TRUE.equals(valid)) {
            return VerificationResult.consumed();
        }

        String status = normalizeStatus(firstText(payload, "status", "result", "codeStatus"));
        if (isUsedStatus(status)) {
            return VerificationResult.used(defaultMessage(message, USED_CODE_MESSAGE));
        }
        if (isInvalidStatus(status)) {
            return VerificationResult.invalid(defaultMessage(message, INVALID_CODE_MESSAGE));
        }
        if (isSuccessStatus(status)) {
            return VerificationResult.consumed();
        }

        Boolean rootSuccess = firstBoolean(root, "success", "ok");
        if (Boolean.FALSE.equals(rootSuccess)) {
            return VerificationResult.unavailable(defaultMessage(message, UNAVAILABLE_MESSAGE));
        }
        return VerificationResult.consumed();
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

    private Boolean firstBoolean(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (valueNode.isBoolean()) {
                return valueNode.asBoolean();
            }
            if (valueNode.isTextual()) {
                String normalized = valueNode.asText().trim().toLowerCase(Locale.ROOT);
                if ("true".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized)) {
                    return false;
                }
            }
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

    private String normalizeStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        return status.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equals(status) || "OK".equals(status) || "VALID".equals(status)
                || "CONSUMED".equals(status) || "VERIFIED".equals(status);
    }

    private boolean isUsedStatus(String status) {
        return "USED".equals(status) || "ALREADY_USED".equals(status);
    }

    private boolean isInvalidStatus(String status) {
        return "INVALID".equals(status) || "NOT_FOUND".equals(status) || "NOT_EXISTS".equals(status)
                || "EXPIRED".equals(status) || "REJECTED".equals(status);
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
