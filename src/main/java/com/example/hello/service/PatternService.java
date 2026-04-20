package com.example.hello.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.enums.ImageSourceType;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class PatternService {
    private final PatternRepository patternRepository;
    private final PatternPendingRepository patternPendingRepository;
    private final ImageService imageService;
    private final PatternCodeService patternCodeService;
    private final DwtSvdWatermarkService dwtSvdWatermarkService;
    private final String publicBaseUrl;

    @Autowired
    public PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this(patternRepository, patternPendingRepository, imageService, patternCodeService, publicBaseUrl, new DwtSvdWatermarkService());
    }

    PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService,
            String publicBaseUrl,
            DwtSvdWatermarkService dwtSvdWatermarkService) {
        this.patternRepository = patternRepository;
        this.patternPendingRepository = patternPendingRepository;
        this.imageService = imageService;
        this.patternCodeService = patternCodeService;
        this.publicBaseUrl = publicBaseUrl;
        this.dwtSvdWatermarkService = dwtSvdWatermarkService;
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
            } catch (IOException e) {
                throw new RuntimeException("处理图片失败: " + e.getMessage(), e);
            }
        }

        return patternRepository.save(pattern);
    }

    public List<Pattern> findAll() {
        return patternRepository.findAll();
    }

    public Page<Pattern> findAll(Pageable pageable) {
        return patternRepository.findAll(pageable);
    }

    public Pattern findById(Long id) {
        return patternRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("纹样不存在"));
    }

    public Pattern findByCode(String code) {
        return patternRepository.findByPatternCode(code)
                .orElseThrow(() -> new RuntimeException("纹样不存在"));
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
        // 更新时不改变日期代码、序列号和纹样编码
        return patternRepository.save(pattern);
    }

    @Transactional
    public void delete(Long id, com.example.hello.enums.UserRole role) {
        Pattern pattern = findById(id);

        // 权限检查
        // 正式纹样表只有管理员和超级管理员可以删除
        // 普通用户无权删除正式纹样
        if (role == com.example.hello.enums.UserRole.USER || role == com.example.hello.enums.UserRole.GUEST) {
            throw new RuntimeException("无权删除正式纹样");
        }

        // 联动删除临时库中同编码记录（不存在则删除0条，不影响流程）
        if (pattern.getPatternCode() != null && !pattern.getPatternCode().isEmpty()) {
            patternPendingRepository.deleteByPatternCode(pattern.getPatternCode());
        }

        // 删除关联的图片文件
        if (pattern.getImageUrl() != null && !pattern.getImageUrl().isEmpty()) {
            try {
                imageService.delete(pattern.getImageUrl());
            } catch (IOException e) {
                // 图片删除失败不影响纹样删除
            }
        }

        patternRepository.deleteById(id);
    }
    
    // 兼容旧接口，供内部调用（如果有）
    public void delete(Long id) {
        delete(id, com.example.hello.enums.UserRole.SUPER_ADMIN); // 默认最高权限
    }

    /**
     * 批量删除纹样
     */
    public void batchDelete(List<Long> ids, com.example.hello.enums.UserRole role) {
        for (Long id : ids) {
            try {
                delete(id, role);
            } catch (Exception e) {
                // 忽略单个删除失败，继续删除下一个
                System.err.println("Failed to delete pattern " + id + ": " + e.getMessage());
            }
        }
    }
    
    // 兼容旧接口
    public void batchDelete(List<Long> ids) {
        batchDelete(ids, com.example.hello.enums.UserRole.SUPER_ADMIN);
    }

    public List<Pattern> findByMainCategory(String mainCategory) {
        return patternRepository.findByMainCategory(mainCategory.toUpperCase());
    }

    public Page<Pattern> findByMainCategory(String mainCategory, Pageable pageable) {
        return patternRepository.findByMainCategory(mainCategory.toUpperCase(), pageable);
    }

    public List<Pattern> findByStyle(String style) {
        return patternRepository.findByStyle(style.toUpperCase());
    }

    public Page<Pattern> findByStyle(String style, Pageable pageable) {
        return patternRepository.findByStyle(style.toUpperCase(), pageable);
    }

    public List<Pattern> findByRegion(String region) {
        return patternRepository.findByRegion(region.toUpperCase());
    }

    public Page<Pattern> findByRegion(String region, Pageable pageable) {
        return patternRepository.findByRegion(region.toUpperCase(), pageable);
    }

    public List<Pattern> findByPeriod(String period) {
        return patternRepository.findByPeriod(period.toUpperCase());
    }

    public Page<Pattern> findByPeriod(String period, Pageable pageable) {
        return patternRepository.findByPeriod(period.toUpperCase(), pageable);
    }

    /**
     * 下载纹样图片（写入DWT-SVD鲁棒水印）
     * @return Pair<InputStream, Filename>
     */
    public java.util.Map<String, Object> download(Long id) throws IOException {
        Pattern pattern = findById(id);
        if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
            throw new RuntimeException("该纹样没有图片");
        }

        String outputExtension = ".png";
        String filename = pattern.getPatternCode() + outputExtension;
        String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode());

        byte[] watermarked;
        try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
            watermarked = addRobustWatermark(inputStream, hiddenWatermark, outputExtension);
        }

        return java.util.Map.of(
            "stream", new ByteArrayInputStream(watermarked),
            "filename", filename,
            "contentType", resolveContentTypeByExtension(outputExtension)
        );
    }

    /**
     * 批量下载纹样图片（写入DWT-SVD鲁棒水印）
     */
    public void batchDownload(List<Long> ids, java.io.OutputStream outputStream) throws IOException {
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
                    byte[] watermarked = addRobustWatermark(inputStream, buildHiddenWatermark(pattern.getPatternCode()), outputExtension);

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

    private String buildHiddenWatermark(String patternCode) {
        String code = (patternCode == null) ? "" : patternCode.trim();
        if (code.isEmpty()) {
            return "WM";
        }
        return "WM:" + code;
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

    private String extractExtension(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains(".")) {
            return ".jpg";
        }
        return imageUrl.substring(imageUrl.lastIndexOf("."));
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

}
