package com.example.hello.service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Security;
import java.security.Signature;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 纯 Java 密码学服务，替代本地 Go SDK 签名服务。
 * <p>
 * 实现三个原 Go SDK HTTP 接口的功能：
 * <ul>
 *   <li>/priKey2PubKey  → {@link #derivePublicKey}</li>
 *   <li>/signByPriKey   → {@link #signData}</li>
 *   <li>/generateApiSign → {@link #generateApiSignature}</li>
 * </ul>
 * <p>
 * 使用 SM2 国密曲线（sm2p256v1）+ SM3withSM2 签名算法。
 */
@Service
public class ZxchainCryptoService {

    private static final String SM2_CURVE_NAME = "sm2p256v1";
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String SIGNATURE_ALGORITHM = "SM3withSM2";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @PostConstruct
    public void init() {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 从 SM2 私钥（PKCS#8 Base64）推导公钥（未压缩格式 Base64）。
     * <p>
     * 替代原 Go SDK /priKey2PubKey 接口。
     *
     * @param privateKeyBase64 PKCS#8 格式的私钥 Base64 字符串
     * @return 未压缩格式公钥（0x04 + X + Y，共 65 字节）的 Base64 字符串
     */
    public String derivePublicKey(String privateKeyBase64) {
        try {
            ECNamedCurveParameterSpec namedSpec = ECNamedCurveTable.getParameterSpec(SM2_CURVE_NAME);
            ECDomainParameters domainParams = new ECDomainParameters(
                    namedSpec.getCurve(), namedSpec.getG(), namedSpec.getN(), namedSpec.getH());

            BigInteger privateKeyValue = parsePkcs8PrivateKey(privateKeyBase64);
            ECPoint publicKeyPoint = namedSpec.getG().multiply(privateKeyValue).normalize();

            if (publicKeyPoint.isInfinity()) {
                throw new IllegalStateException("SM2 公钥推导结果为无穷远点");
            }

            ECPublicKeyParameters pubKeyParams = new ECPublicKeyParameters(publicKeyPoint, domainParams);
            byte[] uncompressed = pubKeyParams.getQ().getEncoded(false);
            return Base64.getEncoder().encodeToString(uncompressed);
        } catch (Exception e) {
            throw new RuntimeException("SM2 公钥推导失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 SM2 私钥对数据进行 SM3withSM2 签名。
     * <p>
     * 替代原 Go SDK /signByPriKey 接口。
     *
     * @param privateKeyBase64 PKCS#8 格式的私钥 Base64 字符串
     * @param data             待签名的数据
     * @return DER 编码签名的 Base64 字符串
     */
    public String signData(String privateKeyBase64, String data) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("EC", PROVIDER_NAME);
            BCECPrivateKey privateKey = (BCECPrivateKey) kf.generatePrivate(keySpec);

            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER_NAME);
            signer.initSign(privateKey);
            signer.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("SM2 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成至信链 API 鉴权签名。
     * <p>
     * 替代原 Go SDK /generateApiSign 接口。
     * 签名算法：HMAC-SHA256(secretKey, secretId + timestamp + nonce)
     *
     * @param secretId  API 鉴权 Secret-Id
     * @param secretKey API 鉴权 Secret-Key
     * @return ApiSignatureResult 包含 signature、signatureTime、nonce
     */
    public ApiSignatureResult generateApiSignature(String secretId, String secretKey) {
        try {
            long timestampSeconds = System.currentTimeMillis() / 1000;
            int nonce = (int) (System.currentTimeMillis() * 1000 + (Math.random() * 1000));

            String stringToSign = secretId + timestampSeconds + nonce;
            String signature = hmacSha256Hex(secretKey, stringToSign);

            return new ApiSignatureResult(signature, String.valueOf(timestampSeconds), nonce);
        } catch (Exception e) {
            throw new RuntimeException("API 签名生成失败: " + e.getMessage(), e);
        }
    }

    // ───────────────────── 内部实现 ─────────────────────

    /**
     * HMAC-SHA256 签名并返回小写十六进制字符串。
     */
    private String hmacSha256Hex(String key, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] result = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(result);
    }

    /**
     * 从 PKCS#8 格式 Base64 私钥中解析出 SM2 私钥的整数值。
     * <p>
     * PKCS#8 结构：SEQUENCE { INTEGER(0), AlgorithmIdentifier, OCTET_STRING(PrivateKeyInfo) }
     * PrivateKeyInfo 内部：SEQUENCE { INTEGER(1), OCTET_STRING(私钥字节) }
     */
    private BigInteger parsePkcs8PrivateKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            ASN1Sequence pkcs8Seq = ASN1Sequence.getInstance(keyBytes);
            // 第三个元素是私钥 OCTET STRING
            org.bouncycastle.asn1.ASN1OctetString privateKeyInfo =
                    (org.bouncycastle.asn1.ASN1OctetString) pkcs8Seq.getObjectAt(2);
            // 解析内部 SEQUENCE，第二个元素是私钥整数
            ASN1Sequence innerSeq = ASN1Sequence.getInstance(privateKeyInfo.getOctets());
            ASN1Integer privateKeyInt = (ASN1Integer) innerSeq.getObjectAt(1);
            return privateKeyInt.getValue();
        } catch (Exception e) {
            throw new RuntimeException("PKCS#8 私钥解析失败: " + e.getMessage(), e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * API 签名结果。
     *
     * @param signature     HMAC-SHA256 签名（十六进制）
     * @param signatureTime 签名时间戳（秒）
     * @param nonce         随机数
     */
    public record ApiSignatureResult(String signature, String signatureTime, int nonce) {
    }
}
