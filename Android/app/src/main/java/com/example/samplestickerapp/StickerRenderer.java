package com.example.samplestickerapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;

import androidx.annotation.NonNull;

final class StickerRenderer {
    static final int SIZE = 512;

    private StickerRenderer() {
    }

    @NonNull
    static Bitmap createStickerBitmap() {
        return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    }

    /**
     * Draws the cropped source and the texts into a {@link #SIZE} x {@link #SIZE} bitmap.
     */
    static void render(@NonNull Bitmap source, @NonNull StickerEdit edit, @NonNull Bitmap target, boolean includeText) {
        final Canvas canvas = new Canvas(target);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        final int save = canvas.save();
        canvas.clipRect(edit.getTargetRect(source.getWidth(), source.getHeight()));
        canvas.drawBitmap(source, edit.getSourceToStickerMatrix(source.getWidth(), source.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.restoreToCount(save);
        if (includeText) {
            for (StickerTextLayer layer : edit.textLayers) {
                layer.draw(canvas);
            }
        }
    }
}
