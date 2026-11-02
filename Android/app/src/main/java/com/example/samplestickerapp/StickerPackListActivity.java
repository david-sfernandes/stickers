/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.samplestickerapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

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
    private View emptyState;
    private ExtendedFloatingActionButton createPackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_list);
        packRecyclerView = findViewById(R.id.sticker_pack_list);
        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        if (stickerPackList == null) {
            stickerPackList = new ArrayList<>();
        }
        emptyState = findViewById(R.id.empty_state);
        createPackButton = findViewById(R.id.create_pack_button);
        createPackButton.setOnClickListener(v -> showCreatePackDialog());
        packRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    createPackButton.shrink();
                } else if (dy < 0) {
                    createPackButton.extend();
                }
            }
        });
        showStickerPackList(stickerPackList);
        updateEmptyState();
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

    private void updateEmptyState() {
        emptyState.setVisibility(stickerPackList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showStickerPackList(List<StickerPack> packs) {
        allStickerPacksListAdapter = new StickerPackListAdapter(packs, onAddButtonClickedListener);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packLayoutManager = new LinearLayoutManager(this);
        packLayoutManager.setOrientation(RecyclerView.VERTICAL);
        packRecyclerView.setLayoutManager(packLayoutManager);
        packRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(this::recalculateColumnCount);
    }

    private void showCreatePackDialog() {
        PackFormDialog.show(this, R.string.create_pack_title, R.string.create_pack_confirm, null, null, (name, publisher) -> new CreatePackAsyncTask(this, name, publisher).execute());
    }

    private void openPack(@NonNull StickerPack pack) {
        final Intent intent = new Intent(this, StickerPackDetailsActivity.class);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_SHOW_UP_BUTTON, true);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_DATA, pack);
        startActivity(intent);
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
        updateEmptyState();
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

    static class CreatePackAsyncTask extends AsyncTask<Void, Void, StickerPack> {
        private final WeakReference<StickerPackListActivity> activityReference;
        private final String name;
        private final String publisher;

        CreatePackAsyncTask(StickerPackListActivity activity, String name, String publisher) {
            this.activityReference = new WeakReference<>(activity);
            this.name = name;
            this.publisher = publisher;
        }

        @Override
        protected StickerPack doInBackground(Void... voids) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return null;
            }
            try {
                final StickerPack pack = UserStickerPackStore.createPack(activity, name, publisher);
                activity.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return pack;
            } catch (Exception e) {
                Log.e("StickerPackListActivity", "could not create pack", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(StickerPack pack) {
            final StickerPackListActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            if (pack == null) {
                Toast.makeText(activity, R.string.create_pack_error, Toast.LENGTH_LONG).show();
                return;
            }
            activity.openPack(pack);
        }
    }
}
