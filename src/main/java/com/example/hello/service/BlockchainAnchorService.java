package com.example.hello.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

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

@Service
public class BlockchainAnchorService {
    private final BlockchainProperties properties;

    public BlockchainAnchorService(BlockchainProperties properties) {
        this.properties = properties;
    }

    public AnchorResult anchor(String patternCode, String imageHash, String imageUrl) {
        validateConfig();

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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record AnchorResult(String txHash, Long blockNumber, LocalDateTime blockTimestamp, String status) {
    }
}
