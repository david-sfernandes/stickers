/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class StickerPackDetailsActivity extends AddStickerPackActivity {

    /**
     * Do not change below values of below 3 lines as this is also used by WhatsApp
     */
    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";

    public static final String EXTRA_SHOW_UP_BUTTON = "show_up_button";
    public static final String EXTRA_STICKER_PACK_DATA = "sticker_pack";
    private static final int PICK_IMAGE_REQUEST = 301;
    private static final int EDIT_STICKER_REQUEST = 302;


    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private StickerPreviewAdapter stickerPreviewAdapter;
    private int numColumns;
    private MaterialButton addButton;
    private MaterialButton addStickerButton;
    private View alreadyAddedText;
    private View tapToPreviewText;
    private View emptyStickersView;
    private View progressIndicator;
    private boolean isWhitelisted;
    private StickerPack stickerPack;
    private View divider;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    private TextView packNameTextView;
    private TextView packPublisherTextView;
    private ImageView packTrayIcon;
    private TextView packSizeTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_details);
        boolean showUpButton = getIntent().getBooleanExtra(EXTRA_SHOW_UP_BUTTON, false);
        stickerPack = getIntent().getParcelableExtra(EXTRA_STICKER_PACK_DATA);
        packNameTextView = findViewById(R.id.pack_name);
        packPublisherTextView = findViewById(R.id.author);
        packTrayIcon = findViewById(R.id.tray_image);
        packSizeTextView = findViewById(R.id.pack_size);
        SimpleDraweeView expandedStickerView = findViewById(R.id.sticker_details_expanded_sticker);

        addButton = findViewById(R.id.add_to_whatsapp_button);
        addStickerButton = findViewById(R.id.add_sticker_button);
        alreadyAddedText = findViewById(R.id.already_added_text);
        tapToPreviewText = findViewById(R.id.sticker_pack_details_tap_to_preview);
        emptyStickersView = findViewById(R.id.empty_stickers);
        progressIndicator = findViewById(R.id.details_progress);
        layoutManager = new GridLayoutManager(this, 1);
        recyclerView = findViewById(R.id.sticker_list);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(pageLayoutListener);
        recyclerView.addOnScrollListener(dividerScrollListener);
        divider = findViewById(R.id.divider);
        if (stickerPreviewAdapter == null) {
            stickerPreviewAdapter = new StickerPreviewAdapter(
                    getLayoutInflater(),
                    R.drawable.sticker_error,
                    getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size),
                    stickerPack,
                    expandedStickerView,
                    this::onStickerLongPress
            );
            recyclerView.setAdapter(stickerPreviewAdapter);
        }
        addButton.setOnClickListener(v -> addStickerPackToWhatsApp(stickerPack.identifier, stickerPack.name));
        addStickerButton.setOnClickListener(v -> selectImageForSticker());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(showUpButton);
            getSupportActionBar().setTitle("");
        }
        updatePackUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_edit_pack) {
            PackFormDialog.show(this, R.string.edit_pack_title, R.string.edit_pack_confirm, stickerPack.name, stickerPack.publisher, (name, publisher) -> new UpdatePackAsyncTask(this, packContext -> UserStickerPackStore.updatePackDetails(packContext, stickerPack.identifier, name, publisher)).execute());
            return true;
        } else if (item.getItemId() == R.id.action_delete_pack) {
            confirmDeletePack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private final ViewTreeObserver.OnGlobalLayoutListener pageLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            final int usableWidth = recyclerView.getWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
            setNumColumns(Math.max(1, usableWidth / recyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size)), usableWidth);
        }
    };

    private void setNumColumns(int numColumns, int usableWidth) {
        if (this.numColumns != numColumns) {
            layoutManager.setSpanCount(numColumns);
            this.numColumns = numColumns;
            if (stickerPreviewAdapter != null) {
                // Cells take the whole width of a column and stay square.
                stickerPreviewAdapter.setCellHeight(usableWidth / numColumns - 2 * getResources().getDimensionPixelSize(R.dimen.sticker_cell_margin));
                stickerPreviewAdapter.notifyDataSetChanged();
            }
        }
    }

    private final RecyclerView.OnScrollListener dividerScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull final RecyclerView recyclerView, final int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            updateDivider(recyclerView);
        }

        @Override
        public void onScrolled(@NonNull final RecyclerView recyclerView, final int dx, final int dy) {
            super.onScrolled(recyclerView, dx, dy);
            updateDivider(recyclerView);
        }

        private void updateDivider(RecyclerView recyclerView) {
            boolean showDivider = recyclerView.computeVerticalScrollOffset() > 0;
            if (divider != null) {
                divider.setVisibility(showDivider ? View.VISIBLE : View.INVISIBLE);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
        whiteListCheckAsyncTask.execute(stickerPack);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
    }

    private void updateAddUI(boolean isWhitelisted) {
        this.isWhitelisted = isWhitelisted;
        final int stickerCount = stickerPack.getStickers().size();
        final int missing = UserStickerPackStore.MIN_STICKERS_PER_PACK - stickerCount;
        addButton.setVisibility(isWhitelisted ? View.GONE : View.VISIBLE);
        addButton.setEnabled(missing <= 0);
        if (missing > 0) {
            addButton.setText(getResources().getQuantityString(R.plurals.pack_missing_stickers, missing, missing));
        } else {
            addButton.setText(R.string.add_to_whatsapp);
        }
        alreadyAddedText.setVisibility(isWhitelisted ? View.VISIBLE : View.GONE);
        tapToPreviewText.setVisibility(stickerCount > 0 ? View.VISIBLE : View.GONE);
        emptyStickersView.setVisibility(stickerCount > 0 ? View.GONE : View.VISIBLE);

        final boolean isFull = stickerCount >= UserStickerPackStore.MAX_STICKERS_PER_PACK;
        addStickerButton.setEnabled(!isFull);
        addStickerButton.setText(isFull ? R.string.pack_is_full : R.string.add_sticker_to_pack);
    }

    @Override
    protected void onPackValidationStateChanged(boolean validating) {
        progressIndicator.setVisibility(validating ? View.VISIBLE : View.INVISIBLE);
        addButton.setEnabled(!validating);
    }

    private void selectImageForSticker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (ActivityNotFoundException e) {
            MessageDialogFragment.newInstance(R.string.create_pack_title, getString(R.string.image_picker_not_available)).show(getSupportFragmentManager(), "image_picker_unavailable");
        }
    }

    private void openEditor(@NonNull Uri source, @Nullable Sticker replacedSticker) {
        final Intent intent = new Intent(this, StickerEditorActivity.class);
        intent.setData(source);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(StickerEditorActivity.EXTRA_PACK_ID, stickerPack.identifier);
        intent.putExtra(StickerEditorActivity.EXTRA_PACK_ANIMATED, stickerPack.animatedStickerPack);
        intent.putExtra(StickerEditorActivity.EXTRA_OTHER_STICKERS, stickerPack.getStickers().size() - (replacedSticker != null ? 1 : 0));
        if (replacedSticker != null) {
            intent.putExtra(StickerEditorActivity.EXTRA_REPLACED_FILE, replacedSticker.imageFileName);
            intent.putExtra(StickerEditorActivity.EXTRA_EMOJIS, TextUtils.join("", replacedSticker.emojis));
        }
        startActivityForResult(intent, EDIT_STICKER_REQUEST);
    }

    private void confirmDeletePack() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_pack_title)
                .setMessage(R.string.delete_pack_message)
                .setPositiveButton(R.string.delete_pack_confirm, (dialogInterface, which) -> new DeletePackAsyncTask(this).execute(stickerPack.identifier))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onStickerLongPress(@NonNull Sticker sticker) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_sticker_actions);
        sheet.findViewById(R.id.action_edit_sticker).setOnClickListener(v -> {
            sheet.dismiss();
            openEditor(StickerPackLoader.getStickerAssetUri(stickerPack.identifier, sticker.imageFileName), sticker);
        });
        sheet.findViewById(R.id.action_set_tray).setOnClickListener(v -> {
            sheet.dismiss();
            new UpdatePackAsyncTask(this, packContext -> UserStickerPackStore.setTraySticker(packContext, stickerPack.identifier, sticker.imageFileName)).execute();
        });
        sheet.findViewById(R.id.action_remove_sticker).setOnClickListener(v -> {
            sheet.dismiss();
            confirmRemoveSticker(sticker);
        });
        sheet.show();
    }

    private void confirmRemoveSticker(@NonNull Sticker sticker) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove_sticker_title)
                .setMessage(R.string.remove_sticker_message)
                .setPositiveButton(R.string.remove_sticker_confirm, (dialogInterface, which) -> new UpdatePackAsyncTask(this, packContext -> UserStickerPackStore.removeStickerFromPack(packContext, stickerPack.identifier, sticker.imageFileName)).execute())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reloadCurrentPack() {
        new ReloadPackAsyncTask(this).execute(stickerPack.identifier);
    }

    private void updatePackUI() {
        stickerPreviewAdapter.hideExpandedStickerPreview();
        packNameTextView.setText(stickerPack.name);
        packPublisherTextView.setText(stickerPack.publisher);
        // The file name never changes, so the view would keep the old image without the reset.
        packTrayIcon.setImageURI(null);
        packTrayIcon.setImageURI(StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile));
        packSizeTextView.setText(getString(R.string.pack_summary, stickerPack.getStickers().size(), UserStickerPackStore.MAX_STICKERS_PER_PACK, Formatter.formatShortFileSize(this, stickerPack.getTotalSize())));
        findViewById(R.id.sticker_pack_animation_indicator).setVisibility(stickerPack.animatedStickerPack ? View.VISIBLE : View.GONE);
        stickerPreviewAdapter.setStickerPack(stickerPack);
        stickerPreviewAdapter.notifyDataSetChanged();
        updateAddUI(isWhitelisted);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            openEditor(data.getData(), null);
        } else if (requestCode == EDIT_STICKER_REQUEST && resultCode == RESULT_OK) {
            reloadCurrentPack();
        }
    }

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, Boolean> {
        private final WeakReference<StickerPackDetailsActivity> stickerPackDetailsActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackDetailsActivity stickerPackListActivity) {
            this.stickerPackDetailsActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @Override
        protected final Boolean doInBackground(StickerPack... stickerPacks) {
            StickerPack stickerPack = stickerPacks[0];
            final StickerPackDetailsActivity stickerPackDetailsActivity = stickerPackDetailsActivityWeakReference.get();
            if (stickerPackDetailsActivity == null) {
                return false;
            }
            return WhitelistCheck.isWhitelisted(stickerPackDetailsActivity, stickerPack.identifier);
        }

        @Override
        protected void onPostExecute(Boolean isWhitelisted) {
            final StickerPackDetailsActivity stickerPackDetailsActivity = stickerPackDetailsActivityWeakReference.get();
            if (stickerPackDetailsActivity != null) {
                stickerPackDetailsActivity.updateAddUI(isWhitelisted);
            }
        }
    }

    static class ReloadPackAsyncTask extends AsyncTask<String, Void, StickerPack> {
        private final WeakReference<StickerPackDetailsActivity> activityReference;

        ReloadPackAsyncTask(StickerPackDetailsActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected StickerPack doInBackground(String... identifiers) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return null;
            }
            try {
                ArrayList<StickerPack> packs = StickerPackLoader.fetchStickerPacks(activity);
                for (StickerPack pack : packs) {
                    if (identifiers[0].equals(pack.identifier)) {
                        return pack;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        @Override
        protected void onPostExecute(StickerPack pack) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null || pack == null) {
                return;
            }
            activity.stickerPack = pack;
            activity.updatePackUI();
            activity.whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(activity);
            activity.whiteListCheckAsyncTask.execute(pack);
            activity.invalidateOptionsMenu();
        }
    }

    static class DeletePackAsyncTask extends AsyncTask<String, Void, String> {
        private final WeakReference<StickerPackDetailsActivity> activityReference;

        DeletePackAsyncTask(StickerPackDetailsActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... identifiers) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return "activity unavailable";
            }
            try {
                boolean deleted = UserStickerPackStore.deletePack(activity, identifiers[0]);
                if (!deleted) {
                    return activity.getString(R.string.delete_pack_not_found);
                }
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return StickerException.userMessage(activity, e);
            }
        }

        @Override
        protected void onPostExecute(String error) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (error != null) {
                MessageDialogFragment.newInstance(R.string.title_validation_error, error).show(activity.getSupportFragmentManager(), "delete_pack_error");
                return;
            }
            activity.finish();
        }
    }

    interface PackUpdate {
        void apply(Context context) throws IOException;
    }

    static class UpdatePackAsyncTask extends AsyncTask<Void, Void, Throwable> {
        private final WeakReference<StickerPackDetailsActivity> activityReference;
        private final Context context;
        private final PackUpdate update;

        UpdatePackAsyncTask(StickerPackDetailsActivity activity, PackUpdate update) {
            this.activityReference = new WeakReference<>(activity);
            this.context = activity.getApplicationContext();
            this.update = update;
        }

        @Override
        protected Throwable doInBackground(Void... voids) {
            try {
                update.apply(context);
                context.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Throwable error) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (error != null) {
                MessageDialogFragment.newInstance(R.string.title_validation_error, StickerException.userMessage(activity, error)).show(activity.getSupportFragmentManager(), "update_pack_error");
                return;
            }
            activity.reloadCurrentPack();
        }
    }
}
