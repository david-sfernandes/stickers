package com.example.samplestickerapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class StickerEmojisTest {
    private static final String SMILE = "😀";
    private static final String HEART = "❤";
    private static final String CAT = "🐱";
    private static final String DOG = "🐶";

    @Test
    public void keepsOnlyEmojis() {
        assertEquals(Arrays.asList(SMILE, CAT), StickerEmojis.parse("abc " + SMILE + ", 12 " + CAT));
    }

    @Test
    public void limitsToThreeAndDropsRepeated() {
        assertEquals(Arrays.asList(SMILE, HEART, CAT), StickerEmojis.parse(SMILE + SMILE + HEART + CAT + DOG));
    }

    @Test
    public void fallsBackToDefaultEmoji() {
        assertEquals(Collections.singletonList(StickerEmojis.DEFAULT_EMOJI), StickerEmojis.parse(""));
        assertEquals(Collections.singletonList(StickerEmojis.DEFAULT_EMOJI), StickerEmojis.parse("abc 123"));
    }
}
