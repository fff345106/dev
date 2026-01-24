package com.example.hello.service;

import com.example.hello.dto.DraftRequest;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternDraft;
import com.example.hello.entity.PatternPending;
import com.example.hello.entity.User;
import com.example.hello.repository.PatternDraftRepository;
import com.example.hello.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
public class DraftService {
    private static final int MAX_DRAFTS_PER_USER = 10;

    private final PatternDraftRepository draftRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ImageService imageService;

    public DraftService(PatternDraftRepository draftRepository,
                        UserRepository userRepository,
                        AuditService auditService,
                        ImageService imageService) {
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.imageService = imageService;
    }

    /**
     * 保存草稿
     */
    public PatternDraft save(DraftRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查草稿数量限制
        long count = draftRepository.countByUserId(userId);
        if (count >= MAX_DRAFTS_PER_USER) {
            throw new RuntimeException("草稿数量已达上限（最多" + MAX_DRAFTS_PER_USER + "条）");
        }

        PatternDraft draft = new PatternDraft();
        setDraftFields(draft, request);
        draft.setUser(user);

        return draftRepository.save(draft);
    }

    /**
     * 更新草稿
     */
    public PatternDraft update(Long draftId, DraftRequest request, Long userId) {
        PatternDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new RuntimeException("草稿不存在"));

        if (!draft.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权修改此草稿");
        }

        // 如果图片URL变了，删除旧图片
        if (draft.getImageUrl() != null && !draft.getImageUrl().equals(request.getImageUrl())) {
            try {
                imageService.deleteTempImage(draft.getImageUrl());
            } catch (IOException e) {
                // 忽略
            }
        }

        setDraftFields(draft, request);
        return draftRepository.save(draft);
    }

    /**
     * 获取用户的草稿列表
     */
    public List<PatternDraft> findByUser(Long userId) {
        return draftRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 获取单个草稿
     */
    public PatternDraft findById(Long draftId, Long userId) {
        PatternDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new RuntimeException("草稿不存在"));

        if (!draft.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权查看此草稿");
        }

        return draft;
    }

    /**
     * 删除草稿
     */
    public void delete(Long draftId, Long userId) {
        PatternDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new RuntimeException("草稿不存在"));

        if (!draft.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权删除此草稿");
        }

        // 删除关联的临时图片
        if (draft.getImageUrl() != null) {
            try {
                imageService.deleteTempImage(draft.getImageUrl());
            } catch (IOException e) {
                // 忽略
            }
        }

        draftRepository.deleteById(draftId);
    }

    /**
     * 提交草稿到审核
     */
    @Transactional
    public PatternPending submitToAudit(Long draftId, Long userId) {
        PatternDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new RuntimeException("草稿不存在"));

        if (!draft.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权提交此草稿");
        }

        // 验证必填字段
        validateDraft(draft);

        // 转换为 PatternRequest
        PatternRequest request = new PatternRequest();
        request.setDescription(draft.getDescription());
        request.setMainCategory(draft.getMainCategory());
        request.setSubCategory(draft.getSubCategory());
        request.setStyle(draft.getStyle());
        request.setRegion(draft.getRegion());
        request.setPeriod(draft.getPeriod());
        request.setImageUrl(draft.getImageUrl());

        // 提交审核
        PatternPending pending = auditService.submit(request, userId);

        // 删除草稿（不删除图片，因为图片已转移到待审核记录）
        draftRepository.deleteById(draftId);

        return pending;
    }

    private void setDraftFields(PatternDraft draft, DraftRequest request) {
        draft.setDescription(request.getDescription());
        if (request.getMainCategory() != null) {
            draft.setMainCategory(request.getMainCategory().toUpperCase());
        }
        if (request.getSubCategory() != null) {
            draft.setSubCategory(request.getSubCategory().toUpperCase());
        }
        if (request.getStyle() != null) {
            draft.setStyle(request.getStyle().toUpperCase());
        }
        if (request.getRegion() != null) {
            draft.setRegion(request.getRegion().toUpperCase());
        }
        if (request.getPeriod() != null) {
            draft.setPeriod(request.getPeriod().toUpperCase());
        }
        draft.setImageUrl(request.getImageUrl());
    }

    private void validateDraft(PatternDraft draft) {
        if (draft.getMainCategory() == null || draft.getMainCategory().isEmpty()) {
            throw new IllegalArgumentException("主类别不能为空");
        }
        if (draft.getSubCategory() == null || draft.getSubCategory().isEmpty()) {
            throw new IllegalArgumentException("子类别不能为空");
        }
        if (draft.getStyle() == null || draft.getStyle().isEmpty()) {
            throw new IllegalArgumentException("风格不能为空");
        }
        if (draft.getRegion() == null || draft.getRegion().isEmpty()) {
            throw new IllegalArgumentException("地区不能为空");
        }
        if (draft.getPeriod() == null || draft.getPeriod().isEmpty()) {
            throw new IllegalArgumentException("时期不能为空");
        }
    }
}
