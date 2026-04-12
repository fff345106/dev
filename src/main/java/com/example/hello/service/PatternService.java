package com.example.hello.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

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
    private final String publicBaseUrl;

    public PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.patternRepository = patternRepository;
        this.patternPendingRepository = patternPendingRepository;
        this.imageService = imageService;
        this.patternCodeService = patternCodeService;
        this.publicBaseUrl = publicBaseUrl;
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
     * 下载纹样图片（写入元数据隐藏水印）
     * @return Pair<InputStream, Filename>
     */
    public java.util.Map<String, Object> download(Long id) throws IOException {
        Pattern pattern = findById(id);
        if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
            throw new RuntimeException("该纹样没有图片");
        }

        String extension = extractExtension(pattern.getImageUrl());
        String filename = pattern.getPatternCode() + extension;
        String hiddenWatermark = buildHiddenWatermark(pattern.getPatternCode());

        byte[] watermarked;
        try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
            watermarked = addMetadataWatermark(inputStream, hiddenWatermark, extension);
        }

        return java.util.Map.of(
            "stream", new ByteArrayInputStream(watermarked),
            "filename", filename,
            "contentType", resolveContentTypeByExtension(extension)
        );
    }

    /**
     * 批量下载纹样图片（写入元数据隐藏水印）
     */
    public void batchDownload(List<Long> ids, java.io.OutputStream outputStream) throws IOException {
        List<Pattern> patterns = patternRepository.findAllById(ids);

        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(outputStream)) {
            Set<String> usedFilenames = new HashSet<>();

            for (Pattern pattern : patterns) {
                if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
                    continue;
                }

                String extension = extractExtension(pattern.getImageUrl());
                String baseFilename = pattern.getPatternCode() + extension;
                String filename = baseFilename;
                int counter = 1;
                while (usedFilenames.contains(filename)) {
                    filename = pattern.getPatternCode() + "_" + counter + extension;
                    counter++;
                }
                usedFilenames.add(filename);

                try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
                    byte[] watermarked = addMetadataWatermark(inputStream, buildHiddenWatermark(pattern.getPatternCode()), extension);

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
        return "hidden-watermark|patternCode=" + patternCode + "|generatedAt=" + Instant.now();
    }

    private byte[] addMetadataWatermark(InputStream sourceStream, String watermarkText, String extension) throws IOException {
        BufferedImage source = ImageIO.read(sourceStream);
        if (source == null) {
            throw new IOException("不支持的图片格式，无法写入隐藏水印");
        }

        String format = normalizeImageFormat(extension);
        if (format == null) {
            throw new IOException("不支持的图片格式: " + extension);
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IOException("未找到可用图片写入器: " + format);
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(source), writeParam);
            IIOMetadata injectedMetadata = injectHiddenMetadata(metadata, format, watermarkText);
            writer.write(null, new IIOImage(source, null, injectedMetadata), writeParam);
            ios.flush();
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private IIOMetadata injectHiddenMetadata(IIOMetadata metadata, String format, String watermarkText) {
        if (metadata == null || watermarkText == null || watermarkText.isBlank()) {
            return metadata;
        }

        try {
            if ("png".equals(format)) {
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_png_1.0");
                IIOMetadataNode textNode = findOrCreateChild(root, "tEXt");
                IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
                entry.setAttribute("keyword", "Comment");
                entry.setAttribute("value", watermarkText);
                textNode.appendChild(entry);
                metadata.setFromTree("javax_imageio_png_1.0", root);
                return metadata;
            }

            if ("jpeg".equals(format)) {
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
                IIOMetadataNode markerSequence = findOrCreateChild(root, "markerSequence");
                IIOMetadataNode com = new IIOMetadataNode("com");
                com.setAttribute("comment", watermarkText);
                markerSequence.appendChild(com);
                metadata.setFromTree("javax_imageio_jpeg_image_1.0", root);
                return metadata;
            }

            if (metadata.isStandardMetadataFormatSupported()) {
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_1.0");
                IIOMetadataNode text = findOrCreateChild(root, "Text");
                IIOMetadataNode textEntry = new IIOMetadataNode("TextEntry");
                textEntry.setAttribute("keyword", "Comment");
                textEntry.setAttribute("value", watermarkText);
                textEntry.setAttribute("encoding", StandardCharsets.UTF_8.name());
                text.appendChild(textEntry);
                metadata.setFromTree("javax_imageio_1.0", root);
            }
        } catch (Exception ignored) {
            // 元数据写入失败时保持可下载，避免影响主流程。
        }
        return metadata;
    }

    private IIOMetadataNode findOrCreateChild(IIOMetadataNode root, String nodeName) {
        for (int i = 0; i < root.getLength(); i++) {
            if (nodeName.equals(root.item(i).getNodeName())) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode child = new IIOMetadataNode(nodeName);
        root.appendChild(child);
        return child;
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
