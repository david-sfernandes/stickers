/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StickerPackListActivity extends AddStickerPackActivity {
    public static final String EXTRA_STICKER_PACK_LIST_DATA = "sticker_pack_list";
    private static final int STICKER_PREVIEW_DISPLAY_LIMIT = 5;

    private LinearLayoutManager packLayoutManager;
    private RecyclerView packRecyclerView;
    private StickerPackListAdapter allStickerPacksListAdapter;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;
    private LoadPacksAsyncTask loadPacksAsyncTask;
    private ArrayList<StickerPack> stickerPackList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_list);
        packRecyclerView = findViewById(R.id.sticker_pack_list);
        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        if (stickerPackList == null) {
            stickerPackList = new ArrayList<>();
        }
        showStickerPackList(stickerPackList);
        updateActionBarTitle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPacksAsyncTask = new LoadPacksAsyncTask(this);
        loadPacksAsyncTask.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
        if (loadPacksAsyncTask != null && !loadPacksAsyncTask.isCancelled()) {
            loadPacksAsyncTask.cancel(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pack_list_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_create_pack) {
            showCreatePackDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateActionBarTitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getResources().getQuantityString(R.plurals.title_activity_sticker_packs_list, stickerPackList.size()));
        }
    }

    private void showStickerPackList(List<StickerPack> packs) {
        allStickerPacksListAdapter = new StickerPackListAdapter(packs, onAddButtonClickedListener);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packLayoutManager = new LinearLayoutManager(this);
        packLayoutManager.setOrientation(RecyclerView.VERTICAL);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                packRecyclerView.getContext(),
                packLayoutManager.getOrientation()
        );
        packRecyclerView.addItemDecoration(dividerItemDecoration);
        packRecyclerView.setLayoutManager(packLayoutManager);
        packRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(this::recalculateColumnCount);
    }

    private void showCreatePackDialog() {
        final EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.pack_name_hint);
        final EditText publisherInput = new EditText(this);
        publisherInput.setHint(R.string.pack_publisher_hint);

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int padding = getResources().getDimensionPixelSize(R.dimen.preview_side_margin);
        layout.setPadding(padding, padding, padding, 0);
        layout.addView(nameInput);
        layout.addView(publisherInput);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.create_pack_title)
                .setView(layout)
                .setPositiveButton(R.string.create_pack_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String name = nameInput.getText().toString().trim();
                final String publisher = publisherInput.getText().toString().trim();
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(publisher)) {
                    Toast.makeText(this, R.string.create_pack_validation, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                new CreatePackAsyncTask(this, name, publisher).execute();
            });
        });
        dialog.show();
    }

    private void recalculateColumnCount() {
        final int previewSize = getResources().getDimensionPixelSize(R.dimen.sticker_pack_list_item_preview_image_size);
        int firstVisibleItemPosition = packLayoutManager.findFirstVisibleItemPosition();
        StickerPackListItemViewHolder viewHolder = (StickerPackListItemViewHolder) packRecyclerView.findViewHolderForAdapterPosition(firstVisibleItemPosition);
        if (viewHolder != null) {
            final int widthOfImageRow = viewHolder.imageRowView.getMeasuredWidth();
            final int max = Math.max(widthOfImageRow / previewSize, 1);
            int maxNumberOfImagesInARow = Math.min(STICKER_PREVIEW_DISPLAY_LIMIT, max);
            int minMarginBetweenImages = 0;
            if (maxNumberOfImagesInARow > 1) {
                minMarginBetweenImages = (widthOfImageRow - maxNumberOfImagesInARow * previewSize) / (maxNumberOfImagesInARow - 1);
            }
            allStickerPacksListAdapter.setImageRowSpec(maxNumberOfImagesInARow, minMarginBetweenImages);
        }
    }

    private void refreshListAndWhitelist() {
        allStickerPacksListAdapter.setStickerPackList(stickerPackList);
        allStickerPacksListAdapter.notifyDataSetChanged();
        updateActionBarTitle();
        whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
        whiteListCheckAsyncTask.execute(stickerPackList.toArray(new StickerPack[0]));
    }

    private final StickerPackListAdapter.OnAddButtonClickedListener onAddButtonClickedListener = pack -> addStickerPackToWhatsApp(pack.identifier, pack.name);

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, List<StickerPack>> {
        private final WeakReference<StickerPackListActivity> stickerPackListActivityWeakReference;

        WhiteListCheckAsyncTask(StickerPackListActivity stickerPackListActivity) {
            this.stickerPackListActivityWeakReference = new WeakReference<>(stickerPackListActivity);
        }

        @Override
        protected final List<StickerPack> doInBackground(StickerPack... stickerPackArray) {
            final StickerPackListActivity activity = stickerPackListActivityWeakReference.get();
            if (activity == null) {
                return Arrays.asList(stickerPackArray);
            }
            for (StickerPack stickerPack : stickerPackArray) {
                stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(activity, stickerPack.identifier));
            }
            return Arrays.asList(stickerPackArray);
        }

        @Override
        protected void onPostExecute(List<StickerPack> stickerPackList) {
            final StickerPackListActivity activity = stickerPackListActivityWeakReference.get();
            if (activity != null) {
                activity.allStickerPacksListAdapter.setStickerPackList(stickerPackList);
                activity.allStickerPacksListAdapter.notifyDataSetChanged();
            }
        }
    }

    static class LoadPacksAsyncTask extends AsyncTask<Void, Void, ArrayList<StickerPack>> {
        private final WeakReference<StickerPackListActivity> activityReference;

        LoadPacksAsyncTask(StickerPackListActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected ArrayList<StickerPack> doInBackground(Void... voids) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return new ArrayList<>();
            }
            try {
                return StickerPackLoader.fetchStickerPacks(activity);
            } catch (Exception ignored) {
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(ArrayList<StickerPack> packs) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.stickerPackList = packs;
            activity.refreshListAndWhitelist();
        }
    }

    static class CreatePackAsyncTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<StickerPackListActivity> activityReference;
        private final String name;
        private final String publisher;

        CreatePackAsyncTask(StickerPackListActivity activity, String name, String publisher) {
            this.activityReference = new WeakReference<>(activity);
            this.name = name;
            this.publisher = publisher;
        }

        @Override
        protected String doInBackground(Void... voids) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return "activity unavailable";
            }
            try {
                UserStickerPackStore.createPack(activity, name, publisher);
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String error) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (error != null) {
                Toast.makeText(activity, activity.getString(R.string.create_pack_error, error), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(activity, R.string.create_pack_success, Toast.LENGTH_SHORT).show();
            activity.loadPacksAsyncTask = new LoadPacksAsyncTask(activity);
            activity.loadPacksAsyncTask.execute();
        }
    }
}
