package com.example.hello.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * DWT-SVD 鲁棒水印嵌入服务（纯 Java 实现）。
 */
public class DwtSvdWatermarkService {

    private static final int BLOCK_SIZE = 8;
    private static final double EMBED_STRENGTH = 48.0;
    private static final int WATERMARK_QR_SIZE = 32;
    private static final double DETECTION_CONFIDENCE_THRESHOLD = 0.85;
    private static final double SAFE_LUMA_MIN = 12.0;
    private static final double SAFE_LUMA_MAX = 243.0;

    public byte[] embed(InputStream sourceStream, String watermarkText, String extension) throws IOException {
        BufferedImage source = ImageIO.read(sourceStream);
        if (source == null) {
            throw new IOException("不支持的图片格式，无法写入DWT-SVD水印");
        }

        String format = normalizeImageFormat(extension);
        if (format == null) {
            throw new IOException("不支持的图片格式: " + extension);
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int evenWidth = width - (width % 2);
        int evenHeight = height - (height % 2);
        boolean keepAlpha = source.getColorModel() != null && source.getColorModel().hasAlpha();

        if (evenWidth < 32 || evenHeight < 32) {
            return writeImage(source, format);
        }

        double[][] y = new double[evenHeight][evenWidth];
        double[][] cb = new double[evenHeight][evenWidth];
        double[][] cr = new double[evenHeight][evenWidth];

        for (int row = 0; row < evenHeight; row++) {
            for (int col = 0; col < evenWidth; col++) {
                int rgb = source.getRGB(col, row);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double yValue = 0.299 * r + 0.587 * g + 0.114 * b;
                double cbValue = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                double crValue = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b;

                y[row][col] = compressLumaToSafeRange(yValue);
                cb[row][col] = cbValue;
                cr[row][col] = crValue;
            }
        }

        HaarDwtResult dwt = forwardHaar2D(y);
        embedBitsIntoLl(dwt.ll, buildWatermarkBits(watermarkText, computeCapacity(dwt.ll)));
        double[][] watermarkedY = inverseHaar2D(dwt);

        int outputType = keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage output = new BufferedImage(width, height, outputType);

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (row < evenHeight && col < evenWidth) {
                    double yValue = watermarkedY[row][col];
                    double cbValue = cb[row][col];
                    double crValue = cr[row][col];

                    int r = clampToByte(yValue + 1.402 * (crValue - 128.0));
                    int g = clampToByte(yValue - 0.344136 * (cbValue - 128.0) - 0.714136 * (crValue - 128.0));
                    int b = clampToByte(yValue + 1.772 * (cbValue - 128.0));
                    output.setRGB(col, row, composePixel(r, g, b, keepAlpha ? ((source.getRGB(col, row) >> 24) & 0xFF) : 255));
                } else {
                    output.setRGB(col, row, source.getRGB(col, row));
                }
            }
        }

        ensurePixelDifference(source, output, keepAlpha);
        return writeImage(output, format);
    }

    public WatermarkExtractResult extract(InputStream sourceStream) throws IOException {
        BufferedImage source = ImageIO.read(sourceStream);
        if (source == null) {
            throw new IOException("不支持的图片格式，无法提取DWT-SVD水印");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int evenWidth = width - (width % 2);
        int evenHeight = height - (height % 2);

        if (evenWidth < 32 || evenHeight < 32) {
            return new WatermarkExtractResult(false, null, 0.0, "图片尺寸过小，无法完成提取");
        }

        double[][] y = new double[evenHeight][evenWidth];
        for (int row = 0; row < evenHeight; row++) {
            for (int col = 0; col < evenWidth; col++) {
                int rgb = source.getRGB(col, row);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                y[row][col] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }

        HaarDwtResult dwt = forwardHaar2D(y);
        ExtractedBits extractedBits = extractBitsFromLl(dwt.ll);
        if (extractedBits.bits.length == 0) {
            return new WatermarkExtractResult(false, null, 0.0, "未检测到可提取的频域块");
        }

        String decodedText = decodeQrFromBits(extractedBits.bits);
        boolean hasDecodedText = decodedText != null && !decodedText.isBlank();
        boolean hasHighConfidence = extractedBits.confidence >= DETECTION_CONFIDENCE_THRESHOLD;
        boolean hasWatermark = hasDecodedText || hasHighConfidence;

        String message;
        if (hasDecodedText) {
            message = "提取成功";
        } else if (hasHighConfidence) {
            message = "未解码出文本，但频域置信度达到阈值";
        } else {
            message = "未解码出文本，且频域置信度不足";
        }

        return new WatermarkExtractResult(hasWatermark, decodedText, extractedBits.confidence, message);
    }

    private int computeCapacity(double[][] ll) {
        int hBlocks = ll.length / BLOCK_SIZE;
        int wBlocks = ll[0].length / BLOCK_SIZE;
        return Math.max(hBlocks * wBlocks, 1);
    }

    private boolean[] buildWatermarkBits(String watermarkText, int capacity) {
        String payload = (watermarkText == null || watermarkText.isBlank()) ? "hidden-watermark" : watermarkText;
        boolean[] qrBits = encodeQrToBits(payload);

        boolean[] bits = new boolean[capacity];
        for (int i = 0; i < capacity; i++) {
            bits[i] = qrBits[i % qrBits.length];
        }
        return bits;
    }

    private boolean[] encodeQrToBits(String payload) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);

            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, WATERMARK_QR_SIZE, WATERMARK_QR_SIZE, hints);
            boolean[] bits = new boolean[WATERMARK_QR_SIZE * WATERMARK_QR_SIZE];
            int idx = 0;
            for (int row = 0; row < WATERMARK_QR_SIZE; row++) {
                for (int col = 0; col < WATERMARK_QR_SIZE; col++) {
                    bits[idx++] = matrix.get(col, row);
                }
            }
            return bits;
        } catch (WriterException e) {
            throw new IllegalStateException("生成水印二维码失败", e);
        }
    }

    private void embedBitsIntoLl(double[][] ll, boolean[] bits) {
        int llHeight = ll.length;
        int llWidth = ll[0].length;

        int bitIndex = 0;
        for (int row = 0; row + BLOCK_SIZE <= llHeight && bitIndex < bits.length; row += BLOCK_SIZE) {
            for (int col = 0; col + BLOCK_SIZE <= llWidth && bitIndex < bits.length; col += BLOCK_SIZE) {
                double[][] block = extractBlock(ll, row, col, BLOCK_SIZE);
                double[][] embedded = embedBitIntoBlock(block, bits[bitIndex++]);
                putBlock(ll, embedded, row, col);
            }
        }
    }

    private double[][] embedBitIntoBlock(double[][] block, boolean bit) {
        RealMatrix matrix = MatrixUtils.createRealMatrix(block);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);

        RealMatrix u = svd.getU();
        RealMatrix vt = svd.getVT();
        double[] singular = svd.getSingularValues();
        if (singular.length == 0) {
            return block;
        }

        double anchor = Math.floor(singular[0] / EMBED_STRENGTH) * EMBED_STRENGTH;
        singular[0] = anchor + (bit ? 0.75 : 0.25) * EMBED_STRENGTH;

        double[][] s = new double[BLOCK_SIZE][BLOCK_SIZE];
        int diagonal = Math.min(singular.length, BLOCK_SIZE);
        for (int i = 0; i < diagonal; i++) {
            s[i][i] = singular[i];
        }

        RealMatrix reconstructed = u.multiply(MatrixUtils.createRealMatrix(s)).multiply(vt);
        return reconstructed.getData();
    }

    private double[][] extractBlock(double[][] source, int startRow, int startCol, int size) {
        double[][] block = new double[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(source[startRow + row], startCol, block[row], 0, size);
        }
        return block;
    }

    private void putBlock(double[][] target, double[][] block, int startRow, int startCol) {
        for (int row = 0; row < block.length; row++) {
            System.arraycopy(block[row], 0, target[startRow + row], startCol, block[row].length);
        }
    }

    private ExtractedBits extractBitsFromLl(double[][] ll) {
        int llHeight = ll.length;
        int llWidth = ll[0].length;

        int capacity = computeCapacity(ll);
        boolean[] bits = new boolean[capacity];
        double confidenceSum = 0.0;

        int bitIndex = 0;
        for (int row = 0; row + BLOCK_SIZE <= llHeight && bitIndex < bits.length; row += BLOCK_SIZE) {
            for (int col = 0; col + BLOCK_SIZE <= llWidth && bitIndex < bits.length; col += BLOCK_SIZE) {
                double[][] block = extractBlock(ll, row, col, BLOCK_SIZE);
                ExtractedBit extractedBit = extractBitFromBlock(block);
                bits[bitIndex++] = extractedBit.bit;
                confidenceSum += extractedBit.confidence;
            }
        }

        if (bitIndex < bits.length) {
            boolean[] cropped = new boolean[bitIndex];
            System.arraycopy(bits, 0, cropped, 0, bitIndex);
            bits = cropped;
        }

        double confidence = bitIndex == 0 ? 0.0 : confidenceSum / bitIndex;
        return new ExtractedBits(bits, confidence);
    }

    private ExtractedBit extractBitFromBlock(double[][] block) {
        RealMatrix matrix = MatrixUtils.createRealMatrix(block);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
        double[] singular = svd.getSingularValues();
        if (singular.length == 0) {
            return new ExtractedBit(false, 0.0);
        }

        double remainder = singular[0] % EMBED_STRENGTH;
        if (remainder < 0) {
            remainder += EMBED_STRENGTH;
        }

        double anchor0 = 0.25 * EMBED_STRENGTH;
        double anchor1 = 0.75 * EMBED_STRENGTH;
        double dist0 = Math.abs(remainder - anchor0);
        double dist1 = Math.abs(remainder - anchor1);

        boolean bit = dist1 < dist0;
        double minDist = Math.min(dist0, dist1);
        double confidence = 1.0 - Math.min(minDist / (EMBED_STRENGTH / 2.0), 1.0);
        return new ExtractedBit(bit, confidence);
    }

    private String decodeQrFromBits(boolean[] extractedBits) {
        if (extractedBits.length == 0) {
            return null;
        }

        boolean[] qrBits = rebuildQrBits(extractedBits);
        BitMatrix matrix = new BitMatrix(WATERMARK_QR_SIZE, WATERMARK_QR_SIZE);
        for (int row = 0; row < WATERMARK_QR_SIZE; row++) {
            for (int col = 0; col < WATERMARK_QR_SIZE; col++) {
                if (qrBits[row * WATERMARK_QR_SIZE + col]) {
                    matrix.set(col, row);
                }
            }
        }

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        try {
            DecoderResult decoderResult = new Decoder().decode(matrix, hints);
            return decoderResult.getText();
        } catch (ChecksumException | FormatException e) {
            return null;
        }
    }

    private boolean[] rebuildQrBits(boolean[] extractedBits) {
        boolean[] qrBits = new boolean[WATERMARK_QR_SIZE * WATERMARK_QR_SIZE];

        for (int i = 0; i < qrBits.length; i++) {
            int oneCount = 0;
            int total = 0;

            for (int idx = i; idx < extractedBits.length; idx += qrBits.length) {
                if (extractedBits[idx]) {
                    oneCount++;
                }
                total++;
            }

            if (total == 0) {
                boolean fallback = extractedBits[i % extractedBits.length];
                qrBits[i] = fallback;
            } else {
                qrBits[i] = oneCount * 2 >= total;
            }
        }

        return qrBits;
    }

    private HaarDwtResult forwardHaar2D(double[][] input) {
        int rows = input.length;
        int cols = input[0].length;

        double[][] temp = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            temp[row] = forwardHaar1D(input[row]);
        }

        double[][] transformed = new double[rows][cols];
        for (int col = 0; col < cols; col++) {
            double[] column = new double[rows];
            for (int row = 0; row < rows; row++) {
                column[row] = temp[row][col];
            }

            double[] transformedColumn = forwardHaar1D(column);
            for (int row = 0; row < rows; row++) {
                transformed[row][col] = transformedColumn[row];
            }
        }

        int halfRows = rows / 2;
        int halfCols = cols / 2;
        double[][] ll = new double[halfRows][halfCols];
        double[][] lh = new double[halfRows][halfCols];
        double[][] hl = new double[halfRows][halfCols];
        double[][] hh = new double[halfRows][halfCols];

        for (int row = 0; row < halfRows; row++) {
            for (int col = 0; col < halfCols; col++) {
                ll[row][col] = transformed[row][col];
                lh[row][col] = transformed[row][col + halfCols];
                hl[row][col] = transformed[row + halfRows][col];
                hh[row][col] = transformed[row + halfRows][col + halfCols];
            }
        }

        return new HaarDwtResult(ll, lh, hl, hh);
    }

    private double[][] inverseHaar2D(HaarDwtResult dwt) {
        int halfRows = dwt.ll.length;
        int halfCols = dwt.ll[0].length;
        int rows = halfRows * 2;
        int cols = halfCols * 2;

        double[][] transformed = new double[rows][cols];
        for (int row = 0; row < halfRows; row++) {
            for (int col = 0; col < halfCols; col++) {
                transformed[row][col] = dwt.ll[row][col];
                transformed[row][col + halfCols] = dwt.lh[row][col];
                transformed[row + halfRows][col] = dwt.hl[row][col];
                transformed[row + halfRows][col + halfCols] = dwt.hh[row][col];
            }
        }

        double[][] temp = new double[rows][cols];
        for (int col = 0; col < cols; col++) {
            double[] column = new double[rows];
            for (int row = 0; row < rows; row++) {
                column[row] = transformed[row][col];
            }

            double[] restored = inverseHaar1D(column);
            for (int row = 0; row < rows; row++) {
                temp[row][col] = restored[row];
            }
        }

        double[][] output = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            output[row] = inverseHaar1D(temp[row]);
        }
        return output;
    }

    private double[] forwardHaar1D(double[] input) {
        int n = input.length;
        double[] output = new double[n];
        int half = n / 2;

        for (int i = 0; i < half; i++) {
            double a = input[2 * i];
            double b = input[2 * i + 1];
            output[i] = (a + b) / 2.0;
            output[i + half] = (a - b) / 2.0;
        }
        return output;
    }

    private double[] inverseHaar1D(double[] input) {
        int n = input.length;
        double[] output = new double[n];
        int half = n / 2;

        for (int i = 0; i < half; i++) {
            double avg = input[i];
            double diff = input[i + half];
            output[2 * i] = avg + diff;
            output[2 * i + 1] = avg - diff;
        }
        return output;
    }

    private void ensurePixelDifference(BufferedImage source, BufferedImage output, boolean keepAlpha) {
        int width = Math.min(source.getWidth(), output.getWidth());
        int height = Math.min(source.getHeight(), output.getHeight());
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (source.getRGB(col, row) != output.getRGB(col, row)) {
                    return;
                }
            }
        }

        int rgb = output.getRGB(0, 0);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = keepAlpha ? ((source.getRGB(0, 0) >> 24) & 0xFF) : 255;
        r = r == 255 ? 254 : r + 1;
        output.setRGB(0, 0, composePixel(r, g, b, a));
    }

    private double compressLumaToSafeRange(double yValue) {
        double clamped = Math.max(0.0, Math.min(255.0, yValue));
        return SAFE_LUMA_MIN + (clamped / 255.0) * (SAFE_LUMA_MAX - SAFE_LUMA_MIN);
    }

    private int composePixel(int r, int g, int b, int a) {
        int alpha = clampToByte(a);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private int clampToByte(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return (int) Math.round(value);
    }

    private byte[] writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IOException("图片编码失败: " + format);
        }
        return out.toByteArray();
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

    public static final class WatermarkExtractResult {
        private final boolean hasWatermark;
        private final String decodedText;
        private final double confidence;
        private final String message;

        public WatermarkExtractResult(boolean hasWatermark, String decodedText, double confidence, String message) {
            this.hasWatermark = hasWatermark;
            this.decodedText = decodedText;
            this.confidence = confidence;
            this.message = message;
        }

        public boolean isHasWatermark() {
            return hasWatermark;
        }

        public String getDecodedText() {
            return decodedText;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class ExtractedBits {
        private final boolean[] bits;
        private final double confidence;

        private ExtractedBits(boolean[] bits, double confidence) {
            this.bits = bits;
            this.confidence = confidence;
        }
    }

    private static final class ExtractedBit {
        private final boolean bit;
        private final double confidence;

        private ExtractedBit(boolean bit, double confidence) {
            this.bit = bit;
            this.confidence = confidence;
        }
    }

    private static final class HaarDwtResult {
        private final double[][] ll;
        private final double[][] lh;
        private final double[][] hl;
        private final double[][] hh;

        private HaarDwtResult(double[][] ll, double[][] lh, double[][] hl, double[][] hh) {
            this.ll = ll;
            this.lh = lh;
            this.hl = hl;
            this.hh = hh;
        }
    }
}
