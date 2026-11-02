package com.example.samplestickerapp;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates animated stickers within the WhatsApp limits. There is no way of knowing the final size
 * up front, so it tries decreasing frame rates and qualities until the file fits.
 */
final class AnimatedStickerEncoder {
    static final int SIZE_LIMIT_BYTES = 500 * 1024;
    private static final int MAX_DURATION_MS = 10 * 1000;
    private static final int MIN_FRAME_DURATION_MS = 8;
    /* Same fix applied by browsers: GIFs without a usable delay would play way too fast. */
    private static final int MIN_SOURCE_DELAY_MS = 20;
    private static final int DEFAULT_SOURCE_DELAY_MS = 100;
    private static final int STILL_FRAME_DURATION_MS = 500;
    private static final int[] STILL_QUALITIES = {90, 75, 60, 45, 30, 15};
    private static final int CONTAINER_OVERHEAD_BYTES = 64;
    /* {frames per second, webp quality}, from best to smallest. */
    private static final int[][] ATTEMPTS = {{15, 75}, {15, 55}, {12, 45}, {10, 40}, {8, 35}, {6, 30}, {5, 20}, {4, 15}, {3, 10}};

    private AnimatedStickerEncoder() {
    }

    @NonNull
    static byte[] encode(@NonNull AnimatedSource source, @NonNull StickerEdit edit) throws IOException {
        final Bitmap frame = source.createFrameBitmap();
        final Bitmap sticker = StickerRenderer.createStickerBitmap();
        for (int[] attempt : ATTEMPTS) {
            final byte[] result = tryEncode(source, edit, frame, sticker, attempt[0], attempt[1]);
            if (result != null) {
                return result;
            }
        }
        throw new StickerException(R.string.error_gif_too_large);
    }

    /**
     * WhatsApp does not accept static images in animated packs, so the image becomes an animation
     * in which nothing moves. Its second frame only needs to redraw a tiny area.
     */
    @NonNull
    static byte[] encodeStill(@NonNull Bitmap sticker) throws IOException {
        final Bitmap corner = Bitmap.createBitmap(sticker, 0, 0, 2, 2);
        final byte[] cornerWebp = StickerImageProcessor.compressToWebp(corner, STILL_QUALITIES[0]);
        for (int quality : STILL_QUALITIES) {
            final List<WebpAnimationMuxer.Frame> frames = new ArrayList<>();
            frames.add(new WebpAnimationMuxer.Frame(StickerImageProcessor.compressToWebp(sticker, quality), 0, 0, StickerRenderer.SIZE, StickerRenderer.SIZE, STILL_FRAME_DURATION_MS));
            frames.add(new WebpAnimationMuxer.Frame(cornerWebp, 0, 0, 2, 2, STILL_FRAME_DURATION_MS));
            final byte[] result = WebpAnimationMuxer.mux(frames, StickerRenderer.SIZE, StickerRenderer.SIZE);
            if (result.length <= SIZE_LIMIT_BYTES) {
                return result;
            }
        }
        throw new StickerException(R.string.error_image_too_large);
    }

    @Nullable
    private static byte[] tryEncode(@NonNull AnimatedSource source, @NonNull StickerEdit edit, @NonNull Bitmap frame, @NonNull Bitmap sticker, int framesPerSecond, int quality) throws IOException {
        final int[] delays = getPlayableDelays(source.getFrameDurations());
        final int minFrameInterval = 1000 / framesPerSecond;

        final List<WebpAnimationMuxer.Frame> frames = new ArrayList<>();
        final int[] previousPixels = new int[StickerRenderer.SIZE * StickerRenderer.SIZE];
        final int[] pixels = new int[StickerRenderer.SIZE * StickerRenderer.SIZE];
        int totalBytes = CONTAINER_OVERHEAD_BYTES;
        int elapsed = 0;
        int nextFrameTime = 0;
        for (int i = 0; i < delays.length && elapsed < MAX_DURATION_MS; i++) {
            final int duration = Math.min(delays[i], MAX_DURATION_MS - elapsed);
            final int start = elapsed;
            elapsed += duration;
            // A skipped frame leaves its time to the previous one, so the animation keeps its speed.
            if (start < nextFrameTime || duration < MIN_FRAME_DURATION_MS) {
                frames.get(frames.size() - 1).durationMs += duration;
                continue;
            }
            source.renderFrame(i, frame);
            StickerRenderer.render(frame, edit, sticker, true);
            sticker.getPixels(pixels, 0, StickerRenderer.SIZE, 0, 0, StickerRenderer.SIZE, StickerRenderer.SIZE);

            final WebpAnimationMuxer.Frame encoded;
            if (frames.isEmpty()) {
                encoded = encodeRegion(sticker, 0, 0, StickerRenderer.SIZE, StickerRenderer.SIZE, duration, quality);
            } else {
                final int[] changed = findChangedRegion(previousPixels, pixels);
                if (changed == null) {
                    frames.get(frames.size() - 1).durationMs += duration;
                    continue;
                }
                encoded = encodeRegion(sticker, changed[0], changed[1], changed[2], changed[3], duration, quality);
            }
            nextFrameTime = start + minFrameInterval;
            System.arraycopy(pixels, 0, previousPixels, 0, pixels.length);
            frames.add(encoded);
            totalBytes += encoded.webp.length;
            if (totalBytes > SIZE_LIMIT_BYTES) {
                return null;
            }
        }
        if (frames.size() < 2) {
            // All frames are equal, but the result still has to be a valid animation.
            return encodeStill(sticker);
        }
        final byte[] result = WebpAnimationMuxer.mux(frames, StickerRenderer.SIZE, StickerRenderer.SIZE);
        return result.length <= SIZE_LIMIT_BYTES ? result : null;
    }

    @NonNull
    private static WebpAnimationMuxer.Frame encodeRegion(@NonNull Bitmap sticker, int x, int y, int width, int height, int duration, int quality) throws IOException {
        final Bitmap region = width == sticker.getWidth() && height == sticker.getHeight() ? sticker : Bitmap.createBitmap(sticker, x, y, width, height);
        return new WebpAnimationMuxer.Frame(StickerImageProcessor.compressToWebp(region, quality), x, y, width, height, duration);
    }

    /**
     * @return {x, y, width, height} of the changed area, or null if nothing changed. Frame offsets
     * must be even in an animated WebP, so x and y are rounded down.
     */
    @Nullable
    static int[] findChangedRegion(@NonNull int[] previous, @NonNull int[] current) {
        int left = StickerRenderer.SIZE;
        int top = StickerRenderer.SIZE;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < StickerRenderer.SIZE; y++) {
            final int row = y * StickerRenderer.SIZE;
            for (int x = 0; x < StickerRenderer.SIZE; x++) {
                if (previous[row + x] != current[row + x]) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = y;
                }
            }
        }
        if (right < 0) {
            return null;
        }
        left &= ~1;
        top &= ~1;
        return new int[]{left, top, right - left + 1, bottom - top + 1};
    }

    @NonNull
    static int[] getPlayableDelays(@NonNull int[] delays) {
        final int[] playable = new int[delays.length];
        for (int i = 0; i < delays.length; i++) {
            playable[i] = delays[i] < MIN_SOURCE_DELAY_MS ? DEFAULT_SOURCE_DELAY_MS : delays[i];
        }
        return playable;
    }
}
