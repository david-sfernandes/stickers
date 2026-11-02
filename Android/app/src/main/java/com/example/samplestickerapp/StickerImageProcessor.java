package com.example.samplestickerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class StickerImageProcessor {
    private static final int STICKER_SIZE_LIMIT_BYTES = 100 * 1024;
    private static final int WEBP_START_QUALITY = 100;
    private static final int WEBP_MIN_QUALITY = 10;
    private static final int WEBP_STEP = 5;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    /* Larger than any crop needs, as the final sticker has 512 pixels. */
    private static final int MAX_DECODE_SIZE_PX = 1536;

    private StickerImageProcessor() {
    }

    @NonNull
    static byte[] readBytes(@NonNull Context context, @NonNull Uri uri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new StickerException(R.string.error_cannot_read_image);
            }
            final byte[] buffer = new byte[16384];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                if (outputStream.size() > MAX_FILE_BYTES) {
                    throw new StickerException(R.string.error_file_too_large);
                }
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Decodes a limited size version of the image, already standing in the right orientation.
     */
    @NonNull
    static Bitmap decodeImage(@NonNull byte[] bytes) throws IOException {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new StickerException(R.string.error_cannot_read_image);
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_SIZE_PX);
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) {
            throw new StickerException(R.string.error_cannot_read_image);
        }
        final Matrix orientation = readOrientation(bytes);
        return orientation.isIdentity() ? bitmap : Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), orientation, true);
    }

    static int calculateSampleSize(int width, int height, int maxSize) {
        int sampleSize = 1;
        // Sample sizes are powers of two and may leave the image larger than maxSize, never smaller.
        while (Math.max(width, height) / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    @NonNull
    private static Matrix readOrientation(@NonNull byte[] bytes) {
        final Matrix matrix = new Matrix();
        try {
            final ExifInterface exif = new ExifInterface(new ByteArrayInputStream(bytes));
            final int rotation = exif.getRotationDegrees();
            if (rotation != 0) {
                matrix.postRotate(rotation);
            }
            if (exif.isFlipped()) {
                matrix.postScale(-1, 1);
            }
        } catch (IOException | RuntimeException ignored) {
            // Formats without EXIF data, the image is used as it is.
        }
        return matrix;
    }

    /**
     * Static stickers are limited to 100KB. The quality only goes as low as needed to fit.
     */
    @NonNull
    static byte[] createStaticSticker(@NonNull Bitmap sticker) throws IOException {
        for (int quality = WEBP_START_QUALITY; quality >= WEBP_MIN_QUALITY; quality -= WEBP_STEP) {
            final byte[] bytes = compressToWebp(sticker, quality);
            if (bytes.length <= STICKER_SIZE_LIMIT_BYTES) {
                return bytes;
            }
        }
        throw new StickerException(R.string.error_image_too_large);
    }

    /**
     * The first frame of a sticker, which works for animated stickers unlike BitmapFactory.
     */
    @Nullable
    static Bitmap decodeFirstFrame(@NonNull byte[] sticker) {
        AnimatedSource source = null;
        try {
            source = AnimatedSource.open(sticker);
            if (source != null) {
                final Bitmap frame = source.createFrameBitmap();
                source.renderFrame(0, frame);
                return frame;
            }
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            // Falls back to the platform decoder below.
        } finally {
            if (source != null) {
                source.close();
            }
        }
        return BitmapFactory.decodeByteArray(sticker, 0, sticker.length);
    }

    @NonNull
    static byte[] compressToWebp(@NonNull Bitmap bitmap, int quality) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final boolean success = bitmap.compress(Bitmap.CompressFormat.WEBP, quality, byteArrayOutputStream);
        if (!success) {
            throw new IOException("could not encode image to webp");
        }
        return byteArrayOutputStream.toByteArray();
    }
}
