package com.example.hello.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.AuditRequest;
import com.example.hello.dto.BatchAuditRequest;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.entity.PatternPending;
import com.example.hello.entity.User;
import com.example.hello.enums.AuditStatus;
import com.example.hello.enums.PatternCodeEnum;
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

    public AuditService(PatternPendingRepository pendingRepository,
                        PatternRepository patternRepository,
                        UserRepository userRepository,
                        ImageService imageService) {
        this.pendingRepository = pendingRepository;
        this.patternRepository = patternRepository;
        this.userRepository = userRepository;
        this.imageService = imageService;
    }

    /**
     * 提交纹样待审核（提交时生成编码）
     */
    @Transactional
    public PatternPending submit(PatternRequest request, Long submitterId) {
        // 验证代码
        validateCodes(request);

        User submitter = userRepository.findById(submitterId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        PatternPending pending = new PatternPending();
        pending.setDescription(request.getDescription());
        pending.setMainCategory(request.getMainCategory().toUpperCase());
        pending.setSubCategory(request.getSubCategory().toUpperCase());
        pending.setStyle(request.getStyle().toUpperCase());
        pending.setRegion(request.getRegion().toUpperCase());
        pending.setPeriod(request.getPeriod().toUpperCase());
        pending.setImageUrl(request.getImageUrl());
        pending.setSubmitter(submitter);
        pending.setStatus(AuditStatus.PENDING);

        // 生成编码（优先回收被驳回的编码）
        String dateCode = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        pending.setDateCode(dateCode);

        // 尝试回收被驳回的编码
        List<PatternPending> recyclable = pendingRepository.findRecyclableCodes(dateCode);
        if (!recyclable.isEmpty()) {
            // 回收第一个被驳回的编码
            PatternPending recycled = recyclable.get(0);
            pending.setSequenceNumber(recycled.getSequenceNumber());
            // 清空被回收记录的编码
            recycled.setPatternCode(null);
            recycled.setSequenceNumber(null);
            pendingRepository.save(recycled);
        } else {
            // 没有可回收的，生成新序列号
            Integer maxSeq = pendingRepository.findMaxActiveSequenceNumberByDateCode(dateCode);
            // 同时检查正式表的最大序列号
            Integer maxPatternSeq = patternRepository.findMaxSequenceNumberByDateCode(dateCode);
            int nextSeq = Math.max(
                maxSeq == null ? 0 : maxSeq,
                maxPatternSeq == null ? 0 : maxPatternSeq
            ) + 1;
            pending.setSequenceNumber(nextSeq);
        }

        // 生成完整编码
        pending.setPatternCode(generatePatternCode(pending));

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
            
            // 删除临时图片
            if (pending.getImageUrl() != null && !pending.getImageUrl().isEmpty()) {
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

        // 验证代码
        validateCodes(request);

        pending.setDescription(request.getDescription());
        pending.setMainCategory(request.getMainCategory().toUpperCase());
        pending.setSubCategory(request.getSubCategory().toUpperCase());
        pending.setStyle(request.getStyle().toUpperCase());
        pending.setRegion(request.getRegion().toUpperCase());
        pending.setPeriod(request.getPeriod().toUpperCase());
        pending.setImageUrl(request.getImageUrl());
        pending.setStatus(AuditStatus.PENDING);
        pending.setAuditor(null);
        pending.setAuditTime(null);
        pending.setRejectReason(null);

        return pendingRepository.save(pending);
    }

    /**
     * 获取待审核列表
     */
    public List<PatternPending> findPending() {
        return pendingRepository.findByStatus(AuditStatus.PENDING);
    }

    /**
     * 获取所有待审核记录
     */
    public List<PatternPending> findAll() {
        return pendingRepository.findAll();
    }

    /**
     * 根据状态查询
     */
    public List<PatternPending> findByStatus(AuditStatus status) {
        return pendingRepository.findByStatus(status);
    }

    /**
     * 查询提交人的记录
     */
    public List<PatternPending> findBySubmitter(Long submitterId) {
        return pendingRepository.findBySubmitterId(submitterId);
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

        // 删除关联的临时图片
        if (pending.getImageUrl() != null && !pending.getImageUrl().isEmpty()) {
            try {
                imageService.deleteTempImage(pending.getImageUrl());
            } catch (IOException e) {
                // 忽略
            }
        }

        pendingRepository.deleteById(id);
    }

    /**
     * 将审核通过的记录移入正式表（直接使用已生成的编码）
     */
    private Pattern moveToPattern(PatternPending pending) {
        Pattern pattern = new Pattern();
        pattern.setDescription(pending.getDescription());
        pattern.setMainCategory(pending.getMainCategory());
        pattern.setSubCategory(pending.getSubCategory());
        pattern.setStyle(pending.getStyle());
        pattern.setRegion(pending.getRegion());
        pattern.setPeriod(pending.getPeriod());
        pattern.setImageUrl(pending.getImageUrl());

        // 直接使用待审核记录中已生成的编码
        pattern.setDateCode(pending.getDateCode());
        pattern.setSequenceNumber(pending.getSequenceNumber());
        pattern.setPatternCode(pending.getPatternCode());

        // 将图片从临时目录移动到正式目录
        if (pattern.getImageUrl() != null && !pattern.getImageUrl().isEmpty()) {
            try {
                String newUrl = imageService.moveToFormal(pattern.getImageUrl(), pattern.getPatternCode());
                pattern.setImageUrl(newUrl);
                // 同步更新 pending 记录的图片URL
                pending.setImageUrl(newUrl);
                pendingRepository.save(pending);
            } catch (IOException e) {
                // 忽略
            }
        }

        return patternRepository.save(pattern);
    }

    private String generatePatternCode(PatternPending pending) {
        return String.format("%s-%s-%s-%s-%s-%s-%03d",
                pending.getMainCategory(),
                pending.getSubCategory(),
                pending.getStyle(),
                pending.getRegion(),
                pending.getPeriod(),
                pending.getDateCode(),
                pending.getSequenceNumber());
    }

    private void validateCodes(PatternRequest request) {
        String mainCategory = request.getMainCategory().toUpperCase();
        String subCategory = request.getSubCategory().toUpperCase();
        String style = request.getStyle().toUpperCase();
        String region = request.getRegion().toUpperCase();
        String period = request.getPeriod().toUpperCase();

        if (!PatternCodeEnum.MainCategory.isValid(mainCategory)) {
            throw new IllegalArgumentException("无效的主类别代码: " + mainCategory);
        }
        if (!PatternCodeEnum.isValidSubCategory(mainCategory, subCategory)) {
            throw new IllegalArgumentException("无效的子类别代码: " + subCategory);
        }
        if (!PatternCodeEnum.Style.isValid(style)) {
            throw new IllegalArgumentException("无效的风格代码: " + style);
        }
        if (!PatternCodeEnum.Region.isValid(region)) {
            throw new IllegalArgumentException("无效的地区代码: " + region);
        }
        if (!PatternCodeEnum.Period.isValid(period)) {
            throw new IllegalArgumentException("无效的时期代码: " + period);
        }
    }
}
