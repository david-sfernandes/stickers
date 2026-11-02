package com.example.samplestickerapp;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

/**
 * The form with the name and the publisher of a pack, used to create and to edit packs.
 */
final class PackFormDialog {
    private static final String PREFS_NAME = "sticker_pack_prefs";
    private static final String PREF_LAST_PUBLISHER = "last_publisher";

    interface Listener {
        void onConfirmed(@NonNull String name, @NonNull String publisher);
    }

    private PackFormDialog() {
    }

    static void show(@NonNull Context context, @StringRes int title, @StringRes int confirm, @Nullable String name, @Nullable String publisher, @NonNull Listener listener) {
        final View form = LayoutInflater.from(context).inflate(R.layout.dialog_pack_form, null);
        final TextInputLayout nameLayout = form.findViewById(R.id.pack_name_layout);
        final TextInputLayout publisherLayout = form.findViewById(R.id.pack_publisher_layout);
        final EditText nameInput = form.findViewById(R.id.pack_name_input);
        final EditText publisherInput = form.findViewById(R.id.pack_publisher_input);
        nameInput.setText(name);
        publisherInput.setText(publisher != null ? publisher : getLastPublisher(context));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(form)
                .setPositiveButton(confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String newName = nameInput.getText().toString().trim();
            final String newPublisher = publisherInput.getText().toString().trim();
            nameLayout.setError(TextUtils.isEmpty(newName) ? context.getString(R.string.form_required_field) : null);
            publisherLayout.setError(TextUtils.isEmpty(newPublisher) ? context.getString(R.string.form_required_field) : null);
            if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(newPublisher)) {
                return;
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_LAST_PUBLISHER, newPublisher).apply();
            dialog.dismiss();
            listener.onConfirmed(newName, newPublisher);
        }));
        dialog.show();
        nameInput.requestFocus();
    }

    @Nullable
    private static String getLastPublisher(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_LAST_PUBLISHER, null);
    }
}
