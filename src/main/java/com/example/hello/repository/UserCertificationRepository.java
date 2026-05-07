package com.example.hello.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;

public interface UserCertificationRepository extends JpaRepository<UserCertification, Long> {
    Optional<UserCertification> findByUserAndCertificationType(User user, CertificationType certificationType);
    List<UserCertification> findByStatus(CertificationStatus status);
    List<UserCertification> findByUser(User user);
    boolean existsByUserAndCertificationTypeAndStatus(User user, CertificationType certificationType, CertificationStatus status);
}
