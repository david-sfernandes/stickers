package com.example.samplestickerapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

import com.canhub.cropper.CropImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Turns a picked image or animation into a sticker in two steps: crop, then texts and emojis.
 */
public class StickerEditorActivity extends BaseActivity {
    static final String EXTRA_PACK_ID = "pack_id";
    static final String EXTRA_PACK_ANIMATED = "pack_animated";
    static final String EXTRA_OTHER_STICKERS = "other_stickers";
    static final String EXTRA_REPLACED_FILE = "replaced_file";
    static final String EXTRA_EMOJIS = "emojis";

    private static final int[] TEXT_COLORS = {Color.WHITE, Color.BLACK, 0xFFFFD600, 0xFFFF3D00, 0xFF00E676, 0xFF2979FF, 0xFFFF4081, 0xFFAA00FF};

    private final StickerEdit edit = new StickerEdit();
    private View cropStage;
    private View textStage;
    private View progressView;
    private TextView progressText;
    private CropImageView cropView;
    private StickerTextOverlayView overlayView;
    private View textSizeRow;
    private Slider textSizeSlider;
    private EditText emojiInput;

    private String packId;
    private boolean packIsAnimated;
    private int otherStickers;
    private String replacedFile;

    /* The first frame in case of animations. */
    private Bitmap sourceImage;
    private Bitmap orientedImage;
    @Nullable
    private AnimatedSource animatedSource;
    private boolean useFirstFrameOnly;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_editor);
        cropStage = findViewById(R.id.crop_stage);
        textStage = findViewById(R.id.text_stage);
        progressView = findViewById(R.id.editor_progress);
        progressText = findViewById(R.id.editor_progress_text);
        cropView = findViewById(R.id.crop_image_view);
        overlayView = findViewById(R.id.text_overlay);
        textSizeRow = findViewById(R.id.text_size_row);
        textSizeSlider = findViewById(R.id.text_size_slider);
        emojiInput = findViewById(R.id.emoji_input);

        packId = getIntent().getStringExtra(EXTRA_PACK_ID);
        packIsAnimated = getIntent().getBooleanExtra(EXTRA_PACK_ANIMATED, false);
        otherStickers = getIntent().getIntExtra(EXTRA_OTHER_STICKERS, 0);
        replacedFile = getIntent().getStringExtra(EXTRA_REPLACED_FILE);
        emojiInput.setText(getIntent().getStringExtra(EXTRA_EMOJIS));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.editor_crop_title);
        setupCropStage();
        setupTextStage();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (saving) {
                    return;
                }
                if (textStage.getVisibility() == View.VISIBLE) {
                    showCropStage();
                } else {
                    finish();
                }
            }
        });
        new LoadSourceAsyncTask(this).execute(getIntent().getData());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // During a save the task is still reading it. In that case the finalizer takes care of it.
        if (animatedSource != null && !saving) {
            animatedSource.close();
            animatedSource = null;
        }
    }

    private void setupCropStage() {
        findViewById(R.id.crop_rotate_button).setOnClickListener(v -> {
            edit.rotateClockwise();
            showOrientedImage();
        });
        findViewById(R.id.crop_flip_button).setOnClickListener(v -> {
            edit.flipHorizontally();
            showOrientedImage();
        });
        findViewById(R.id.crop_reset_button).setOnClickListener(v -> {
            edit.resetOrientation();
            showOrientedImage();
        });
        findViewById(R.id.crop_next_button).setOnClickListener(v -> showTextStage());
    }

    private void setupTextStage() {
        overlayView.bind(edit, new StickerTextOverlayView.Listener() {
            @Override
            public void onEditTextRequested(@NonNull StickerTextLayer layer) {
                showTextDialog(layer);
            }

            @Override
            public void onSelectionChanged(@Nullable StickerTextLayer layer) {
                textSizeRow.setVisibility(layer == null ? View.GONE : View.VISIBLE);
                if (layer != null) {
                    textSizeSlider.setValue(layer.textSize);
                }
            }
        });
        textSizeSlider.setValueFrom(StickerTextLayer.MIN_TEXT_SIZE);
        textSizeSlider.setValueTo(StickerTextLayer.MAX_TEXT_SIZE);
        textSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                overlayView.setSelectedTextSize(value);
            }
        });
        findViewById(R.id.add_text_button).setOnClickListener(v -> showTextDialog(null));
        findViewById(R.id.text_back_button).setOnClickListener(v -> showCropStage());
        findViewById(R.id.save_sticker_button).setOnClickListener(v -> startSaving());
    }

    private void onSourceLoaded(@NonNull LoadedSource loaded) {
        sourceImage = loaded.firstFrame;
        animatedSource = loaded.animatedSource;
        setProgressVisible(false, 0);
        // Only replacing the single sticker of a pack also leaves its type open.
        if (animatedSource != null && animatedSource.isAnimated() && otherStickers > 0 && !packIsAnimated) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.gif_in_static_pack_title)
                    .setMessage(R.string.gif_in_static_pack_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.gif_use_first_frame, (dialog, which) -> {
                        useFirstFrameOnly = true;
                        showCropStage();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .show();
        } else {
            showCropStage();
        }
        showOrientedImage();
    }

    private void showOrientedImage() {
        orientedImage = edit.applyOrientation(sourceImage);
        cropView.setImageBitmap(orientedImage);
    }

    private void showCropStage() {
        setTitle(R.string.editor_crop_title);
        textStage.setVisibility(View.INVISIBLE);
        cropStage.setVisibility(View.VISIBLE);
    }

    private void showTextStage() {
        final Rect cropRect = cropView.getCropRect();
        if (cropRect != null) {
            edit.setCrop(cropRect, orientedImage.getWidth(), orientedImage.getHeight());
        }
        final Bitmap preview = StickerRenderer.createStickerBitmap();
        StickerRenderer.render(sourceImage, edit, preview, false);
        overlayView.setSticker(preview);

        final TextView notice = findViewById(R.id.text_stage_notice);
        final boolean savedAsAnimation = isSavedAsAnimation();
        if (savedAsAnimation) {
            notice.setText(R.string.editor_notice_gif);
        } else if (packIsAnimated && otherStickers > 0) {
            notice.setText(R.string.editor_notice_still_in_animated_pack);
        }
        notice.setVisibility(savedAsAnimation || packIsAnimated && otherStickers > 0 ? View.VISIBLE : View.GONE);

        setTitle(R.string.editor_text_title);
        cropStage.setVisibility(View.INVISIBLE);
        textStage.setVisibility(View.VISIBLE);
    }

    private boolean isSavedAsAnimation() {
        return animatedSource != null && animatedSource.isAnimated() && !useFirstFrameOnly;
    }

    private void showTextDialog(@Nullable StickerTextLayer layer) {
        final View form = LayoutInflater.from(this).inflate(R.layout.dialog_sticker_text, null);
        final TextInputLayout textLayout = form.findViewById(R.id.sticker_text_layout);
        final EditText input = form.findViewById(R.id.sticker_text_input);
        final LinearLayout colors = form.findViewById(R.id.sticker_text_colors);
        final int[] selectedColor = {layer != null ? layer.color : TEXT_COLORS[0]};
        if (layer != null) {
            input.setText(layer.text);
            input.setSelection(layer.text.length());
        }
        bindColorOptions(colors, selectedColor);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(layer == null ? R.string.editor_add_text : R.string.editor_edit_text)
                .setView(form)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String text = input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                textLayout.setError(getString(R.string.form_required_field));
                return;
            }
            dialog.dismiss();
            if (layer == null) {
                final StickerTextLayer newLayer = new StickerTextLayer(text, selectedColor[0]);
                // Avoids a new text landing exactly over the previous one.
                newLayer.centerY -= edit.textLayers.size() * 80 % (StickerRenderer.SIZE * 0.6f);
                edit.textLayers.add(newLayer);
                overlayView.select(newLayer);
            } else {
                layer.text = text;
                layer.color = selectedColor[0];
                layer.invalidateLayout();
                overlayView.select(layer);
            }
        }));
        dialog.show();
        input.requestFocus();
    }

    private void bindColorOptions(@NonNull LinearLayout container, @NonNull int[] selectedColor) {
        final int size = getResources().getDimensionPixelSize(R.dimen.editor_color_option_size);
        final int margin = getResources().getDimensionPixelSize(R.dimen.sticker_cell_margin);
        for (int color : TEXT_COLORS) {
            final View option = new View(this);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, margin, margin, margin);
            option.setLayoutParams(params);
            option.setTag(color);
            option.setContentDescription(getString(R.string.editor_text_color));
            option.setOnClickListener(v -> {
                selectedColor[0] = color;
                updateColorOptions(container, color);
            });
            container.addView(option);
        }
        updateColorOptions(container, selectedColor[0]);
    }

    private void updateColorOptions(@NonNull ViewGroup container, int selected) {
        final float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < container.getChildCount(); i++) {
            final View option = container.getChildAt(i);
            final int color = (int) option.getTag();
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            circle.setStroke(Math.round((color == selected ? 3 : 1) * density), color == selected ? getColor(R.color.colorPrimary) : getColor(R.color.colorOutline));
            option.setBackground(circle);
            option.setSelected(color == selected);
        }
    }

    private void startSaving() {
        overlayView.select(null);
        setProgressVisible(true, R.string.editor_saving);
        saving = true;
        new SaveStickerAsyncTask(this).execute();
    }

    private void onSaveFinished(@Nullable Throwable error) {
        saving = false;
        setProgressVisible(false, 0);
        if (error == null) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        final MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_validation_error)
                .setMessage(StickerException.userMessage(this, error))
                .setNegativeButton(android.R.string.cancel, null);
        if (error instanceof StickerException && ((StickerException) error).getMessageRes() == R.string.error_gif_too_large) {
            dialog.setPositiveButton(R.string.gif_use_first_frame, (d, which) -> {
                useFirstFrameOnly = true;
                startSaving();
            });
        }
        dialog.show();
    }

    private void setProgressVisible(boolean visible, int messageRes) {
        progressView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            progressText.setText(messageRes);
        }
    }

    private void onLoadFailed(@NonNull Throwable error) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_validation_error)
                .setMessage(StickerException.userMessage(this, error))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    private static final class LoadedSource {
        final Bitmap firstFrame;
        @Nullable
        final AnimatedSource animatedSource;
        final Throwable error;

        LoadedSource(Bitmap firstFrame, @Nullable AnimatedSource animatedSource, Throwable error) {
            this.firstFrame = firstFrame;
            this.animatedSource = animatedSource;
            this.error = error;
        }
    }

    static class LoadSourceAsyncTask extends AsyncTask<Uri, Void, LoadedSource> {
        private final WeakReference<StickerEditorActivity> activityReference;
        private final Context context;

        LoadSourceAsyncTask(StickerEditorActivity activity) {
            activityReference = new WeakReference<>(activity);
            context = activity.getApplicationContext();
        }

        @Override
        protected LoadedSource doInBackground(Uri... uris) {
            try {
                final byte[] bytes = StickerImageProcessor.readBytes(context, uris[0]);
                final AnimatedSource animated = openAnimated(bytes);
                if (animated == null) {
                    return new LoadedSource(StickerImageProcessor.decodeImage(bytes), null, null);
                }
                final Bitmap frame = animated.createFrameBitmap();
                animated.renderFrame(0, frame);
                return new LoadedSource(frame, animated, null);
            } catch (Exception | OutOfMemoryError e) {
                return new LoadedSource(null, null, e);
            }
        }

        /* Other formats and static WebP files are left to the platform decoder. */
        @Nullable
        private AnimatedSource openAnimated(@NonNull byte[] bytes) {
            AnimatedSource source;
            try {
                source = AnimatedSource.open(bytes);
            } catch (RuntimeException e) {
                return null;
            }
            if (source != null && !source.isAnimated()) {
                source.close();
                return null;
            }
            return source;
        }

        @Override
        protected void onPostExecute(LoadedSource loaded) {
            final StickerEditorActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (loaded.error != null) {
                activity.onLoadFailed(loaded.error);
            } else {
                activity.onSourceLoaded(loaded);
            }
        }
    }

    static class SaveStickerAsyncTask extends AsyncTask<Void, Void, Throwable> {
        private final WeakReference<StickerEditorActivity> activityReference;
        private final Context context;
        private final StickerEdit edit;
        private final Bitmap sourceImage;
        private final AnimatedSource animatedSource;
        private final boolean saveAnimation;
        private final boolean wrapStillInAnimation;
        private final String packId;
        private final String replacedFile;
        private final List<String> emojis;

        SaveStickerAsyncTask(StickerEditorActivity activity) {
            activityReference = new WeakReference<>(activity);
            context = activity.getApplicationContext();
            edit = activity.edit;
            sourceImage = activity.sourceImage;
            animatedSource = activity.animatedSource;
            saveAnimation = activity.isSavedAsAnimation();
            wrapStillInAnimation = !saveAnimation && activity.packIsAnimated && activity.otherStickers > 0;
            packId = activity.packId;
            replacedFile = activity.replacedFile;
            emojis = StickerEmojis.parse(activity.emojiInput.getText().toString());
        }

        @Override
        protected Throwable doInBackground(Void... voids) {
            try {
                final byte[] sticker;
                if (saveAnimation) {
                    sticker = AnimatedStickerEncoder.encode(animatedSource, edit);
                } else {
                    final Bitmap bitmap = StickerRenderer.createStickerBitmap();
                    StickerRenderer.render(sourceImage, edit, bitmap, true);
                    sticker = wrapStillInAnimation ? AnimatedStickerEncoder.encodeStill(bitmap) : StickerImageProcessor.createStaticSticker(bitmap);
                }
                UserStickerPackStore.addStickerToPack(context, packId, sticker, emojis, saveAnimation || wrapStillInAnimation, replacedFile);
                context.getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);
                return null;
            } catch (Exception | OutOfMemoryError e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Throwable error) {
            final StickerEditorActivity activity = activityReference.get();
            if (activity != null) {
                activity.onSaveFinished(error);
            }
        }
    }
}
