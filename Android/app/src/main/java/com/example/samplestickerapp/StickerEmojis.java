package com.example.samplestickerapp;

import androidx.annotation.NonNull;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WhatsApp uses the emojis of a sticker to find it, each sticker needs from 1 to 3 of them.
 */
final class StickerEmojis {
    static final String DEFAULT_EMOJI = "🙂";

    private StickerEmojis() {
    }

    /**
     * Picks the emojis out of what was typed, which may have other characters too.
     */
    @NonNull
    static List<String> parse(@NonNull String input) {
        final List<String> emojis = new ArrayList<>();
        final BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(input);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE && emojis.size() < StickerPackValidator.EMOJI_MAX_LIMIT; start = end, end = iterator.next()) {
            final String character = input.substring(start, end);
            if (isEmoji(character) && !emojis.contains(character)) {
                emojis.add(character);
            }
        }
        return emojis.isEmpty() ? Collections.singletonList(DEFAULT_EMOJI) : emojis;
    }

    private static boolean isEmoji(@NonNull String character) {
        final int codePoint = character.codePointAt(0);
        return Character.getType(codePoint) == Character.OTHER_SYMBOL || codePoint >= 0x1F000 && codePoint <= 0x1FAFF;
    }
}
