package com.example.samplestickerapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.animated.gif.GifImage;
import com.facebook.animated.webp.WebPImage;
import com.facebook.common.references.CloseableReference;
import com.facebook.imagepipeline.animated.base.AnimatedImage;
import com.facebook.imagepipeline.animated.base.AnimatedImageResult;
import com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl;
import com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor;
import com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil;
import com.facebook.imagepipeline.common.ImageDecodeOptions;

/**
 * Gives access to the complete frames of a GIF or WebP file. These formats may store only what
 * changed between frames, the compositor is what turns that into full images.
 */
final class AnimatedSource {
    private final AnimatedImage image;
    private final AnimatedImageCompositor compositor;
    private final Bitmap lastFrame;
    private int lastFrameIndex = -1;

    /**
     * @return null if the bytes are not a GIF or a WebP file
     */
    @Nullable
    static AnimatedSource open(@NonNull byte[] bytes) {
        final AnimatedImage image;
        if (isGif(bytes)) {
            image = GifImage.createFromByteArray(bytes);
        } else if (isWebp(bytes)) {
            image = WebPImage.createFromByteArray(bytes, ImageDecodeOptions.defaults());
        } else {
            return null;
        }
        return new AnimatedSource(image);
    }

    private AnimatedSource(@NonNull AnimatedImage image) {
        this.image = image;
        lastFrame = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        final AnimatedImageResult result = AnimatedImageResult.forAnimatedImage(image);
        final Rect bounds = new Rect(0, 0, image.getWidth(), image.getHeight());
        compositor = new AnimatedImageCompositor(
                new AnimatedDrawableBackendImpl(new AnimatedDrawableUtil(), result, bounds, false),
                new AnimatedImageCompositor.Callback() {
                    @Override
                    public void onIntermediateResult(int frameNumber, Bitmap bitmap) {
                    }

                    @Nullable
                    @Override
                    public CloseableReference<Bitmap> getCachedBitmap(int frameNumber) {
                        // Avoids composing the animation from its start for every frame.
                        return frameNumber == lastFrameIndex ? CloseableReference.of(lastFrame, value -> {
                        }) : null;
                    }
                });
    }

    int getWidth() {
        return image.getWidth();
    }

    int getHeight() {
        return image.getHeight();
    }

    int getFrameCount() {
        return image.getFrameCount();
    }

    @NonNull
    int[] getFrameDurations() {
        return image.getFrameDurations();
    }

    boolean isAnimated() {
        return getFrameCount() > 1;
    }

    @NonNull
    Bitmap createFrameBitmap() {
        return Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
    }

    void renderFrame(int index, @NonNull Bitmap target) {
        compositor.renderFrame(index, target);
        final Canvas canvas = new Canvas(lastFrame);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawBitmap(target, 0, 0, null);
        lastFrameIndex = index;
    }

    void close() {
        image.dispose();
    }

    private static boolean isGif(@NonNull byte[] bytes) {
        return bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
    }

    private static boolean isWebp(@NonNull byte[] bytes) {
        return bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
