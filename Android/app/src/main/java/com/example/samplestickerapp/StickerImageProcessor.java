package com.example.samplestickerapp;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class StickerImageProcessor {
    private static final int STICKER_SIZE_PX = 512;
    private static final int STICKER_SIZE_LIMIT_BYTES = 100 * 1024;
    private static final int WEBP_START_QUALITY = 100;
    private static final int WEBP_MIN_QUALITY = 30;
    private static final int WEBP_STEP = 5;

    private StickerImageProcessor() {
    }

    @NonNull
    static byte[] createStickerWebp(@NonNull Context context, @NonNull Uri imageUri) throws IOException {
        final Bitmap sourceBitmap = decodeBitmapFromUri(context.getContentResolver(), imageUri);
        if (sourceBitmap == null) {
            throw new IOException("unable to decode selected image");
        }

        final Bitmap stickerBitmap = Bitmap.createBitmap(STICKER_SIZE_PX, STICKER_SIZE_PX, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(stickerBitmap);

        final int sourceWidth = sourceBitmap.getWidth();
        final int sourceHeight = sourceBitmap.getHeight();
        final int squareSize = Math.min(sourceWidth, sourceHeight);
        final int offsetX = (sourceWidth - squareSize) / 2;
        final int offsetY = (sourceHeight - squareSize) / 2;
        final Rect srcRect = new Rect(offsetX, offsetY, offsetX + squareSize, offsetY + squareSize);
        final Rect dstRect = new Rect(0, 0, STICKER_SIZE_PX, STICKER_SIZE_PX);
        canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null);

        for (int quality = WEBP_START_QUALITY; quality >= WEBP_MIN_QUALITY; quality -= WEBP_STEP) {
            final byte[] bytes = compressBitmapToWebp(stickerBitmap, quality);
            if (bytes.length <= STICKER_SIZE_LIMIT_BYTES) {
                return bytes;
            }
        }
        throw new IOException("could not compress image below 100KB");
    }

    @Nullable
    static Bitmap decodeBitmap(@NonNull InputStream inputStream) {
        return BitmapFactory.decodeStream(inputStream);
    }

    @Nullable
    private static Bitmap decodeBitmapFromUri(@NonNull ContentResolver contentResolver, @NonNull Uri imageUri) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(imageUri)) {
            if (inputStream == null) {
                return null;
            }
            return decodeBitmap(inputStream);
        }
    }

    @NonNull
    private static byte[] compressBitmapToWebp(@NonNull Bitmap bitmap, int quality) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final boolean success = bitmap.compress(Bitmap.CompressFormat.WEBP, quality, byteArrayOutputStream);
        if (!success) {
            throw new IOException("could not encode image to webp");
        }
        return byteArrayOutputStream.toByteArray();
    }
}
