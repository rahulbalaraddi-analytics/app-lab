package com.shruti.nidra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Plays a list of bundled audio files in the background and stops itself when the sleep timer ends. */
public class PlayerService extends Service {
    public static final String ACTION_PLAY = "play", ACTION_TOGGLE = "toggle", ACTION_STOP = "stop";
    private static final String CHANNEL = "nidra_playback";
    private static final long FADE_MS = 30_000;

    // Shared state read by the screen (single process app).
    public static volatile boolean active = false, paused = false, fading = false;
    public static volatile String title = "", partName = "";
    public static volatile long sleepEndAt = 0;
    public static volatile int partIndex = 0, partCount = 0;

    private MediaPlayer player;
    private int[] ids = new int[0];
    private String[] names = new String[0];
    private final Handler h = new Handler(Looper.getMainLooper());
    private AudioManager am;
    private AudioFocusRequest focus;
    private float volume = 1f;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_PLAY.equals(a)) {
            ids = intent.getIntArrayExtra("ids");
            names = intent.getStringArrayExtra("names");
            title = intent.getStringExtra("title");
            int sleepMin = intent.getIntExtra("sleepMin", 30);
            goForeground();
            requestFocus();
            h.removeCallbacksAndMessages(null);
            fading = false; volume = 1f; paused = false; active = true;
            sleepEndAt = sleepMin > 0 ? System.currentTimeMillis() + sleepMin * 60_000L : 0;
            if (sleepEndAt > 0) h.postDelayed(this::startFade, Math.max(0, sleepMin * 60_000L - FADE_MS));
            playPart(0);
        } else if (ACTION_TOGGLE.equals(a)) {
            if (player != null) {
                if (player.isPlaying()) { player.pause(); paused = true; } else { player.start(); paused = false; }
                goForeground();
            }
        } else if (ACTION_STOP.equals(a)) {
            stopAll();
        }
        return START_NOT_STICKY;
    }

    private void playPart(int i) {
        releasePlayer();
        if (i >= ids.length) { stopAll(); return; }
        partIndex = i; partCount = ids.length; partName = names[i];
        player = MediaPlayer.create(this, ids[i]);
        if (player == null) { playPart(i + 1); return; }
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setVolume(volume, volume);
        player.setOnCompletionListener(mp -> playPart(i + 1));
        player.start();
        goForeground();
    }

    private void startFade() {
        if (!active) return;
        fading = true;
        final long t0 = System.currentTimeMillis();
        h.post(new Runnable() { @Override public void run() {
            float k = 1f - (System.currentTimeMillis() - t0) / (float) FADE_MS;
            if (k <= 0) { stopAll(); return; }
            volume = k * k;
            if (player != null) player.setVolume(volume, volume);
            h.postDelayed(this, 200);
        }});
    }

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        releasePlayer();
        active = false; paused = false; fading = false; sleepEndAt = 0; partName = "";
        if (am != null && focus != null) am.abandonAudioFocusRequest(focus);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
    }

    private void requestFocus() {
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(c -> {
                    if (player == null) return;
                    if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { if (player.isPlaying()) { player.pause(); paused = true; } }
                    else if (c == AudioManager.AUDIOFOCUS_GAIN && paused) { player.start(); paused = false; }
                }).build();
        am.requestAudioFocus(focus);
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        int f = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), f);
        PendingIntent toggle = PendingIntent.getService(this, 1, new Intent(this, PlayerService.class).setAction(ACTION_TOGGLE), f);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, PlayerService.class).setAction(ACTION_STOP), f);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_note).setContentTitle(title).setContentText(partName)
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, paused ? "Resume" : "Pause", toggle).build())
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
    }

    @Override public void onDestroy() { h.removeCallbacksAndMessages(null); releasePlayer(); active = false; super.onDestroy(); }
}
