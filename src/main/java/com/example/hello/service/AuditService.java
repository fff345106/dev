package com.example.hello.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.AuditRequest;
import com.example.hello.dto.BatchAuditRequest;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.entity.PatternPending;
import com.example.hello.entity.User;
import com.example.hello.enums.AuditStatus;
import com.example.hello.enums.ImageSourceType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserRepository;

@Service
public class AuditService {
    private final PatternPendingRepository pendingRepository;
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final PatternHashService patternHashService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final PatternCodeService patternCodeService;

    public AuditService(PatternPendingRepository pendingRepository,
                        PatternRepository patternRepository,
                        UserRepository userRepository,
                        ImageService imageService,
                        PatternHashService patternHashService,
                        BlockchainAnchorService blockchainAnchorService,
                        PatternCodeService patternCodeService) {
        this.pendingRepository = pendingRepository;
        this.patternRepository = patternRepository;
        this.userRepository = userRepository;
        this.imageService = imageService;
        this.patternHashService = patternHashService;
        this.blockchainAnchorService = blockchainAnchorService;
        this.patternCodeService = patternCodeService;
    }

    /**
     * 提交纹样待审核（提交时生成编码）
     */
    @Transactional
    public PatternPending submit(PatternRequest request, Long submitterId) {
        patternCodeService.validateRequest(request);
        PatternCodeService.NormalizedPatternCodes normalizedCodes = patternCodeService.normalizeRequest(request);

        User submitter = userRepository.findById(submitterId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        PatternPending pending = new PatternPending();
        pending.setDescription(request.getDescription());
        pending.setMainCategory(normalizedCodes.mainCategory());
        pending.setSubCategory(normalizedCodes.subCategory());
        pending.setStyle(normalizedCodes.style());
        pending.setRegion(normalizedCodes.region());
        pending.setPeriod(normalizedCodes.period());
        pending.setImageUrl(request.getImageUrl());
        pending.setImageSourceType(imageService.normalizeImageSourceTypeValue(request.getImageSourceType(), request.getImageUrl()));
        pending.setStoryText(request.getStoryText());
        pending.setStoryImageUrl(request.getStoryImageUrl());
        pending.setSubmitter(submitter);
        pending.setStatus(AuditStatus.PENDING);

        patternCodeService.assignPendingCode(pending);
        return pendingRepository.save(pending);
    }

    /**
     * 审核纹样
     */
    @Transactional
    public Object audit(Long pendingId, AuditRequest request, Long auditorId) {
        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证审核权限
        if (auditor.getRole() != UserRole.SUPER_ADMIN && auditor.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("无审核权限");
        }

        PatternPending pending = pendingRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("待审核记录不存在"));

        if (pending.getStatus() != AuditStatus.PENDING) {
            throw new RuntimeException("该记录已审核");
        }

        pending.setAuditor(auditor);
        pending.setAuditTime(LocalDateTime.now());

        if (request.getApproved()) {
            // 审核通过，移入正式表
            pending.setStatus(AuditStatus.APPROVED);
            pendingRepository.save(pending);
            return moveToPattern(pending);
        } else {
            // 审核拒绝
            if (request.getRejectReason() == null || request.getRejectReason().isEmpty()) {
                throw new IllegalArgumentException("拒绝时必须填写原因");
            }
            pending.setStatus(AuditStatus.REJECTED);
            pending.setRejectReason(request.getRejectReason());
            
            // 仅删除临时上传来源图片
            if (pending.getImageUrl() != null && !pending.getImageUrl().isEmpty()
                    && imageService.shouldDeleteTempImage(pending.getImageUrl(), pending.getImageSourceType())) {
                try {
                    imageService.deleteTempImage(pending.getImageUrl());
                    pending.setImageUrl(null);
                } catch (IOException e) {
                    // 忽略删除失败
                }
            }
            
            return pendingRepository.save(pending);
        }
    }

    /**
     * 批量审核
     */
    @Transactional
    public void batchAudit(BatchAuditRequest request, Long auditorId) {
        AuditRequest singleRequest = new AuditRequest();
        singleRequest.setApproved(request.getApproved());
        singleRequest.setRejectReason(request.getRejectReason());

        for (Long id : request.getIds()) {
            audit(id, singleRequest, auditorId);
        }
    }

    /**
     * 重新提交被拒绝的纹样
     */
    public PatternPending resubmit(Long pendingId, PatternRequest request, Long submitterId) {
        PatternPending pending = pendingRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("记录不存在"));

        if (pending.getStatus() != AuditStatus.REJECTED) {
            throw new RuntimeException("只有被拒绝的记录才能重新提交");
        }

        if (!pending.getSubmitter().getId().equals(submitterId)) {
            throw new RuntimeException("只能重新提交自己的记录");
        }

        patternCodeService.validateRequest(request);
        PatternCodeService.NormalizedPatternCodes normalizedCodes = patternCodeService.normalizeRequest(request);

        pending.setDescription(request.getDescription());
        pending.setMainCategory(normalizedCodes.mainCategory());
        pending.setSubCategory(normalizedCodes.subCategory());
        pending.setStyle(normalizedCodes.style());
        pending.setRegion(normalizedCodes.region());
        pending.setPeriod(normalizedCodes.period());
        pending.setImageUrl(request.getImageUrl());
        pending.setImageSourceType(imageService.normalizeImageSourceTypeValue(request.getImageSourceType(), request.getImageUrl()));
        pending.setStoryText(request.getStoryText());
        pending.setStoryImageUrl(request.getStoryImageUrl());
        pending.setStatus(AuditStatus.PENDING);
        pending.setAuditor(null);
        pending.setAuditTime(null);
        pending.setRejectReason(null);

        patternCodeService.ensurePendingCode(pending);
        return pendingRepository.save(pending);
    }

    /**
     * 获取待审核列表
     */
    public List<PatternPending> findPending() {
        return pendingRepository.findByStatus(AuditStatus.PENDING);
    }

    public Page<PatternPending> findPending(Pageable pageable) {
        return pendingRepository.findByStatus(AuditStatus.PENDING, pageable);
    }

    /**
     * 获取所有待审核记录
     */
    public List<PatternPending> findAll() {
        return pendingRepository.findAll();
    }

    public Page<PatternPending> findAll(Pageable pageable) {
        return pendingRepository.findAll(pageable);
    }

    /**
     * 根据状态查询
     */
    public List<PatternPending> findByStatus(AuditStatus status) {
        return pendingRepository.findByStatus(status);
    }

    public Page<PatternPending> findByStatus(AuditStatus status, Pageable pageable) {
        return pendingRepository.findByStatus(status, pageable);
    }

    /**
     * 查询提交人的记录
     */
    public List<PatternPending> findBySubmitter(Long submitterId) {
        return pendingRepository.findBySubmitterId(submitterId);
    }

    public Page<PatternPending> findBySubmitter(Long submitterId, Pageable pageable) {
        return pendingRepository.findBySubmitterId(submitterId, pageable);
    }

    /**
     * 查询当前用户最近录入的记录（最多100条）
     */
    public List<PatternPending> findRecentBySubmitter(Long submitterId) {
        return pendingRepository.findBySubmitterIdOrderByCreatedAtDesc(
                submitterId, 
                org.springframework.data.domain.PageRequest.of(0, 100)
        );
    }

    /**
     * 根据ID查询
     */
    public PatternPending findById(Long id) {
        return pendingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("记录不存在"));
    }

    /**
     * 删除待审核记录
     */
    public void delete(Long id, Long userId) {
        PatternPending pending = findById(id);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 只有提交人或管理员可以删除
        boolean isSubmitter = pending.getSubmitter().getId().equals(userId);
        boolean isAdmin = user.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.ADMIN;
        
        if (!isSubmitter && !isAdmin) {
            throw new RuntimeException("无删除权限");
        }

        // 普通用户只能删除 PENDING 或 REJECTED 状态的记录
        // 已通过的记录是审计历史，普通用户不可删
        if (!isAdmin) {
            if (pending.getStatus() == AuditStatus.APPROVED) {
                throw new RuntimeException("无法删除已通过审核的记录");
            }
        }

        // 仅删除临时上传来源图片
        if (pending.getImageUrl() != null && !pending.getImageUrl().isEmpty()
                && imageService.shouldDeleteTempImage(pending.getImageUrl(), pending.getImageSourceType())) {
            try {
                imageService.deleteTempImage(pending.getImageUrl());
            } catch (IOException e) {
                // 忽略
            }
        }

        pendingRepository.deleteById(id);
    }

    /**
     * 批量删除待审核记录
     */
    @Transactional
    public void batchDelete(List<Long> ids, Long userId) {
        for (Long id : ids) {
            try {
                delete(id, userId);
            } catch (Exception e) {
                // 忽略单个删除失败，继续删除下一个
                System.err.println("Failed to delete audit record " + id + ": " + e.getMessage());
            }
        }
    }

    /**
     * 确保待审核记录有完整的编码（处理旧数据丢失编码的情况）
     */
    private void ensureCodes(PatternPending pending) {
        patternCodeService.ensurePendingCode(pending);
        pendingRepository.save(pending);
    }

    /**
     * 将审核通过的记录移入正式表（直接使用已生成的编码）
     */
    private Pattern moveToPattern(PatternPending pending) {
        // 确保编码存在
        ensureCodes(pending);

        // 安全检查：如果编码仍然缺失，抛出异常而不是让SQL报错
        if (pending.getDateCode() == null || pending.getDateCode().isEmpty()) {
            throw new RuntimeException("无法生成纹样编码: dateCode 为空 (ID: " + pending.getId() + ")");
        }
        if (pending.getImageUrl() == null || pending.getImageUrl().isEmpty()) {
            throw new RuntimeException("审核通过失败：纹样图片不能为空");
        }

        Pattern pattern = new Pattern();
        pattern.setDescription(pending.getDescription());
        pattern.setMainCategory(pending.getMainCategory());
        pattern.setSubCategory(pending.getSubCategory());
        pattern.setStyle(pending.getStyle());
        pattern.setRegion(pending.getRegion());
        pattern.setPeriod(pending.getPeriod());
        pattern.setImageUrl(pending.getImageUrl());
        pattern.setImageSourceType(imageService.normalizeImageSourceTypeValue(pending.getImageSourceType(), pending.getImageUrl()));
        pattern.setStoryText(pending.getStoryText());
        pattern.setStoryImageUrl(pending.getStoryImageUrl());

        // 直接使用待审核记录中已生成的编码
        pattern.setDateCode(pending.getDateCode());
        pattern.setSequenceNumber(pending.getSequenceNumber());
        pattern.setPatternCode(pending.getPatternCode());

        // 按来源处理图片
        try {
            ImageSourceType sourceType = imageService.resolveImageSourceType(pending.getImageSourceType(), pattern.getImageUrl());
            String newUrl;
            switch (sourceType) {
                case TEMP_UPLOAD -> newUrl = imageService.moveToFormal(pattern.getImageUrl(), pattern.getPatternCode());
                case LIBRARY -> newUrl = imageService.copyToFormalWithoutDeletingSource(pattern.getImageUrl(), pattern.getPatternCode());
                case EXTERNAL -> newUrl = imageService.fetchExternalToFormal(pattern.getImageUrl(), pattern.getPatternCode());
                default -> throw new IllegalStateException("不支持的图片来源类型: " + sourceType);
            }
            pattern.setImageUrl(newUrl);
            pattern.setImageSourceType(sourceType.name());
            // 同步更新 pending 记录
            pending.setImageUrl(newUrl);
            pending.setImageSourceType(sourceType.name());
            pendingRepository.save(pending);
        } catch (IOException e) {
            throw new RuntimeException("审核通过失败：处理正式图片失败: " + e.getMessage(), e);
        }

        // 1) 计算哈希并先落库
        String imageHash = patternHashService.computeSha256ByImageUrl(pattern.getImageUrl());
        pattern.setImageHash(imageHash);
        pattern.setHashAlgorithm(patternHashService.hashAlgorithm());
        pattern.setChainStatus("PENDING");
        pattern = patternRepository.save(pattern);

        // 2) 上链并回填凭证（失败不影响正式入库）
        if (!blockchainAnchorService.isEnabled()) {
            pattern.setChainStatus("SKIPPED");
            return patternRepository.save(pattern);
        }

        try {
            BlockchainAnchorService.AnchorResult anchorResult = blockchainAnchorService.anchor(
                    pattern.getPatternCode(),
                    pattern.getImageHash(),
                    pattern.getImageUrl());

            pattern.setChainTxHash(anchorResult.txHash());
            pattern.setChainBlockNumber(anchorResult.blockNumber());
            pattern.setChainTimestamp(anchorResult.blockTimestamp());
            pattern.setChainStatus(anchorResult.status());
        } catch (Exception e) {
            pattern.setChainStatus("FAILED");
            System.err.println("区块链存证失败，已保留正式入库: " + e.getMessage());
        }

        return patternRepository.save(pattern);
    }

}
