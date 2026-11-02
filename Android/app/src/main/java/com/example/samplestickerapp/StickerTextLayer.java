package com.example.samplestickerapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * A text placed over a sticker. Positions and sizes are in sticker pixels (0 to 512), so the same
 * drawing code is used by the on-screen preview and by the final image.
 */
final class StickerTextLayer {
    static final float MIN_TEXT_SIZE = 24;
    static final float MAX_TEXT_SIZE = 160;
    private static final float DEFAULT_TEXT_SIZE = 64;
    private static final float OUTLINE_RATIO = 0.16f;
    private static final float DARK_TEXT_LUMINANCE = 0.2f;
    private static final int MAX_LINE_WIDTH = StickerRenderer.SIZE - 24;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;

    String text;
    int color;
    float centerX = StickerRenderer.SIZE / 2f;
    float centerY = StickerRenderer.SIZE * 0.8f;
    float textSize = DEFAULT_TEXT_SIZE;

    StickerTextLayer(@NonNull String text, int color) {
        this.text = text;
        this.color = color;
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void draw(@NonNull Canvas canvas) {
        final StaticLayout textLayout = buildLayout();
        final int save = canvas.save();
        canvas.translate(centerX - MAX_LINE_WIDTH / 2f, centerY - textLayout.getHeight() / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(textSize * OUTLINE_RATIO);
        paint.setColor(ColorUtils.calculateLuminance(color) < DARK_TEXT_LUMINANCE ? Color.WHITE : Color.BLACK);
        textLayout.draw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        textLayout.draw(canvas);
        canvas.restoreToCount(save);
    }

    /**
     * The area covered by the text, in sticker pixels.
     */
    @NonNull
    RectF getBounds() {
        final StaticLayout textLayout = buildLayout();
        float width = 0;
        for (int i = 0; i < textLayout.getLineCount(); i++) {
            width = Math.max(width, textLayout.getLineWidth(i));
        }
        final float halfWidth = width / 2f + textSize * OUTLINE_RATIO;
        final float halfHeight = textLayout.getHeight() / 2f + textSize * OUTLINE_RATIO;
        return new RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
    }

    void invalidateLayout() {
        layout = null;
    }

    @NonNull
    private StaticLayout buildLayout() {
        if (layout == null || paint.getTextSize() != textSize) {
            paint.setTextSize(textSize);
            layout = new StaticLayout(text, paint, MAX_LINE_WIDTH, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        }
        return layout;
    }
}
