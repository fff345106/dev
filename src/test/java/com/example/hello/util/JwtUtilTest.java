package com.example.hello.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET = "mySecretKeyForJWTTokenGenerationMustBeLongEnough256Bits";
    private static final long EXPIRATION = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION);
    }

    // ==================== Token 生成测试 ====================
    @Nested
    @DisplayName("generateToken - Token 生成")
    class GenerateTokenTests {

        @Test
        @DisplayName("应生成非空的 JWT Token")
        void shouldGenerateNonEmptyToken() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            assertNotNull(token);
            assertFalse(token.isEmpty());
            // JWT 格式: header.payload.signature (3 个由 . 分隔的部分)
            assertEquals(3, token.split("\\.").length, "JWT 应包含 3 个由点分隔的部分");
        }

        @Test
        @DisplayName("不同的用户名应生成不同的 Token")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            String token1 = jwtUtil.generateToken(1L, "alice", "USER");
            String token2 = jwtUtil.generateToken(2L, "bob", "ADMIN");

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("相同的参数应生成不同的 Token（因为 issuedAt 时间不同）")
        void shouldGenerateDifferentTokensEvenWithSameArgs() throws InterruptedException {
            String token1 = jwtUtil.generateToken(1L, "alice", "USER");
            Thread.sleep(1100); // 确保时间戳不同（秒级精度）
            String token2 = jwtUtil.generateToken(1L, "alice", "USER");

            assertNotEquals(token1, token2);
        }
    }

    // ==================== Claims 提取测试 ====================
    @Nested
    @DisplayName("extract* - Claims 提取")
    class ExtractClaimsTests {

        @Test
        @DisplayName("应正确提取用户名")
        void shouldExtractUsername() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            assertEquals("alice", jwtUtil.extractUsername(token));
        }

        @Test
        @DisplayName("应正确提取 userId")
        void shouldExtractUserId() {
            String token = jwtUtil.generateToken(42L, "alice", "USER");

            assertEquals(42L, jwtUtil.extractUserId(token));
        }

        @Test
        @DisplayName("应正确提取 role")
        void shouldExtractRole() {
            String token = jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN");

            assertEquals("SUPER_ADMIN", jwtUtil.extractRole(token));
        }

        @Test
        @DisplayName("应正确提取所有 Claims")
        void shouldExtractAllClaimsFromSingleToken() {
            String token = jwtUtil.generateToken(99L, "master", "MASTER_ARTISAN");

            assertAll(
                    () -> assertEquals("master", jwtUtil.extractUsername(token)),
                    () -> assertEquals(99L, jwtUtil.extractUserId(token)),
                    () -> assertEquals("MASTER_ARTISAN", jwtUtil.extractRole(token))
            );
        }

        @Test
        @DisplayName("GUEST 角色的 Token 应正确提取 userId=-1")
        void shouldExtractGuestTokenClaims() {
            String token = jwtUtil.generateToken(-1L, "guest_12345678", "GUEST");

            assertAll(
                    () -> assertEquals("guest_12345678", jwtUtil.extractUsername(token)),
                    () -> assertEquals(-1L, jwtUtil.extractUserId(token)),
                    () -> assertEquals("GUEST", jwtUtil.extractRole(token))
            );
        }
    }

    // ==================== Token 验证测试 ====================
    @Nested
    @DisplayName("validateToken - Token 验证")
    class ValidateTokenTests {

        @Test
        @DisplayName("有效的 Token 与匹配的用户名应验证通过")
        void shouldValidateTokenWithMatchingUsername() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            assertTrue(jwtUtil.validateToken(token, "alice"));
        }

        @Test
        @DisplayName("有效的 Token 与不匹配的用户名应验证失败")
        void shouldRejectTokenWithMismatchedUsername() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            assertFalse(jwtUtil.validateToken(token, "bob"));
        }

        @Test
        @DisplayName("已过期的 Token 应抛出 ExpiredJwtException")
        void shouldRejectExpiredToken() {
            // 设置一个很短的过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", 1L); // 1 毫秒
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            // 等待 Token 过期
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            // validateToken 内部调用 getClaims() 时会抛出 ExpiredJwtException
            assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                    () -> jwtUtil.validateToken(token, "alice"));
        }

        @Test
        @DisplayName("篡改的 Token 应抛出异常")
        void shouldThrowOnTamperedToken() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");
            String tamperedToken = token + "tampered";

            assertThrows(Exception.class, () -> jwtUtil.validateToken(tamperedToken, "alice"));
        }

        @Test
        @DisplayName("空 Token 应抛出异常")
        void shouldThrowOnEmptyToken() {
            assertThrows(Exception.class, () -> jwtUtil.validateToken("", "alice"));
        }

        @Test
        @DisplayName("null Token 应抛出异常")
        void shouldThrowOnNullToken() {
            assertThrows(Exception.class, () -> jwtUtil.validateToken(null, "alice"));
        }
    }

    // ==================== 边界情况测试 ====================
    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("使用错误的密钥解析 Token 应抛出异常")
        void shouldThrowWhenSecretKeyMismatch() {
            String token = jwtUtil.generateToken(1L, "alice", "USER");

            // 创建一个使用不同密钥的 JwtUtil
            JwtUtil otherJwtUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherJwtUtil, "secret", "differentSecretKeyMustBeLongEnoughFor256Bits!!");
            ReflectionTestUtils.setField(otherJwtUtil, "expiration", EXPIRATION);

            assertThrows(Exception.class, () -> otherJwtUtil.extractUsername(token));
        }

        @Test
        @DisplayName("Token 中的 userId 应保持 Long 类型精度")
        void shouldPreserveLongPrecision() {
            long largeId = Long.MAX_VALUE;
            String token = jwtUtil.generateToken(largeId, "alice", "USER");

            assertEquals(largeId, jwtUtil.extractUserId(token));
        }

        @Test
        @DisplayName("包含特殊字符的用户名应正确编码")
        void shouldHandleSpecialCharactersInUsername() {
            String token = jwtUtil.generateToken(1L, "guest_a1b2c3d4", "GUEST");

            assertEquals("guest_a1b2c3d4", jwtUtil.extractUsername(token));
        }
    }
}
