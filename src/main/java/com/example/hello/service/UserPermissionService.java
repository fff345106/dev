package com.example.hello.service;

import org.springframework.stereotype.Service;

import com.example.hello.entity.User;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;

@Service
public class UserPermissionService {

    private final UserCertificationRepository certificationRepository;

    public UserPermissionService(UserCertificationRepository certificationRepository) {
        this.certificationRepository = certificationRepository;
    }

    public boolean canSubmitPattern(User user) {
        if (user.getRole() == UserRole.GUEST) {
            return false;
        }
        if (user.getRole() == UserRole.SUPER_ADMIN
                || user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.USER) {
            return true;
        }

        if (!isRealNameVerified(user)) {
            return false;
        }

        if (user.getRole() == UserRole.ENTERPRISE_USER) {
            return isRoleCertificationApproved(user, CertificationType.ENTERPRISE);
        }
        if (user.getRole() == UserRole.MASTER_ARTISAN) {
            return isRoleCertificationApproved(user, CertificationType.MASTER);
        }

        return true;
    }

    public boolean isRealNameVerified(User user) {
        return certificationRepository
                .findByUserAndCertificationType(user, CertificationType.REAL_NAME)
                .map(c -> c.getStatus() == CertificationStatus.APPROVED)
                .orElse(false);
    }

    public boolean isRoleCertificationApproved(User user, CertificationType type) {
        return certificationRepository
                .findByUserAndCertificationType(user, type)
                .map(c -> c.getStatus() == CertificationStatus.APPROVED)
                .orElse(false);
    }
}
