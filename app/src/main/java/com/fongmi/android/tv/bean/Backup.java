package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Backup {

    public static final String PREF_WEB_HOME_EXTENSION = "web_home_extension";
    public static final String PREF_WEB_HOME_EXTENSION_SOURCES = "web_home_extension_user_sources";

    private static final String PLAYBACK_REMOTE_SYNC_CONFIG = "playback_remote_sync_config";
    private static final String PLAYBACK_WEBHOOK_CONFIG = "playback_webhook_config";
    private static final Set<String> DEPRECATED_PREFS = Set.of("theme_color", "wall_color");
    private static final Set<String> APP_PREFS = Set.of("doh", "ua", "wall", "wall_type", "reset", "site_mode", "sync_mode", "sync_paths", "sync_device", "incognito", "family_filter_enabled", "family_filter_keywords", "family_filter_pass", "drive_check", "drive_check_cache", "web_home_fullscreen", "playback_artwork_wall", "csp_warmup", "csp_warmup_mode", "csp_warmup_sites", "shell_proxy", "shell_proxy_rules", "shell_proxy_url", "shell_proxy_hosts", "viewing_record_sync_enabled", "viewing_record_sync_local_write", PLAYBACK_REMOTE_SYNC_CONFIG, PLAYBACK_WEBHOOK_CONFIG, "update", "adblock", "zhuyin", "language", "ui_scale", "crash", "player", "mpv_render", "render", "size", "scale", "buffer", "background", "speed", "caption", "tunnel", "audio_prefer", "video_prefer", "prefer_aac", "subtitle_text_size", "subtitle_position", "boot_live", "across", "change", "invert", "scale_live");

    @SerializedName("site")
    private List<Site> site;
    @SerializedName("live")
    private List<Live> live;
    @SerializedName("keep")
    private List<Keep> keep;
    @SerializedName("config")
    private List<Config> config;
    @SerializedName("history")
    private List<History> history;
    @SerializedName("prefers")
    private Map<String, ?> prefers;

    public static Backup create() {
        Backup backup = new Backup();
        backup.setPrefers(withoutDeprecated(Prefers.getPrefers().getAll()));
        backup.setSite(AppDatabase.get().getSiteDao().findAll());
        backup.setLive(AppDatabase.get().getLiveDao().findAll());
        backup.setKeep(AppDatabase.get().getKeepDao().findAll());
        backup.setConfig(AppDatabase.get().getConfigDao().findAll());
        backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
        return backup;
    }

    public static Backup create(SyncOptions options) {
        Backup backup = new Backup();
        if (options.isConfig()) {
            backup.setSite(AppDatabase.get().getSiteDao().findAll());
            backup.setLive(AppDatabase.get().getLiveDao().findAll());
            backup.setConfig(AppDatabase.get().getConfigDao().findAll());
        }
        if (options.isKeep()) backup.setKeep(AppDatabase.get().getKeepDao().findAll());
        if (options.isHistory()) backup.setHistory(AppDatabase.get().getHistoryDao().findAll());
        backup.setPrefers(filter(withoutDeprecated(Prefers.getPrefers().getAll()), options));
        return backup;
    }

    public static Backup objectFrom(String json) {
        try {
            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
            Backup backup = gson.fromJson(json, Backup.class);
            return backup == null ? new Backup() : backup;
        } catch (Exception e) {
            return new Backup();
        }
    }

    public void restore() {
        AppDatabase db = AppDatabase.get();
        // Room forbids clearAllTables() inside a transaction, so the tables are emptied by hand:
        // an aborted restore (process death, insert failure) must not leave a half-cleared database.
        db.runInTransaction(() -> {
            db.getSiteDao().delete();
            db.getLiveDao().delete();
            db.getKeepDao().deleteAll();
            db.getConfigDao().delete();
            db.getHistoryDao().delete();
            db.getSiteDao().insertOrUpdate(getSite());
            db.getLiveDao().insertOrUpdate(getLive());
            db.getKeepDao().insertOrUpdate(getKeep());
            db.getConfigDao().insertOrUpdate(getConfig());
            db.getHistoryDao().insertOrUpdate(getHistory());
        });
        for (Map.Entry<String, ?> entry : withoutDeprecated(getPrefers()).entrySet()) Prefers.put(entry.getKey(), entry.getValue());
        Prefers.remove("playback_webhook_privacy_accepted");
        Setting.applyLanguage();
        RefreshEvent.language();
    }

    public void restore(boolean preserveMissingWebHomePrefs) {
        restore();
    }

    public static void refreshWebHomeExtensions() {
        WebHomeExtensionRegistry.get().clear();
        HomeWebController.requestExtensionReload();
    }

    public int getWebHomeExtensionPreferenceCount() {
        int count = 0;
        for (String key : getPrefers().keySet()) if (isWebHomeExtensionPref(key)) count++;
        return count;
    }

    public int getWebHomeExtensionSourceCount() {
        Object value = getPrefers().get(PREF_WEB_HOME_EXTENSION_SOURCES);
        if (!(value instanceof String) || ((String) value).isEmpty()) return 0;
        try {
            JsonElement element = new Gson().fromJson((String) value, JsonElement.class);
            return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    static boolean isWebHomeExtensionPref(String key) {
        return PREF_WEB_HOME_EXTENSION.equals(key) || key.startsWith("web_home_extension_") || key.startsWith("web_home_ext_enabled_");
    }

    public void restore(SyncOptions options, boolean force) {
        Map<Integer, Integer> cids = restoreDb(options, force);
        for (Map.Entry<String, ?> entry : filter(getPrefers(), options).entrySet()) Prefers.put(entry.getKey(), entry.getValue());
        Prefers.remove("playback_webhook_privacy_accepted");
        if (options.isSettings()) {
            Setting.applyLanguage();
            RefreshEvent.language();
        }
        if (options.isSpider()) BaseLoader.get().clear();
        if (options.isConfig() || options.isSpider()) reloadConfig();
        if (options.isKeep()) RefreshEvent.keep();
        if (options.isHistory()) RefreshEvent.history();
        RefreshEvent.home();
    }

    /**
     * All database mutations of a sync restore run in one transaction: a failure mid-way must
     * roll back together instead of leaving sites restored while keep/history were deleted.
     */
    private Map<Integer, Integer> restoreDb(SyncOptions options, boolean force) {
        AppDatabase db = AppDatabase.get();
        Map<Integer, Integer> cids = new HashMap<>();
        db.runInTransaction(() -> {
            if (options.isConfig()) {
                if (force) {
                    db.getSiteDao().delete();
                    db.getLiveDao().delete();
                    db.getConfigDao().delete();
                }
                db.getSiteDao().insertOrUpdate(getSite());
                db.getLiveDao().insertOrUpdate(getLive());
                cids.putAll(restoreConfig());
            }
            if (options.isKeep()) {
                if (force) db.getKeepDao().deleteAll();
                for (Keep item : getKeep()) if (cids.containsKey(item.getCid())) item.setCid(cids.get(item.getCid()));
                db.getKeepDao().insertOrUpdate(getKeep());
            }
            if (options.isHistory()) {
                if (force) db.getHistoryDao().delete();
                for (History item : getHistory()) if (cids.containsKey(item.getCid())) item.setCid(cids.get(item.getCid()));
                db.getHistoryDao().insertOrUpdate(getHistory());
            }
        });
        return cids;
    }

    private void reloadConfig() {
        VodConfig.get().clear().init().load(new Callback());
        LiveConfig.get().clear().init().load();
        WallConfig.get().init().load();
        ConfigEvent.common();
    }

    private Map<Integer, Integer> restoreConfig() {
        Map<Integer, Integer> cids = new HashMap<>();
        for (Config item : getConfig()) {
            int source = item.getId();
            Config current = AppDatabase.get().getConfigDao().find(item.getUrl(), item.getType());
            item.setId(current == null ? 0 : current.getId());
            long id = AppDatabase.get().getConfigDao().insert(item);
            if (id == -1) AppDatabase.get().getConfigDao().update(item);
            else item.setId(Math.toIntExact(id));
            if (source > 0) cids.put(source, item.getId());
        }
        return cids;
    }

    private static Map<String, ?> filter(Map<String, ?> source, SyncOptions options) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getValue() != null && !isDeprecated(entry.getKey()) && include(entry.getKey(), options)) result.put(entry.getKey(), redact(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    static Map<String, ?> withoutDeprecated(Map<String, ?> source) {
        Map<String, Object> result = new HashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (!isDeprecated(entry.getKey())) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static boolean isDeprecated(String key) {
        return DEPRECATED_PREFS.contains(key);
    }

    static Object redact(String key, Object value) {
        if (!(value instanceof String) || (!PLAYBACK_REMOTE_SYNC_CONFIG.equals(key) && !PLAYBACK_WEBHOOK_CONFIG.equals(key))) return value;
        try {
            JsonElement root = JsonParser.parseString((String) value);
            if (!root.isJsonObject() && !root.isJsonArray()) return "[]";
            redactSecrets(root);
            return root.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static void redactSecrets(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) redactSecrets(child);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        boolean credentialRemoved = object.remove("token") != null;
        credentialRemoved |= object.remove("secret") != null;
        credentialRemoved |= object.remove("headers") != null;
        credentialRemoved |= object.remove("header") != null;
        if (credentialRemoved) object.addProperty("enabled", false);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) redactSecrets(entry.getValue());
    }

    static boolean include(String key, SyncOptions options) {
        if (key.startsWith("cache_")) return options.isWebHome() || options.isSpider();
        if (key.startsWith("config_")) return options.isConfig();
        if (key.startsWith("login_state_")) return options.isLoginState();
        if ("keyword".equals(key) || "hot".equals(key)) return options.isSearch();
        if (isAppPref(key)) return options.isSettings();
        return options.isSpider();
    }

    private static boolean isAppPref(String key) {
        return APP_PREFS.contains(key) || key.startsWith("danmaku_") || key.startsWith("subtitle_") || key.startsWith("decode_") || key.startsWith("kernel_") || key.startsWith("perf_exo_") || key.startsWith("perf_mpv_") || key.startsWith("perf_ijk_") || key.startsWith("perf_kernel_");
    }

    public List<Site> getSite() {
        return site == null ? Collections.emptyList() : site;
    }

    public void setSite(List<Site> site) {
        this.site = site;
    }

    public List<Live> getLive() {
        return live == null ? Collections.emptyList() : live;
    }

    public void setLive(List<Live> live) {
        this.live = live;
    }

    public List<Keep> getKeep() {
        return keep == null ? Collections.emptyList() : keep;
    }

    public void setKeep(List<Keep> keep) {
        this.keep = keep;
    }

    public List<Config> getConfig() {
        return config == null ? Collections.emptyList() : config;
    }

    public void setConfig(List<Config> config) {
        this.config = config;
    }

    public List<History> getHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    public void setHistory(List<History> history) {
        this.history = history;
    }

    public Map<String, ?> getPrefers() {
        return prefers == null ? new HashMap<>() : prefers;
    }

    public void setPrefers(Map<String, ?> prefers) {
        Map<String, Object> safe = new HashMap<>();
        for (Map.Entry<String, ?> entry : withoutDeprecated(prefers).entrySet()) {
            safe.put(entry.getKey(), redact(entry.getKey(), entry.getValue()));
        }
        this.prefers = safe;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
