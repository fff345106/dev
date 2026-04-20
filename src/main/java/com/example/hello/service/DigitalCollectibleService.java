package com.example.hello.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.hello.dto.DigitalCollectibleCreateRequest;
import com.example.hello.entity.DigitalCollectible;
import com.example.hello.entity.User;
import com.example.hello.enums.CollectibleEntryMode;
import com.example.hello.enums.ImageSourceType;
import com.example.hello.repository.DigitalCollectibleRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class DigitalCollectibleService {

    private final DigitalCollectibleRepository digitalCollectibleRepository;
    private final UserRepository userRepository;
    private final PatternRepository patternRepository;
    private final ImageService imageService;

    public DigitalCollectibleService(
            DigitalCollectibleRepository digitalCollectibleRepository,
            UserRepository userRepository,
            PatternRepository patternRepository,
            ImageService imageService) {
        this.digitalCollectibleRepository = digitalCollectibleRepository;
        this.userRepository = userRepository;
        this.patternRepository = patternRepository;
        this.imageService = imageService;
    }

    @Transactional
    public DigitalCollectible create(DigitalCollectibleCreateRequest request, Long userId) {
        User createdBy = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String patternImageUrl = safeTrim(request.getPatternImageUrl());
        if (isBlank(patternImageUrl)) {
            throw new IllegalArgumentException("纹样图片不能为空");
        }

        DigitalCollectible collectible = new DigitalCollectible();
        collectible.setEntryMode(CollectibleEntryMode.LIBRARY.name());
        collectible.setPatternImageUrl(patternImageUrl);
        collectible.setCreatedBy(createdBy);

        validateLibraryMode(request, patternImageUrl, collectible);

        return digitalCollectibleRepository.save(collectible);
    }

    public Page<DigitalCollectible> findMy(Long userId, Pageable pageable) {
        return digitalCollectibleRepository.findByCreatedByIdOrderByCreatedAtDesc(userId, pageable);
    }

    public DigitalCollectible findMyById(Long id, Long userId) {
        DigitalCollectible collectible = digitalCollectibleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数字藏品不存在"));
        if (collectible.getCreatedBy() == null || !collectible.getCreatedBy().getId().equals(userId)) {
            throw new RuntimeException("无权查看该数字藏品");
        }
        return collectible;
    }

    @Transactional
    public DigitalCollectible updateVisibility(Long id, Long userId, Boolean visible) {
        DigitalCollectible collectible = findMyById(id, userId);
        collectible.setIsVisible(Boolean.TRUE.equals(visible));
        return digitalCollectibleRepository.save(collectible);
    }

    private void validateLibraryMode(
            DigitalCollectibleCreateRequest request,
            String patternImageUrl,
            DigitalCollectible collectible) {
        String sourceType = safeTrim(request.getPatternImageSourceType());
        if (!isBlank(sourceType) && !ImageSourceType.LIBRARY.name().equalsIgnoreCase(sourceType)) {
            throw new IllegalArgumentException("LIBRARY模式下纹样图片来源必须为LIBRARY");
        }

        imageService.validateLibraryUrl(patternImageUrl);

        Long sourcePatternId = request.getSourcePatternId();
        if (sourcePatternId == null) {
            throw new IllegalArgumentException("LIBRARY模式下来源纹样ID不能为空");
        }
        if (!patternRepository.existsById(sourcePatternId)) {
            throw new IllegalArgumentException("来源纹样不存在");
        }

        collectible.setPatternImageSourceType(ImageSourceType.LIBRARY.name());
        collectible.setSourcePatternId(sourcePatternId);
        collectible.setDescription(safeTrim(request.getDescription()));
        collectible.setStoryText(safeTrim(request.getStoryText()));
        collectible.setStoryFileUrl(safeTrim(request.getStoryFileUrl()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
