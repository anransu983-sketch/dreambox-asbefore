package com.suanran.zhixiang;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;

/**
 * 播放原生桥 —— 功能包重写版（原版见芷湘独立 App 的 PlaybackPlugin，
 * 它的方法面与事件约定全部保留，变的只是实现底座）：
 *
 * 为什么不能照抄原版：原版 start / stop / setMedia / setPlaying /
 * clearMedia 都是给 PlaybackService（一个真正的 Android Service 组件）
 * 发 Intent。功能包的 dex 类不能被系统实例化成 Service 组件，这条路
 * 在包里走不通。
 *
 * 包版实现：
 * - ensureProxy / stopProxy：与原版逐字一致，StreamProxyServer 是纯
 *   进程内 ServerSocket，本来就不依赖 Service，必须真的能用（Web 层
 *   拿到端口后才肯把 googlevideo 地址交给 audio 元素，绝不能假造）。
 * - start / stop / setMedia / setPlaying / clearMedia：改为进程内降级
 *   实现 —— android.media.session.MediaSession（系统播放卡、耳机按键
 *   回调经 mediaAction 事件回 Web 层，动作名与原版服务一致：
 *   play / pause / next / previous）+ 一条普通通知（非前台服务通知，
 *   展示当前曲目）。没有前台服务意味着：梦盒进程被系统回收时包与
 *   播放一起结束，保活强度弱于独立 App，详见 PACK_NOTES.md。
 * - 系统卡封面不加载（原版服务会下载封面图），v1 降级项。
 *
 * 注册方式：由功能包入口 ZhixiangPackEntry 显式实例化并路由，
 * 不走 Capacitor 的插件扫描。
 */
@CapacitorPlugin(name = "Playback")
public class PlaybackPlugin extends Plugin {

    private static PlaybackPlugin instance;

    private static final String CHANNEL_ID = "zhixiang_pack_playback";
    private static final int NOTIFICATION_ID = 42137;

    private StreamProxyServer proxy;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaSession mediaSession;
    private boolean notificationActive;
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private boolean currentPlaying;

    @Override
    public void load() {
        super.load();
        instance = this;
    }

    /** 会话回调入口：与原版同名同义，Web 层不在时直接丢弃。 */
    public static void emitMediaAction(String action) {
        PlaybackPlugin plugin = instance;
        if (plugin == null) {
            return;
        }
        JSObject data = new JSObject();
        data.put("action", action);
        plugin.notifyListeners("mediaAction", data);
    }

    @PluginMethod
    public void start(PluginCall call) {
        instance = this;
        if (getContext() == null) {
            call.reject("系统服务不可用", "NO_CONTEXT");
            return;
        }
        currentTitle = stringOrEmpty(call.getString("title"));
        currentArtist = stringOrEmpty(call.getString("artist"));
        runOnMain(() -> {
            ensureSession();
            applyMetadata();
            applyPlaybackState();
            showNotification();
        });
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        instance = this;
        runOnMain(() -> {
            cancelNotification();
            if (mediaSession != null) {
                mediaSession.setActive(false);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void setMedia(PluginCall call) {
        instance = this;
        if (getContext() == null) {
            call.reject("系统服务不可用", "NO_CONTEXT");
            return;
        }
        currentTitle = stringOrEmpty(call.getString("title"));
        currentArtist = stringOrEmpty(call.getString("artist"));
        currentAlbum = stringOrEmpty(call.getString("album"));
        // coverUrl / albumId 在 v1 降级实现里不用（系统卡不加载封面），
        // 只读不存，避免误以为已消费。
        runOnMain(() -> {
            ensureSession();
            applyMetadata();
            applyPlaybackState();
            showNotification();
        });
        call.resolve();
    }

    @PluginMethod
    public void setPlaying(PluginCall call) {
        instance = this;
        currentPlaying = call.getBoolean("playing", false);
        runOnMain(() -> {
            ensureSession();
            applyPlaybackState();
            if (notificationActive) {
                showNotification();
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void clearMedia(PluginCall call) {
        instance = this;
        currentTitle = "";
        currentArtist = "";
        currentAlbum = "";
        currentPlaying = false;
        runOnMain(() -> {
            cancelNotification();
            if (mediaSession != null) {
                mediaSession.setMetadata(new MediaMetadata.Builder().build());
                applyPlaybackState();
                mediaSession.setActive(false);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public synchronized void ensureProxy(PluginCall call) {
        try {
            if (proxy == null) {
                proxy = new StreamProxyServer();
            }
            int port = proxy.start();
            JSObject result = new JSObject();
            result.put("port", port);
            call.resolve(result);
        } catch (IOException e) {
            call.reject("本机流代理启动失败", "PROXY_START_FAILED", e);
        }
    }

    @PluginMethod
    public synchronized void stopProxy(PluginCall call) {
        if (proxy != null) {
            proxy.stop();
            proxy = null;
        }
        call.resolve();
    }

    /** 入口 release 时调用：停代理、撤通知、释放会话。 */
    public void releasePack() {
        synchronized (this) {
            if (proxy != null) {
                proxy.stop();
                proxy = null;
            }
        }
        runOnMain(() -> {
            cancelNotification();
            if (mediaSession != null) {
                mediaSession.release();
                mediaSession = null;
            }
        });
        if (instance == this) {
            instance = null;
        }
    }

    // ── 进程内降级实现 ──────────────────────────────────────

    private void runOnMain(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    private void ensureSession() {
        if (mediaSession != null || getContext() == null) {
            return;
        }
        mediaSession = new MediaSession(getContext(), "ZhixiangPackPlayback");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                emitMediaAction("play");
            }

            @Override
            public void onPause() {
                emitMediaAction("pause");
            }

            @Override
            public void onSkipToNext() {
                emitMediaAction("next");
            }

            @Override
            public void onSkipToPrevious() {
                emitMediaAction("previous");
            }

            @Override
            public void onStop() {
                emitMediaAction("pause");
            }
        }, mainHandler);
        mediaSession.setActive(true);
    }

    private void applyMetadata() {
        if (mediaSession == null) {
            return;
        }
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, currentAlbum)
                .build());
    }

    private void applyPlaybackState() {
        if (mediaSession == null) {
            return;
        }
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(
                        currentPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        currentPlaying ? 1.0f : 0.0f)
                .build());
    }

    private void showNotification() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "芷湘播放", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle(currentTitle.isEmpty() ? "芷湘" : currentTitle)
                    .setContentText(currentArtist)
                    .setOngoing(currentPlaying)
                    .setShowWhen(false)
                    .build();
            manager.notify(NOTIFICATION_ID, notification);
            notificationActive = true;
        } catch (Exception ignored) {
            // 通知发不出（权限未授予等）不许影响播放主流程：系统卡靠
            // MediaSession 本身也成立，通知只是附带展示。
            notificationActive = false;
        }
    }

    private void cancelNotification() {
        Context context = getContext();
        if (context == null) {
            notificationActive = false;
            return;
        }
        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception ignored) {
        }
        notificationActive = false;
    }

    private String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
