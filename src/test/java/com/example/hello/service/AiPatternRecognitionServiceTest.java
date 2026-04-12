package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.hello.config.AiProperties;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class AiPatternRecognitionServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void recognizeByImageUrl_shouldCallAiApiAndParseStructuredJson() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        String responseBody = """
                {
                  "data": {
                    "choices": [
                      {
                        "message": {
                          "role": "assistant",
                          "content": "{\\\"patternName\\\":\\\"凤凰纹\\\",\\\"mainCategory\\\":\\\"AN\\\",\\\"subCategory\\\":\\\"MY\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"MG\\\",\\\"keywords\\\":[\\\"凤凰\\\",\\\"传统\\\",\\\"明清\\\"]}"
                        }
                      }
                    ]
                  }
                }
                """;
        String baseUrl = startServer(200, responseBody, authorization, contentType, requestBody);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/a.png");

        assertEquals("凤凰纹", result.getPatternName());
        assertEquals("AN", result.getMainCategory());
        assertEquals("MY", result.getSubCategory());
        assertEquals("TR", result.getStyle());
        assertEquals("CN", result.getRegion());
        assertEquals("MG", result.getPeriod());
        assertTrue(result.isValid());
        assertFalse(result.getKeywords().isEmpty());

        assertEquals("application/json", contentType.get());
        assertTrue(authorization.get().startsWith("Bearer "));
        assertTrue(requestBody.get().contains("\"model\":\"moonshot-v1-8k-vision-preview\""));
        assertTrue(requestBody.get().contains("\"messages\""));
        assertTrue(requestBody.get().contains("\"content\":"));
        // 验证多模态格式：content 应该是数组，包含 text 和 image_url
        assertTrue(requestBody.get().contains("\"type\":\"text\""));
        assertTrue(requestBody.get().contains("\"type\":\"image_url\""));
        assertTrue(requestBody.get().contains("\"url\":\"https://img/a.png\""));
    }

    @Test
    void recognizeByImageUrl_shouldBuildConversationChatRequestForV3ChatEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();

        String responseBody = """
                {
                  "data": {
                    "messages": [
                      {
                        "role": "assistant",
                        "type": "answer",
                        "content": "{\\\"patternName\\\":\\\"凤凰纹\\\",\\\"mainCategory\\\":\\\"AN\\\",\\\"subCategory\\\":\\\"MY\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"MG\\\",\\\"keywords\\\":[\\\"凤凰\\\"]}"
                      }
                    ]
                  }
                }
                """;
        String baseUrl = startServer("/v3/chat", 200, responseBody, null, null, requestBody);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl, "/v3/chat"));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/chat-api.png");

        assertEquals("凤凰纹", result.getPatternName());
        assertTrue(requestBody.get().contains("\"additional_messages\""));
        assertTrue(requestBody.get().contains("\"auto_save_history\":false"));
        assertFalse(requestBody.get().contains("\"messages\""));
        // 验证多模态格式：V3 API 也应该使用数组格式的 content
        assertTrue(requestBody.get().contains("\"type\":\"text\""));
        assertTrue(requestBody.get().contains("\"type\":\"image_url\""));
        assertTrue(requestBody.get().contains("\"url\":\"https://img/chat-api.png\""));
    }

    @Test
    void recognizeByImageUrl_shouldPollRetrieveWhenV3ChatReturnsInProgress() throws Exception {
        AtomicReference<String> createRequestBody = new AtomicReference<>();
        AtomicReference<String> retrieveMethod = new AtomicReference<>();
        AtomicReference<String> retrieveQuery = new AtomicReference<>();
        AtomicReference<String> retrieveRequestBody = new AtomicReference<>();
        AtomicInteger retrieveCount = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/chat", new CapturingHandler(
                200,
                """
                        {
                          "data": {
                            "id": "chat_123",
                            "conversation_id": "conv_123",
                            "status": "in_progress",
                            "last_error": {"code": 0, "msg": ""}
                          },
                          "code": 0,
                          "msg": ""
                        }
                        """,
                null,
                null,
                createRequestBody));
        server.createContext("/v3/chat/retrieve", exchange -> {
            retrieveCount.incrementAndGet();
            retrieveMethod.set(exchange.getRequestMethod());
            retrieveQuery.set(exchange.getRequestURI().getRawQuery());
            retrieveRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = """
                    {
                      "data": {
                        "id": "chat_123",
                        "conversation_id": "conv_123",
                        "status": "completed",
                        "messages": [
                          {
                            "role": "assistant",
                            "type": "answer",
                            "content": "{\\\"patternName\\\":\\\"凤凰纹\\\",\\\"mainCategory\\\":\\\"AN\\\",\\\"subCategory\\\":\\\"MY\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"MG\\\",\\\"keywords\\\":[\\\"凤凰\\\"]}"
                          }
                        ]
                      },
                      "code": 0,
                      "msg": ""
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiPatternRecognitionService service = createService(buildProperties(baseUrl, "/v3/chat"));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/polling.png");

        assertEquals("凤凰纹", result.getPatternName());
        assertEquals(1, retrieveCount.get());
        assertTrue(createRequestBody.get().contains("\"additional_messages\""));
        assertEquals("GET", retrieveMethod.get());
        assertEquals("conversation_id=conv_123&chat_id=chat_123", retrieveQuery.get());
        assertTrue(retrieveRequestBody.get().isBlank());
    }

    @Test
    void recognizeByImageUrl_shouldFetchMessageListWhenRetrieveHasNoAssistantMessages() throws Exception {
        AtomicInteger retrieveCount = new AtomicInteger();
        AtomicInteger messageListCount = new AtomicInteger();
        AtomicReference<String> messageListMethod = new AtomicReference<>();
        AtomicReference<String> messageListQuery = new AtomicReference<>();
        AtomicReference<String> messageListRequestBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/chat", new CapturingHandler(
                200,
                """
                        {
                          "data": {
                            "id": "chat_456",
                            "conversation_id": "conv_456",
                            "status": "in_progress",
                            "last_error": {"code": 0, "msg": ""}
                          },
                          "code": 0,
                          "msg": ""
                        }
                        """,
                null,
                null,
                null));
        server.createContext("/v3/chat/retrieve", exchange -> {
            retrieveCount.incrementAndGet();
            byte[] bytes = """
                    {
                      "data": {
                        "id": "chat_456",
                        "conversation_id": "conv_456",
                        "status": "completed"
                      },
                      "code": 0,
                      "msg": ""
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/v3/chat/message/list", exchange -> {
            messageListCount.incrementAndGet();
            messageListMethod.set(exchange.getRequestMethod());
            messageListQuery.set(exchange.getRequestURI().getRawQuery());
            messageListRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = """
                    {
                      "data": [
                        {
                          "role": "assistant",
                          "type": "answer",
                          "content": "{\\\"patternName\\\":\\\"凤凰纹\\\",\\\"mainCategory\\\":\\\"AN\\\",\\\"subCategory\\\":\\\"MY\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"MG\\\",\\\"keywords\\\":[\\\"凤凰\\\"]}"
                        }
                      ],
                      "code": 0,
                      "msg": ""
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiPatternRecognitionService service = createService(buildProperties(baseUrl, "/v3/chat"));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/message-list.png");

        assertEquals("凤凰纹", result.getPatternName());
        assertEquals(1, retrieveCount.get());
        assertEquals(1, messageListCount.get());
        assertEquals("GET", messageListMethod.get());
        assertEquals("conversation_id=conv_456&chat_id=chat_456", messageListQuery.get());
        assertTrue(messageListRequestBody.get().isBlank());
    }

    @Test
    void recognizeByImageUrl_shouldFallbackSubCategoryToStyleForMainCategoriesWithoutChildren() throws Exception {
        String responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "```json\\n{\\\"patternName\\\":\\\"山水纹\\\",\\\"mainCategory\\\":\\\"LA\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"OT\\\",\\\"keywords\\\":[\\\"山水\\\",\\\"中国\\\"]}\\n```"
                      }
                    }
                  ]
                }
                """;
        String baseUrl = startServer(200, responseBody, null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/landscape.png");

        assertEquals("LA", result.getMainCategory());
        assertEquals("TR", result.getSubCategory());
        assertEquals("TR", result.getStyle());
        assertEquals("CN", result.getRegion());
        assertEquals("山水纹", result.getPatternName());
        assertTrue(result.isValid());
    }

    @Test
    void recognizeByImageUrl_shouldForceStyleAsSubCategoryWhenMainCategoryHasNoChildrenEvenIfAiReturnsOt() throws Exception {
        String responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\\"patternName\\\":\\\"吉祥符号纹\\\",\\\"mainCategory\\\":\\\"SY\\\",\\\"subCategory\\\":\\\"OT\\\",\\\"style\\\":\\\"TR\\\",\\\"region\\\":\\\"CN\\\",\\\"period\\\":\\\"MG\\\",\\\"keywords\\\":[\\\"符号\\\",\\\"传统\\\"]}"
                      }
                    }
                  ]
                }
                """;
        String baseUrl = startServer(200, responseBody, null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/symbol.png");

        assertEquals("SY", result.getMainCategory());
        assertEquals("TR", result.getSubCategory());
        assertEquals("TR", result.getStyle());
        assertTrue(result.isValid());
    }

    @Test
    void recognizeByImageUrl_shouldFallbackToTextMappingWhenBotReplyIsPlainText() throws Exception {
        String responseBody = """
                {
                  "messages": [
                    {
                      "role": "assistant",
                      "type": "answer",
                      "content": "这是一幅现代中国仕女纹图案。"
                    }
                  ]
                }
                """;
        String baseUrl = startServer(200, responseBody, null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/plain-text.png");

        assertEquals("仕女纹", result.getPatternName());
        assertEquals("PE", result.getMainCategory());
        assertEquals("FE", result.getSubCategory());
        assertEquals("MO", result.getStyle());
        assertEquals("CN", result.getRegion());
        assertEquals("XD", result.getPeriod());
        assertTrue(result.isValid());
    }

    @Test
    void recognizeByImageUrl_shouldParseLabeledPlainTextFields() throws Exception {
        String responseBody = """
                {
                  "messages": [
                    {
                      "role": "assistant",
                      "type": "answer",
                      "content": "主类别：AN\\n子类别：BD\\n风格：TR\\n地区：CN\\n时期：MG"
                    }
                  ]
                }
                """;
        String baseUrl = startServer(200, responseBody, null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        AiPatternRecognitionService.RecognitionResult result = service.recognizeByImageUrl("https://img/labeled-text.png");

        assertEquals("其他纹样", result.getPatternName());
        assertEquals("AN", result.getMainCategory());
        assertEquals("BD", result.getSubCategory());
        assertEquals("TR", result.getStyle());
        assertEquals("CN", result.getRegion());
        assertEquals("MG", result.getPeriod());
        assertTrue(result.isValid());
    }

    @Test
    void recognizeByImageUrl_shouldThrowWhenApiReturnsBusinessError() throws Exception {
        String baseUrl = startServer(200, "{\"code\":401,\"msg\":\"invalid token\"}", null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.recognizeByImageUrl("https://img/error.png"));

        assertEquals("AI接口调用失败: invalid token", ex.getMessage());
    }

    @Test
    void recognizeByImageUrl_shouldThrowWhenHttpStatusNot2xx() throws Exception {
        String baseUrl = startServer(401, "{\"msg\":\"unauthorized\"}", null, null, null);

        AiPatternRecognitionService service = createService(buildProperties(baseUrl));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.recognizeByImageUrl("https://img/http-error.png"));

        assertEquals("AI接口调用失败，HTTP状态: 401，错误: unauthorized", ex.getMessage());
    }

    private AiProperties buildProperties(String baseUrl) {
        return buildProperties(baseUrl, "/v1/chat/completions");
    }

    private AiProperties buildProperties(String baseUrl, String endpointPath) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiBotId("7621108836975968282");
        properties.setApiKey("test-secret-token");
        properties.setApiEndpoint(baseUrl + endpointPath);
        properties.setModel("moonshot-v1-8k-vision-preview");
        properties.setTimeoutMillis(3000);
        return properties;
    }

    private AiPatternRecognitionService createService(AiProperties properties) {
        PatternCodeService patternCodeService = new PatternCodeService(
                Mockito.mock(PatternPendingRepository.class),
                Mockito.mock(PatternRepository.class));
        return new AiPatternRecognitionService(properties, new ObjectMapper(), patternCodeService);
    }

    private String startServer(
            int status,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> contentType,
            AtomicReference<String> requestBody) throws IOException {
        return startServer("/v1/chat/completions", status, responseBody, authorization, contentType, requestBody);
    }

    private String startServer(
            String endpointPath,
            int status,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> contentType,
            AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(endpointPath, new CapturingHandler(status, responseBody, authorization, contentType, requestBody));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static class CapturingHandler implements HttpHandler {
        private final int status;
        private final String responseBody;
        private final AtomicReference<String> authorization;
        private final AtomicReference<String> contentType;
        private final AtomicReference<String> requestBody;

        private CapturingHandler(
                int status,
                String responseBody,
                AtomicReference<String> authorization,
                AtomicReference<String> contentType,
                AtomicReference<String> requestBody) {
            this.status = status;
            this.responseBody = responseBody;
            this.authorization = authorization;
            this.contentType = contentType;
            this.requestBody = requestBody;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (authorization != null) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            if (contentType != null) {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            }
            if (requestBody != null) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
