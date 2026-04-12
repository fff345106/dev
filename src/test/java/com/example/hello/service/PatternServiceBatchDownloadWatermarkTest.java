package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.Pattern;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@ExtendWith(MockitoExtension.class)
class PatternServiceBatchDownloadWatermarkTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternPendingRepository patternPendingRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private PatternCodeService patternCodeService;

    private PatternService patternService;

    @BeforeEach
    void setUp() {
        patternService = new PatternService(patternRepository, patternPendingRepository, imageService, patternCodeService, "https://example.com");
    }

    @Test
    void batchDownload_shouldWriteMetadataWatermarkIntoZip() throws Exception {
        Pattern pattern = buildPattern(1L, "AN-BD-TR-CN-QG-260319-001", "https://img/pattern-1.png");
        byte[] sourcePng = toPng(buildSolidImage(420, 300, new Color(200, 40, 40)));

        when(patternRepository.findAllById(List.of(1L))).thenReturn(List.of(pattern));
        when(imageService.download("https://img/pattern-1.png")).thenReturn(responseInputStreamOf(sourcePng, "image/png"));

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        patternService.batchDownload(List.of(1L), zipBytes);

        byte[] downloaded = readFirstZipEntryBytes(zipBytes.toByteArray());
        assertNotNull(downloaded);
        assertPngContainsWatermark(downloaded, pattern.getPatternCode());
    }

    @Test
    void download_shouldReturnMetadataWatermarkedImage() throws Exception {
        Pattern pattern = buildPattern(2L, "AN-BD-TR-CN-QG-260319-002", "https://img/pattern-2.png");
        byte[] sourcePng = toPng(buildSolidImage(240, 180, new Color(80, 120, 220)));

        when(patternRepository.findById(2L)).thenReturn(java.util.Optional.of(pattern));
        when(imageService.download("https://img/pattern-2.png")).thenReturn(responseInputStreamOf(sourcePng, "image/png"));

        Map<String, Object> result = patternService.download(2L);
        InputStream stream = (InputStream) result.get("stream");
        byte[] bytes = stream.readAllBytes();

        assertNotNull(bytes);
        assertPngContainsWatermark(bytes, pattern.getPatternCode());
    }

    private Pattern buildPattern(Long id, String patternCode, String imageUrl) {
        Pattern pattern = new Pattern();
        pattern.setId(id);
        pattern.setPatternCode(patternCode);
        pattern.setImageUrl(imageUrl);
        return pattern;
    }

    private BufferedImage buildSolidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private ResponseInputStream<GetObjectResponse> responseInputStreamOf(byte[] bytes, String contentType) {
        GetObjectResponse response = GetObjectResponse.builder().contentType(contentType).build();
        AbortableInputStream abortable = AbortableInputStream.create(new ByteArrayInputStream(bytes));
        return new ResponseInputStream<>(response, abortable);
    }

    private byte[] readFirstZipEntryBytes(byte[] zipData) throws Exception {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zipData))) {
            java.util.zip.ZipEntry entry = zipIn.getNextEntry();
            assertNotNull(entry);
            ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
            zipIn.transferTo(imageOut);
            return imageOut.toByteArray();
        }
    }

    private void assertPngContainsWatermark(byte[] imageBytes, String patternCode) {
        String text = new String(imageBytes, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("hidden-watermark|patternCode=" + patternCode));
    }
}
