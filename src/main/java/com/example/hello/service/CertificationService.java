package com.example.hello.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.repository.UserCertificationRepository;
import com.example.hello.repository.UserRepository;

@Service
public class CertificationService {

    private final UserCertificationRepository certificationRepository;
    private final UserRepository userRepository;

    public CertificationService(UserCertificationRepository certificationRepository,
                                UserRepository userRepository) {
        this.certificationRepository = certificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 提交实名认证
     */
    @Transactional
    public UserCertification submitRealNameAuth(User user, RealNameAuthRequest request) {
        checkNotAlreadyApproved(user, CertificationType.REAL_NAME, "实名认证");

        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setRealName(request.getRealName());
        cert.setIdCardNumber(request.getIdCardNumber());
        cert.setIdCardFrontUrl(request.getIdCardFrontUrl());
        cert.setIdCardBackUrl(request.getIdCardBackUrl());
        return certificationRepository.save(cert);
    }

    /**
     * 提交企业认证
     */
    @Transactional
    public UserCertification submitEnterpriseAuth(User user, EnterpriseAuthRequest request) {
        checkNotAlreadyApproved(user, CertificationType.ENTERPRISE, "企业认证");

        // 非法人代表需提供授权委托书
        if (!Boolean.TRUE.equals(request.getIsLegalRepresentative())
                && (request.getAuthorizationLetterUrl() == null
                    || request.getAuthorizationLetterUrl().isBlank())) {
            throw new RuntimeException("非法人代表需提供授权委托书");
        }

        UserCertification cert = new UserCertification(user, CertificationType.ENTERPRISE);
        cert.setBusinessLicenseUrl(request.getBusinessLicenseUrl());
        cert.setAuthorizationLetterUrl(request.getAuthorizationLetterUrl());
        cert.setLegalRepresentativeName(request.getLegalRepresentativeName());
        cert.setIsLegalRepresentative(request.getIsLegalRepresentative());
        return certificationRepository.save(cert);
    }

    /**
     * 提交技艺认证
     */
    @Transactional
    public UserCertification submitMasterAuth(User user, MasterAuthRequest request) {
        checkNotAlreadyApproved(user, CertificationType.MASTER, "技艺认证");

        // 至少需要提供证书照片或代表作品之一
        if ((request.getCertificationUrl() == null || request.getCertificationUrl().isBlank())
                && (request.getRepresentativeWorkUrl() == null
                    || request.getRepresentativeWorkUrl().isBlank())) {
            throw new RuntimeException("技艺认证至少需要提供证书照片或代表作品之一");
        }

        UserCertification cert = new UserCertification(user, CertificationType.MASTER);
        cert.setCertificationUrl(request.getCertificationUrl());
        cert.setRepresentativeWorkUrl(request.getRepresentativeWorkUrl());
        return certificationRepository.save(cert);
    }

    /**
     * 审核通过认证
     */
    @Transactional
    public UserCertification approveCertification(Long certificationId, Long auditorId) {
        UserCertification cert = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new RuntimeException("认证记录不存在"));

        if (cert.getStatus() != CertificationStatus.PENDING) {
            throw new RuntimeException("该认证已处理，无法重复操作");
        }

        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new RuntimeException("审核人不存在"));

        cert.setStatus(CertificationStatus.APPROVED);
        cert.setAuditorId(auditorId);
        cert.setAuditTime(LocalDateTime.now());

        // 实名认证通过时设置 realNameVerified
        if (cert.getCertificationType() == CertificationType.REAL_NAME) {
            cert.setRealNameVerified(true);
        }

        return certificationRepository.save(cert);
    }

    /**
     * 审核拒绝认证
     */
    @Transactional
    public UserCertification rejectCertification(Long certificationId, Long auditorId,
                                                  String rejectReason) {
        UserCertification cert = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new RuntimeException("认证记录不存在"));

        if (cert.getStatus() != CertificationStatus.PENDING) {
            throw new RuntimeException("该认证已处理，无法重复操作");
        }

        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new RuntimeException("审核人不存在"));

        cert.setStatus(CertificationStatus.REJECTED);
        cert.setAuditorId(auditorId);
        cert.setAuditTime(LocalDateTime.now());
        cert.setRejectReason(rejectReason);

        return certificationRepository.save(cert);
    }

    /**
     * 获取用户的所有认证记录
     */
    public List<UserCertification> getMyCertifications(User user) {
        return certificationRepository.findByUser(user);
    }

    /**
     * 获取所有待审核的认证记录
     */
    public List<UserCertification> getPendingCertifications() {
        return certificationRepository.findByStatus(CertificationStatus.PENDING);
    }

    /**
     * 检查用户是否已通过某类认证，若已通过则抛出异常
     */
    private void checkNotAlreadyApproved(User user, CertificationType type, String certName) {
        certificationRepository.findByUserAndCertificationType(user, type)
                .ifPresent(existing -> {
                    if (existing.getStatus() == CertificationStatus.APPROVED) {
                        throw new RuntimeException("该用户已通过" + certName + "，无需重复提交");
                    }
                });
    }
}
