package com.shruti.nidra;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {
    static final int BG = 0xFF0E1022, CARD = 0xFF161A33, CARD2 = 0xFF20254A, LINE = 0xFF2C3260,
            TEXT = 0xFFE6E3F2, MUTED = 0xFF9A98B8, DIYA = 0xFFE0A35C, INK = 0xFF1A1300;

    static class S { final int res; final String title, sub, group;
        S(int r, String t, String s, String g) { res = r; title = t; sub = s; group = g; } }

    static final S[] ALL = {
        new S(R.raw.b478, "4-7-8 breathing", "In 4 · hold 7 · out 8. About 3 min", "Breathing"),
        new S(R.raw.bexh, "Long out-breath", "In 4 · out 6. About 3 min", "Breathing"),
        new S(R.raw.pmr, "Clench and release, feet to head", "Progressive muscle relaxation. About 7 min", "Body"),
        new S(R.raw.scan, "Gentle body scan", "No tensing, only noticing. About 4 min", "Body"),
        new S(R.raw.shabari, "Shabari's Berries", "Ramayana", "Stories"),
        new S(R.raw.govardhan, "Krishna Lifts Govardhan", "Bhagavata Purana", "Stories"),
        new S(R.raw.shivaji, "Shivaji and the Farmers' Grass", "Maratha Swarajya, 1600s", "Stories"),
    };
    static final int[] TIMERS = {15, 30, 45, 60, 0};

    SharedPreferences prefs;
    int sleepMin;
    TextView pTitle, pLine, pTimer, btnToggle, btnStop;
    LinearLayout timerRow;
    final Handler h = new Handler(Looper.getMainLooper());

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1), stroke); return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("serif", Typeface.BOLD)); return t;
    }

    TextView pill(String s, boolean primary) {
        TextView b = text(s, 16, primary ? INK : TEXT, false);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        b.setBackground(round(primary ? DIYA : CARD2, primary ? 0 : LINE, 999));
        return b;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("nidra", MODE_PRIVATE);
        sleepMin = prefs.getInt("sleepMin", 30);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);

        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(190));
        sv.addView(col); root.addView(sv);

        TextView h1 = text("Nidra", 34, DIYA, true); col.addView(h1);
        TextView sub = text("Lie down. Lights off. Phone face down. Just listen.", 15, MUTED, false);
        sub.setPadding(0, dp(2), 0, dp(20)); col.addView(sub);

        // Wind-down card
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(round(CARD, LINE, 18));
        card.addView(label("TONIGHT'S WIND-DOWN · ABOUT 13 MIN"));
        TextView t2 = text("Breath → Body → Story", 21, TEXT, true); t2.setPadding(0, dp(4), 0, dp(6)); card.addView(t2);
        card.addView(text("4-7-8 breathing, then clench-and-release from feet to head, then a story chosen at random.", 14, MUTED, false));
        TextView go = pill("Begin wind-down", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(14); card.addView(go, lp);
        go.setOnClickListener(v -> {
            List<S> stories = new ArrayList<>(); for (S s : ALL) if (s.group.equals("Stories")) stories.add(s);
            S story = stories.get(new Random().nextInt(stories.size()));
            play("Wind-down · " + story.title, new S[]{ALL[0], ALL[2], story});
        });
        col.addView(card);

        // Sleep timer
        col.addView(spacer(22)); col.addView(label("STOP AUTOMATICALLY AFTER"));
        timerRow = new LinearLayout(this); timerRow.setPadding(0, dp(8), 0, 0);
        for (int m : TIMERS) {
            TextView c = text(m == 0 ? "Never" : m + "m", 14, TEXT, false);
            c.setGravity(Gravity.CENTER); c.setPadding(0, dp(9), 0, dp(9));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1); cl.rightMargin = dp(6);
            c.setOnClickListener(v -> { sleepMin = m; prefs.edit().putInt("sleepMin", m).apply(); paintTimers(); });
            c.setTag(m); timerRow.addView(c, cl);
        }
        col.addView(timerRow); paintTimers();
        TextView tn = text("Sound fades out over 30 seconds, then the app stops by itself.", 13, MUTED, false);
        tn.setPadding(0, dp(8), 0, 0); col.addView(tn);

        String group = "";
        for (S s : ALL) {
            if (!s.group.equals(group)) { group = s.group; col.addView(spacer(26)); col.addView(label(group.toUpperCase())); col.addView(spacer(6)); }
            col.addView(row(s));
        }

        // Player bar
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(CARD); bar.setPadding(dp(20), dp(14), dp(20), dp(18));
        View top = new View(this); top.setBackgroundColor(LINE);
        pTitle = text("Pick something to listen to", 17, TEXT, true); pTitle.setSingleLine(true);
        pLine = text("Plays with the screen off.", 13, MUTED, false); pLine.setSingleLine(true);
        LinearLayout ctr = new LinearLayout(this); ctr.setGravity(Gravity.CENTER_VERTICAL); ctr.setPadding(0, dp(10), 0, 0);
        btnToggle = pill("Pause", true); btnStop = pill("Stop", false);
        pTimer = text("", 14, DIYA, false); pTimer.setGravity(Gravity.END);
        ctr.addView(btnToggle); LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2); sl.leftMargin = dp(8); ctr.addView(btnStop, sl);
        ctr.addView(pTimer, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(pTitle); bar.addView(pLine); bar.addView(ctr);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bar, bl);
        btnToggle.setOnClickListener(v -> startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_TOGGLE)));
        btnStop.setOnClickListener(v -> startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_STOP)));

        setContentView(root);
        h.post(tick);
    }

    final Runnable tick = new Runnable() { @Override public void run() { refresh(); h.postDelayed(this, 1000); } };

    void refresh() {
        boolean on = PlayerService.active;
        btnToggle.setVisibility(on ? View.VISIBLE : View.GONE);
        btnStop.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            pTitle.setText(PlayerService.title);
            String part = PlayerService.partName + (PlayerService.partCount > 1 ? "  ·  part " + (PlayerService.partIndex + 1) + " of " + PlayerService.partCount : "");
            pLine.setText(part);
            btnToggle.setText(PlayerService.paused ? "Resume" : "Pause");
            if (PlayerService.fading) pTimer.setText("Fading out…");
            else if (PlayerService.sleepEndAt > 0) {
                long s = Math.max(0, (PlayerService.sleepEndAt - System.currentTimeMillis()) / 1000);
                pTimer.setText(String.format("Stops in %d:%02d", s / 60, s % 60));
            } else pTimer.setText("");
        } else { pTimer.setText(""); pLine.setText("Plays with the screen off."); }
    }

    void play(String title, S[] parts) {
        int[] ids = new int[parts.length]; String[] names = new String[parts.length];
        for (int i = 0; i < parts.length; i++) { ids[i] = parts[i].res; names[i] = parts[i].title; }
        Intent i = new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PLAY)
                .putExtra("ids", ids).putExtra("names", names).putExtra("title", title).putExtra("sleepMin", sleepMin);
        startForegroundService(i);
        PlayerService.active = true; PlayerService.title = title; PlayerService.partName = names[0];
        refresh();
    }

    View row(S s) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(4), dp(13), dp(4), dp(13));
        TextView t = text(s.title, 16, TEXT, false); t.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(t); r.addView(text(s.sub, 13, MUTED, false));
        r.setOnClickListener(v -> play(s.title, new S[]{s}));
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r); View line = new View(this); line.setBackgroundColor(LINE); wrap.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return wrap;
    }

    void paintTimers() {
        for (int i = 0; i < timerRow.getChildCount(); i++) {
            TextView c = (TextView) timerRow.getChildAt(i); boolean sel = ((int) c.getTag()) == sleepMin;
            c.setTextColor(sel ? INK : TEXT); c.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.setBackground(round(sel ? DIYA : Color.TRANSPARENT, sel ? 0 : LINE, 999));
        }
    }

    TextView label(String s) { TextView t = text(s, 11, MUTED, false); t.setLetterSpacing(0.14f); return t; }
    View spacer(int h) { View v = new View(this); v.setMinimumHeight(dp(h)); return v; }

    @Override protected void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }
}
