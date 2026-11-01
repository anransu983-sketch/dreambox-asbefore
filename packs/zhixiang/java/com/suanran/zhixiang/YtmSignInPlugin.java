package com.suanran.zhixiang;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.suanran.dreambox.core.pack.PackAuthBus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * YTM 登录插件 —— 功能包重写版（方法面与结果结构与独立 App 原版逐字
 * 对齐，Web 层 src/sources/ytm-session.ts 无需任何改动）：
 *
 * - signIn：原版起独立的 YtmSignInActivity 等登录结果（Cookie + UA）。
 *   包内没有 Activity，改走宿主的通用授权壳 PackAuthBus：宿主打开
 *   https://music.youtube.com/ 登录页，用户登录后点「完成」，宿主把
 *   该页 Cookie 回给包。差别如实记在 PACK_NOTES.md：原版是检测到
 *   SAPISID 后自动交接、且合并 www.youtube.com 与 music.youtube.com
 *   两域 Cookie；包版靠用户点「完成」、Cookie 只取 music.youtube.com
 *   一域（SAPISID 是 .youtube.com 域 Cookie，两边都在其中）；
 *   userAgent 原版取登录 WebView 的 UA，包版取同引擎默认 WebView UA
 *   （宿主授权壳未改 UA，两者一致）。
 *   取消/无凭证一律 resolve 成 ok:false，与原版一致，不抛错。
 * - probeWatchPage / extractViaPage：原版把离屏 WebView 挂到 Activity
 *   的内容视图上；包版没有 Activity，改用应用 Context 建 WebView 并
 *   手动 measure/layout 后驱动同一套 JS 与轮询。页面媒体管线在未挂载
 *   WebView 上可能被节流，抽链成功率可能低于独立 App —— 这是 v1 的
 *   已知降级（Web 层对这两个方法本来就按「失败回 null、走兜底」写的）。
 */
@CapacitorPlugin(name = "YtmSignIn")
public class YtmSignInPlugin extends Plugin {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicReference<WebView> activeWeb = new AtomicReference<>();

    @PluginMethod
    public void signIn(PluginCall call) {
        Context context = getContext();
        if (context == null) {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            call.resolve(ret);
            return;
        }
        mainHandler.post(() -> {
            String ua = "";
            try {
                WebView probe = new WebView(context);
                ua = probe.getSettings().getUserAgentString();
                probe.destroy();
            } catch (Exception ignored) {
                ua = "";
            }
            final String userAgent = ua == null ? "" : ua;
            PackAuthBus.INSTANCE.requestJava(
                    context,
                    "https://music.youtube.com/",
                    "YouTube Music 登录",
                    cookie -> {
                        JSObject ret = new JSObject();
                        if (cookie != null && !cookie.isEmpty()) {
                            ret.put("ok", true);
                            ret.put("cookie", cookie);
                            ret.put("userAgent", userAgent);
                        } else {
                            ret.put("ok", false);
                        }
                        call.resolve(ret);
                    });
        });
    }

    // 登录门探针：与原版同一段 JS、同一轮询节奏、同一返回结构（{raw}）。
    private static final String PROBE_JS =
        "(function(){try{" +
        "var r=window.ytInitialPlayerResponse;" +
        "if(!r||!r.playabilityStatus){return JSON.stringify({ok:false,err:'no-player-yet'});}" +
        "var ps=r.playabilityStatus;var sd=r.streamingData;" +
        "var fmts=(sd&&sd.adaptiveFormats)||[];var audio=0,direct=0;" +
        "for(var i=0;i<fmts.length;i++){var m=fmts[i].mimeType||'';" +
        "if(m.indexOf('audio')===0){audio++;if(fmts[i].url){direct++;}}}" +
        "return JSON.stringify({ok:true,status:String(ps.status||'')," +
        "reason:String(ps.reason||''),hasStreaming:!!sd,audioCount:audio," +
        "directCount:direct,visitorData:(r.responseContext&&r.responseContext.visitorData)||''});" +
        "}catch(e){return JSON.stringify({ok:false,err:String(e)});}})()";

    @PluginMethod
    public void probeWatchPage(PluginCall call) {
        String videoId = call.getString("videoId", "");
        if (videoId == null || videoId.isEmpty()) {
            call.reject("videoId 为空");
            return;
        }
        Context context = getContext();
        if (context == null) {
            call.reject("无 Context");
            return;
        }
        final Handler handler = mainHandler;
        final AtomicBoolean done = new AtomicBoolean(false);
        mainHandler.post(() -> {
            final WebView web;
            try {
                web = new WebView(context);
            } catch (Exception e) {
                call.reject("WebView 不可用", "NO_WEBVIEW", e);
                return;
            }
            activeWeb.set(web);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            web.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY));
            web.layout(0, 0, 720, 1280);
            web.onResume();
            web.resumeTimers();
            final Runnable[] poll = new Runnable[1];
            final long deadline = System.currentTimeMillis() + 20000;
            final Runnable finish = () -> {
                if (!done.compareAndSet(false, true)) {
                    return;
                }
                handler.removeCallbacks(poll[0]);
                activeWeb.compareAndSet(web, null);
                web.destroy();
            };
            poll[0] = () -> {
                if (done.get()) {
                    return;
                }
                web.evaluateJavascript(PROBE_JS, value -> {
                    if (done.get()) {
                        return;
                    }
                    boolean ready = value != null && value.contains("\\\"ok\\\":true");
                    if (ready || System.currentTimeMillis() > deadline) {
                        JSObject ret = new JSObject();
                        ret.put("raw", value == null ? "" : value);
                        finish.run();
                        call.resolve(ret);
                        return;
                    }
                    handler.postDelayed(poll[0], 1500);
                });
            };
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    handler.postDelayed(poll[0], 800);
                }
            });
            handler.postDelayed(poll[0], 6000);
            web.loadUrl("https://www.youtube.com/watch?v=" + videoId);
        });
    }

    // 页面抽取兜底：与原版同一套 JS 与双级捕获逻辑（音频轨优先、任意
    // videoplayback 兜底），差别只有 WebView 的挂载方式（见类注释）。
    private static final String EXTRACT_JS =
        "(function(){try{" +
        "var r=window.ytInitialPlayerResponse;" +
        "if(!r||!r.playabilityStatus){return JSON.stringify({ok:false,err:'no-player-yet'});}" +
        "var ps=r.playabilityStatus;var sd=r.streamingData;" +
        "var fmts=(sd&&sd.adaptiveFormats)||[];var audio=0,direct=0;" +
        "for(var i=0;i<fmts.length;i++){var m=fmts[i].mimeType||'';" +
        "if(m.indexOf('audio')===0){audio++;if(fmts[i].url){direct++;}}}" +
        "var vd='';try{vd=(window.ytcfg&&ytcfg.data_&&ytcfg.data_.VISITOR_DATA)||'';}catch(e){}" +
        "if(!vd){vd=(r.responseContext&&r.responseContext.visitorData)||'';}" +
        "return JSON.stringify({ok:true,status:String(ps.status||'')," +
        "reason:String(ps.reason||''),hasStreaming:!!sd,audioCount:audio," +
        "directCount:direct,visitorData:vd});" +
        "}catch(e){return JSON.stringify({ok:false,err:String(e)});}})()";

    private static final String NUDGE_JS =
        "(function(){try{" +
        "var mp=document.getElementById('movie_player');" +
        "if(mp&&mp.playVideo){mp.playVideo();}" +
        "var v=document.querySelector('video');" +
        "if(v){v.muted=true;var p=v.play();if(p&&p.catch){p.catch(function(){});}}" +
        "var b=document.querySelector('.ytp-large-play-button,.html5-large-play-button');" +
        "if(b){b.click();}return 'nudged';}catch(e){return 'err';}})()";

    private static final String STATE_JS =
        "(function(){try{" +
        "var v=document.querySelector('video');" +
        "var mp=document.getElementById('movie_player');" +
        "var out={v:!!v,mp:!!mp};" +
        "if(v){out.rs=v.readyState;out.paused=v.paused;" +
        "out.ct=Math.round((v.currentTime||0)*10)/10;" +
        "out.buf=(v.buffered&&v.buffered.length)||0;" +
        "out.src=(v.currentSrc||'').slice(0,5);}" +
        "if(mp&&mp.getPlayerState){try{out.ps=mp.getPlayerState();}catch(e){}}" +
        "return JSON.stringify(out);" +
        "}catch(e){return JSON.stringify({err:String(e)});}})()";

    private static void observeRequest(
            java.util.concurrent.ConcurrentHashMap<String, Integer> tally,
            AtomicReference<String> audioUrl,
            AtomicReference<String> audioMime,
            AtomicReference<String> audioItag,
            AtomicReference<String> anyUrl,
            AtomicReference<String> anyMime,
            AtomicReference<String> anyItag,
            java.util.concurrent.atomic.AtomicLong anyAt,
            android.net.Uri uri) {
        try {
            String host = uri.getHost() == null ? "?" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            int slash = path.indexOf('/', 1);
            String key = host + (slash > 0 ? path.substring(0, slash) : path);
            tally.merge(key, 1, Integer::sum);
            String url = uri.toString();
            if (url.contains("googlevideo.com/videoplayback")) {
                String mime = uri.getQueryParameter("mime");
                String itag = uri.getQueryParameter("itag");
                if (anyUrl.get().isEmpty()) {
                    anyMime.set(mime == null ? "" : mime);
                    anyItag.set(itag == null ? "" : itag);
                    anyUrl.set(url);
                    anyAt.set(System.currentTimeMillis());
                }
                if (mime != null && mime.startsWith("audio") && audioUrl.get().isEmpty()) {
                    audioMime.set(mime);
                    audioItag.set(itag == null ? "" : itag);
                    audioUrl.set(url);
                }
            }
        } catch (Exception ignored) {
            // 观察只许帮忙、不许把页面请求弄崩。
        }
    }

    private static String tallySummary(java.util.concurrent.ConcurrentHashMap<String, Integer> tally) {
        java.util.List<java.util.Map.Entry<String, Integer>> entries =
            new java.util.ArrayList<>(tally.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        int n = Math.min(entries.size(), 12);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(entries.get(i).getKey()).append('x').append(entries.get(i).getValue());
        }
        return sb.toString();
    }

    private static void tapCenter(WebView web) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down =
                android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 360f, 200f, 0);
            android.view.MotionEvent up =
                android.view.MotionEvent.obtain(now, now + 90, android.view.MotionEvent.ACTION_UP, 360f, 200f, 0);
            web.dispatchTouchEvent(down);
            web.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
        } catch (Exception ignored) {
        }
    }

    @PluginMethod
    public void extractViaPage(PluginCall call) {
        String videoId = call.getString("videoId", "");
        if (videoId == null || videoId.isEmpty()) {
            call.reject("videoId 为空");
            return;
        }
        Context context = getContext();
        if (context == null) {
            call.reject("无 Context");
            return;
        }
        final Handler handler = mainHandler;
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<String> audioUrl = new AtomicReference<>("");
        final AtomicReference<String> audioMime = new AtomicReference<>("");
        final AtomicReference<String> audioItag = new AtomicReference<>("");
        final AtomicReference<String> anyUrl = new AtomicReference<>("");
        final AtomicReference<String> anyMime = new AtomicReference<>("");
        final AtomicReference<String> anyItag = new AtomicReference<>("");
        final java.util.concurrent.atomic.AtomicLong anyAt = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.ConcurrentHashMap<String, Integer> tally =
            new java.util.concurrent.ConcurrentHashMap<>();
        mainHandler.post(() -> {
            final WebView web;
            try {
                web = new WebView(context);
            } catch (Exception e) {
                call.reject("WebView 不可用", "NO_WEBVIEW", e);
                return;
            }
            activeWeb.set(web);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            web.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY));
            web.layout(0, 0, 720, 400);
            web.onResume();
            web.resumeTimers();
            final Runnable[] poll = new Runnable[1];
            final long deadline = System.currentTimeMillis() + 45000;
            final String[] lastRaw = {""};
            final String[] lastState = {""};
            final int[] gatedPolls = {0};
            final int[] taps = {0};
            final Runnable finish = () -> {
                if (!done.compareAndSet(false, true)) {
                    return;
                }
                handler.removeCallbacks(poll[0]);
                try {
                    ServiceWorkerController.getInstance().setServiceWorkerClient(null);
                } catch (Exception ignored) {
                }
                JSObject ret = new JSObject();
                ret.put("raw", lastRaw[0]);
                ret.put("audioUrl", audioUrl.get());
                ret.put("audioMime", audioMime.get());
                ret.put("audioItag", audioItag.get());
                ret.put("anyUrl", anyUrl.get());
                ret.put("anyMime", anyMime.get());
                ret.put("anyItag", anyItag.get());
                ret.put("tally", tallySummary(tally));
                ret.put("stateRaw", lastState[0]);
                activeWeb.compareAndSet(web, null);
                web.destroy();
                call.resolve(ret);
            };
            try {
                ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        observeRequest(tally, audioUrl, audioMime, audioItag, anyUrl, anyMime, anyItag, anyAt, request.getUrl());
                        return null;
                    }
                });
            } catch (Exception ignored) {
            }
            poll[0] = () -> {
                if (done.get()) {
                    return;
                }
                boolean haveAudio = !audioUrl.get().isEmpty();
                boolean anyStale = !anyUrl.get().isEmpty()
                    && System.currentTimeMillis() - anyAt.get() > 8000;
                if (haveAudio || anyStale || System.currentTimeMillis() > deadline) {
                    finish.run();
                    return;
                }
                web.evaluateJavascript(EXTRACT_JS, value -> {
                    if (done.get()) {
                        return;
                    }
                    if (value != null && value.contains("\\\"ok\\\":true")) {
                        lastRaw[0] = value;
                        boolean pageOk = value.contains("\\\"status\\\":\\\"OK\\\"");
                        if (!pageOk) {
                            gatedPolls[0]++;
                            if (gatedPolls[0] >= 2) {
                                finish.run();
                                return;
                            }
                        } else if (taps[0] < 3) {
                            taps[0]++;
                            tapCenter(web);
                        }
                    }
                    if (!audioUrl.get().isEmpty()
                        || (!anyUrl.get().isEmpty()
                            && System.currentTimeMillis() - anyAt.get() > 8000)) {
                        finish.run();
                        return;
                    }
                    web.evaluateJavascript(STATE_JS, state -> {
                        if (state != null && !state.isEmpty()) {
                            lastState[0] = state;
                        }
                    });
                    web.evaluateJavascript(NUDGE_JS, null);
                    handler.postDelayed(poll[0], 1200);
                });
            };
            web.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    observeRequest(tally, audioUrl, audioMime, audioItag, anyUrl, anyMime, anyItag, anyAt, request.getUrl());
                    return null;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    handler.postDelayed(poll[0], 800);
                }
            });
            handler.postDelayed(poll[0], 6000);
            web.loadUrl("https://www.youtube.com/watch?v=" + videoId);
        });
    }
}
