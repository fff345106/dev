package com.example.hello.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class PatternService {
    private final PatternRepository patternRepository;
    private final PatternPendingRepository patternPendingRepository;
    private final ImageService imageService;
    private final PatternCodeService patternCodeService;

    public PatternService(
            PatternRepository patternRepository,
            PatternPendingRepository patternPendingRepository,
            ImageService imageService,
            PatternCodeService patternCodeService) {
        this.patternRepository = patternRepository;
        this.patternPendingRepository = patternPendingRepository;
        this.imageService = imageService;
        this.patternCodeService = patternCodeService;
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

        patternCodeService.assignFormalCode(pattern);

        if (pattern.getImageUrl() != null && !pattern.getImageUrl().isEmpty()) {
            try {
                String newUrl = imageService.renameToPatternCode(pattern.getImageUrl(), pattern.getPatternCode());
                pattern.setImageUrl(newUrl);
            } catch (IOException e) {
                // 重命名失败保留原URL
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
     * 下载纹样图片
     * @return Pair<InputStream, Filename>
     */
    public java.util.Map<String, Object> download(Long id) throws IOException {
        Pattern pattern = findById(id);
        if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
            throw new RuntimeException("该纹样没有图片");
        }

        var inputStream = imageService.download(pattern.getImageUrl());
        
        // 提取扩展名
        String extension = ".jpg"; // 默认
        if (pattern.getImageUrl().contains(".")) {
            extension = pattern.getImageUrl().substring(pattern.getImageUrl().lastIndexOf("."));
        }
        
        String filename = pattern.getPatternCode() + extension;
        
        return java.util.Map.of(
            "stream", inputStream,
            "filename", filename,
            "contentType", inputStream.response().contentType()
        );
    }

    /**
     * 批量下载纹样图片
     */
    public void batchDownload(List<Long> ids, java.io.OutputStream outputStream) throws IOException {
        List<Pattern> patterns = patternRepository.findAllById(ids);

        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(outputStream)) {
            Set<String> usedFilenames = new HashSet<>();

            for (Pattern pattern : patterns) {
                if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
                    continue;
                }

                try (InputStream inputStream = imageService.download(pattern.getImageUrl())) {
                    String extension = ".jpg";
                    if (pattern.getImageUrl().contains(".")) {
                        extension = pattern.getImageUrl().substring(pattern.getImageUrl().lastIndexOf("."));
                    }

                    String baseFilename = pattern.getPatternCode() + extension;
                    String filename = baseFilename;
                    int counter = 1;
                    while (usedFilenames.contains(filename)) {
                        filename = pattern.getPatternCode() + "_" + counter + extension;
                        counter++;
                    }
                    usedFilenames.add(filename);

                    byte[] watermarked = addWatermark(inputStream, "仅供内部使用 " + pattern.getPatternCode(), extension);

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

    private byte[] addWatermark(InputStream sourceStream, String watermarkText, String extension) throws IOException {
        BufferedImage source = javax.imageio.ImageIO.read(sourceStream);
        if (source == null) {
            throw new IOException("不支持的图片格式，无法添加水印");
        }

        String format = normalizeImageFormat(extension);
        if (format == null) {
            throw new IOException("不支持的图片格式: " + extension);
        }

        boolean jpeg = "jpeg".equals(format);
        int imageType = jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), imageType);

        Graphics2D g2d = target.createGraphics();
        try {
            if (jpeg) {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, target.getWidth(), target.getHeight());
            }
            g2d.drawImage(source, 0, 0, null);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int minSize = Math.min(target.getWidth(), target.getHeight());
            int fontSize = Math.max(20, minSize / 12);
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
            g2d.setColor(Color.WHITE);

            AffineTransform oldTransform = g2d.getTransform();
            g2d.rotate(Math.toRadians(-30), target.getWidth() / 2.0, target.getHeight() / 2.0);

            java.awt.FontMetrics fontMetrics = g2d.getFontMetrics();
            int centerX = Math.max(0, (target.getWidth() - fontMetrics.stringWidth(watermarkText)) / 2);
            int centerY = Math.max(fontMetrics.getAscent(), target.getHeight() / 2);
            g2d.drawString(watermarkText, centerX, centerY);

            int tileX = Math.max(240, target.getWidth() / 2);
            int tileY = Math.max(180, target.getHeight() / 3);
            for (int y = -target.getHeight(); y < target.getHeight() * 2; y += tileY) {
                for (int x = -target.getWidth(); x < target.getWidth() * 2; x += tileX) {
                    g2d.drawString(watermarkText, x, y);
                }
            }
            g2d.setTransform(oldTransform);
        } finally {
            g2d.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(target, format, baos)) {
            throw new IOException("写入水印图片失败");
        }
        return baos.toByteArray();
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
