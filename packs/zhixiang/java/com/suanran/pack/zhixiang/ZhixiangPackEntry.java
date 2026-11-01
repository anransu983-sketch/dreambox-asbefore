package com.suanran.pack.zhixiang;

import android.content.Context;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.suanran.dreambox.core.pack.PackBridgeDispatcher;
import com.suanran.dreambox.core.pack.PackCallback;
import com.suanran.zhixiang.ExternalOpenPlugin;
import com.suanran.zhixiang.HttpBridgePlugin;
import com.suanran.zhixiang.LocalMusicPlugin;
import com.suanran.zhixiang.PlaybackPlugin;
import com.suanran.zhixiang.WallpaperPlugin;
import com.suanran.zhixiang.YtmSignInPlugin;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/**
 * 芷湘功能包入口：实现宿主的 PackBridgeDispatcher，把 Capacitor 协议的
 * 插件调用（pluginId / method / options JSON）路由到各插件实例。
 *
 * 路由是显式表，不靠反射扫 @PluginMethod：方法覆盖面就以这张表为准，
 * 与原版的对照表在 PACK_NOTES.md 里。表里没有的插件/方法返回 false，
 * 宿主会回「功能包未实现」错误，Web 层按插件不可用处理。
 *
 * addListener / removeListener 不会到这里：宿主在桥上先行拦截并登记
 * JS 回调；包的事件经 init 的 emit 直接推出（compat Plugin 的
 * notifyListeners 已接好这条线）。
 */
public class ZhixiangPackEntry implements PackBridgeDispatcher {

    private Context appContext;
    private Function3<? super String, ? super String, ? super String, Unit> emitFn;

    private HttpBridgePlugin httpBridge;
    private PlaybackPlugin playback;
    private LocalMusicPlugin localMusic;
    private YtmSignInPlugin ytmSignIn;
    private WallpaperPlugin wallpaper;
    private ExternalOpenPlugin externalOpen;

    @Override
    public void init(Context context, Function3<? super String, ? super String, ? super String, Unit> emit) {
        this.appContext = context.getApplicationContext();
        this.emitFn = emit;

        httpBridge = register(new HttpBridgePlugin(), "HttpBridge");
        playback = register(new PlaybackPlugin(), "Playback");
        localMusic = register(new LocalMusicPlugin(), "LocalMusic");
        ytmSignIn = register(new YtmSignInPlugin(), "YtmSignIn");
        wallpaper = register(new WallpaperPlugin(), "Wallpaper");
        externalOpen = register(new ExternalOpenPlugin(), "ExternalOpen");
    }

    private <T extends Plugin> T register(T plugin, String pluginId) {
        plugin.initForPack(appContext, (id, eventName, dataJson) -> {
            Function3<? super String, ? super String, ? super String, Unit> fn = emitFn;
            if (fn != null) {
                fn.invoke(id, eventName, dataJson);
            }
        }, pluginId);
        plugin.load();
        return plugin;
    }

    @Override
    public boolean dispatch(String pluginId, String method, String optionsJson, PackCallback callback) {
        PluginCall call = new PluginCall(optionsJson, callback);
        switch (pluginId == null ? "" : pluginId) {
            case "HttpBridge":
                if ("request".equals(method)) {
                    httpBridge.request(call);
                    return true;
                }
                return false;
            case "Playback":
                switch (method == null ? "" : method) {
                    case "start":
                        playback.start(call);
                        return true;
                    case "stop":
                        playback.stop(call);
                        return true;
                    case "ensureProxy":
                        playback.ensureProxy(call);
                        return true;
                    case "stopProxy":
                        playback.stopProxy(call);
                        return true;
                    case "setMedia":
                        playback.setMedia(call);
                        return true;
                    case "setPlaying":
                        playback.setPlaying(call);
                        return true;
                    case "clearMedia":
                        playback.clearMedia(call);
                        return true;
                    default:
                        return false;
                }
            case "LocalMusic":
                switch (method == null ? "" : method) {
                    case "scan":
                        localMusic.scan(call);
                        return true;
                    case "coverArt":
                        localMusic.coverArt(call);
                        return true;
                    case "coverFile":
                        localMusic.coverFile(call);
                        return true;
                    case "checkAllFilesAccess":
                        localMusic.checkAllFilesAccess(call);
                        return true;
                    case "requestAllFilesAccess":
                        localMusic.requestAllFilesAccess(call);
                        return true;
                    case "writeSidecar":
                        localMusic.writeSidecar(call);
                        return true;
                    case "downloadCover":
                        localMusic.downloadCover(call);
                        return true;
                    case "embeddedLyrics":
                        localMusic.embeddedLyrics(call);
                        return true;
                    case "saveToDownloads":
                        localMusic.saveToDownloads(call);
                        return true;
                    default:
                        return false;
                }
            case "YtmSignIn":
                switch (method == null ? "" : method) {
                    case "signIn":
                        ytmSignIn.signIn(call);
                        return true;
                    case "probeWatchPage":
                        ytmSignIn.probeWatchPage(call);
                        return true;
                    case "extractViaPage":
                        ytmSignIn.extractViaPage(call);
                        return true;
                    default:
                        return false;
                }
            case "Wallpaper":
                switch (method == null ? "" : method) {
                    case "setWallpaperState":
                        wallpaper.setWallpaperState(call);
                        return true;
                    case "openLiveWallpaperPicker":
                        wallpaper.openLiveWallpaperPicker(call);
                        return true;
                    default:
                        return false;
                }
            case "ExternalOpen":
                if ("openUrl".equals(method)) {
                    externalOpen.openUrl(call);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public void release() {
        if (playback != null) {
            playback.releasePack();
        }
    }
}
