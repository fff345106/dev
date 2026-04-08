package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.hello.config.AppInvitationCodeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class AppRegistrationCallbackServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void notifyRegisterSuccess_shouldPostPayloadWithApiKeyHeader() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String baseUrl = startServer(apiKey, requestBody);

        AppInvitationCodeProperties properties = new AppInvitationCodeProperties();
        properties.setEnabled(true);
        properties.setTimeoutMillis(2000);
        properties.setRegisterCallbackEndpoint(baseUrl + "/register/callback");
        properties.setCallbackApiKeyHeader("X-API-Key");
        properties.setCallbackApiKey("test-key");

        AppRegistrationCallbackService service = new AppRegistrationCallbackService(properties, new ObjectMapper());
        service.notifyRegisterSuccess("12345678", "user_10", "alice", "alice@example.com");

        assertEquals("test-key", apiKey.get());
        assertTrue(requestBody.get().contains("\"jianyi_id\":\"12345678\""));
        assertTrue(requestBody.get().contains("\"external_user_id\":\"user_10\""));
        assertTrue(requestBody.get().contains("\"external_user_info\":{"));
        assertTrue(requestBody.get().contains("\"nickname\":\"alice\""));
        assertTrue(requestBody.get().contains("\"email\":\"alice@example.com\""));
    }

    private String startServer(AtomicReference<String> apiKey, AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/register/callback", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
