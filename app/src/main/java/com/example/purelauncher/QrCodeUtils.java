package com.example.purelauncher;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;

final class QrCodeUtils {

    private static final String PAIRING_PREFIX = "PLINK:";

    private QrCodeUtils() {
    }

    static String buildPairingPayload(String childUid) {
        return PAIRING_PREFIX + childUid.trim();
    }

    static String extractChildUid(String rawText) {
        if (rawText == null) {
            return null;
        }
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(PAIRING_PREFIX)) {
            return trimmed.substring(PAIRING_PREFIX.length()).trim();
        }
        return trimmed;
    }

    static Bitmap generateQrBitmap(String content, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    static String decodeQrBitmap(Bitmap bitmap) throws ReaderException {
        Result result = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BitmapLuminanceSource(bitmap)))
        );
        return result == null ? null : result.getText();
    }

    private static final class BitmapLuminanceSource extends LuminanceSource {
        private final byte[] luminances;

        BitmapLuminanceSource(Bitmap bitmap) {
            super(bitmap.getWidth(), bitmap.getHeight());
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            luminances = new byte[width * height];
            for (int index = 0; index < pixels.length; index++) {
                int pixel = pixels[index];
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;
                luminances[index] = (byte) ((red + green + blue) / 3);
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            int width = getWidth();
            if (row == null || row.length < width) {
                row = new byte[width];
            }
            System.arraycopy(luminances, y * width, row, 0, width);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return luminances;
        }

        @Override
        public boolean isCropSupported() {
            return false;
        }

        @Override
        public boolean isRotateSupported() {
            return false;
        }
    }
}