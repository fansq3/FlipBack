package com.flipback.memory;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements MemoryGameView.GameListener {
    private static final int REQUEST_PICK_IMAGES = 1001;
    private static final int MIN_PHOTOS = 2;
    private static final int MAX_PHOTOS = 8;
    private static final String PREFS = "flipback_prefs";
    private static final String KEY_URIS = "photo_uris";

    private final List<Uri> currentUris = new ArrayList<>();
    private MemoryGameView gameView;
    private TextView moveText;
    private TextView titleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        hideSystemBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 242, 235));
        root.setPadding(dp(28), dp(18), dp(28), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new TextView(this);
        titleText.setText("FlipBack");
        titleText.setTextSize(28);
        titleText.setTextColor(Color.rgb(24, 27, 31));
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        titleText.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleText, titleParams);

        moveText = new TextView(this);
        moveText.setText("0 步");
        moveText.setTextSize(16);
        moveText.setTextColor(Color.rgb(72, 76, 82));
        moveText.setGravity(Gravity.CENTER);
        header.addView(moveText, new LinearLayout.LayoutParams(dp(92), dp(48)));

        Button shuffleButton = makeButton("重新洗牌");
        shuffleButton.setOnClickListener(v -> {
            if (currentUris.isEmpty()) {
                openPhotoPicker();
            } else {
                gameView.startGame(currentUris);
            }
        });
        header.addView(shuffleButton, new LinearLayout.LayoutParams(dp(118), dp(48)));

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));

        Button chooseButton = makeButton("选择照片");
        chooseButton.setOnClickListener(v -> openPhotoPicker());
        header.addView(chooseButton, new LinearLayout.LayoutParams(dp(118), dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        gameView = new MemoryGameView(this);
        gameView.setGameListener(this);
        LinearLayout.LayoutParams gameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gameParams.topMargin = dp(12);
        root.addView(gameView, gameParams);

        setContentView(root);
        restorePhotos();
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(245, 242, 235));
        button.setBackgroundResource(android.R.drawable.btn_default);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 35, 41)));
        return button;
    }

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGES || resultCode != RESULT_OK || data == null) {
            return;
        }

        LinkedHashSet<Uri> picked = new LinkedHashSet<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && picked.size() < MAX_PHOTOS; i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) picked.add(uri);
            }
        } else if (data.getData() != null) {
            picked.add(data.getData());
        }

        if (picked.size() < MIN_PHOTOS) {
            Toast.makeText(this, "请至少选择 2 张照片", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUris.clear();
        currentUris.addAll(picked);
        persistPermissions(currentUris, data.getFlags());
        savePhotos();
        gameView.startGame(currentUris);
        Toast.makeText(this,
                "已选择 " + currentUris.size() + " 张照片，生成 " + (currentUris.size() * 2) + " 张卡片",
                Toast.LENGTH_SHORT).show();
    }

    private void persistPermissions(List<Uri> uris, int resultFlags) {
        int flags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        ContentResolver resolver = getContentResolver();
        for (Uri uri : uris) {
            try {
                resolver.takePersistableUriPermission(uri, flags | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some gallery providers do not offer persistent grants. The current session still works.
            }
        }
    }

    private void savePhotos() {
        Set<String> values = new LinkedHashSet<>();
        for (Uri uri : currentUris) values.add(uri.toString());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_URIS, values).apply();
    }

    private void restorePhotos() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> values = prefs.getStringSet(KEY_URIS, null);
        if (values == null || values.size() < MIN_PHOTOS) {
            gameView.showEmptyState();
            return;
        }
        currentUris.clear();
        for (String value : values) {
            try {
                currentUris.add(Uri.parse(value));
            } catch (Exception ignored) { }
        }
        if (currentUris.size() >= MIN_PHOTOS) {
            gameView.startGame(currentUris);
        } else {
            gameView.showEmptyState();
        }
    }

    @Override
    public void onMovesChanged(int moves) {
        moveText.setText(moves + " 步");
    }

    @Override
    public void onGameCompleted(int moves) {
        titleText.setText("完成 · " + moves + " 步");
        titleText.postDelayed(() -> titleText.setText("FlipBack"), 2200);
        Toast.makeText(this, "全部回忆都找到了 ✦", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
