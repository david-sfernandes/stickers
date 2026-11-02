package com.example.samplestickerapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes how a source image becomes a sticker. Everything is relative to the source size, so an
 * edit made over the first frame of an animation applies to all of its frames.
 */
final class StickerEdit {
    private static final float[] ROTATE_CLOCKWISE = {0, -1, 1, 1, 0, 0, 0, 0, 1};
    private static final float[] FLIP_HORIZONTAL = {-1, 0, 1, 0, 1, 0, 0, 0, 1};

    /* Maps the source to its rotated and flipped version, both as a square of side 1. */
    private final Matrix orientation = new Matrix();
    /* The selected area, as a fraction of the rotated and flipped source. */
    private final RectF crop = new RectF(0, 0, 1, 1);
    private boolean sidesSwapped;

    final List<StickerTextLayer> textLayers = new ArrayList<>();

    void rotateClockwise() {
        concatOrientation(ROTATE_CLOCKWISE);
        sidesSwapped = !sidesSwapped;
        resetCrop();
    }

    void flipHorizontally() {
        concatOrientation(FLIP_HORIZONTAL);
        resetCrop();
    }

    void resetOrientation() {
        orientation.reset();
        sidesSwapped = false;
        resetCrop();
    }

    void resetCrop() {
        crop.set(0, 0, 1, 1);
    }

    /**
     * @param cropRect the selected area in pixels of the bitmap returned by {@link #applyOrientation}
     */
    void setCrop(@NonNull Rect cropRect, int orientedWidth, int orientedHeight) {
        crop.set(cropRect.left / (float) orientedWidth, cropRect.top / (float) orientedHeight, cropRect.right / (float) orientedWidth, cropRect.bottom / (float) orientedHeight);
        crop.intersect(0, 0, 1, 1);
        if (crop.isEmpty()) {
            resetCrop();
        }
    }

    @NonNull
    Bitmap applyOrientation(@NonNull Bitmap source) {
        final int width = sidesSwapped ? source.getHeight() : source.getWidth();
        final int height = sidesSwapped ? source.getWidth() : source.getHeight();
        final Bitmap oriented = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Matrix matrix = new Matrix();
        matrix.setScale(1f / source.getWidth(), 1f / source.getHeight());
        matrix.postConcat(orientation);
        matrix.postScale(width, height);
        new Canvas(oriented).drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return oriented;
    }

    /**
     * The area of the sticker that shows the crop: it keeps its proportions and stays centered, with
     * transparent margins filling the rest of the square.
     */
    @NonNull
    RectF getTargetRect(int sourceWidth, int sourceHeight) {
        final float width = crop.width() * (sidesSwapped ? sourceHeight : sourceWidth);
        final float height = crop.height() * (sidesSwapped ? sourceWidth : sourceHeight);
        final float scale = StickerRenderer.SIZE / Math.max(width, height);
        final float left = (StickerRenderer.SIZE - width * scale) / 2f;
        final float top = (StickerRenderer.SIZE - height * scale) / 2f;
        return new RectF(left, top, StickerRenderer.SIZE - left, StickerRenderer.SIZE - top);
    }

    @NonNull
    Matrix getSourceToStickerMatrix(int sourceWidth, int sourceHeight) {
        final RectF target = getTargetRect(sourceWidth, sourceHeight);
        final Matrix matrix = new Matrix();
        matrix.setScale(1f / sourceWidth, 1f / sourceHeight);
        matrix.postConcat(orientation);
        matrix.postTranslate(-crop.left, -crop.top);
        matrix.postScale(target.width() / crop.width(), target.height() / crop.height());
        matrix.postTranslate(target.left, target.top);
        return matrix;
    }

    private void concatOrientation(@NonNull float[] values) {
        final Matrix matrix = new Matrix();
        matrix.setValues(values);
        orientation.postConcat(matrix);
    }
}
