package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.junit.jupiter.api.Test;

class DwtSvdWatermarkServiceTest {

    @Test
    void extract_shouldDecodeEmbeddedPayload_whenEnoughCapacity() throws Exception {
        DwtSvdWatermarkService service = new DwtSvdWatermarkService();
        String payload = "WM:AN-BD-TR-CN-QG-260319-001";

        byte[] source = toPng(buildTexturedImage(512, 512));
        byte[] watermarked = service.embed(new ByteArrayInputStream(source), payload, ".png");

        DwtSvdWatermarkService.WatermarkExtractResult result = service.extract(new ByteArrayInputStream(watermarked));

        assertNotNull(result);
        assertTrue(result.isHasWatermark());
        assertTrue(result.getConfidence() >= 0.85);
        if (result.getDecodedText() != null && !result.getDecodedText().isBlank()) {
            assertTrue(result.getDecodedText().startsWith("WM:"));
        }
    }

    @Test
    void extract_shouldReturnFalse_whenImageTooSmall() throws Exception {
        DwtSvdWatermarkService service = new DwtSvdWatermarkService();

        byte[] source = toPng(buildTexturedImage(24, 24));
        DwtSvdWatermarkService.WatermarkExtractResult result = service.extract(new ByteArrayInputStream(source));

        assertNotNull(result);
        assertFalse(result.isHasWatermark());
        assertTrue(result.getMessage().contains("尺寸过小"));
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

    private BufferedImage buildNearWhiteImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(20260420L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = 245 + ((x + y) % 7);
                int noise = random.nextInt(7) - 3;
                int channel = clampChannel(base + noise);
                image.setRGB(x, y, (channel << 16) | (channel << 8) | channel);
            }
        }
        return image;
    }

    private BufferedImage buildTransparentImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(20260421L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (x < width / 2) ? 0 : 220;
                if ((x + y) % 29 == 0) {
                    alpha = 80;
                }
                int r = clampChannel((x * 255) / Math.max(width - 1, 1) + random.nextInt(11) - 5);
                int g = clampChannel((y * 255) / Math.max(height - 1, 1) + random.nextInt(11) - 5);
                int b = clampChannel(((x + y) * 255) / Math.max(width + height - 2, 1) + random.nextInt(11) - 5);
                int argb = (alpha << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private int clampChannel(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
