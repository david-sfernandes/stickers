package com.example.samplestickerapp;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;

/**
 * An error with a translated message that can be shown to the user as is.
 */
final class StickerException extends IOException {
    @StringRes
    private final int messageRes;

    StickerException(@StringRes int messageRes) {
        super("sticker error " + messageRes);
        this.messageRes = messageRes;
    }

    @StringRes
    int getMessageRes() {
        return messageRes;
    }

    @NonNull
    static String userMessage(@NonNull Context context, @NonNull Throwable error) {
        if (error instanceof StickerException) {
            return context.getString(((StickerException) error).messageRes);
        }
        Log.e("StickerException", "unexpected sticker error", error);
        return context.getString(error instanceof OutOfMemoryError ? R.string.error_file_too_large : R.string.error_generic);
    }
}
