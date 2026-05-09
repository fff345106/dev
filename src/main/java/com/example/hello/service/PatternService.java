package com.example.hello.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import com.example.hello.dto.PatternRequest;
import com.example.hello.dto.WatermarkResult;
import com.example.hello.entity.Pattern;
import com.example.hello.enums.ImageSourceType;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class PatternService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final PatternRepository patternRepository;
    private final PatternPendingRepository patternPendingRepository;
    private final ImageService imageService;
    private final PatternCodeService patternCodeService;
    private final DwtSvdWatermarkService dwtSvdWatermarkService;
    private final WatermarkStorageService watermarkStorageService;
    private final RedisCacheService redisCacheService;
    private final String publicBaseUrl;

    @Autowired
    public PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService,
            RedisCacheService redisCacheService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this(patternRepository, patternPendingRepository, imageService, patternCodeService, redisCacheService, publicBaseUrl, new DwtSvdWatermarkService(), null);
    }

    PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService,
            RedisCacheService redisCacheService,
            String publicBaseUrl,
            DwtSvdWatermarkService dwtSvdWatermarkService,
            WatermarkStorageService watermarkStorageService) {
        this.patternRepository = patternRepository;
        this.patternPendingRepository = patternPendingRepository;
        this.imageService = imageService;
        this.patternCodeService = patternCodeService;
        this.publicBaseUrl = publicBaseUrl;
        this.dwtSvdWatermarkService = dwtSvdWatermarkService;
        this.watermarkStorageService = watermarkStorageService;
        this.redisCacheService = redisCacheService;
    }

    public Pattern create(PatternRequest request) {
        patternCodeService.validateRequest(request);
        PatternCodeService.NormalizedPatternCodes normalizedCodes = patternCodeService.normalizeRequest(request);

        Pattern pattern = new Pattern();
        pattern.setDescription(request.getDescription());
        pattern.setMainCategory(normalizedCodes.mainCategory());
        pattern.setSubCategory(normalizedCodes.subCategory());
        pattern.setStyle(normalizedCodes.style());
        pattern.setRegion(normalizedCodes.region());
        pattern.setPeriod(normalizedCodes.period());
        pattern.setImageUrl(request.getImageUrl());
        pattern.setImageSourceType(imageService.normalizeImageSourceTypeValue(request.getImageSourceType(), request.getImageUrl()));

        patternCodeService.assignFormalCode(pattern);

        if (pattern.getImageUrl() != null && !pattern.getImageUrl().isEmpty()) {
            try {
                ImageSourceType sourceType = imageService.resolveImageSourceType(pattern.getImageSourceType(), pattern.getImageUrl());
                String newUrl;
                switch (sourceType) {
                    case TEMP_UPLOAD -> newUrl = imageService.moveToFormal(pattern.getImageUrl(), pattern.getPatternCode());
                    case LIBRARY -> newUrl = imageService.copyToFormalWithoutDeletingSource(pattern.getImageUrl(), pattern.getPatternCode());
                    case EXTERNAL -> newUrl = imageService.fetchExternalToFormal(pattern.getImageUrl(), pattern.getPatternCode());
                    default -> throw new IllegalStateException("不支持的图片来源类型: " + sourceType);
                }
                pattern.setImageUrl(newUrl);
                pattern.setImageSourceType(sourceType.name());

                // 嵌入水印并存储双版本
                if (watermarkStorageService != null) {
                    try {
                        WatermarkResult wmResult = watermarkStorageService.embedAndStore(
                                newUrl, pattern.getPatternCode(), 0L);
                        pattern.setImageUrl(wmResult.getOriginalUrl());
                        pattern.setWatermarkedUrl(wmResult.getWatermarkedUrl());
                    } catch (Exception e) {
                        System.err.println("水印嵌入失败，降级使用原图: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("处理图片失败: " + e.getMessage(), e);
            }
        }

        Pattern saved = patternRepository.save(pattern);
        evictPatternCaches();
        return saved;
    }

    public List<Pattern> findAll() {
        String key = "patterns::all";
        List<Pattern> cached = redisCacheService.get(key, new TypeReference<List<Pattern>>() {});
        if (cached != null) return cached;
        List<Pattern> result = patternRepository.findAll();
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Page<Pattern> findAll(@NonNull Pageable pageable) {
        return patternRepository.findAll(pageable);
    }

    public Pattern findById(@NonNull Long id) {
        String key = "patterns::id:" + id;
        Pattern cached = redisCacheService.get(key, Pattern.class);
        if (cached != null) return cached;
        Pattern result = patternRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("纹样不存在"));
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Pattern findByCode(String code) {
        String key = "patterns::code:" + code;
        Pattern cached = redisCacheService.get(key, Pattern.class);
        if (cached != null) return cached;
        Pattern result = patternRepository.findByPatternCode(code)
                .orElseThrow(() -> new RuntimeException("纹样不存在"));
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public byte[] generatePatternQrCode(Long id) {
        Pattern pattern = findById(id);
        return generatePatternQrCodeByPatternCode(pattern.getPatternCode());
    }

    public byte[] generatePatternQrCodeByCode(String code) {
        Pattern pattern = findByCode(code);
        return generatePatternQrCodeByPatternCode(pattern.getPatternCode());
    }

    private byte[] generatePatternQrCodeByPatternCode(String patternCode) {
        if (patternCode == null || patternCode.isBlank()) {
            throw new RuntimeException("纹样编码不存在，无法生成二维码");
        }

        String encodedCode = UriUtils.encodePathSegment(patternCode, java.nio.charset.StandardCharsets.UTF_8);
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String targetUrl = base + "/api/open/patterns/" + encodedCode + "/table";

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(targetUrl, BarcodeFormat.QR_CODE, 320, 320, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("生成二维码失败: " + e.getMessage());
        }
    }

    public Pattern update(Long id, PatternRequest request) {
        patternCodeService.validateRequest(request);
        PatternCodeService.NormalizedPatternCodes normalizedCodes = patternCodeService.normalizeRequest(request);

        Pattern pattern = findById(id);
        pattern.setDescription(request.getDescription());
        pattern.setMainCategory(normalizedCodes.mainCategory());
        pattern.setSubCategory(normalizedCodes.subCategory());
        pattern.setStyle(normalizedCodes.style());
        pattern.setRegion(normalizedCodes.region());
        pattern.setPeriod(normalizedCodes.period());
        pattern.setImageUrl(request.getImageUrl());
        pattern.setImageSourceType(imageService.normalizeImageSourceTypeValue(request.getImageSourceType(), request.getImageUrl()));
        pattern.setStoryText(request.getStoryText());
        pattern.setStoryImageUrl(request.getStoryImageUrl());
        Pattern saved = patternRepository.save(pattern);
        evictPatternCaches();
        return saved;
    }

    @Transactional
    public void delete(@NonNull Long id, @NonNull com.example.hello.enums.UserRole role) {
        Pattern pattern = findById(id);

        if (role == com.example.hello.enums.UserRole.USER || role == com.example.hello.enums.UserRole.GUEST) {
            throw new RuntimeException("无权删除正式纹样");
        }

        if (pattern.getPatternCode() != null && !pattern.getPatternCode().isEmpty()) {
            patternPendingRepository.deleteByPatternCode(pattern.getPatternCode());
        }

        if (pattern.getImageUrl() != null && !pattern.getImageUrl().isEmpty()) {
            try {
                imageService.delete(pattern.getImageUrl());
            } catch (IOException e) {
                // ignore
            }
        }

        patternRepository.deleteById(id);
        evictPatternCaches();
    }

    public void delete(Long id) {
        delete(id, com.example.hello.enums.UserRole.SUPER_ADMIN);
    }

    public void batchDelete(List<Long> ids, com.example.hello.enums.UserRole role) {
        for (Long id : ids) {
            try {
                delete(id, role);
            } catch (Exception e) {
                System.err.println("Failed to delete pattern " + id + ": " + e.getMessage());
            }
        }
    }

    public void batchDelete(List<Long> ids) {
        batchDelete(ids, com.example.hello.enums.UserRole.SUPER_ADMIN);
    }

    public List<Pattern> findByMainCategory(String mainCategory) {
        String key = "patterns::cat:" + mainCategory.toUpperCase();
        List<Pattern> cached = redisCacheService.get(key, new TypeReference<List<Pattern>>() {});
        if (cached != null) return cached;
        List<Pattern> result = patternRepository.findByMainCategory(mainCategory.toUpperCase());
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Page<Pattern> findByMainCategory(String mainCategory, Pageable pageable) {
        return patternRepository.findByMainCategory(mainCategory.toUpperCase(), pageable);
    }

    public List<Pattern> findByStyle(String style) {
        String key = "patterns::style:" + style.toUpperCase();
        List<Pattern> cached = redisCacheService.get(key, new TypeReference<List<Pattern>>() {});
        if (cached != null) return cached;
        List<Pattern> result = patternRepository.findByStyle(style.toUpperCase());
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Page<Pattern> findByStyle(String style, Pageable pageable) {
        return patternRepository.findByStyle(style.toUpperCase(), pageable);
    }

    public List<Pattern> findByRegion(String region) {
        String key = "patterns::region:" + region.toUpperCase();
        List<Pattern> cached = redisCacheService.get(key, new TypeReference<List<Pattern>>() {});
        if (cached != null) return cached;
        List<Pattern> result = patternRepository.findByRegion(region.toUpperCase());
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Page<Pattern> findByRegion(String region, Pageable pageable) {
        return patternRepository.findByRegion(region.toUpperCase(), pageable);
    }

    public List<Pattern> findByPeriod(String period) {
        String key = "patterns::period:" + period.toUpperCase();
        List<Pattern> cached = redisCacheService.get(key, new TypeReference<List<Pattern>>() {});
        if (cached != null) return cached;
        List<Pattern> result = patternRepository.findByPeriod(period.toUpperCase());
        redisCacheService.put(key, result, CACHE_TTL);
        return result;
    }

    public Page<Pattern> findByPeriod(String period, Pageable pageable) {
        return patternRepository.findByPeriod(period.toUpperCase(), pageable);
    }

    public java.util.Map<String, Object> download(Long id) throws IOException {
        Pattern pattern = findById(id);
        if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
            throw new RuntimeException("该纹样没有图片");
        }

        String outputExtension = ".png";
        String filename = pattern.getPatternCode() + outputExtension;

        byte[] watermarked;
        // 优先使用已存储的水印图（新纹样都有 watermarkedUrl）
        if (pattern.getWatermarkedUrl() != null && !pattern.getWatermarkedUrl().isEmpty()) {
            try (InputStream inputStream = imageService.download(pattern.getWatermarkedUrl())) {
                watermarked = inputStream.readAllBytes();
            }
        } else {
            // 降级：历史纹样无 watermarkedUrl，实时嵌入水印
            String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode(), 0L);
            try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
                watermarked = addRobustWatermark(inputStream, hiddenWatermark, outputExtension);
            }
        }

        return java.util.Map.of(
                "stream", new ByteArrayInputStream(watermarked),
                "filename", filename,
                "contentType", resolveContentTypeByExtension(outputExtension)
        );
    }

    public void batchDownload(@NonNull List<Long> ids, @NonNull java.io.OutputStream outputStream) throws IOException {
        List<Pattern> patterns = patternRepository.findAllById(ids);

        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(outputStream)) {
            Set<String> usedFilenames = new HashSet<>();

            for (Pattern pattern : patterns) {
                if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
                    continue;
                }

                String outputExtension = ".png";
                String baseFilename = pattern.getPatternCode() + outputExtension;
                String filename = baseFilename;
                int counter = 1;
                while (usedFilenames.contains(filename)) {
                    filename = pattern.getPatternCode() + "_" + counter + outputExtension;
                    counter++;
                }
                usedFilenames.add(filename);

                try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
                    byte[] watermarked = addRobustWatermark(inputStream, buildHiddenWatermark(pattern.getPatternCode(), 0L), outputExtension);

                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(filename);
                    zipOut.putNextEntry(zipEntry);
                    zipOut.write(watermarked);
                    zipOut.closeEntry();
                } catch (Exception e) {
                    System.err.println("Failed to download pattern " + pattern.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    private String buildHiddenWatermark(String patternCode, Long uploaderId) {
        String code = (patternCode == null) ? "" : patternCode.trim();
        String userId = (uploaderId == null) ? "0" : uploaderId.toString();
        if (code.isEmpty()) {
            return "WM::" + userId;
        }
        return "WM:" + code + ":" + userId;
    }

    private byte[] addRobustWatermark(InputStream sourceStream, String watermarkText, String extension) throws IOException {
        byte[] original = sourceStream.readAllBytes();
        try {
            return dwtSvdWatermarkService.embed(new ByteArrayInputStream(original), watermarkText, extension);
        } catch (Exception e) {
            System.err.println("DWT-SVD水印嵌入失败，降级返回原图: " + e.getMessage());
            return original;
        }
    }

    public java.util.Map<String, Object> verifyRobustWatermark(InputStream imageStream) throws IOException {
        DwtSvdWatermarkService.WatermarkExtractResult result = dwtSvdWatermarkService.extract(imageStream);
        return java.util.Map.of(
                "hasWatermark", result.isHasWatermark(),
                "decodedText", result.getDecodedText() == null ? "" : result.getDecodedText(),
                "confidence", result.getConfidence(),
                "message", result.getMessage());
    }

    private String resolveContentTypeByExtension(String extension) {
        String format = normalizeImageFormat(extension);
        if (format == null) {
            return "application/octet-stream";
        }
        return switch (format) {
            case "png" -> "image/png";
            case "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private String normalizeImageFormat(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "jpeg";
        }

        String value = extension.startsWith(".") ? extension.substring(1) : extension;
        value = value.toLowerCase();

        if ("jpg".equals(value)) {
            return "jpeg";
        }
        if ("jpeg".equals(value) || "png".equals(value) || "bmp".equals(value) || "gif".equals(value)) {
            return value;
        }
        return null;
    }

    private void evictPatternCaches() {
        redisCacheService.evictPattern("patterns::*");
        redisCacheService.evictPattern("stats::*");
    }
}
