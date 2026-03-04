package com.example.hello.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.enums.PatternCodeEnum;
import com.example.hello.repository.PatternRepository;

@Service
public class PatternService {
    private final PatternRepository patternRepository;
    private final ImageService imageService;

    public PatternService(PatternRepository patternRepository, ImageService imageService) {
        this.patternRepository = patternRepository;
        this.imageService = imageService;
    }

    public Pattern create(PatternRequest request) {
        // 验证代码
        validateCodes(request);

        Pattern pattern = new Pattern();
        pattern.setDescription(request.getDescription());
        pattern.setMainCategory(request.getMainCategory().toUpperCase());
        pattern.setSubCategory(request.getSubCategory().toUpperCase());
        pattern.setStyle(request.getStyle().toUpperCase());
        pattern.setRegion(request.getRegion().toUpperCase());
        pattern.setPeriod(request.getPeriod().toUpperCase());
        pattern.setImageUrl(request.getImageUrl());

        // 生成日期代码和序列号
        String dateCode = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        pattern.setDateCode(dateCode);

        int sequenceNumber = generateSequenceNumber(dateCode);
        pattern.setSequenceNumber(sequenceNumber);

        // 生成完整编码
        pattern.setPatternCode(generatePatternCode(pattern));

        // 如果有图片URL，重命名为纹样编码
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
        // 验证代码
        validateCodes(request);

        Pattern pattern = findById(id);
        pattern.setDescription(request.getDescription());
        pattern.setMainCategory(request.getMainCategory().toUpperCase());
        pattern.setSubCategory(request.getSubCategory().toUpperCase());
        pattern.setStyle(request.getStyle().toUpperCase());
        pattern.setRegion(request.getRegion().toUpperCase());
        pattern.setPeriod(request.getPeriod().toUpperCase());
        pattern.setImageUrl(request.getImageUrl());
        // 更新时不改变日期代码、序列号和纹样编码
        return patternRepository.save(pattern);
    }

    public void delete(Long id, com.example.hello.enums.UserRole role) {
        Pattern pattern = findById(id);
        
        // 权限检查
        // 正式纹样表只有管理员和超级管理员可以删除
        // 普通用户无权删除正式纹样
        if (role == com.example.hello.enums.UserRole.USER || role == com.example.hello.enums.UserRole.GUEST) {
            throw new RuntimeException("无权删除正式纹样");
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
            java.util.Set<String> usedFilenames = new java.util.HashSet<>();
            
            for (Pattern pattern : patterns) {
                if (pattern.getImageUrl() == null || pattern.getImageUrl().isEmpty()) {
                    continue;
                }
                
                try (java.io.InputStream inputStream = imageService.download(pattern.getImageUrl())) {
                    // 确定扩展名
                    String extension = ".jpg";
                    if (pattern.getImageUrl().contains(".")) {
                        extension = pattern.getImageUrl().substring(pattern.getImageUrl().lastIndexOf("."));
                    }
                    
                    // 生成唯一文件名
                    String baseFilename = pattern.getPatternCode() + extension;
                    String filename = baseFilename;
                    int counter = 1;
                    while (usedFilenames.contains(filename)) {
                        filename = pattern.getPatternCode() + "_" + counter + extension;
                        counter++;
                    }
                    usedFilenames.add(filename);
                    
                    // 添加到ZIP
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(filename);
                    zipOut.putNextEntry(zipEntry);
                    
                    inputStream.transferTo(zipOut);
                    zipOut.closeEntry();
                } catch (Exception e) {
                    // 忽略单个文件下载失败，或者添加错误日志
                    System.err.println("Failed to download pattern " + pattern.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 验证所有代码的有效性
     */
    private void validateCodes(PatternRequest request) {
        String mainCategory = request.getMainCategory().toUpperCase();
        String subCategory = request.getSubCategory().toUpperCase();
        String style = request.getStyle().toUpperCase();
        String region = request.getRegion().toUpperCase();
        String period = request.getPeriod().toUpperCase();

        // 验证主类别
        if (!PatternCodeEnum.MainCategory.isValid(mainCategory)) {
            throw new IllegalArgumentException("无效的主类别代码: " + mainCategory);
        }

        // 验证子类别（根据主类别判断）
        if (!PatternCodeEnum.isValidSubCategory(mainCategory, subCategory)) {
            if (PatternCodeEnum.CATEGORIES_WITH_SUB.contains(mainCategory)) {
                throw new IllegalArgumentException("无效的子类别代码: " + subCategory + "，主类别: " + mainCategory);
            } else {
                throw new IllegalArgumentException("无效的风格代码(子类别位置): " + subCategory);
            }
        }

        // 验证风格
        if (!PatternCodeEnum.Style.isValid(style)) {
            throw new IllegalArgumentException("无效的风格代码: " + style);
        }

        // 验证地区
        if (!PatternCodeEnum.Region.isValid(region)) {
            throw new IllegalArgumentException("无效的地区代码: " + region);
        }

        // 验证时期
        if (!PatternCodeEnum.Period.isValid(period)) {
            throw new IllegalArgumentException("无效的时期代码: " + period);
        }
    }

    /**
     * 生成当日序列号
     */
    private int generateSequenceNumber(String dateCode) {
        // 按天递增：查询当天最大序号
        Integer maxSeq = patternRepository.findMaxSequenceNumberByDateCode(dateCode);
        return (maxSeq == null ? 0 : maxSeq) + 1;
    }

    /**
     * 生成纹样编码
     * 格式: 主类别-子类别-风格-地区-时期-日期-序列号
     */
    private String generatePatternCode(Pattern pattern) {
        return String.format("%s-%s-%s-%s-%s-%s-%03d",
                pattern.getMainCategory(),
                pattern.getSubCategory(),
                pattern.getStyle(),
                pattern.getRegion(),
                pattern.getPeriod(),
                pattern.getDateCode(),
                pattern.getSequenceNumber());
    }
}
