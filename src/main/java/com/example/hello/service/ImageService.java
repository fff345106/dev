package com.example.hello.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    private static final String TEMP_FOLDER = "temp/";

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

        // 保存上传的文件到本地临时文件
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        File tempInputFile = File.createTempFile("upload_", extension);
        file.transferTo(tempInputFile);

        try {
            // 上传原图到S3
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
            // 清理输入临时文件
            if (tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
    }

    /**
     * 审核通过：将图片从temp移动到正式目录并重命名
     */
    public String moveToFormal(String tempUrl, String patternCode) throws IOException {
        if (tempUrl == null || tempUrl.isEmpty()) {
            return null;
        }

        try {
            // 从URL提取临时文件的key
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
                    CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                            .sourceBucket(bucket)
                            .sourceKey(sourceKey)
                            .destinationBucket(bucket)
                            .destinationKey(newKey)
                            .build();
                    s3Client.copyObject(copyRequest);
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

            // 删除临时文件
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

    /**
     * 从URL中提取对象存储的key
     */
    private String extractKeyFromUrl(String url) {
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
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
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
