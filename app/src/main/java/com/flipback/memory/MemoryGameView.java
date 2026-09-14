package com.flipback.memory;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryGameView extends View {
    public interface GameListener {
        void onMovesChanged(int moves);
        void onGameCompleted(int moves);
    }

    private static class Card {
        final int pairId;
        final Bitmap bitmap;
        final Uri uri;
        boolean matched = false;
        boolean revealed = false;
        float flip = 0f;

        Card(int pairId, Bitmap bitmap, Uri uri) {
            this.pairId = pairId;
            this.bitmap = bitmap;
            this.uri = uri;
        }
    }

    private final List<Card> cards = new ArrayList<>();
    private final List<RectF> cardRects = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GameListener listener;
    private int firstIndex = -1;
    private int moves = 0;
    private boolean inputLocked = false;
    private boolean emptyState = true;
    private float downX;
    private float downY;

    public MemoryGameView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    }

    public void setGameListener(GameListener listener) {
        this.listener = listener;
    }

    public void showEmptyState() {
        recycleCurrentBitmaps();
        cards.clear();
        cardRects.clear();
        firstIndex = -1;
        moves = 0;
        inputLocked = false;
        emptyState = true;
        if (listener != null) listener.onMovesChanged(0);
        invalidate();
    }

    public void startGame(List<Uri> uris) {
        handler.removeCallbacksAndMessages(null);
        recycleCurrentBitmaps();
        cards.clear();
        cardRects.clear();
        firstIndex = -1;
        moves = 0;
        inputLocked = false;
        emptyState = false;

        int pairId = 0;
        for (Uri uri : uris) {
            Bitmap bitmap = decodeBitmap(uri, 1200);
            if (bitmap == null) continue;
            cards.add(new Card(pairId, bitmap, uri));
            cards.add(new Card(pairId, bitmap, uri));
            pairId++;
        }

        if (cards.size() < 4) {
            emptyState = true;
            cards.clear();
        } else {
            Collections.shuffle(cards);
        }

        if (listener != null) listener.onMovesChanged(0);
        requestLayout();
        invalidate();
    }

    private Bitmap decodeBitmap(Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / sample > maxSide * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recycleCurrentBitmaps() {
        // Duplicate cards intentionally share the same Bitmap instance.
        ArrayList<Bitmap> recycled = new ArrayList<>();
        for (Card card : cards) {
            Bitmap b = card.bitmap;
            if (b != null && !b.isRecycled() && !recycled.contains(b)) {
                recycled.add(b);
                b.recycle();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(245, 242, 235));

        if (emptyState || cards.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        layoutCards(getWidth(), getHeight());
        for (int i = 0; i < cards.size(); i++) {
            drawCard(canvas, cards.get(i), cardRects.get(i));
        }
    }

    private void drawEmptyState(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.rgb(31, 35, 41));
        textPaint.setTextSize(sp(28));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("把回忆变成一局翻翻乐", cx, cy - dp(16), textPaint);

        textPaint.setTextSize(sp(16));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        textPaint.setColor(Color.rgb(105, 107, 111));
        canvas.drawText("点击右上角「选择照片」，选择 2–8 张照片", cx, cy + dp(24), textPaint);
    }

    private void layoutCards(int width, int height) {
        cardRects.clear();
        int count = cards.size();
        float availableW = width - dp(20);
        float availableH = height - dp(20);
        float targetRatio = availableW / Math.max(1f, availableH);

        int bestCols = 2;
        float bestScore = Float.MAX_VALUE;
        for (int cols = 2; cols <= Math.min(6, count); cols++) {
            int rows = (int) Math.ceil(count / (float) cols);
            float gridRatio = cols / (float) rows;
            float score = Math.abs(gridRatio - targetRatio * 0.72f);
            if (score < bestScore) {
                bestScore = score;
                bestCols = cols;
            }
        }

        int cols = bestCols;
        int rows = (int) Math.ceil(count / (float) cols);
        float gap = dp(14);
        float cardW = (availableW - gap * (cols - 1)) / cols;
        float cardH = (availableH - gap * (rows - 1)) / rows;

        float desiredRatio = 0.74f; // width / height, like a memory card.
        if (cardW / cardH > desiredRatio) {
            cardW = cardH * desiredRatio;
        } else {
            cardH = cardW / desiredRatio;
        }

        float gridW = cardW * cols + gap * (cols - 1);
        float gridH = cardH * rows + gap * (rows - 1);
        float startX = (width - gridW) / 2f;
        float startY = (height - gridH) / 2f;

        for (int i = 0; i < count; i++) {
            int row = i / cols;
            int col = i % cols;
            float left = startX + col * (cardW + gap);
            float top = startY + row * (cardH + gap);
            cardRects.add(new RectF(left, top, left + cardW, top + cardH));
        }
    }

    private void drawCard(Canvas canvas, Card card, RectF rect) {
        float progress = Math.max(0f, Math.min(1f, card.flip));
        float scaleX = (float) Math.abs(Math.cos(Math.PI * progress));
        scaleX = Math.max(0.035f, scaleX);

        float cx = rect.centerX();
        canvas.save();
        canvas.scale(scaleX, 1f, cx, rect.centerY());

        boolean front = progress >= 0.5f;
        if (front) {
            drawPhotoFront(canvas, card, rect);
        } else {
            drawCardBack(canvas, rect);
        }
        canvas.restore();
    }

    private void drawCardBack(Canvas canvas, RectF rect) {
        float radius = dp(18);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(29, 33, 39));
        paint.setShadowLayer(dp(8), 0, dp(3), 0x26000000);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.clearShadowLayer();

        RectF inner = new RectF(rect.left + dp(8), rect.top + dp(8), rect.right - dp(8), rect.bottom - dp(8));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.rgb(78, 83, 90));
        canvas.drawRoundRect(inner, radius - dp(5), radius - dp(5), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(229, 222, 207));
        canvas.drawCircle(rect.centerX(), rect.centerY() - dp(10), dp(15), paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        textPaint.setTextSize(sp(11));
        textPaint.setLetterSpacing(0.16f);
        textPaint.setColor(Color.rgb(229, 222, 207));
        canvas.drawText("FLIPBACK", rect.centerX(), rect.centerY() + dp(27), textPaint);
        textPaint.setLetterSpacing(0f);
    }

    private void drawPhotoFront(Canvas canvas, Card card, RectF rect) {
        float radius = dp(18);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(dp(8), 0, dp(3), 0x25000000);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.clearShadowLayer();

        RectF photoRect = new RectF(rect.left + dp(5), rect.top + dp(5), rect.right - dp(5), rect.bottom - dp(5));
        Path clip = new Path();
        clip.addRoundRect(photoRect, radius - dp(4), radius - dp(4), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        drawCenterCrop(canvas, card.bitmap, photoRect);
        canvas.restore();

        if (card.matched) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.rgb(36, 135, 88));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF dst) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        float srcRatio = bw / (float) bh;
        float dstRatio = dst.width() / dst.height();
        Rect src;
        if (srcRatio > dstRatio) {
            int srcW = Math.round(bh * dstRatio);
            int left = (bw - srcW) / 2;
            src = new Rect(left, 0, left + srcW, bh);
        } else {
            int srcH = Math.round(bw / dstRatio);
            int top = (bh - srcH) / 2;
            src = new Rect(0, top, bw, top + srcH);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (emptyState || cards.isEmpty()) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (dx * dx + dy * dy > dp(18) * dp(18)) return true;
            handleTap(event.getX(), event.getY());
            return true;
        }
        return true;
    }

    private void handleTap(float x, float y) {
        if (inputLocked) return;
        for (int i = 0; i < cardRects.size(); i++) {
            if (cardRects.get(i).contains(x, y)) {
                revealCard(i);
                return;
            }
        }
    }

    private void revealCard(int index) {
        Card card = cards.get(index);
        if (card.matched || card.revealed) return;

        card.revealed = true;
        animateFlip(card, true, null);

        if (firstIndex < 0) {
            firstIndex = index;
            return;
        }

        int secondIndex = index;
        int first = firstIndex;
        firstIndex = -1;
        moves++;
        if (listener != null) listener.onMovesChanged(moves);

        Card a = cards.get(first);
        Card b = cards.get(secondIndex);
        if (a.pairId == b.pairId) {
            a.matched = true;
            b.matched = true;
            handler.postDelayed(() -> {
                invalidate();
                checkCompletion();
            }, 180);
        } else {
            inputLocked = true;
            handler.postDelayed(() -> {
                animateFlip(a, false, null);
                animateFlip(b, false, () -> inputLocked = false);
            }, 720);
        }
    }

    private void animateFlip(Card card, boolean reveal, Runnable onEnd) {
        float start = card.flip;
        float end = reveal ? 1f : 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            card.flip = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                card.flip = end;
                if (!reveal) card.revealed = false;
                invalidate();
                if (onEnd != null) onEnd.run();
            }
        });
        animator.start();
    }

    private void checkCompletion() {
        for (Card card : cards) {
            if (!card.matched) return;
        }
        inputLocked = true;
        if (listener != null) listener.onGameCompleted(moves);
        handler.postDelayed(() -> inputLocked = false, 1200);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
