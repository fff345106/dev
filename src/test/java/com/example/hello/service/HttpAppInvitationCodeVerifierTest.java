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
import com.example.hello.service.AppInvitationCodeVerifier.VerificationResult;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class HttpAppInvitationCodeVerifierTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifyAndConsume_shouldPostConfiguredFieldAndAuthHeader() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String baseUrl = startServer(200, "{\"status\":\"SUCCESS\"}", header, requestBody);

        AppInvitationCodeProperties properties = buildProperties(baseUrl + "/codes/consume");
        properties.setAuthHeaderName("X-App-Token");
        properties.setAuthScheme("");
        properties.setAuthToken("secret-token");
        properties.setRequestCodeField("jianyima");

        HttpAppInvitationCodeVerifier verifier = new HttpAppInvitationCodeVerifier(properties, new ObjectMapper());
        VerificationResult result = verifier.verifyAndConsume("123456");

        assertEquals(VerificationStatus.CONSUMED, result.status());
        assertEquals("secret-token", header.get());
        assertTrue(requestBody.get().contains("\"jianyima\":\"123456\""));
    }

    @Test
    void verifyAndConsume_shouldMapConflictToUsed() throws Exception {
        String baseUrl = startServer(409, "{\"message\":\"剪艺码已使用\"}", null, null);

        HttpAppInvitationCodeVerifier verifier = new HttpAppInvitationCodeVerifier(
                buildProperties(baseUrl + "/codes/consume"), new ObjectMapper());
        VerificationResult result = verifier.verifyAndConsume("123456");

        assertEquals(VerificationStatus.USED, result.status());
        assertEquals("剪艺码已使用", result.message());
    }

    @Test
    void verifyAndConsume_shouldMapExplicitInvalidResponse() throws Exception {
        String baseUrl = startServer(200, "{\"valid\":false,\"message\":\"剪艺码不存在\"}", null, null);

        HttpAppInvitationCodeVerifier verifier = new HttpAppInvitationCodeVerifier(
                buildProperties(baseUrl + "/codes/consume"), new ObjectMapper());
        VerificationResult result = verifier.verifyAndConsume("123456");

        assertEquals(VerificationStatus.INVALID, result.status());
        assertEquals("剪艺码不存在", result.message());
    }

    @Test
    void verifyAndConsume_shouldMapCanRegisterFalseToUsed() throws Exception {
        String baseUrl = startServer(200, "{\"valid\":true,\"can_register\":false,\"message\":\"剪艺码已使用\"}", null, null);

        HttpAppInvitationCodeVerifier verifier = new HttpAppInvitationCodeVerifier(
                buildProperties(baseUrl + "/codes/consume"), new ObjectMapper());
        VerificationResult result = verifier.verifyAndConsume("123456");

        assertEquals(VerificationStatus.USED, result.status());
        assertEquals("剪艺码已使用", result.message());
    }

    @Test
    void verifyAndConsume_shouldReturnUnavailableWhenServiceReturnsServerError() throws Exception {
        String baseUrl = startServer(500, "{\"message\":\"系统繁忙\"}", null, null);

        HttpAppInvitationCodeVerifier verifier = new HttpAppInvitationCodeVerifier(
                buildProperties(baseUrl + "/codes/consume"), new ObjectMapper());
        VerificationResult result = verifier.verifyAndConsume("123456");

        assertEquals(VerificationStatus.UNAVAILABLE, result.status());
        assertEquals("系统繁忙", result.message());
    }

    private AppInvitationCodeProperties buildProperties(String endpoint) {
        AppInvitationCodeProperties properties = new AppInvitationCodeProperties();
        properties.setEnabled(true);
        properties.setVerifyConsumeEndpoint(endpoint);
        properties.setTimeoutMillis(2000);
        return properties;
    }

    private String startServer(int statusCode, String responseBody, AtomicReference<String> header,
            AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/codes/consume", exchange -> {
            if (header != null) {
                header.set(exchange.getRequestHeaders().getFirst("X-App-Token"));
            }
            if (requestBody != null) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
