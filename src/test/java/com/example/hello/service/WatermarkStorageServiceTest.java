package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.WatermarkResult;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@ExtendWith(MockitoExtension.class)
class WatermarkStorageServiceTest {

    @Mock
    private ImageService imageService;

    @Mock
    private DwtSvdWatermarkService dwtSvdWatermarkService;

    private WatermarkStorageService service;

    @BeforeEach
    void setUp() {
        service = new WatermarkStorageService(imageService, dwtSvdWatermarkService);
    }

    // ─── buildWatermarkText tests ───────────────────────────────────────

    @Test
    void buildWatermarkText_withValidCodeAndUser() {
        String result = service.buildWatermarkText("AN-BD-TR-YU-QD-260509-001", 42L);
        assertEquals("WM:AN-BD-TR-YU-QD-260509-001:42", result);
    }

    @Test
    void buildWatermarkText_withNullCode() {
        String result = service.buildWatermarkText(null, 42L);
        assertEquals("WM::42", result);
    }

    @Test
    void buildWatermarkText_withEmptyCode() {
        String result = service.buildWatermarkText("", 42L);
        assertEquals("WM::42", result);
    }

    @Test
    void buildWatermarkText_withBlankCode() {
        String result = service.buildWatermarkText("   ", 42L);
        assertEquals("WM::42", result);
    }

    @Test
    void buildWatermarkText_withNullUser() {
        String result = service.buildWatermarkText("AN-BD-TR-YU-QD-260509-001", null);
        assertEquals("WM:AN-BD-TR-YU-QD-260509-001:0", result);
    }

    // ─── embedAndStore tests ────────────────────────────────────────────

    @Test
    void embedAndStore_success_returnsBothUrls() throws IOException {
        String originalUrl = "https://s3.example.com/bucket/temp/filename.png";
        String originalKey = "temp/filename.png";
        String watermarkedKey = "watermarked/filename.png";
        String watermarkedUrl = "https://s3.example.com/bucket/watermarked/filename.png";

        byte[] originalBytes = new byte[]{1, 2, 3, 4};
        byte[] watermarkedBytes = new byte[]{5, 6, 7, 8};

        // Mock download: return a real ResponseInputStream backed by a ByteArrayInputStream
        ResponseInputStream<GetObjectResponse> downloadStream = responseInputStreamOf(originalBytes);
        when(imageService.download(originalUrl)).thenReturn(downloadStream);

        // Mock key extraction and watermark key generation
        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);
        when(imageService.toWatermarkedKey(originalKey)).thenReturn(watermarkedKey);

        // Mock watermark embedding
        when(dwtSvdWatermarkService.embed(any(ByteArrayInputStream.class), eq("WM:AN-BD-TR-YU-QD-260509-001:42"), eq(".png")))
                .thenReturn(watermarkedBytes);

        // Mock S3 upload of watermarked image
        when(imageService.uploadBytes(watermarkedKey, watermarkedBytes, "image/png"))
                .thenReturn(watermarkedUrl);

        WatermarkResult result = service.embedAndStore(originalUrl, "AN-BD-TR-YU-QD-260509-001", 42L);

        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        assertEquals(watermarkedUrl, result.getWatermarkedUrl());
    }

    @Test
    void embedAndStore_watermarkEmbeddingFails_returnsNullWatermarkedUrl() throws IOException {
        String originalUrl = "https://s3.example.com/bucket/temp/filename.png";
        String originalKey = "temp/filename.png";

        byte[] originalBytes = new byte[]{1, 2, 3, 4};

        // Mock download
        ResponseInputStream<GetObjectResponse> downloadStream = responseInputStreamOf(originalBytes);
        when(imageService.download(originalUrl)).thenReturn(downloadStream);

        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);

        // Mock watermark embedding to throw an exception
        when(dwtSvdWatermarkService.embed(any(ByteArrayInputStream.class), anyString(), eq(".png")))
                .thenThrow(new RuntimeException("Embedding failed"));

        WatermarkResult result = service.embedAndStore(originalUrl, "AN-BD-TR-YU-QD-260509-001", 42L);

        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getWatermarkedUrl());
    }

    @Test
    void embedAndStore_s3UploadFails_returnsNullWatermarkedUrl() throws IOException {
        String originalUrl = "https://s3.example.com/bucket/temp/filename.png";
        String originalKey = "temp/filename.png";
        String watermarkedKey = "watermarked/filename.png";

        byte[] originalBytes = new byte[]{1, 2, 3, 4};
        byte[] watermarkedBytes = new byte[]{5, 6, 7, 8};

        // Mock download
        ResponseInputStream<GetObjectResponse> downloadStream = responseInputStreamOf(originalBytes);
        when(imageService.download(originalUrl)).thenReturn(downloadStream);

        when(imageService.extractKeyFromUrl(originalUrl)).thenReturn(originalKey);
        when(imageService.toWatermarkedKey(originalKey)).thenReturn(watermarkedKey);

        // Mock watermark embedding succeeds
        when(dwtSvdWatermarkService.embed(any(ByteArrayInputStream.class), anyString(), eq(".png")))
                .thenReturn(watermarkedBytes);

        // Mock S3 upload to throw IOException
        when(imageService.uploadBytes(watermarkedKey, watermarkedBytes, "image/png"))
                .thenThrow(new IOException("S3 upload failed"));

        WatermarkResult result = service.embedAndStore(originalUrl, "AN-BD-TR-YU-QD-260509-001", 42L);

        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getWatermarkedUrl());
    }

    // ─── helper ─────────────────────────────────────────────────────────

    private ResponseInputStream<GetObjectResponse> responseInputStreamOf(byte[] bytes) {
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        AbortableInputStream abortable = AbortableInputStream.create(new ByteArrayInputStream(bytes));
        return new ResponseInputStream<>(response, abortable);
    }
}
