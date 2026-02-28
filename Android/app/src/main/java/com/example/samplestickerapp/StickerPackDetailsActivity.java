/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class StickerPackDetailsActivity extends AddStickerPackActivity {

    /**
     * Do not change below values of below 3 lines as this is also used by WhatsApp
     */
    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";

    public static final String EXTRA_STICKER_PACK_WEBSITE = "sticker_pack_website";
    public static final String EXTRA_STICKER_PACK_EMAIL = "sticker_pack_email";
    public static final String EXTRA_STICKER_PACK_PRIVACY_POLICY = "sticker_pack_privacy_policy";
    public static final String EXTRA_STICKER_PACK_LICENSE_AGREEMENT = "sticker_pack_license_agreement";
    public static final String EXTRA_STICKER_PACK_TRAY_ICON = "sticker_pack_tray_icon";
    public static final String EXTRA_SHOW_UP_BUTTON = "show_up_button";
    public static final String EXTRA_STICKER_PACK_DATA = "sticker_pack";
    private static final int PICK_IMAGE_REQUEST = 301;


    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private StickerPreviewAdapter stickerPreviewAdapter;
    private int numColumns;
    private View addButton;
    private View alreadyAddedText;
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
        alreadyAddedText = findViewById(R.id.already_added_text);
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
                    getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_padding),
                    stickerPack,
                    expandedStickerView,
                    this::onStickerLongPress
            );
            recyclerView.setAdapter(stickerPreviewAdapter);
        }
        packNameTextView.setText(stickerPack.name);
        packPublisherTextView.setText(stickerPack.publisher);
        packTrayIcon.setImageURI(StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile));
        packSizeTextView.setText(Formatter.formatShortFileSize(this, stickerPack.getTotalSize()));
        addButton.setOnClickListener(v -> addStickerPackToWhatsApp(stickerPack.identifier, stickerPack.name));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(showUpButton);
            getSupportActionBar().setTitle(showUpButton ? getResources().getString(R.string.title_activity_sticker_pack_details_multiple_pack) : getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, 1));
        }
        findViewById(R.id.sticker_pack_animation_indicator).setVisibility(stickerPack.animatedStickerPack ? View.VISIBLE : View.GONE);
        View addStickerButton = findViewById(R.id.add_sticker_button);
        addStickerButton.setVisibility(stickerPack.isCustomPack() ? View.VISIBLE : View.GONE);
        addStickerButton.setOnClickListener(v -> selectImageForSticker());
    }

    private void launchInfoActivity(String publisherWebsite, String publisherEmail, String privacyPolicyWebsite, String licenseAgreementWebsite, String trayIconUriString) {
        Intent intent = new Intent(StickerPackDetailsActivity.this, StickerPackInfoActivity.class);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_ID, stickerPack.identifier);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_WEBSITE, publisherWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_EMAIL, publisherEmail);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_PRIVACY_POLICY, privacyPolicyWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_LICENSE_AGREEMENT, licenseAgreementWebsite);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_TRAY_ICON, trayIconUriString);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        menu.findItem(R.id.action_delete_pack).setVisible(stickerPack != null);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_info && stickerPack != null) {
            Uri trayIconUri = StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile);
            launchInfoActivity(stickerPack.publisherWebsite, stickerPack.publisherEmail, stickerPack.privacyPolicyWebsite, stickerPack.licenseAgreementWebsite, trayIconUri.toString());
            return true;
        } else if (item.getItemId() == R.id.action_delete_pack && stickerPack != null) {
            confirmDeletePack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private final ViewTreeObserver.OnGlobalLayoutListener pageLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            setNumColumns(recyclerView.getWidth() / recyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size));
        }
    };

    private void setNumColumns(int numColumns) {
        if (this.numColumns != numColumns) {
            layoutManager.setSpanCount(numColumns);
            this.numColumns = numColumns;
            if (stickerPreviewAdapter != null) {
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

    private void updateAddUI(Boolean isWhitelisted) {
        if (isWhitelisted) {
            addButton.setVisibility(View.GONE);
            alreadyAddedText.setVisibility(View.VISIBLE);
            findViewById(R.id.sticker_pack_details_tap_to_preview).setVisibility(View.GONE);
        } else {
            addButton.setVisibility(View.VISIBLE);
            alreadyAddedText.setVisibility(View.GONE);
            findViewById(R.id.sticker_pack_details_tap_to_preview).setVisibility(View.VISIBLE);
        }
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

    private void confirmDeletePack() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_pack_title)
                .setMessage(R.string.delete_pack_message)
                .setPositiveButton(R.string.delete_pack_confirm, (dialog, which) -> new DeletePackAsyncTask(this).execute(stickerPack.identifier))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onStickerLongPress(@NonNull Sticker sticker) {
        if (!stickerPack.isCustomPack()) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.remove_sticker_title)
                .setMessage(R.string.remove_sticker_message)
                .setPositiveButton(R.string.remove_sticker_confirm, (dialog, which) -> new RemoveStickerAsyncTask(this).execute(sticker.imageFileName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void reloadCurrentPack() {
        new ReloadPackAsyncTask(this).execute(stickerPack.identifier);
    }

    private void updatePackUI() {
        packNameTextView.setText(stickerPack.name);
        packPublisherTextView.setText(stickerPack.publisher);
        packTrayIcon.setImageURI(StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile));
        packSizeTextView.setText(Formatter.formatShortFileSize(this, stickerPack.getTotalSize()));
        findViewById(R.id.add_sticker_button).setVisibility(stickerPack.isCustomPack() ? View.VISIBLE : View.GONE);
        stickerPreviewAdapter.setStickerPack(stickerPack);
        stickerPreviewAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            new AddStickerAsyncTask(this).execute(data.getData());
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

    static class AddStickerAsyncTask extends AsyncTask<Uri, Void, String> {
        private final WeakReference<StickerPackDetailsActivity> activityReference;

        AddStickerAsyncTask(StickerPackDetailsActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(Uri... uris) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return "activity unavailable";
            }
            try {
                UserStickerPackStore.addStickerToPack(activity, activity.stickerPack.identifier, uris[0]);
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String error) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (error != null) {
                MessageDialogFragment.newInstance(R.string.title_validation_error, error).show(activity.getSupportFragmentManager(), "add_sticker_error");
                return;
            }
            activity.reloadCurrentPack();
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
                if (activity.stickerPack.isCustomPack()) {
                    boolean deleted = UserStickerPackStore.deletePack(activity, identifiers[0]);
                    if (!deleted) {
                        return activity.getString(R.string.delete_pack_not_found);
                    }
                } else {
                    UserStickerPackStore.hidePack(activity, identifiers[0]);
                }
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return e.getMessage();
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

    static class RemoveStickerAsyncTask extends AsyncTask<String, Void, String> {
        private final WeakReference<StickerPackDetailsActivity> activityReference;

        RemoveStickerAsyncTask(StickerPackDetailsActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... fileNames) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return "activity unavailable";
            }
            try {
                UserStickerPackStore.removeStickerFromPack(activity, activity.stickerPack.identifier, fileNames[0]);
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String error) {
            final StickerPackDetailsActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (error != null) {
                MessageDialogFragment.newInstance(R.string.title_validation_error, error).show(activity.getSupportFragmentManager(), "remove_sticker_error");
                return;
            }
            activity.reloadCurrentPack();
        }
    }
}
