package com.example.hello.config;

import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {
    private boolean enabled;

    /**
     * 上链实现类型：ZXCHAIN_OPEN 或 EVM
     */
    private String provider = "ZXCHAIN_OPEN";

    // ====== 至信链开放联盟链 API 配置 ======
    private String apiBaseUrl = "https://open.zxchain.qq.com";
    private String secretId;
    private String secretKey;
    private int apiTimeoutMillis = 15000;

    /**
     * 存证业务签名使用的私钥（用于 SM3withSM2 签名）
     */
    private String evidencePrivateKey;

    /**
     * 存证业务公钥（可选；为空时从私钥自动推导）
     */
    private String evidencePublicKey;

    // ====== 兼容原 EVM 方式配置 ======
    private String rpcUrl;
    private Long chainId;
    private String privateKey;
    private String toAddress;
    private BigInteger gasLimit = BigInteger.valueOf(210000L);
    private BigInteger gasPriceWei = BigInteger.valueOf(2_000_000_000L);
    private int receiptTimeoutSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getApiTimeoutMillis() {
        return apiTimeoutMillis;
    }

    public void setApiTimeoutMillis(int apiTimeoutMillis) {
        this.apiTimeoutMillis = apiTimeoutMillis;
    }

    public String getEvidencePrivateKey() {
        return evidencePrivateKey;
    }

    public void setEvidencePrivateKey(String evidencePrivateKey) {
        this.evidencePrivateKey = evidencePrivateKey;
    }

    public String getEvidencePublicKey() {
        return evidencePublicKey;
    }

    public void setEvidencePublicKey(String evidencePublicKey) {
        this.evidencePublicKey = evidencePublicKey;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
    }

    public BigInteger getGasPriceWei() {
        return gasPriceWei;
    }

    public void setGasPriceWei(BigInteger gasPriceWei) {
        this.gasPriceWei = gasPriceWei;
    }

    public int getReceiptTimeoutSeconds() {
        return receiptTimeoutSeconds;
    }

    public void setReceiptTimeoutSeconds(int receiptTimeoutSeconds) {
        this.receiptTimeoutSeconds = receiptTimeoutSeconds;
    }
}
