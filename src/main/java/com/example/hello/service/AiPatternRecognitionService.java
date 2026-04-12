package com.example.hello.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.hello.config.AiProperties;
import com.example.hello.enums.PatternCodeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AiPatternRecognitionService {
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final PatternCodeService patternCodeService;
    private final HttpClient httpClient;

    public AiPatternRecognitionService(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            PatternCodeService patternCodeService) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.patternCodeService = patternCodeService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(aiProperties.getTimeoutMillis()))
                .build();
    }

    public RecognitionResult recognizeByImageUrl(String imageUrl) {
        if (!aiProperties.isEnabled()) {
            throw new RuntimeException("AI识别未启用，请先设置 ai.enabled=true");
        }
        ensureConfig();

        try {
            String botReply = callAiApi(imageUrl);
            RecognitionResult result = parseRecognitionResult(botReply);
            return attachValidation(result);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI识别失败: " + e.getMessage(), e);
        }
    }

    private void ensureConfig() {
        if (isBlank(aiProperties.getApiKey())) {
            throw new RuntimeException("AI识别配置缺失: ai.api-key");
        }
        if (isBlank(aiProperties.getApiEndpoint())) {
            throw new RuntimeException("AI识别配置缺失: ai.api-endpoint");
        }
        if (useConversationChatApi() && isBlank(aiProperties.getApiBotId())) {
            throw new RuntimeException("AI识别配置缺失: ai.api-bot-id");
        }
        if (useChatCompletionsApi() && isBlank(aiProperties.getModel())) {
            throw new RuntimeException("AI识别配置缺失: ai.model");
        }
    }

    private String callAiApi(String imageUrl) throws Exception {
        String requestBody = buildRequestBody(imageUrl);
        JsonNode root = sendJsonPost(aiProperties.getApiEndpoint(), requestBody);
        root = awaitConversationResult(root);

        String content = extractAssistantContent(root);
        if (isBlank(content) && useConversationChatApi()) {
            JsonNode messageListRoot = fetchConversationMessages(root);
            if (messageListRoot != null) {
                content = extractAssistantContent(messageListRoot);
                if (isBlank(content)) {
                    root = messageListRoot;
                }
            }
        }
        if (isBlank(content)) {
            throw new RuntimeException("AI接口未返回有效回答: " + abbreviate(root.toString(), 300));
        }
        return content.trim();
    }

    private String buildRequestBody(String imageUrl) throws Exception {
        String prompt = buildRecognitionPrompt();
        if (useConversationChatApi()) {
            String userId = buildUserId();
            return objectMapper.createObjectNode()
                    .put("bot_id", aiProperties.getApiBotId())
                    .put("user_id", userId)
                    .put("stream", false)
                    .put("auto_save_history", false)
                    .set("additional_messages", objectMapper.createArrayNode()
                            .add(createMultimodalMessage(prompt, imageUrl)))
                    .toString();
        }
        return objectMapper.createObjectNode()
                .put("model", aiProperties.getModel())
                .put("stream", false)
                .put("max_tokens", 512)
                .put("temperature", 0.7)
                .set("messages", objectMapper.createArrayNode()
                        .add(createMultimodalMessage(prompt, imageUrl)))
                .toString();
    }

    private ObjectNode createMultimodalMessage(String prompt, String imageUrl) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");

        ArrayNode contentArray = objectMapper.createArrayNode();

        // 文本部分
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        contentArray.add(textPart);

        // 图片部分
        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrlNode = objectMapper.createObjectNode();
        imageUrlNode.put("url", imageUrl);
        imagePart.set("image_url", imageUrlNode);
        contentArray.add(imagePart);

        message.set("content", contentArray);
        return message;
    }

    private boolean useConversationChatApi() {
        String endpoint = aiProperties.getApiEndpoint();
        if (isBlank(endpoint)) {
            return false;
        }
        String normalized = endpoint.toLowerCase(Locale.ROOT);
        return normalized.contains("/v3/chat") && !normalized.contains("chat/completions");
    }

    private boolean useChatCompletionsApi() {
        String endpoint = aiProperties.getApiEndpoint();
        if (isBlank(endpoint)) {
            return false;
        }
        return endpoint.toLowerCase(Locale.ROOT).contains("chat/completions");
    }

    private JsonNode sendJsonPost(String endpoint, String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(aiProperties.getTimeoutMillis()))
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private JsonNode sendJsonGet(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(aiProperties.getTimeoutMillis()))
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(buildHttpErrorMessage(response.statusCode(), response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        throwIfApiError(root);
        return root;
    }

    private JsonNode awaitConversationResult(JsonNode root) throws Exception {
        if (!useConversationChatApi()) {
            return root;
        }
        String status = extractConversationStatus(root);
        if (isBlank(status) || !isConversationPending(status)) {
            return root;
        }

        String conversationId = extractConversationId(root);
        String chatId = extractChatId(root);
        if (isBlank(conversationId) || isBlank(chatId)) {
            return root;
        }

        JsonNode current = root;
        long deadline = System.nanoTime() + Duration.ofMillis(aiProperties.getTimeoutMillis()).toNanos();
        while (System.nanoTime() < deadline) {
            Thread.sleep(determinePollIntervalMillis());
            current = retrieveConversation(conversationId, chatId);
            String currentStatus = extractConversationStatus(current);
            if (hasAssistantContent(current) || isBlank(currentStatus) || !isConversationPending(currentStatus)) {
                break;
            }
        }

        String finalStatus = extractConversationStatus(current);
        if (isConversationPending(finalStatus)) {
            throw new RuntimeException("AI接口响应超时: " + abbreviate(current.toString(), 300));
        }
        if (isConversationFailure(finalStatus)) {
            throw new RuntimeException("AI接口调用失败: " + firstNonBlank(
                    extractErrorMessage(current.toString()),
                    abbreviate(current.toString(), 300)));
        }
        return current;
    }

    private JsonNode retrieveConversation(String conversationId, String chatId) throws Exception {
        return sendJsonGet(buildConversationQueryEndpoint(
                deriveConversationEndpoint("/retrieve"),
                conversationId,
                chatId));
    }

    private JsonNode fetchConversationMessages(JsonNode root) throws Exception {
        String conversationId = extractConversationId(root);
        String chatId = extractChatId(root);
        if (isBlank(conversationId) || isBlank(chatId)) {
            return null;
        }
        return sendJsonGet(buildConversationQueryEndpoint(
                deriveConversationEndpoint("/message/list"),
                conversationId,
                chatId));
    }

    private String buildConversationQueryEndpoint(String endpoint, String conversationId, String chatId) {
        String result = appendQueryParameter(endpoint, "conversation_id", conversationId);
        return appendQueryParameter(result, "chat_id", chatId);
    }

    private String appendQueryParameter(String endpoint, String name, String value) {
        if (isBlank(value)) {
            return endpoint;
        }
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String deriveConversationEndpoint(String suffix) {
        String endpoint = aiProperties.getApiEndpoint();
        if (endpoint.endsWith(suffix)) {
            return endpoint;
        }
        int queryIndex = endpoint.indexOf('?');
        String query = queryIndex >= 0 ? endpoint.substring(queryIndex) : "";
        String base = queryIndex >= 0 ? endpoint.substring(0, queryIndex) : endpoint;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v3/chat")) {
            return base + suffix + query;
        }
        return base + suffix + query;
    }

    private String extractConversationStatus(JsonNode root) {
        return firstNonBlank(
                asText(root.path("data").path("status")),
                asText(root.path("status")));
    }

    private String extractConversationId(JsonNode root) {
        return firstNonBlank(
                asText(root.path("data").path("conversation_id")),
                asText(root.path("conversation_id")));
    }

    private String extractChatId(JsonNode root) {
        return firstNonBlank(
                asText(root.path("data").path("id")),
                asText(root.path("data").path("chat_id")),
                asText(root.path("chat_id")),
                asText(root.path("id")));
    }

    private boolean hasAssistantContent(JsonNode root) {
        return !isBlank(extractAssistantContent(root));
    }

    private boolean isConversationPending(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return "in_progress".equals(normalized) || "queued".equals(normalized) || "processing".equals(normalized);
    }

    private boolean isConversationFailure(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return "failed".equals(normalized) || "error".equals(normalized) || "cancelled".equals(normalized);
    }

    private long determinePollIntervalMillis() {
        return Math.max(100L, Math.min(500L, aiProperties.getTimeoutMillis() / 10L));
    }

    private String buildHttpErrorMessage(int statusCode, String responseBody) {
        String message = extractErrorMessage(responseBody);
        if (!isBlank(message)) {
            return "AI接口调用失败，HTTP状态: " + statusCode + "，错误: " + message;
        }
        return "AI接口调用失败，HTTP状态: " + statusCode + "，响应: " + abbreviate(responseBody, 300);
    }

    private String extractErrorMessage(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return firstNonBlank(
                    asText(root.path("msg")),
                    asText(root.path("message")),
                    asText(root.path("error").path("message")),
                    asText(root.path("error").path("msg")),
                    asText(root.path("data").path("last_error").path("msg")),
                    asText(root.path("data").path("last_error").path("message")));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private void throwIfApiError(JsonNode root) {
        JsonNode codeNode = root.path("code");
        if (!codeNode.isMissingNode() && !codeNode.isNull()) {
            String codeText = asText(codeNode);
            if (!isBlank(codeText) && !"0".equals(codeText.trim()) && !"success".equalsIgnoreCase(codeText.trim())) {
                throw new RuntimeException("AI接口调用失败: " + firstNonBlank(
                        asText(root.path("msg")),
                        asText(root.path("message")),
                        "未知错误"));
            }
        }

        JsonNode errorNode = root.path("error");
        if (!errorNode.isMissingNode() && !errorNode.isNull()) {
            String message = firstNonBlank(
                    errorNode.path("message").asText(null),
                    errorNode.path("msg").asText(null),
                    root.path("msg").asText(null),
                    root.path("message").asText(null),
                    "未知错误");
            throw new RuntimeException("AI接口调用失败: " + message);
        }

        JsonNode lastErrorNode = root.path("data").path("last_error");
        if (!lastErrorNode.isMissingNode() && !lastErrorNode.isNull()) {
            String message = firstNonBlank(
                    asText(lastErrorNode.path("msg")),
                    asText(lastErrorNode.path("message")));
            if (!isBlank(message)) {
                throw new RuntimeException("AI接口调用失败: " + message);
            }
        }
    }

    private String extractAssistantContent(JsonNode root) {
        String content = extractChoiceContent(root.path("data").path("choices"));
        if (!isBlank(content)) {
            return content;
        }

        content = extractChoiceContent(root.path("choices"));
        if (!isBlank(content)) {
            return content;
        }

        content = extractMessageContent(root.path("data").path("messages"));
        if (!isBlank(content)) {
            return content;
        }

        content = extractMessageContent(root.path("messages"));
        if (!isBlank(content)) {
            return content;
        }

        content = extractMessageContent(root.path("data"));
        if (!isBlank(content)) {
            return content;
        }

        content = firstNonBlank(
                asText(root.path("data").path("content")),
                asText(root.path("data").path("answer")),
                asText(root.path("data").path("output")),
                asText(root.path("content")),
                asText(root.path("answer")));
        if (!isBlank(content)) {
            return content;
        }

        return asText(root.path("data").path("message").path("content"));
    }

    private String extractChoiceContent(JsonNode choicesNode) {
        if (!choicesNode.isArray()) {
            return null;
        }
        for (JsonNode choiceNode : choicesNode) {
            String content = asText(choiceNode.path("message").path("content"));
            if (!isBlank(content)) {
                return content;
            }
            content = asText(choiceNode.path("content"));
            if (!isBlank(content)) {
                return content;
            }
        }
        return null;
    }

    private String extractMessageContent(JsonNode messagesNode) {
        if (!messagesNode.isArray()) {
            return null;
        }
        for (JsonNode messageNode : messagesNode) {
            String role = messageNode.path("role").asText("");
            String type = messageNode.path("type").asText("");
            if (!"assistant".equalsIgnoreCase(role) && !"answer".equalsIgnoreCase(type)) {
                continue;
            }
            String content = asText(messageNode.path("content"));
            if (!isBlank(content)) {
                return content;
            }
        }
        return null;
    }

    private RecognitionResult parseRecognitionResult(String botReply) throws Exception {
        JsonNode parsedJson = tryParseJsonPayload(botReply);
        if (parsedJson != null) {
            return parseRecognitionJson(parsedJson);
        }

        RecognitionResult labeledResult = tryParseLabeledPayload(botReply);
        if (labeledResult != null) {
            return labeledResult;
        }

        List<KeywordHit> keywordHits = buildKeywordHitsFromDescription(botReply);
        if (keywordHits.isEmpty()) {
            throw new RuntimeException("AI接口未返回可用结果");
        }
        return mapFromKeywords(keywordHits);
    }

    private JsonNode tryParseJsonPayload(String botReply) {
        if (isBlank(botReply)) {
            return null;
        }
        String normalized = stripMarkdownCodeFence(botReply.trim());
        String jsonCandidate = extractFirstJsonObject(normalized);
        if (isBlank(jsonCandidate)) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonCandidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private RecognitionResult tryParseLabeledPayload(String botReply) {
        if (isBlank(botReply)) {
            return null;
        }
        Map<String, String> labeledFields = extractLabeledFields(stripMarkdownCodeFence(botReply.trim()));
        if (!containsRecognitionField(labeledFields)) {
            return null;
        }

        String mainCategory = normalizeCodeOrDefault(labeledFields.get("mainCategory"), "OT");
        String style = normalizeCodeOrDefault(labeledFields.get("style"), "OT");
        String region = normalizeCodeOrDefault(labeledFields.get("region"), "OT");
        String period = normalizeCodeOrDefault(labeledFields.get("period"), "OT");

        String subCategory = resolveSubCategory(
                mainCategory,
                style,
                patternCodeService.normalizeCode(labeledFields.get("subCategory")));

        List<String> keywords = readKeywords(labeledFields.get("keywords"));
        String patternName = firstNonBlank(
                labeledFields.get("patternName"),
                inferPatternNameFromKeywords(keywords),
                "其他纹样");

        return new RecognitionResult(
                patternName,
                mainCategory,
                subCategory,
                style,
                region,
                period,
                keywords,
                List.of());
    }

    private Map<String, String> extractLabeledFields(String text) {
        if (isBlank(text)) {
            return Map.of();
        }
        Matcher matcher = Pattern.compile(
                "(?i)(纹样名称|样式名称|pattern_name|patternname|主类别|主类|main_category|maincategory|子类别|子类|sub_category|subcategory|风格|style|地区|region|时期|period|关键词|关键字|keywords?)\\s*[：:]")
                .matcher(text);
        List<FieldSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            String key = canonicalizeFieldKey(matcher.group(1));
            if (key != null) {
                spans.add(new FieldSpan(key, matcher.start(), matcher.end()));
            }
        }
        if (spans.isEmpty()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < spans.size(); i++) {
            FieldSpan current = spans.get(i);
            int valueEnd = i + 1 < spans.size() ? spans.get(i + 1).start() : text.length();
            String value = cleanupLabeledValue(text.substring(current.end(), valueEnd));
            if (!isBlank(value)) {
                values.putIfAbsent(current.key(), value);
            }
        }
        return values;
    }

    private String canonicalizeFieldKey(String label) {
        if (isBlank(label)) {
            return null;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "纹样名称", "样式名称", "pattern_name", "patternname" -> "patternName";
            case "主类别", "主类", "main_category", "maincategory" -> "mainCategory";
            case "子类别", "子类", "sub_category", "subcategory" -> "subCategory";
            case "风格", "style" -> "style";
            case "地区", "region" -> "region";
            case "时期", "period" -> "period";
            case "关键词", "关键字", "keyword", "keywords" -> "keywords";
            default -> null;
        };
    }

    private String cleanupLabeledValue(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceFirst("^[\\s：:]+", "").trim();
    }

    private boolean containsRecognitionField(Map<String, String> labeledFields) {
        return !isBlank(labeledFields.get("mainCategory"))
                || !isBlank(labeledFields.get("subCategory"))
                || !isBlank(labeledFields.get("style"))
                || !isBlank(labeledFields.get("region"))
                || !isBlank(labeledFields.get("period"))
                || !isBlank(labeledFields.get("patternName"));
    }

    private List<String> readKeywords(String keywordsText) {
        if (isBlank(keywordsText)) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String part : keywordsText.split("[、,，;；\\n]+")) {
            if (!isBlank(part)) {
                keywords.add(part.trim());
            }
        }
        return keywords;
    }

    private RecognitionResult parseRecognitionJson(JsonNode root) {
        String mainCategory = normalizeCodeOrDefault(firstText(root, "mainCategory", "main_category"), "OT");
        String style = normalizeCodeOrDefault(firstText(root, "style"), "OT");
        String region = normalizeCodeOrDefault(firstText(root, "region"), "OT");
        String period = normalizeCodeOrDefault(firstText(root, "period"), "OT");

        String subCategory = resolveSubCategory(
                mainCategory,
                style,
                patternCodeService.normalizeCode(firstText(root, "subCategory", "sub_category")));

        List<String> keywords = readKeywords(root.path("keywords"));
        if (keywords.isEmpty()) {
            keywords = readKeywords(root.path("keyword_list"));
        }

        String patternName = firstNonBlank(
                firstText(root, "patternName", "pattern_name", "name"),
                inferPatternNameFromKeywords(keywords),
                "其他纹样");

        return new RecognitionResult(
                patternName,
                mainCategory,
                subCategory,
                style,
                region,
                period,
                keywords,
                List.of());
    }

    private String inferPatternNameFromKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        List<KeywordHit> keywordHits = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            keywordHits.add(new KeywordHit(keyword.trim(), keyword.trim().toLowerCase(Locale.ROOT), 1D));
        }
        if (keywordHits.isEmpty()) {
            return null;
        }
        return extractPatternName(keywordHits);
    }

    private List<String> readKeywords(JsonNode keywordsNode) {
        if (!keywordsNode.isArray()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (JsonNode item : keywordsNode) {
            String value = asText(item);
            if (!isBlank(value)) {
                keywords.add(value.trim());
            }
        }
        return keywords;
    }

    private String buildUserId() {
        return "ai-batch-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildRecognitionPrompt() {
        return """
                你是一个剪纸纹样识别专家。请基于上传的图片识别纹样，并严格按代码表输出结构化结果。

                约束要求：
                1) 纹样名称需具体，不可仅输出“动物/植物/人物”等大类。
                2) 代码必须从下方代码表中选择。
                3) 时期不能是 OT，若无法判断请根据图像风格做最接近推断。
                4) 最终仅输出 JSON，不要输出 Markdown、解释文字或额外字段。

                主类别代码|类别名称|分类指引
                AN 动物: 包含禽鸟、鱼虫、走兽等现实或想象中的动物形象。
                PL 植物: 包含花卉、草木、果实、叶子等各种植物形态。
                PE 人物: 包含各年龄段、各社会角色的人像或人物活动。
                LA 风景: 包含山水、园林、建筑等自然或人工景观。
                AB 抽象: 包含几何纹、装饰性线条等无具体实物对应的图案。
                OR 器物: 包含家具、兵器、乐器、日常用品等人工制造的物体。
                SY 符号: 包含文字、吉祥图符、八卦、万字纹等抽象语义符号。
                CE 庆典: 包含岁时节令、民俗舞蹈、戏剧场景、婚嫁等仪式。
                MY 神话: 包含神仙佛道、民间传说、仙境等超自然题材。
                OT 其他: 无法归入上述明确类别的题材。

                动物子类别（AN）指引
                BD 鸟类: 飞禽类，如凤凰、喜鹊、仙鹤、鸳鸯等。
                FS 鱼类: 各种鱼类形象，常代表年年有余。
                IN 昆虫: 蝴蝶、蝉、螳螂、蚂蚱等。
                MA 哺乳动物: 走兽类，如虎、狮、马、鹿、兔、猫等。
                MY 神话动物: 龙、麒麟、貔貅、饕餮等非现实动物。
                RP 爬行动物: 蛇、龟、蜥蜴、蟾蜍等。
                OT 其他动物: 其他无法分类的动物。

                植物子类别（PL）指引
                FL 花卉: 以花朵为核心，如牡丹、莲花、菊花、梅花等。
                TR 树木: 以树干、树枝或整体树形为主，如松、柏、柳、竹。
                FR 果实: 植物的果实或种子，如石榴（多子）、桃（长寿）、葫芦。
                GR 谷物: 农作物，如麦穗、稻谷，代表丰收。
                LV 叶子: 仅以叶片作为主要表现对象。
                OT 其他植物: 其他无法分类的植物。

                人物类子类别（PE）指引
                MU 男性: 表现成年男性形象。
                FE 女性: 表现成年女性形象。
                CH 儿童: 表现婴幼儿或少年形象，如“童子戏莲”。
                EL 老人: 表现长者、老寿星等形象。
                CE 名人: 表现历史人物、神话英雄或知名文人。
                OT 其他人物: 群像或难以区分角色的人物。

                风格代码表
                TR 传统
                MO 现代
                FO 民间
                ET 民族
                GE 几何
                RE 写实
                DE 装饰
                MI 混合
                OT 其他风格

                时期代码表
                XS 先秦
                QG 秦汉
                WS 魏晋
                TG 隋唐
                SG 宋元
                MG 明清
                MJ 民国
                XD 现代
                OT 其他时期

                地区代码表
                CN 中国
                BJ 北京
                TJ 天津
                HB 河北
                SX 山西
                SD 山东
                JS 江苏
                ZJ 浙江
                AH 安徽
                FJ 福建
                GD 广东
                SC 四川
                YN 云南
                OT 其他省份

                输出 JSON 模板（键名必须一致）：
                {
                  "patternName": "",
                  "mainCategory": "",
                  "subCategory": "",
                  "style": "",
                  "region": "",
                  "period": "",
                  "keywords": ["", "", ""]
                }

                请根据上传的图片进行纹样识别，并严格按照上述JSON格式输出。""";
    }

    private String stripMarkdownCodeFence(String text) {
        String normalized = text.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        int firstLineBreak = normalized.indexOf('\n');
        if (firstLineBreak < 0) {
            return normalized;
        }
        int lastFence = normalized.lastIndexOf("```");
        if (lastFence <= firstLineBreak) {
            return normalized.substring(firstLineBreak + 1).trim();
        }
        return normalized.substring(firstLineBreak + 1, lastFence).trim();
    }

    private String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = asText(root.path(fieldName));
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String asText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private String normalizeCodeOrDefault(String value, String defaultValue) {
        String normalized = patternCodeService.normalizeCode(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String resolveSubCategory(String mainCategory, String style, String subCategory) {
        if (!hasSubCategory(mainCategory)) {
            return style;
        }
        return subCategory == null ? "OT" : subCategory;
    }

    private boolean hasSubCategory(String mainCategory) {
        return PatternCodeEnum.CATEGORIES_WITH_SUB.contains(mainCategory);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<KeywordHit> buildKeywordHitsFromDescription(String description) {
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.isEmpty()) {
            return List.of();
        }
        return List.of(new KeywordHit(
                normalizedDescription,
                normalizedDescription.toLowerCase(Locale.ROOT),
                1D));
    }

    private RecognitionResult mapFromKeywords(List<KeywordHit> keywordHits) {
        String mainCategory = "OT";
        String subCategory = "OT";

        if (containsAny(keywordHits, "龙", "dragon")) {
            mainCategory = "AN";
            subCategory = "MY";
        } else if (containsAny(keywordHits, "凤凰", "phoenix")) {
            mainCategory = "AN";
            subCategory = "MY";
        } else if (containsAny(keywordHits, "麒麟", "神兽", "myth", "mythology", "deity")) {
            mainCategory = "MY";
            subCategory = "TR";
        } else if (containsAny(keywordHits, "鸟", "孔雀", "鹰", "鹤", "雀", "鸡", "鸽", "燕", "bird")) {
            mainCategory = "AN";
            subCategory = "BD";
        } else if (containsAny(keywordHits, "鱼", "虾", "蟹", "鲸", "海豚", "鲤", "金鱼", "fish")) {
            mainCategory = "AN";
            subCategory = "FS";
        } else if (containsAny(keywordHits, "昆虫", "蝴蝶", "蝉", "蜜蜂", "蜻蜓", "甲虫", "insect")) {
            mainCategory = "AN";
            subCategory = "IN";
        } else if (containsAny(keywordHits, "蛇", "龟", "蜥蜴", "鳄", "爬行", "reptile")) {
            mainCategory = "AN";
            subCategory = "RP";
        } else if (containsAny(keywordHits, "马", "牛", "羊", "虎", "狮", "猫", "狗", "鹿", "兔", "熊", "象", "猴", "mammal", "animal")) {
            mainCategory = "AN";
            subCategory = "MA";
        } else if (containsAny(keywordHits, "花", "莲", "牡丹", "菊", "梅", "兰", "荷", "flower")) {
            mainCategory = "PL";
            subCategory = "FL";
        } else if (containsAny(keywordHits, "树", "松", "柏", "柳", "竹", "林", "tree", "bamboo")) {
            mainCategory = "PL";
            subCategory = "TR";
        } else if (containsAny(keywordHits, "果", "桃", "石榴", "葡萄", "瓜", "柿", "fruit")) {
            mainCategory = "PL";
            subCategory = "FR";
        } else if (containsAny(keywordHits, "麦", "稻", "谷", "粮", "玉米", "高粱", "grain", "rice", "wheat")) {
            mainCategory = "PL";
            subCategory = "GR";
        } else if (containsAny(keywordHits, "叶", "树叶", "叶片", "leaf")) {
            mainCategory = "PL";
            subCategory = "LV";
        } else if (containsAny(keywordHits, "儿童", "小孩", "孩子", "child", "kid")) {
            mainCategory = "PE";
            subCategory = "CH";
        } else if (containsAny(keywordHits, "老人", "长者", "elder")) {
            mainCategory = "PE";
            subCategory = "EL";
        } else if (containsAny(keywordHits, "女性", "女人", "仕女", "妇女", "female", "woman")) {
            mainCategory = "PE";
            subCategory = "FE";
        } else if (containsAny(keywordHits, "男性", "男人", "武士", "人物", "人像", "male", "man", "person", "human")) {
            mainCategory = "PE";
            subCategory = "MU";
        } else if (containsAny(keywordHits, "风景", "山", "水", "云", "建筑", "亭", "楼", "桥", "landscape")) {
            mainCategory = "LA";
            subCategory = "TR";
        } else if (containsAny(keywordHits, "抽象", "几何", "纹理", "图案", "abstract", "geometric", "pattern")) {
            mainCategory = "AB";
            subCategory = "GE";
        } else if (containsAny(keywordHits, "器物", "器皿", "陶瓷", "青铜", "瓷器", "花瓶", "兵器", "object", "artifact")) {
            mainCategory = "OR";
            subCategory = "DE";
        } else if (containsAny(keywordHits, "符号", "文字", "图腾", "徽章", "标志", "symbol", "totem")) {
            mainCategory = "SY";
            subCategory = "DE";
        } else if (containsAny(keywordHits, "庆典", "节日", "婚礼", "喜庆", "舞龙", "舞狮", "celebration", "festival")) {
            mainCategory = "CE";
            subCategory = "TR";
        } else if (containsAny(keywordHits, "神话", "神仙", "佛", "传说")) {
            mainCategory = "MY";
            subCategory = "TR";
        }

        String style = mapStyleFromKeywords(keywordHits);
        String region = mapRegionFromKeywords(keywordHits);
        String period = mapPeriodFromKeywords(keywordHits);
        String patternName = extractPatternName(keywordHits);
        return new RecognitionResult(
                patternName,
                mainCategory,
                subCategory,
                style,
                region,
                period,
                toKeywordTexts(keywordHits),
                List.of());
    }

    private RecognitionResult attachValidation(RecognitionResult result) {
        try {
            patternCodeService.validateSegments(
                    result.getMainCategory(),
                    result.getSubCategory(),
                    result.getStyle(),
                    result.getRegion(),
                    result.getPeriod());
            return result.withValidationErrors(List.of());
        } catch (IllegalArgumentException e) {
            return result.withValidationErrors(List.of(e.getMessage()));
        }
    }

    private String extractPatternName(List<KeywordHit> keywordHits) {
        StringBuilder normalizedTextBuilder = new StringBuilder();
        for (KeywordHit keywordHit : keywordHits) {
            if (normalizedTextBuilder.length() > 0) {
                normalizedTextBuilder.append(' ');
            }
            normalizedTextBuilder.append(keywordHit.normalizedKeyword());
        }
        String inferredPatternName = inferPatternNameFromNormalizedText(normalizedTextBuilder.toString());
        if (inferredPatternName != null) {
            return inferredPatternName;
        }

        for (KeywordHit keywordHit : keywordHits) {
            if (isPatternNameCandidate(keywordHit.normalizedKeyword())) {
                return normalizePatternName(keywordHit.keyword(), keywordHit.normalizedKeyword());
            }
        }
        return "其他纹样";
    }

    private String inferPatternNameFromNormalizedText(String normalizedText) {
        if (isBlank(normalizedText)) {
            return null;
        }
        if (containsAnyTerm(normalizedText, "龙", "dragon")) {
            return "龙纹";
        }
        if (containsAnyTerm(normalizedText, "凤凰", "phoenix")) {
            return "凤凰纹";
        }
        if (containsAnyTerm(normalizedText, "麒麟")) {
            return "麒麟纹";
        }
        if (containsAnyTerm(normalizedText, "bird", "鸟")) {
            return "鸟纹";
        }
        if (containsAnyTerm(normalizedText, "fish", "鱼")) {
            return "鱼纹";
        }
        if (containsAnyTerm(normalizedText, "flower", "花", "莲", "牡丹", "菊", "梅", "兰", "荷")) {
            return "花卉纹";
        }
        if (containsAnyTerm(normalizedText, "woman", "female", "仕女")) {
            return "仕女纹";
        }
        if (containsAnyTerm(normalizedText, "man", "male", "person", "human", "人物", "人像")) {
            return "人物纹";
        }
        if (containsAnyTerm(normalizedText, "landscape", "风景", "山", "水", "云", "桥", "亭", "楼")) {
            return "山水纹";
        }
        if (containsAnyTerm(normalizedText, "pattern", "geometric", "abstract", "图案", "纹理", "几何")) {
            return "几何纹";
        }
        if (containsAnyTerm(normalizedText, "artifact", "object", "器物", "器皿", "青铜", "瓷器")) {
            return "器物纹";
        }
        if (containsAnyTerm(normalizedText, "symbol", "totem", "图腾", "徽章", "标志")) {
            return "符号纹";
        }
        return null;
    }

    private boolean isPatternNameCandidate(String normalizedKeyword) {
        if (isBlank(normalizedKeyword)) {
            return false;
        }
        if (containsAnyTerm(
                normalizedKeyword,
                "传统", "古典", "古风", "文物", "非遗", "classical", "traditional", "ancient",
                "民间", "民俗", "folk",
                "民族", "ethnic",
                "现代", "当代", "modern", "contemporary",
                "写实", "realistic",
                "装饰", "decorative",
                "中国", "中华", "国风", "china", "chinese",
                "北京", "天津", "河北", "山西", "山东", "江苏", "浙江", "安徽", "福建", "广东", "四川", "云南",
                "先秦", "商周", "战国", "春秋", "秦", "汉", "魏晋", "南北朝", "隋", "唐", "宋", "元", "明", "清", "民国")) {
            return false;
        }
        return true;
    }

    private String normalizePatternName(String rawKeyword, String normalizedKeyword) {
        if (containsAnyTerm(normalizedKeyword, "龙", "dragon")) {
            return "龙纹";
        }
        if (containsAnyTerm(normalizedKeyword, "凤凰", "phoenix")) {
            return "凤凰纹";
        }
        if (containsAnyTerm(normalizedKeyword, "麒麟")) {
            return "麒麟纹";
        }
        if (containsAnyTerm(normalizedKeyword, "bird", "鸟")) {
            return "鸟纹";
        }
        if (containsAnyTerm(normalizedKeyword, "fish", "鱼")) {
            return "鱼纹";
        }
        if (containsAnyTerm(normalizedKeyword, "flower")) {
            return "花卉纹";
        }
        if (containsAnyTerm(normalizedKeyword, "woman", "female", "仕女")) {
            return "仕女纹";
        }
        if (containsAnyTerm(normalizedKeyword, "man", "male", "person", "human", "人物", "人像")) {
            return "人物纹";
        }
        if (containsAnyTerm(normalizedKeyword, "landscape", "山", "水", "云", "桥", "亭", "楼")) {
            return "山水纹";
        }
        if (containsAnyTerm(normalizedKeyword, "pattern", "geometric", "abstract", "图案", "纹理", "几何")) {
            return "几何纹";
        }
        if (containsAnyTerm(normalizedKeyword, "artifact", "object", "器物", "器皿", "青铜", "瓷器")) {
            return "器物纹";
        }
        if (containsAnyTerm(normalizedKeyword, "symbol", "totem", "图腾", "徽章", "标志")) {
            return "符号纹";
        }

        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        if (keyword.isEmpty()) {
            return "其他纹样";
        }
        if (keyword.endsWith("纹") || keyword.endsWith("纹样")) {
            return keyword;
        }
        if (keyword.endsWith("图案")) {
            return keyword;
        }
        return keyword + "纹";
    }

    private List<String> toKeywordTexts(List<KeywordHit> keywordHits) {
        List<String> keywords = new ArrayList<>(keywordHits.size());
        for (KeywordHit keywordHit : keywordHits) {
            keywords.add(keywordHit.keyword());
        }
        return keywords;
    }

    private String mapStyleFromKeywords(List<KeywordHit> keywordHits) {
        if (containsAny(keywordHits, "传统", "古典", "古风", "文物", "非遗", "classical", "traditional", "ancient")) {
            return "TR";
        }
        if (containsAny(keywordHits, "民间", "民俗", "folk")) {
            return "FO";
        }
        if (containsAny(keywordHits, "民族", "ethnic")) {
            return "ET";
        }
        if (containsAny(keywordHits, "几何", "geometry", "geometric")) {
            return "GE";
        }
        if (containsAny(keywordHits, "写实", "realistic")) {
            return "RE";
        }
        if (containsAny(keywordHits, "装饰", "decorative")) {
            return "DE";
        }
        if (containsAny(keywordHits, "现代", "当代", "modern", "contemporary")) {
            return "MO";
        }
        return "OT";
    }

    private String mapRegionFromKeywords(List<KeywordHit> keywordHits) {
        if (containsAny(keywordHits, "北京", "京", "故宫")) {
            return "BJ";
        }
        if (containsAny(keywordHits, "天津")) {
            return "TJ";
        }
        if (containsAny(keywordHits, "河北")) {
            return "HB";
        }
        if (containsAny(keywordHits, "山西")) {
            return "SX";
        }
        if (containsAny(keywordHits, "山东")) {
            return "SD";
        }
        if (containsAny(keywordHits, "江苏")) {
            return "JS";
        }
        if (containsAny(keywordHits, "浙江")) {
            return "ZJ";
        }
        if (containsAny(keywordHits, "安徽")) {
            return "AH";
        }
        if (containsAny(keywordHits, "福建")) {
            return "FJ";
        }
        if (containsAny(keywordHits, "广东")) {
            return "GD";
        }
        if (containsAny(keywordHits, "四川")) {
            return "SC";
        }
        if (containsAny(keywordHits, "云南")) {
            return "YN";
        }
        if (containsAny(keywordHits, "中国", "中华", "国风", "china", "chinese")) {
            return "CN";
        }
        return "OT";
    }

    private String mapPeriodFromKeywords(List<KeywordHit> keywordHits) {
        if (containsAny(keywordHits, "先秦", "商周", "战国", "春秋")) {
            return "XS";
        }
        if (containsAny(keywordHits, "秦", "汉")) {
            return "QG";
        }
        if (containsAny(keywordHits, "魏晋", "南北朝")) {
            return "WS";
        }
        if (containsAny(keywordHits, "隋", "唐")) {
            return "TG";
        }
        if (containsAny(keywordHits, "宋", "元")) {
            return "SG";
        }
        if (containsAny(keywordHits, "明", "清")) {
            return "MG";
        }
        if (containsAny(keywordHits, "民国")) {
            return "MJ";
        }
        if (containsAny(keywordHits, "现代", "当代", "modern", "contemporary")) {
            return "XD";
        }
        return "OT";
    }

    private boolean containsAny(List<KeywordHit> keywordHits, String... terms) {
        for (KeywordHit keywordHit : keywordHits) {
            if (containsAnyTerm(keywordHit.normalizedKeyword(), terms)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyTerm(String keyword, String... terms) {
        for (String term : terms) {
            if (keyword.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FieldSpan(String key, int start, int end) {
    }

    private record KeywordHit(String keyword, String normalizedKeyword, double score) {
    }

    public static class RecognitionResult {
        private final String patternName;
        private final String mainCategory;
        private final String subCategory;
        private final String style;
        private final String region;
        private final String period;
        private final List<String> keywords;
        private final List<String> validationErrors;

        public RecognitionResult(String mainCategory, String subCategory, String style, String region, String period) {
            this(null, mainCategory, subCategory, style, region, period, List.of(), List.of());
        }

        public RecognitionResult(
                String patternName,
                String mainCategory,
                String subCategory,
                String style,
                String region,
                String period,
                List<String> keywords,
                List<String> validationErrors) {
            this.patternName = patternName;
            this.mainCategory = mainCategory;
            this.subCategory = subCategory;
            this.style = style;
            this.region = region;
            this.period = period;
            this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
            this.validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        }

        public RecognitionResult withValidationErrors(List<String> validationErrors) {
            return new RecognitionResult(
                    patternName,
                    mainCategory,
                    subCategory,
                    style,
                    region,
                    period,
                    keywords,
                    validationErrors);
        }

        public String getPatternName() {
            return patternName;
        }

        public String getMainCategory() {
            return mainCategory;
        }

        public String getSubCategory() {
            return subCategory;
        }

        public String getStyle() {
            return style;
        }

        public String getRegion() {
            return region;
        }

        public String getPeriod() {
            return period;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public List<String> getValidationErrors() {
            return validationErrors;
        }

        public boolean isValid() {
            return validationErrors.isEmpty();
        }
    }
}
