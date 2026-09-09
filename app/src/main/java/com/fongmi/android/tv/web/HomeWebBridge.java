package com.fongmi.android.tv.web;

import android.app.Activity;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.drive.DriveCheckRequest;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.process.Media;
import com.fongmi.android.tv.service.DriveCheckService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.FamilyFilter;
import com.fongmi.android.tv.utils.DependencyTrust;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class HomeWebBridge {

    private static final int INLINE_LIMIT = 12000;
    private static final int CHUNK_SIZE = 60000;
    private static final int DIAG_LIMIT = 80;
    private static final List<String> MAIN_FRAME_ONLY = List.of("app.history", "site.info", "config.info", "device.info");
    private static final Map<String, Integer> CALL_COUNTS = new ConcurrentHashMap<>();
    private static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

    private final HomeWebController controller;
    private final Activity activity;
    private final WebView webView;
    private final Map<String, String> results;

    public HomeWebBridge(HomeWebController controller, Activity activity, WebView webView) {
        this.controller = controller;
        this.activity = activity;
        this.webView = webView;
        this.results = new ConcurrentHashMap<>();
    }

    @JavascriptInterface
    public void invoke(String requestId, String method, String payload) {
        final boolean trusted = controller.isTrustedHomePage();
        Task.execute(() -> handle(requestId, method, WebCall.object(payload), trusted));
    }

    @JavascriptInterface
    public void console(String level, String message) {
        controller.dispatchDebugConsole(level, message);
    }

    @JavascriptInterface
    public void network(String type, String method, String url, int status, long durationMs, String detail) {
        controller.dispatchDebugNetwork(type, method, url, status, durationMs, detail);
    }

    @JavascriptInterface
    public String resourceUrl(String url, String options) {
        return resourceUrl(url, options, controller.isTrustedHomePage());
    }

    private String resourceUrl(String url, String options, boolean trusted) {
        JsonObject object = WebCall.object(options);
        if (trusted && sensitiveRequest(object)) requireBridgeToken(object);
        if (!trusted && sensitiveRequest(object)) throw new SecurityException("Forbidden method: net.resourceUrl");
        StringBuilder builder = new StringBuilder(Server.get().getAddress("/webResource?url=")).append(encode(url));
        if (object.has("headers")) builder.append("&headers=").append(encode(object.get("headers").toString()));
        if ("include".equals(Json.safeString(object, "credentials"))) builder.append("&credentials=include");
        return builder.toString();
    }

    @JavascriptInterface
    public int resultLength(String id) {
        String result = results.get(id);
        return result == null ? 0 : result.length();
    }

    @JavascriptInterface
    public String resultChunk(String id, int start) {
        String result = results.get(id);
        if (result == null || start < 0 || start >= result.length()) return "";
        return result.substring(start, Math.min(start + CHUNK_SIZE, result.length()));
    }

    @JavascriptInterface
    public void clearResult(String id) {
        results.remove(id);
    }

    private void handle(String requestId, String method, JsonObject payload, boolean trusted) {
        String origin = controller.getTrustedOrigin();
        record(origin, method, "call", trusted);
        try {
            SpiderDebug.log("webhome", "invoke method=%s", method);
            guard(method, payload, trusted);
            String result = switch (method) {
                case "net.request" -> WebCall.request(payload, controller);
                case "net.resourceUrl" -> quote(resourceUrl(Json.safeString(payload, "url"), payload.toString(), trusted));
                case "player.playUrl" -> playUrl(payload, trusted);
                case "player.playVod" -> playVod(payload);
                case "player.playVodInline" -> playVodInline(payload);
                case "player.control" -> control(payload);
                case "player.status" -> status();
                case "app.search" -> search(payload);
                case "app.openLive" -> openLive();
                case "app.openKeep" -> openKeep();
                case "app.openVod" -> openVod();
                case "app.openSetting" -> openSetting();
                case "app.history" -> history();
                case "pan.check" -> checkLinks(payload);
                case "pan.play" -> playPan(payload, trusted);
                case "cache.get" -> quote(Prefers.getString(cacheKey(payload)));
                case "cache.set" -> cacheSet(payload);
                case "cache.del" -> cacheDel(payload);
                case "device.info" -> device();
                case "site.info" -> site();
                case "config.info" -> config();
                case "ext.info" -> extInfo();
                case "ext.log" -> extLog(payload);
                case "ext.toast" -> extToast(payload);
                case "ui.setToolbar" -> setToolbar(payload);
                case "navigation.back" -> back();
                case "navigation.reload" -> reload();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            record(origin, method, "ok", trusted);
            resolve(requestId, result);
        } catch (Throwable e) {
            record(origin, method, "deny:" + e.getMessage(), trusted);
            reject(requestId, e.getMessage());
        }
    }

    public static String diagnostics(HomeWebController controller) {
        StringBuilder builder = new StringBuilder();
        String origin = controller == null ? "" : controller.getTrustedOrigin();
        builder.append("Bridge Sandbox\n");
        builder.append("Origin: ").append(origin).append('\n');
        builder.append("Trusted: ").append(controller != null && controller.isTrustedHomePage()).append("\n\n");
        builder.append("Call Counts\n");
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : CALL_COUNTS.entrySet()) if (entry.getKey().startsWith(origin + "|")) counts.put(entry.getKey().substring(origin.length() + 1), entry.getValue());
        if (counts.isEmpty()) builder.append("-\n");
        else for (Map.Entry<String, Integer> entry : counts.entrySet()) builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        builder.append("\nRecent Events\n");
        synchronized (EVENTS) {
            int start = Math.max(0, EVENTS.size() - 30);
            for (int i = start; i < EVENTS.size(); i++) builder.append(EVENTS.get(i)).append('\n');
        }
        return builder.toString();
    }

    private static void record(String origin, String method, String result, boolean trusted) {
        String key = origin + "|" + method;
        CALL_COUNTS.merge(key, 1, Integer::sum);
        synchronized (EVENTS) {
            EVENTS.add((trusted ? "T" : "U") + " " + result + " " + method + " @ " + origin);
            while (EVENTS.size() > DIAG_LIMIT) EVENTS.remove(0);
        }
    }

    private void guard(String method, JsonObject payload, boolean trusted) {
        if (trusted) {
            // addJavascriptInterface exposes the bridge to every frame; reads that return device or
            // configuration data must present the per-load main-frame bridge token, so a cross-origin
            // iframe inside a trusted page cannot read them.
            if (MAIN_FRAME_ONLY.contains(method) || sensitiveRequest(payload)) requireBridgeToken(payload);
            return;
        }
        if ("player.playUrl".equals(method) || "pan.play".equals(method)) validatePlayable(Json.safeString(payload, "url"));
        if (isUntrustedAllowed(method, payload)) return;
        throw new SecurityException("Forbidden method: " + method);
    }

    private void requireBridgeToken(JsonObject payload) {
        String token = payload == null ? "" : Json.safeString(payload, "__tk");
        String expected = controller.getBridgeToken();
        if (TextUtils.isEmpty(expected) || !expected.equals(token)) throw new SecurityException("Bridge token required");
    }

    private boolean isUntrustedAllowed(String method, JsonObject payload) {
        return switch (method) {
            case "net.request", "net.resourceUrl" -> !sensitiveRequest(payload);
            case "player.playUrl", "player.status", "ext.info", "ext.log", "ext.toast" -> true;
            case "pan.check", "pan.play" -> true;
            default -> false;
        };
    }

    private boolean sensitiveRequest(JsonObject payload) {
        return payload.has("headers") || payload.has("cookies") || "include".equals(Json.safeString(payload, "credentials"));
    }

    private void validatePlayable(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) throw new SecurityException("Unsupported url");
        } catch (Throwable e) {
            throw new SecurityException("Unsupported url");
        }
    }

    private String playUrl(JsonObject payload, boolean trusted) {
        String url = Json.safeString(payload, "url");
        String title = Json.safeString(payload, "title");
        if (payload.has("headers") || "include".equals(Json.safeString(payload, "credentials"))) url = resourceUrl(url, payload.toString(), trusted);
        final String playUrl = url;
        final String playTitle = TextUtils.isEmpty(title) ? playUrl : title;
        SpiderDebug.log("webhome", "player.playUrl title=%s url=%s", playTitle, playUrl);
        if (!trusted && !DependencyTrust.confirmPlay(controller.getTrustedOrigin(), playUrl)) return "{}";
        App.post(() -> VideoActivity.start(activity, SiteApi.PUSH, playUrl, playTitle));
        return "{}";
    }

    private String playVod(JsonObject payload) {
        String siteKey = Json.safeString(payload, "siteKey");
        String vodId = Json.safeString(payload, "vodId");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        App.post(() -> VideoActivity.start(activity, siteKey, vodId, title, pic));
        return "{}";
    }

    private String playVodInline(JsonObject payload) {
        String vodId = WebHomeInlineVodStore.put(payload, this::resolveInlineEpisode);
        String title = Json.safeString(payload, "title");
        if (TextUtils.isEmpty(title)) title = Json.safeString(payload, "vod_name");
        String pic = Json.safeString(payload, "pic");
        if (TextUtils.isEmpty(pic)) pic = Json.safeString(payload, "vod_pic");
        String mark = Json.safeString(payload, "mark");
        final String playTitle = TextUtils.isEmpty(title) ? vodId : title;
        final String playPic = pic;
        final String playMark = mark;
        SpiderDebug.log("webhome", "player.playVodInline title=%s id=%s mark=%s", playTitle, vodId, playMark);
        App.post(() -> VideoActivity.start(activity, WebHomeInlineVodStore.KEY, vodId, playTitle, playPic, playMark));
        JsonObject result = new JsonObject();
        result.addProperty("siteKey", WebHomeInlineVodStore.KEY);
        result.addProperty("vodId", vodId);
        return result.toString();
    }

    /** Resolves an inline episode to a direct media URL; pageUrl-based resolution is not supported yet. */
    private JsonObject resolveInlineEpisode(JsonObject payload) {
        JsonObject result = new JsonObject();
        String url = Json.safeString(payload, "url");
        if (TextUtils.isEmpty(url)) url = Json.safeString(payload, "mediaUrl");
        if (!TextUtils.isEmpty(url)) result.addProperty("url", url);
        return result;
    }

    private String control(JsonObject payload) {
        PlaybackService service = Server.get().getService();
        String action = Json.safeString(payload, "action");
        if (service == null) return "{}";
        App.post(() -> {
            if ("play".equals(action)) service.player().play();
            else if ("pause".equals(action)) service.player().pause();
            else if ("stop".equals(action)) service.dispatchStop();
            else if ("prev".equals(action)) service.dispatchPrev();
            else if ("next".equals(action)) service.dispatchNext();
            else if ("loop".equals(action)) service.dispatchRepeat();
            else if ("replay".equals(action)) service.dispatchReplay();
        });
        return "{}";
    }

    private String status() {
        PlaybackService service = Server.get().getService();
        return service == null ? "{}" : Media.build(service.player()).toString();
    }

    private String search(JsonObject payload) {
        String keyword = Json.safeString(payload, "keyword");
        boolean direct = payload.has("direct") && payload.get("direct").getAsBoolean();
        App.post(() -> {
            if (direct) SearchActivity.direct(activity, keyword);
            else SearchActivity.start(activity, keyword);
        });
        return "{}";
    }

    private String openLive() {
        App.post(() -> LiveActivity.start(activity));
        return "{}";
    }

    private String openKeep() {
        App.post(() -> KeepActivity.start(activity));
        return "{}";
    }

    private String openVod() {
        App.post(controller::openVod);
        return "{}";
    }

    private String openSetting() {
        App.post(controller::openSetting);
        return "{}";
    }

    private String history() {
        return App.gson().toJson(History.get());
    }

    private String stripPush(String url) {
        if (url == null) return "";
        String value = url.trim();
        return value.regionMatches(true, 0, "push://", 0, 7) ? value.substring(7) : value;
    }

    private String cacheSet(JsonObject payload) {
        Prefers.put(cacheKey(payload), Json.safeString(payload, "value"));
        return "{}";
    }

    private String cacheDel(JsonObject payload) {
        Prefers.remove(cacheKey(payload));
        return "{}";
    }

    private String cacheKey(JsonObject payload) {
        String rule = sanitize(Json.safeString(payload, "rule"));
        String key = sanitize(Json.safeString(payload, "key"));
        String originHash = Integer.toHexString(controller.getTrustedOrigin().hashCode());
        return "cache_" + originHash + "_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') sb.append(c);
        }
        return sb.toString();
    }

    private String device() {
        return App.gson().toJson(Device.get());
    }

    private String site() {
        Site site = VodConfig.get().getHome();
        JsonObject object = new JsonObject();
        object.addProperty("key", site.getKey());
        object.addProperty("name", site.getName());
        object.addProperty("homePage", site.getHomePage());
        object.addProperty("type", site.getType());
        object.add("header", App.gson().toJsonTree(site.getHeader()));
        return object.toString();
    }

    private String checkLinks(JsonObject payload) {
        if (!Setting.isDriveCheck()) throw new IllegalStateException("网盘检测未开启");
        DriveCheckRequest request = App.gson().fromJson(payload, DriveCheckRequest.class);
        if (request == null || request.getItems().isEmpty()) throw new IllegalArgumentException("items不能为空");
        SpiderDebug.log("webhome", "pan.check count=%s", request.getItems().size());
        return App.gson().toJson(DriveCheckService.get().check(request.getItems()));
    }

    private String playPan(JsonObject payload, boolean trusted) {
        String url = Json.safeString(payload, "url");
        String title = Json.safeString(payload, "title");
        String pic = Json.safeString(payload, "pic");
        if (TextUtils.isEmpty(url)) throw new IllegalArgumentException("url不能为空");
        final String playUrl = stripPush(url.trim());
        final String playTitle = TextUtils.isEmpty(title) ? playUrl : title;
        if (!trusted && !DependencyTrust.confirmPlay(controller.getTrustedOrigin(), playUrl)) return "{}";
        SpiderDebug.log("webhome", "pan.play type=%s title=%s url=%s", SiteApi.PUSH, playTitle, playUrl);
        App.post(() -> VideoActivity.start(activity, SiteApi.PUSH, playUrl, playTitle, pic));
        return "{}";
    }

    private String config() {
        JsonObject object = new JsonObject();
        object.addProperty("id", VodConfig.getCid());
        object.addProperty("url", VodConfig.getUrl());
        object.addProperty("desc", VodConfig.getDesc());
        object.addProperty("driveCheck", Setting.isDriveCheck());
        object.addProperty("familyFilterEnabled", Setting.isFamilyFilter());
        object.add("familyFilterKeywords", App.gson().toJsonTree(FamilyFilter.keywords()));
        return object.toString();
    }

    private String extInfo() {
        JsonObject object = new JsonObject();
        Site site = VodConfig.get().getHome();
        object.addProperty("siteKey", site.getKey());
        object.addProperty("siteName", site.getName());
        object.addProperty("homePage", site.getHomePage());
        WebHomeExtensionRegistry.Snapshot snapshot = WebHomeExtensionRegistry.get().snapshot();
        object.addProperty("enabled", snapshot.enabled);
        object.addProperty("matched", snapshot.matchedCount);
        object.addProperty("ready", snapshot.readyCount);
        return object.toString();
    }

    private String extLog(JsonObject payload) {
        WebHomeExtensionRegistry.get().recordScriptLog(payload);
        SpiderDebug.log("webhome-ext", "script message=%s data=%s", Json.safeString(payload, "message"), payload.has("data") ? payload.get("data") : "");
        return "{}";
    }

    private String extToast(JsonObject payload) {
        String message = Json.safeString(payload, "message");
        if (!TextUtils.isEmpty(message)) App.post(() -> Notify.show(message));
        return "{}";
    }

    private String setToolbar(JsonObject payload) {
        boolean visible = !payload.has("visible") || payload.get("visible").getAsBoolean();
        App.post(() -> controller.setToolbar(visible));
        return "{}";
    }

    private String back() {
        App.post(controller::handleBack);
        return "{}";
    }

    private String reload() {
        App.post(controller::reload);
        return "{}";
    }

    private void resolve(String requestId, String data) {
        String payload = TextUtils.isEmpty(data) ? "null" : data;
        if (payload.length() > INLINE_LIMIT) {
            String resultId = requestId + "_" + System.nanoTime();
            results.put(resultId, payload);
            payload = "{\"__fmResultId\":" + quote(resultId) + "}";
        }
        eval("window.fongmiNative&&window.fongmiNative.resolve(" + quote(requestId) + "," + payload + ")");
    }

    private void reject(String requestId, String error) {
        eval("window.fongmiNative&&window.fongmiNative.reject(" + quote(requestId) + "," + quote(error) + ")");
    }

    private void eval(String script) {
        App.post(() -> webView.evaluateJavascript(script, null));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String quote(String text) {
        return App.gson().toJson(text == null ? "" : text);
    }
}
