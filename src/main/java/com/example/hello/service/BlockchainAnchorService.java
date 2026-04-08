package com.example.hello.service;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

import com.example.hello.config.BlockchainProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BlockchainAnchorService {
    private static final String PROVIDER_ZXCHAIN_OPEN = "ZXCHAIN_OPEN";
    private static final String PROVIDER_EVM = "EVM";

    private final BlockchainProperties properties;
    private final ObjectMapper objectMapper;

    public BlockchainAnchorService(BlockchainProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AnchorResult anchor(String patternCode, String imageHash, String imageUrl) {
        validateConfig();

        String provider = normalizeProvider(properties.getProvider());
        if (PROVIDER_EVM.equals(provider)) {
            return anchorByEvm(patternCode, imageHash, imageUrl);
        }
        return anchorByZxchainOpen(patternCode, imageHash, imageUrl);
    }

    private AnchorResult anchorByZxchainOpen(String patternCode, String imageHash, String imageUrl) {
        HttpClient httpClient = buildHttpClient();

        try {
            String extendInfo = buildExtendInfo(patternCode, imageUrl);
            String publicKey = resolveEvidencePublicKey(httpClient);
            String dataToSign = publicKey + "_" + imageHash + "_" + extendInfo;
            String bizSign = signByPriKey(httpClient, properties.getEvidencePrivateKey(), dataToSign);

            ApiSignData signData = generateApiSignViaSdk(httpClient, properties.getSecretId(), properties.getSecretKey());

            String requestBody = objectMapper.createObjectNode()
                    .put("publicKey", publicKey)
                    .put("hash", imageHash)
                    .put("sign", bizSign)
                    .put("extendInfo", extendInfo)
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getApiBaseUrl()) + "/api/v1/ev/save"))
                    .timeout(Duration.ofMillis(properties.getApiTimeoutMillis()))
                    .header("Content-Type", "application/json;charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Secret-Id", properties.getSecretId())
                    .header("Signature-Time", signData.signatureTime())
                    .header("Signature", signData.signature())
                    .header("Nonce", String.valueOf(signData.nonce()))
                    .header("Cloud-trace-id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("至信链存证接口HTTP失败: status=" + response.statusCode() + ", body=" + abbreviate(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            int retCode = root.path("retCode").asInt(-1);
            String retMsg = root.path("retMsg").asText("UNKNOWN");
            if (retCode != 0) {
                throw new RuntimeException("至信链存证失败: retCode=" + retCode + ", retMsg=" + retMsg);
            }

            JsonNode data = root.path("data");
            String txId = textOrNull(data.path("txId"));
            Long blockHeight = parseLongSafely(textOrNull(data.path("blockHeight")));
            LocalDateTime txTime = parseTxTime(textOrNull(data.path("txTime")));

            return new AnchorResult(txId, blockHeight, txTime, "ANCHORED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("至信链存证请求被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("至信链存证调用失败: " + e.getMessage(), e);
        }
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getApiTimeoutMillis()))
                .build();
    }

    private ApiSignData generateApiSignViaSdk(HttpClient httpClient, String secretId, String secretKey)
            throws IOException, InterruptedException {
        JsonNode req = objectMapper.createObjectNode()
                .put("secretId", secretId)
                .put("secretKey", secretKey);
        JsonNode root = postJson(httpClient, properties.getSdkBaseUrl(), "/generateApiSign", req.toString());

        String err = textOrNull(root.path("err"));
        if (!isBlank(err)) {
            throw new RuntimeException("本地SDK generateApiSign失败: " + err);
        }

        JsonNode signData = root.path("signData");
        String signature = textOrNull(signData.path("signature"));
        String signatureTime = textOrNull(signData.path("signatureTime"));
        Integer nonce = intOrNull(signData.path("nonce"));

        if (isBlank(signature) || isBlank(signatureTime) || nonce == null) {
            throw new RuntimeException("本地SDK generateApiSign返回不完整");
        }
        return new ApiSignData(signature, signatureTime, nonce);
    }

    private String resolveEvidencePublicKey(HttpClient httpClient) throws IOException, InterruptedException {
        if (!isBlank(properties.getEvidencePublicKey())) {
            return properties.getEvidencePublicKey().trim();
        }

        JsonNode req = objectMapper.createObjectNode()
                .put("pri", properties.getEvidencePrivateKey());
        JsonNode root = postJson(httpClient, properties.getSdkBaseUrl(), "/priKey2PubKey", req.toString());

        String err = textOrNull(root.path("err"));
        if (!isBlank(err)) {
            throw new RuntimeException("本地SDK priKey2PubKey失败: " + err);
        }

        String pub = textOrNull(root.path("pub"));
        if (isBlank(pub)) {
            throw new RuntimeException("本地SDK priKey2PubKey返回公钥为空");
        }
        return pub;
    }

    private String signByPriKey(HttpClient httpClient, String priKey, String data)
            throws IOException, InterruptedException {
        JsonNode req = objectMapper.createObjectNode()
                .put("priKey", priKey)
                .put("data", data);
        JsonNode root = postJson(httpClient, properties.getSdkBaseUrl(), "/signByPriKey", req.toString());

        String err = textOrNull(root.path("err"));
        if (!isBlank(err)) {
            throw new RuntimeException("本地SDK signByPriKey失败: " + err);
        }

        String signedData = textOrNull(root.path("signedData"));
        if (isBlank(signedData)) {
            throw new RuntimeException("本地SDK signByPriKey返回签名为空");
        }
        return signedData;
    }

    private JsonNode postJson(HttpClient httpClient, String baseUrl, String path, String json)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(baseUrl) + path))
                .timeout(Duration.ofMillis(properties.getApiTimeoutMillis()))
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("调用本地SDK失败: path=" + path + ", status=" + response.statusCode() + ", body="
                    + abbreviate(response.body()));
        }
        return objectMapper.readTree(response.body());
    }

    private String buildExtendInfo(String patternCode, String imageUrl) {
        String safeCode = patternCode == null ? "" : patternCode;
        String safeUrl = imageUrl == null ? "" : imageUrl;
        String info = "patternCode=" + safeCode + ";imageUrl=" + safeUrl;
        if (info.length() <= 1024) {
            return info;
        }
        return info.substring(0, 1024);
    }

    private AnchorResult anchorByEvm(String patternCode, String imageHash, String imageUrl) {
        Web3j web3j = Web3j.build(new HttpService(properties.getRpcUrl()));
        try {
            Credentials credentials = Credentials.create(properties.getPrivateKey());
            TransactionManager txManager = new RawTransactionManager(web3j, credentials, properties.getChainId());

            String payload = String.format(
                    "patternCode=%s;imageHash=%s;imageUrl=%s;timestamp=%d",
                    patternCode,
                    imageHash,
                    imageUrl,
                    System.currentTimeMillis());

            String data = Numeric.toHexString(payload.getBytes(StandardCharsets.UTF_8));
            EthSendTransaction sent = txManager.sendTransaction(
                    properties.getGasPriceWei(),
                    properties.getGasLimit(),
                    properties.getToAddress(),
                    data,
                    BigInteger.ZERO);

            if (sent.hasError()) {
                throw new RuntimeException("上链交易发送失败: " + sent.getError().getMessage());
            }

            String txHash = sent.getTransactionHash();
            TransactionReceipt receipt = waitForReceipt(web3j, txHash);

            if (!"0x1".equals(receipt.getStatus())) {
                throw new RuntimeException("上链交易执行失败，txHash=" + txHash + ", status=" + receipt.getStatus());
            }

            Long blockNumber = receipt.getBlockNumber() == null ? null : receipt.getBlockNumber().longValue();
            LocalDateTime blockTime = queryBlockTime(web3j, receipt.getBlockNumber());

            return new AnchorResult(txHash, blockNumber, blockTime, "ANCHORED");
        } catch (Exception e) {
            throw new RuntimeException("上链存证失败: " + e.getMessage(), e);
        } finally {
            web3j.shutdown();
        }
    }

    private TransactionReceipt waitForReceipt(Web3j web3j, String txHash) throws Exception {
        int timeoutSeconds = properties.getReceiptTimeoutSeconds();
        for (int i = 0; i < timeoutSeconds; i++) {
            EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(txHash).send();
            Optional<TransactionReceipt> receiptOpt = receiptResp.getTransactionReceipt();
            if (receiptOpt.isPresent()) {
                return receiptOpt.get();
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("等待交易回执超时，txHash=" + txHash);
    }

    private LocalDateTime queryBlockTime(Web3j web3j, BigInteger blockNumber) throws Exception {
        if (blockNumber == null) {
            return null;
        }
        EthBlock blockResp = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send();
        if (blockResp.getBlock() == null || blockResp.getBlock().getTimestamp() == null) {
            return null;
        }
        long ts = blockResp.getBlock().getTimestamp().longValue();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private void validateConfig() {
        if (!properties.isEnabled()) {
            throw new RuntimeException("区块链存证未启用，请配置 blockchain.enabled=true");
        }

        String provider = normalizeProvider(properties.getProvider());
        if (PROVIDER_EVM.equals(provider)) {
            validateEvmConfig();
            return;
        }
        validateZxchainConfig();
    }

    private void validateZxchainConfig() {
        if (isBlank(properties.getApiBaseUrl())) {
            throw new RuntimeException("缺少区块链配置: blockchain.api-base-url");
        }
        if (isBlank(properties.getSdkBaseUrl())) {
            throw new RuntimeException("缺少区块链配置: blockchain.sdk-base-url");
        }
        if (isBlank(properties.getSecretId())) {
            throw new RuntimeException("缺少区块链配置: blockchain.secret-id");
        }
        if (isBlank(properties.getSecretKey())) {
            throw new RuntimeException("缺少区块链配置: blockchain.secret-key");
        }
        if (isBlank(properties.getEvidencePrivateKey())) {
            throw new RuntimeException("缺少区块链配置: blockchain.evidence-private-key");
        }
    }

    private void validateEvmConfig() {
        if (isBlank(properties.getRpcUrl())) {
            throw new RuntimeException("缺少区块链配置: blockchain.rpc-url");
        }
        if (properties.getChainId() == null) {
            throw new RuntimeException("缺少区块链配置: blockchain.chain-id");
        }
        if (isBlank(properties.getPrivateKey())) {
            throw new RuntimeException("缺少区块链配置: blockchain.private-key");
        }
        if (isBlank(properties.getToAddress())) {
            throw new RuntimeException("缺少区块链配置: blockchain.to-address");
        }
    }

    private LocalDateTime parseTxTime(String txTimeRaw) {
        if (isBlank(txTimeRaw)) {
            return null;
        }

        String value = txTimeRaw.trim();
        if (value.matches("^\\d{13}$")) {
            long millis = Long.parseLong(value);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        }
        if (value.matches("^\\d{10}$")) {
            long seconds = Long.parseLong(value);
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Long parseLongSafely(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        String text = textOrNull(node);
        if (isBlank(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return isBlank(text) ? null : text.trim();
    }

    private String normalizeProvider(String provider) {
        if (isBlank(provider)) {
            return PROVIDER_ZXCHAIN_OPEN;
        }
        return provider.trim().toUpperCase();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 500) {
            return body;
        }
        return body.substring(0, 500) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ApiSignData(String signature, String signatureTime, int nonce) {
    }

    public record AnchorResult(String txHash, Long blockNumber, LocalDateTime blockTimestamp, String status) {
    }
}
