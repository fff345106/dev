package com.example.hello.config;

import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {
    private boolean enabled;
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
