package com.example.hello.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.CertificationService;
import com.example.hello.service.ImageService;
import com.example.hello.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CertificationControllerTest {

    @Mock
    private CertificationService certificationService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CertificationController(certificationService, jwtUtil, userRepository, imageService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void submitRealNameAuth_returnsSuccess() throws Exception {
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);

        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.PENDING);

        RealNameAuthRequest authRequest = new RealNameAuthRequest();
        authRequest.setRealName("张三");
        authRequest.setIdCardNumber("110101199001011234");
        authRequest.setIdCardFrontUrl("https://s3.example.com/temp/front.jpg");
        authRequest.setIdCardBackUrl("https://s3.example.com/temp/back.jpg");

        String jsonBody = """
                {
                    "realName": "张三",
                    "idCardNumber": "110101199001011234",
                    "idCardFrontUrl": "https://s3.example.com/temp/front.jpg",
                    "idCardBackUrl": "https://s3.example.com/temp/back.jpg"
                }
                """;

        when(jwtUtil.extractUserId("valid-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(objectMapper.readValue(jsonBody, RealNameAuthRequest.class)).thenReturn(authRequest);
        when(imageService.moveToIdentity(any(), anyLong(), any()))
                .thenReturn("https://s3.example.com/Identity/1/idCardFront.jpg");
        when(certificationService.submitRealNameAuth(eq(user), any(), any(), any(), any())).thenReturn(cert);

        mockMvc.perform(post("/api/certifications/real-name")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.certificationType").value("REAL_NAME"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(certificationService).submitRealNameAuth(eq(user), eq("张三"), eq("110101199001011234"), any(), any());
    }

    @Test
    void submitRealNameAuth_returns401WhenNoToken() throws Exception {
        mockMvc.perform(post("/api/certifications/real-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "realName": "张三",
                                    "idCardNumber": "110101199001011234",
                                    "idCardFrontUrl": "https://s3.example.com/temp/front.jpg",
                                    "idCardBackUrl": "https://s3.example.com/temp/back.jpg"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未提供认证令牌"));
    }

    @Test
    void getMyCertifications_returnsList() throws Exception {
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);

        UserCertification cert1 = new UserCertification(user, CertificationType.REAL_NAME);
        cert1.setId(1L);
        cert1.setStatus(CertificationStatus.APPROVED);

        UserCertification cert2 = new UserCertification(user, CertificationType.ENTERPRISE);
        cert2.setId(2L);
        cert2.setStatus(CertificationStatus.PENDING);

        when(jwtUtil.extractUserId("valid-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(certificationService.getMyCertifications(user)).thenReturn(List.of(cert1, cert2));

        mockMvc.perform(get("/api/certifications/my")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].certificationType").value("REAL_NAME"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].certificationType").value("ENTERPRISE"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));

        verify(certificationService).getMyCertifications(user);
    }

    @Test
    void getPendingCertifications_returns403ForNonAdmin() throws Exception {
        Long userId = 1L;
        User user = new User("testuser", "password", UserRole.USER);
        user.setId(userId);

        when(jwtUtil.extractUserId("valid-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/certifications/pending")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("权限不足"));
    }

    @Test
    void approveCertification_returnsSuccessForAdmin() throws Exception {
        Long adminId = 1L;
        User admin = new User("admin", "password", UserRole.SUPER_ADMIN);
        admin.setId(adminId);

        User user = new User("testuser", "password", UserRole.USER);
        user.setId(2L);

        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setId(10L);
        cert.setStatus(CertificationStatus.APPROVED);

        when(jwtUtil.extractUserId("admin-token")).thenReturn(adminId);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(certificationService.approveCertification(10L, adminId)).thenReturn(cert);

        mockMvc.perform(post("/api/certifications/10/approve")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("认证审核通过"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(certificationService).approveCertification(10L, adminId);
    }

    @Test
    void rejectCertification_returnsSuccessForAdmin() throws Exception {
        Long adminId = 1L;
        User admin = new User("admin", "password", UserRole.SUPER_ADMIN);
        admin.setId(adminId);

        User user = new User("testuser", "password", UserRole.USER);
        user.setId(2L);

        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setId(10L);
        cert.setStatus(CertificationStatus.REJECTED);
        cert.setRejectReason("材料不清晰");

        when(jwtUtil.extractUserId("admin-token")).thenReturn(adminId);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(certificationService.rejectCertification(10L, adminId, "材料不清晰")).thenReturn(cert);

        mockMvc.perform(post("/api/certifications/10/reject")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rejectReason": "材料不清晰"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("认证已拒绝"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(certificationService).rejectCertification(10L, adminId, "材料不清晰");
    }
}
