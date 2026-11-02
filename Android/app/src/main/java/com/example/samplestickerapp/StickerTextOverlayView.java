package com.example.samplestickerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Shows the sticker as it will be saved and lets the texts over it be moved, resized and removed.
 */
public class StickerTextOverlayView extends View {
    private static final float CHECKER_SIZE_DP = 10;
    private static final float DELETE_HANDLE_RADIUS_DP = 13;
    private static final float DELETE_HANDLE_TOUCH_RADIUS_DP = 24;

    interface Listener {
        void onEditTextRequested(@NonNull StickerTextLayer layer);

        void onSelectionChanged(@Nullable StickerTextLayer layer);
    }

    private final Paint checkerPaint = new Paint();
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleCrossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleDetector;
    private final float density;

    private Bitmap sticker;
    private StickerEdit edit;
    private StickerTextLayer selected;
    private Listener listener;
    private boolean dragging;
    private float lastTouchX;
    private float lastTouchY;

    public StickerTextOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        checkerPaint.setShader(createCheckerShader(context));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(2 * density);
        selectionPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));
        selectionPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        handlePaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        handleCrossPaint.setColor(ContextCompat.getColor(context, R.color.colorOnPrimary));
        handleCrossPaint.setStrokeWidth(2 * density);
        handleCrossPaint.setStrokeCap(Paint.Cap.ROUND);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent event) {
                handleTap(event.getX(), event.getY());
                return true;
            }
        });
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (selected != null) {
                    setSelectedTextSize(selected.textSize * detector.getScaleFactor());
                    if (listener != null) {
                        listener.onSelectionChanged(selected);
                    }
                }
                return true;
            }
        });
    }

    void bind(@NonNull StickerEdit edit, @NonNull Listener listener) {
        this.edit = edit;
        this.listener = listener;
    }

    void setSticker(@NonNull Bitmap sticker) {
        this.sticker = sticker;
        invalidate();
    }

    void select(@Nullable StickerTextLayer layer) {
        selected = layer;
        if (listener != null) {
            listener.onSelectionChanged(layer);
        }
        invalidate();
    }

    void setSelectedTextSize(float textSize) {
        if (selected != null) {
            selected.textSize = Math.max(StickerTextLayer.MIN_TEXT_SIZE, Math.min(StickerTextLayer.MAX_TEXT_SIZE, textSize));
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), checkerPaint);
        if (sticker == null || edit == null) {
            return;
        }
        final float scale = getScale();
        final int save = canvas.save();
        canvas.scale(scale, scale);
        canvas.drawBitmap(sticker, 0, 0, bitmapPaint);
        for (StickerTextLayer layer : edit.textLayers) {
            layer.draw(canvas);
        }
        canvas.restoreToCount(save);
        if (selected != null) {
            final RectF bounds = getViewBounds(selected);
            canvas.drawRect(bounds, selectionPaint);
            final float radius = DELETE_HANDLE_RADIUS_DP * density;
            final float[] handle = getDeleteHandle(bounds);
            canvas.drawCircle(handle[0], handle[1], radius, handlePaint);
            final float cross = radius * 0.4f;
            canvas.drawLine(handle[0] - cross, handle[1] - cross, handle[0] + cross, handle[1] + cross, handleCrossPaint);
            canvas.drawLine(handle[0] - cross, handle[1] + cross, handle[0] + cross, handle[1] - cross, handleCrossPaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (edit == null) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                final StickerTextLayer touched = findLayerAt(event.getX(), event.getY());
                dragging = touched != null;
                if (touched != null && touched != selected) {
                    select(touched);
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && selected != null && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    final float scale = getScale();
                    selected.centerX = clamp(selected.centerX + (event.getX() - lastTouchX) / scale);
                    selected.centerY = clamp(selected.centerY + (event.getY() - lastTouchY) / scale);
                    invalidate();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void handleTap(float x, float y) {
        if (edit == null) {
            return;
        }
        if (selected != null) {
            final float[] handle = getDeleteHandle(getViewBounds(selected));
            if (Math.hypot(x - handle[0], y - handle[1]) <= DELETE_HANDLE_TOUCH_RADIUS_DP * density) {
                edit.textLayers.remove(selected);
                select(null);
                return;
            }
        }
        final StickerTextLayer layer = findLayerAt(x, y);
        if (layer == null) {
            select(null);
        } else if (listener != null) {
            listener.onEditTextRequested(layer);
        }
    }

    @Nullable
    private StickerTextLayer findLayerAt(float x, float y) {
        final float scale = getScale();
        for (int i = edit.textLayers.size() - 1; i >= 0; i--) {
            final StickerTextLayer layer = edit.textLayers.get(i);
            if (layer.getBounds().contains(x / scale, y / scale)) {
                return layer;
            }
        }
        return null;
    }

    @NonNull
    private RectF getViewBounds(@NonNull StickerTextLayer layer) {
        final RectF bounds = layer.getBounds();
        final float scale = getScale();
        bounds.set(bounds.left * scale, bounds.top * scale, bounds.right * scale, bounds.bottom * scale);
        return bounds;
    }

    /* Keeps the handle reachable when the text touches the edges of the sticker. */
    @NonNull
    private float[] getDeleteHandle(@NonNull RectF bounds) {
        final float radius = DELETE_HANDLE_RADIUS_DP * density;
        return new float[]{
                Math.max(radius, Math.min(getWidth() - radius, bounds.right)),
                Math.max(radius, Math.min(getHeight() - radius, bounds.top))};
    }

    private float getScale() {
        return getWidth() / (float) StickerRenderer.SIZE;
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(StickerRenderer.SIZE, value));
    }

    @NonNull
    private BitmapShader createCheckerShader(@NonNull Context context) {
        final int cell = Math.round(CHECKER_SIZE_DP * density);
        final Bitmap bitmap = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.RGB_565);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ContextCompat.getColor(context, R.color.checkerboardLight));
        final Paint paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, R.color.checkerboardDark));
        canvas.drawRect(0, 0, cell, cell, paint);
        canvas.drawRect(cell, cell, cell * 2, cell * 2, paint);
        return new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }
}
