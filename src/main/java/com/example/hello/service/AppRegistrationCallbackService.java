package com.example.hello.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.hello.config.AppInvitationCodeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AppRegistrationCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AppRegistrationCallbackService.class);

    private final AppInvitationCodeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AppRegistrationCallbackService(AppInvitationCodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .build();
    }

    public void notifyRegisterSuccess(String jianyiId, String externalUserId, String nickname, String email) {
        if (!properties.isEnabled() || !hasText(properties.getRegisterCallbackEndpoint())) {
            return;
        }

        try {
            ObjectNode requestNode = objectMapper.createObjectNode()
                    .put("jianyi_id", jianyiId)
                    .put("external_user_id", externalUserId);
            ObjectNode userInfoNode = requestNode.putObject("external_user_info")
                    .put("nickname", nickname);
            if (hasText(email)) {
                userInfoNode.put("email", email);
            }
            String requestBody = requestNode.toString();

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getRegisterCallbackEndpoint()))
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (hasText(properties.getCallbackApiKey()) && hasText(properties.getCallbackApiKeyHeader())) {
                builder.header(properties.getCallbackApiKeyHeader(), properties.getCallbackApiKey().trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("注册回调失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("注册回调异常: {}", e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
