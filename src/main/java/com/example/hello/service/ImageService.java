package com.example.hello.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.hello.enums.ImageSourceType;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ImageService {

    private static final String TEMP_FOLDER = "temp/";
    private static final long MAX_EXTERNAL_IMAGE_BYTES = 15L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTERNAL_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp");

    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${s3.region:us-east-1}")
    private String region;

    @Value("${image.base.url}")
    private String baseUrl;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }

    /**
     * 上传图片到临时目录（待审核）
     * 仅保存原图
     */
    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }

        return uploadToTemp(file, contentType);
    }

    /**
     * 上传故事文件到临时目录（支持图片/PDF）
     */
    public String uploadStoryFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String contentType = file.getContentType();
        if (!isSupportedStoryFileType(contentType)) {
            throw new IllegalArgumentException("故事文件仅支持图片或PDF");
        }

        return uploadToTemp(file, contentType);
    }

    private String uploadToTemp(MultipartFile file, String contentType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = resolveUploadExtension(originalFilename, contentType);
        File tempInputFile = File.createTempFile("upload_", extension);
        file.transferTo(tempInputFile);

        try {
            String originalKey = TEMP_FOLDER + UUID.randomUUID().toString() + extension;
            try {
                PutObjectRequest originalRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(originalKey)
                        .contentType(contentType)
                        .build();
                s3Client.putObject(originalRequest, RequestBody.fromFile(tempInputFile));
                return baseUrl + "/" + originalKey;
            } catch (S3Exception e) {
                System.err.println("S3原图上传失败: " + e.getMessage());
                throw new IOException("原图上传到对象存储失败: " + e.getMessage(), e);
            }
        } finally {
            if (tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
    }

    private boolean isSupportedStoryFileType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        return normalizedType.startsWith("image/") || "application/pdf".equals(normalizedType);
    }

    /**
     * 审核通过：将图片从temp移动到正式目录并重命名（仅 TEMP_UPLOAD）
     */
    public String moveToFormal(String tempUrl, String patternCode) throws IOException {
        if (tempUrl == null || tempUrl.isEmpty()) {
            return null;
        }

        try {
            String tempKey = extractKeyFromUrl(tempUrl);
            String extension = getFileExtension(tempKey);
            String newKey = patternCode + extension;

            List<String> sourceKeyCandidates = new ArrayList<>();
            sourceKeyCandidates.add(tempKey);
            if (!tempKey.startsWith(TEMP_FOLDER)) {
                String fileName = tempKey.contains("/") ? tempKey.substring(tempKey.lastIndexOf("/") + 1) : tempKey;
                sourceKeyCandidates.add(TEMP_FOLDER + fileName);
            }

            String copiedSourceKey = null;
            S3Exception lastException = null;
            for (String sourceKey : sourceKeyCandidates) {
                try {
                    copyObject(sourceKey, newKey);
                    copiedSourceKey = sourceKey;
                    break;
                } catch (S3Exception e) {
                    lastException = e;
                    if (e.statusCode() != 404) {
                        throw e;
                    }
                }
            }

            if (copiedSourceKey == null) {
                if (objectExists(newKey)) {
                    return baseUrl + "/" + newKey;
                }
                throw lastException == null ? new IOException("移动图片失败: 找不到源文件")
                        : new IOException("移动图片失败: " + lastException.getMessage(), lastException);
            }

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(copiedSourceKey)
                    .build();
            s3Client.deleteObject(deleteRequest);

            return baseUrl + "/" + newKey;
        } catch (S3Exception e) {
            System.err.println("移动图片失败: " + e.getMessage());
            throw new IOException("移动图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 审核通过：库内图片复制到正式目录，不删除源文件
     */
    public String copyToFormalWithoutDeletingSource(String sourceUrl, String patternCode) throws IOException {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return null;
        }
        validateLibraryUrl(sourceUrl);

        try {
            String sourceKey = extractKeyFromUrl(sourceUrl);
            String extension = getFileExtension(sourceKey);
            String newKey = patternCode + extension;
            copyObject(sourceKey, newKey);
            return baseUrl + "/" + newKey;
        } catch (S3Exception e) {
            System.err.println("复制库内图片失败: " + e.getMessage());
            throw new IOException("复制库内图片失败: " + e.getMessage(), e);
        }
    }

    /**
     * 审核通过：外部图片抓取入对象存储正式目录
     */
    public String fetchExternalToFormal(String externalUrl, String patternCode) throws IOException {
        if (externalUrl == null || externalUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(externalUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("外部图片仅支持 HTTP/HTTPS URL");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载外部图片被中断", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载外部图片失败，HTTP状态: " + response.statusCode());
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                .orElse("application/octet-stream");

        if (!ALLOWED_EXTERNAL_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("外部图片类型不支持: " + contentType);
        }

        byte[] bodyBytes;
        try (InputStream bodyStream = response.body()) {
            bodyBytes = readAllWithLimit(bodyStream, MAX_EXTERNAL_IMAGE_BYTES);
        }

        String extension = resolveExtensionForExternal(uri.getPath(), contentType);
        String newKey = patternCode + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(newKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bodyBytes));
            return baseUrl + "/" + newKey;
        } catch (S3Exception e) {
            throw new IOException("外部图片入库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除临时图片（审核拒绝或删除待审核记录时调用）
     */
    public void deleteTempImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(imageUrl);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            System.err.println("删除临时图片失败: " + e.getMessage());
        }
    }

    /**
     * 删除正式图片
     */
    public void delete(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(imageUrl);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            System.err.println("删除图片失败: " + e.getMessage());
        }
    }

    /**
     * 重命名图片文件（兼容旧方法）
     */
    public String renameToPatternCode(String oldUrl, String patternCode) throws IOException {
        return moveToFormal(oldUrl, patternCode);
    }

    /**
     * 下载图片
     */
    public software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> download(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("图片URL不能为空");
        }

        try {
            String key = extractKeyFromUrl(imageUrl);
            software.amazon.awssdk.services.s3.model.GetObjectRequest request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObject(request);
        } catch (S3Exception e) {
            System.err.println("下载图片失败: " + e.getMessage());
            throw new IOException("下载图片失败: " + e.getMessage(), e);
        }
    }

    public String normalizeImageSourceTypeValue(String sourceType, String imageUrl) {
        return resolveImageSourceType(sourceType, imageUrl).name();
    }

    public ImageSourceType resolveImageSourceType(String sourceType, String imageUrl) {
        ImageSourceType parsed = ImageSourceType.fromValue(sourceType);
        if (parsed != null) {
            if (parsed == ImageSourceType.LIBRARY) {
                validateLibraryUrl(imageUrl);
            }
            return parsed;
        }
        return inferImageSourceType(imageUrl);
    }

    public boolean shouldDeleteTempImage(String imageUrl, String sourceType) {
        return resolveImageSourceType(sourceType, imageUrl) == ImageSourceType.TEMP_UPLOAD;
    }

    public void validateLibraryUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("纹样图片不能为空");
        }
        if (!isLibraryUrl(imageUrl)) {
            throw new IllegalArgumentException("纹样库图片必须是系统对象存储地址");
        }
    }

    private ImageSourceType inferImageSourceType(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return ImageSourceType.EXTERNAL;
        }
        String key = tryExtractInternalKey(imageUrl);
        if (key != null && key.startsWith(TEMP_FOLDER)) {
            return ImageSourceType.TEMP_UPLOAD;
        }
        if (isLibraryUrl(imageUrl)) {
            return ImageSourceType.LIBRARY;
        }
        return ImageSourceType.EXTERNAL;
    }

    private boolean isLibraryUrl(String imageUrl) {
        String key = tryExtractInternalKey(imageUrl);
        return key != null && !key.isBlank() && !key.startsWith(TEMP_FOLDER);
    }

    private void copyObject(String sourceKey, String targetKey) {
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(targetKey)
                .build();
        s3Client.copyObject(copyRequest);
    }

    /**
     * 从URL中提取对象存储的key
     */
    private String extractKeyFromUrl(String url) {
        String key = tryExtractInternalKey(url);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("无法识别对象存储路径: " + url);
        }
        return key;
    }

    private String tryExtractInternalKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalizedBaseUrl = normalizeBaseUrl();
        if (url.startsWith(normalizedBaseUrl + "/")) {
            return url.substring((normalizedBaseUrl + "/").length());
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                String normalized = path.startsWith("/") ? path.substring(1) : path;
                String bucketPrefix = bucket + "/";
                if (normalized.startsWith(bucketPrefix)) {
                    normalized = normalized.substring(bucketPrefix.length());
                }
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        } catch (Exception ignored) {
            // ignore and fallback
        }

        String bucketPath = "/" + bucket + "/";
        int index = url.indexOf(bucketPath);
        if (index != -1) {
            return url.substring(index + bucketPath.length());
        }
        return null;
    }

    private String normalizeBaseUrl() {
        if (baseUrl == null) {
            return "";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (ext.equals(".pdf")) {
            return ".pdf";
        }
        return ext;
    }

    private String resolveUploadExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return getFileExtension(filename);
        }
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return ".pdf";
        }
        return ".jpg";
    }

    private String resolveExtensionForExternal(String path, String contentType) {
        String byPath = getFileExtension(path);
        if (byPath != null && !byPath.equals(".jpg") && byPath.length() > 1) {
            return byPath;
        }

        Map<String, String> extMap = Map.of(
                "image/jpeg", ".jpg",
                "image/png", ".png",
                "image/webp", ".webp",
                "image/gif", ".gif",
                "image/bmp", ".bmp");
        return extMap.getOrDefault(contentType, ".jpg");
    }

    private byte[] readAllWithLimit(InputStream inputStream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("外部图片过大，超过限制 " + (maxBytes / 1024 / 1024) + "MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean objectExists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
