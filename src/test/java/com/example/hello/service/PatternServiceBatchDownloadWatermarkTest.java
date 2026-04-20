package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
        byte[] sourcePng = toPng(buildTexturedImage(420, 300));

        when(patternRepository.findAllById(List.of(1L))).thenReturn(List.of(pattern));
        when(imageService.download("https://img/pattern-1.png")).thenReturn(responseInputStreamOf(sourcePng, "image/png"));

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        patternService.batchDownload(List.of(1L), zipBytes);

        String entryName = readFirstZipEntryName(zipBytes.toByteArray());
        byte[] downloaded = readFirstZipEntryBytes(zipBytes.toByteArray());
        assertNotNull(downloaded);
        assertEquals("AN-BD-TR-CN-QG-260319-001.png", entryName);
        assertWatermarkApplied(sourcePng, downloaded, "AN-BD-TR-CN-QG-260319-001");
    }

    @Test
    void download_shouldReturnMetadataWatermarkedImage() throws Exception {
        Pattern pattern = buildPattern(2L, "AN-BD-TR-CN-QG-260319-002", "https://img/pattern-2.png");
        byte[] sourcePng = toPng(buildTexturedImage(240, 180));

        when(patternRepository.findById(2L)).thenReturn(java.util.Optional.of(pattern));
        when(imageService.download("https://img/pattern-2.png")).thenReturn(responseInputStreamOf(sourcePng, "image/png"));

        Map<String, Object> result = patternService.download(2L);
        InputStream stream = (InputStream) result.get("stream");
        byte[] bytes = stream.readAllBytes();

        assertEquals("AN-BD-TR-CN-QG-260319-002.png", result.get("filename"));
        assertEquals("image/png", result.get("contentType"));
        assertNotNull(bytes);
        assertWatermarkApplied(sourcePng, bytes, "AN-BD-TR-CN-QG-260319-002");
    }

    private Pattern buildPattern(Long id, String patternCode, String imageUrl) {
        Pattern pattern = new Pattern();
        pattern.setId(id);
        pattern.setPatternCode(patternCode);
        pattern.setImageUrl(imageUrl);
        return pattern;
    }

    private BufferedImage buildTexturedImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(20260319L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gradR = (x * 255) / Math.max(width - 1, 1);
                int gradG = (y * 255) / Math.max(height - 1, 1);
                int gradB = ((x + y) * 255) / Math.max(width + height - 2, 1);
                int checker = ((x / 8 + y / 8) % 2 == 0) ? 10 : -10;
                int noise = random.nextInt(21) - 10;
                int r = clampChannel(gradR + checker + noise);
                int g = clampChannel(gradG - checker + noise / 2);
                int b = clampChannel(gradB + noise);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private BufferedImage buildTransparentImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(20260422L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = x < width / 3 ? 0 : 210;
                if ((x + y) % 31 == 0) {
                    alpha = 96;
                }
                int r = clampChannel((x * 255) / Math.max(width - 1, 1) + random.nextInt(11) - 5);
                int g = clampChannel((y * 255) / Math.max(height - 1, 1) + random.nextInt(11) - 5);
                int b = clampChannel(((x + y) * 255) / Math.max(width + height - 2, 1) + random.nextInt(11) - 5);
                image.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private int clampChannel(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
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

    private String readFirstZipEntryName(byte[] zipData) throws Exception {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zipData))) {
            java.util.zip.ZipEntry entry = zipIn.getNextEntry();
            assertNotNull(entry);
            return entry.getName();
        }
    }

    private void assertAlphaPreserved(byte[] originalBytes, byte[] watermarkedBytes) throws Exception {
        BufferedImage original = javax.imageio.ImageIO.read(new ByteArrayInputStream(originalBytes));
        BufferedImage watermarked = javax.imageio.ImageIO.read(new ByteArrayInputStream(watermarkedBytes));
        assertNotNull(original);
        assertNotNull(watermarked);
        assertEquals(((original.getRGB(12, 12) >> 24) & 0xFF), ((watermarked.getRGB(12, 12) >> 24) & 0xFF));
        assertEquals(((original.getRGB(original.getWidth() - 12, original.getHeight() - 12) >> 24) & 0xFF),
                ((watermarked.getRGB(watermarked.getWidth() - 12, watermarked.getHeight() - 12) >> 24) & 0xFF));
    }

    private void assertWatermarkApplied(byte[] originalBytes, byte[] watermarkedBytes, String expectedPatternCode) throws Exception {
        BufferedImage original = javax.imageio.ImageIO.read(new ByteArrayInputStream(originalBytes));
        BufferedImage watermarked = javax.imageio.ImageIO.read(new ByteArrayInputStream(watermarkedBytes));

        assertNotNull(original);
        assertNotNull(watermarked);
        assertEquals(original.getWidth(), watermarked.getWidth());
        assertEquals(original.getHeight(), watermarked.getHeight());

        int changedPixels = 0;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                if (original.getRGB(x, y) != watermarked.getRGB(x, y)) {
                    changedPixels++;
                }
            }
        }
        assertTrue(changedPixels > 0, "嵌入鲁棒水印后应产生可检测像素差异");

        DwtSvdWatermarkService watermarkService = new DwtSvdWatermarkService();
        DwtSvdWatermarkService.WatermarkExtractResult extractResult = watermarkService.extract(new ByteArrayInputStream(watermarkedBytes));
        assertTrue(extractResult.isHasWatermark(), "下载后的图片应可检测出鲁棒水印");
        assertTrue(extractResult.getConfidence() >= 0.85, "下载后的图片水印置信度应达标");
        if (extractResult.getDecodedText() != null && !extractResult.getDecodedText().isBlank()) {
            assertTrue(extractResult.getDecodedText().contains("WM:" + expectedPatternCode));
        }
    }
}
