package com.example.hello.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.CollaborationCreateRequest;
import com.example.hello.dto.CollaborationResponse;
import com.example.hello.dto.CollaborationUpdateRequest;
import com.example.hello.entity.Collaboration;
import com.example.hello.entity.User;
import com.example.hello.enums.CollaborationStatus;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.CollaborationRepository;
import com.example.hello.repository.UserRepository;

@Service
public class CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final UserRepository userRepository;

    public CollaborationService(CollaborationRepository collaborationRepository, UserRepository userRepository) {
        this.collaborationRepository = collaborationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 查询合作列表
     * - ENTERPRISE_USER：只看自己发起的合作
     * - MASTER_ARTISAN：只看自己收到的合作
     * - ADMIN/SUPER_ADMIN：按状态过滤或查看全部
     */
    public Page<CollaborationResponse> list(CollaborationStatus status, Long userId, String role, Pageable pageable) {
        Page<Collaboration> page;

        if (UserRole.ENTERPRISE_USER.name().equals(role)) {
            page = collaborationRepository.findByEnterpriseIdOrderByCreatedAtDesc(userId, pageable);
        } else if (UserRole.MASTER_ARTISAN.name().equals(role)) {
            page = collaborationRepository.findByMasterIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            if (status != null) {
                page = collaborationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } else {
                page = collaborationRepository.findAll(pageable);
            }
        }

        return page.map(CollaborationResponse::fromEntity);
    }

    /**
     * 创建合作申请
     */
    @Transactional
    public CollaborationResponse create(CollaborationCreateRequest request, Long userId) {
        User enterprise = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (enterprise.getRole() != UserRole.ENTERPRISE_USER) {
            throw new RuntimeException("只有企商用户才能发起合作申请");
        }

        User master = userRepository.findById(request.getMasterId())
                .orElseThrow(() -> new RuntimeException("大师用户不存在"));

        if (master.getRole() != UserRole.MASTER_ARTISAN) {
            throw new RuntimeException("目标用户不是技艺大师");
        }

        if (userId.equals(request.getMasterId())) {
            throw new RuntimeException("不能向自己发起合作申请");
        }

        boolean exists = collaborationRepository.existsByEnterpriseIdAndMasterIdAndStatus(
                userId, request.getMasterId(), CollaborationStatus.PENDING);
        if (exists) {
            throw new RuntimeException("已存在待处理的合作申请，请勿重复提交");
        }

        Collaboration collaboration = new Collaboration();
        collaboration.setEnterprise(enterprise);
        collaboration.setMaster(master);
        collaboration.setStatus(CollaborationStatus.PENDING);
        collaboration.setMessage(request.getMessage());

        collaboration = collaborationRepository.save(collaboration);
        return CollaborationResponse.fromEntity(collaboration);
    }

    /**
     * 更新合作状态（状态机校验）
     * - PENDING -> ACCEPTED/REJECTED：仅 master 可操作
     * - ACCEPTED -> COMPLETED：仅 enterprise 可操作
     */
    @Transactional
    public CollaborationResponse updateStatus(Long id, CollaborationUpdateRequest request, Long userId) {
        Collaboration collaboration = collaborationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("合作记录不存在"));

        CollaborationStatus currentStatus = collaboration.getStatus();
        CollaborationStatus targetStatus = request.getStatus();

        if (currentStatus == CollaborationStatus.PENDING) {
            if (targetStatus != CollaborationStatus.ACCEPTED && targetStatus != CollaborationStatus.REJECTED) {
                throw new RuntimeException("待处理状态只能变更为已接受或已拒绝");
            }
            if (!userId.equals(collaboration.getMaster().getId())) {
                throw new RuntimeException("只有大师才能接受或拒绝合作申请");
            }
        } else if (currentStatus == CollaborationStatus.ACCEPTED) {
            if (targetStatus != CollaborationStatus.COMPLETED) {
                throw new RuntimeException("已接受状态只能变更为已完成");
            }
            if (!userId.equals(collaboration.getEnterprise().getId())) {
                throw new RuntimeException("只有发起合作的企商用户才能标记为已完成");
            }
        } else {
            throw new RuntimeException("当前状态不允许变更");
        }

        collaboration.setStatus(targetStatus);
        collaboration.setReply(request.getReply());

        collaboration = collaborationRepository.save(collaboration);
        return CollaborationResponse.fromEntity(collaboration);
    }
}
