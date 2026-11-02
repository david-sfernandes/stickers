package com.example.samplestickerapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AnimatedStickerEncoderTest {
    private static final int SIZE = StickerRenderer.SIZE;

    @Test
    public void equalFramesHaveNoChangedRegion() {
        assertNull(AnimatedStickerEncoder.findChangedRegion(new int[SIZE * SIZE], new int[SIZE * SIZE]));
    }

    @Test
    public void changedRegionCoversAllChangedPixels() {
        final int[] current = new int[SIZE * SIZE];
        current[20 * SIZE + 10] = 1;
        current[40 * SIZE + 30] = 1;
        assertArrayEquals(new int[]{10, 20, 21, 21}, AnimatedStickerEncoder.findChangedRegion(new int[SIZE * SIZE], current));
    }

    @Test
    public void changedRegionStartsAtEvenCoordinates() {
        final int[] current = new int[SIZE * SIZE];
        current[21 * SIZE + 11] = 1;
        assertArrayEquals(new int[]{10, 20, 2, 2}, AnimatedStickerEncoder.findChangedRegion(new int[SIZE * SIZE], current));
    }

    @Test
    public void changedRegionCanReachTheLastPixel() {
        final int[] current = new int[SIZE * SIZE];
        current[SIZE * SIZE - 1] = 1;
        assertArrayEquals(new int[]{510, 510, 2, 2}, AnimatedStickerEncoder.findChangedRegion(new int[SIZE * SIZE], current));
    }

    @Test
    public void unusableDelaysBecomeTheDefault() {
        assertArrayEquals(new int[]{100, 100, 20, 150}, AnimatedStickerEncoder.getPlayableDelays(new int[]{0, 10, 20, 150}));
    }
}
