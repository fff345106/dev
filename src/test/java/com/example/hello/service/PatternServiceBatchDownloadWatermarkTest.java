package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

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
        patternService = new PatternService(patternRepository, patternPendingRepository, imageService, patternCodeService);
    }

    @Test
    void batchDownload_shouldWriteWatermarkedImagesIntoZip() throws Exception {
        Pattern pattern = new Pattern();
        pattern.setId(1L);
        pattern.setPatternCode("AN-BD-TR-CN-QG-260319-001");
        pattern.setImageUrl("https://img/pattern-1.png");

        BufferedImage source = buildSolidImage(420, 300, new Color(200, 40, 40));
        byte[] sourcePng = toPng(source);

        when(patternRepository.findAllById(List.of(1L))).thenReturn(List.of(pattern));
        when(imageService.download("https://img/pattern-1.png")).thenReturn(responseInputStreamOf(sourcePng, "image/png"));

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        patternService.batchDownload(List.of(1L), zipBytes);

        BufferedImage downloaded = readFirstZipEntryImage(zipBytes.toByteArray());
        assertNotNull(downloaded);
        assertTrue(hasDifferentPixel(source, downloaded));
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

    private BufferedImage readFirstZipEntryImage(byte[] zipData) throws Exception {
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zipData))) {
            java.util.zip.ZipEntry entry = zipIn.getNextEntry();
            assertNotNull(entry);
            ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
            zipIn.transferTo(imageOut);
            return javax.imageio.ImageIO.read(new ByteArrayInputStream(imageOut.toByteArray()));
        }
    }

    private boolean hasDifferentPixel(BufferedImage a, BufferedImage b) {
        int width = Math.min(a.getWidth(), b.getWidth());
        int height = Math.min(a.getHeight(), b.getHeight());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}
