package com.fongmi.android.tv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.update.GithubProxy;
import com.fongmi.android.tv.update.HttpUpdateTransfer;
import com.fongmi.android.tv.update.UpdateTransfer;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Updater implements UpdateListener, UpdateTransfer.Callback {

    private static final String GITHUB_RELEASE = "https://github.com/motao123/webtv/releases/latest";
    private static final String CNB_RELEASE = "https://cnb.cool/code_free/webtv/-/git/raw/main/apk";

    private UpdateDialog dialog;
    private FragmentActivity activity;
    private Update update;
    private List<String> routes;
    private int routeIndex;
    private UpdateTransfer transfer;
    private boolean downloading;
    private boolean canceled;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getFlavor() {
        return BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        this.activity = activity;
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            update = fetchUpdate();
            if (update == null || !update.hasManifest()) {
                App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            if (!update.hasUpdate()) {
                App.post(() -> Notify.show(update.error != null ? R.string.update_failed : R.string.update_latest));
                return;
            }
            App.post(() -> show(activity, update));
        } catch (Exception e) {
            SpiderDebug.log(e);
            App.post(() -> Notify.show(R.string.update_failed));
        }
    }

    private Update fetchUpdate() throws Exception {
        Update best = null;
        Exception last = null;
        for (String url : Github.getJsonCandidates(getFlavor())) {
            try {
                Update update = readManifest(url);
                if (best == null || update.code > best.code) best = update;
            } catch (Exception e) {
                SpiderDebug.log(e);
                last = e;
            }
        }
        if (best == null) throw last != null ? last : new IllegalStateException("No update source reachable");
        return best;
    }

    private Update readManifest(String url) throws Exception {
        JSONObject object = new JSONObject(OkHttp.string(url));
        Update update = Update.empty(Update.CHANNEL_STABLE);
        update.name = object.optString("name");
        update.versionName = object.optString("versionName");
        update.desc = object.optString("desc");
        update.code = object.optInt("code");
        update.apk = object.optString("apk", getFlavor() + ".apk");
        update.size = object.optLong("size");
        update.sha256 = object.optString("sha256");
        update.cnb = object.optBoolean("cnb", true);
        String apk = TextUtils.isEmpty(update.apk) ? getFlavor() + ".apk" : update.apk;
        update.githubUrl = GITHUB_RELEASE + "/" + fileName(apk);
        update.cnbUrl = CNB_RELEASE + "/" + fileName(apk);
        return update;
    }

    private String fileName(String apk) {
        if (apk.startsWith("http://") || apk.startsWith("https://")) {
            String value = apk;
            int query = value.indexOf('?');
            if (query >= 0) value = value.substring(0, query);
            int slash = value.lastIndexOf('/');
            if (slash >= 0) value = value.substring(slash + 1);
            return value;
        }
        return apk;
    }

    private void show(FragmentActivity activity, Update update) {
        dismiss();
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, TextUtils.isEmpty(update.versionName) ? update.name : update.versionName))
                .desc(update.getText() + "\n\n" + ResUtil.getString(R.string.update_current_version, BuildConfig.VERSION_NAME) + "\n" + ResUtil.getString(R.string.update_manual_msg))
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (downloading) {
            onCancel(view);
            return;
        }
        List<String> list = buildRoutes(update);
        if (list.isEmpty()) {
            copyAndOpen(Github.getApk(getFlavor()), GITHUB_RELEASE + "/" + getFlavor() + ".apk");
            dismiss();
            return;
        }
        downloading = true;
        canceled = false;
        routes = list;
        routeIndex = 0;
        Path.clear(getFile());
        progress(0, 0, update.size, 0, 0);
        startNextDownload();
    }

    private List<String> buildRoutes(Update update) {
        Set<String> result = new LinkedHashSet<>();
        try {
            String serverUrl = Github.getServerApk(fileName(update == null || TextUtils.isEmpty(update.apk) ? getFlavor() + ".apk" : update.apk));
            boolean serverFirst = "server".equals(Github.getMirror()) || "auto".equals(Github.getMirror());
            if (serverFirst) result.add(serverUrl);
            GithubProxy.Config proxy = GithubProxy.resolve(Setting.getUpdateGithubProxy(), Setting.getUpdateGithubProxyUrl(), Setting.getUpdateGithubProxyMode());
            String primary = update == null || TextUtils.isEmpty(update.githubUrl) ? Github.getApk(getFlavor()) : update.githubUrl;
            if (proxy != null && !GithubProxy.DIRECT.equals(proxy.id)) {
                try {
                    result.add(proxy.rewrite(primary));
                } catch (Exception ignored) {
                }
            }
            result.add(primary);
            if (!serverFirst) result.add(serverUrl);
            if (update != null && update.cnb && !TextUtils.isEmpty(update.cnbUrl)) result.add(update.cnbUrl);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new ArrayList<>(result);
    }

    private void startNextDownload() {
        if (routes == null || routeIndex >= routes.size()) return;
        String url = routes.get(routeIndex++);
        transfer = new HttpUpdateTransfer(url, getFile(), update == null ? 0 : update.size);
        transfer.start(this);
    }

    private boolean retryFallback() {
        if (canceled || routes == null || routeIndex >= routes.size()) return false;
        Path.clear(getFile());
        progress(0, 0, update == null ? 0 : update.size, 0, 0);
        startNextDownload();
        return true;
    }

    @Override
    public void onCancel(View view) {
        // Canceling the prompt or the download must not silently disable the auto-update check:
        // putUpdate(false) here used to turn it off permanently with no way back from the UI.
        if (downloading) {
            canceled = true;
            downloading = false;
            if (transfer != null) transfer.cancel();
            transfer = null;
            routes = null;
            Notify.show(R.string.update_canceled);
        }
        dismiss();
    }

    @Override
    public void progress(int progress, long bytes, long total, long speed, long elapsed) {
        if (canceled || !downloading || dialog == null) return;
        if (total <= 0 && update != null) total = update.size;
        if (progress < 0 && total > 0 && bytes > 0) progress = (int) (bytes * 100.0 / total);
        dialog.setProgress(progress, speed);
    }

    @Override
    public void error(String msg) {
        if (canceled) return;
        transfer = null;
        if (retryFallback()) return;
        downloading = false;
        routes = null;
        Notify.show(R.string.update_failed);
        copyAndOpen(Github.getApk(getFlavor()), GITHUB_RELEASE + "/" + getFlavor() + ".apk");
        dismiss();
    }

    @Override
    public void success(File file) {
        if (canceled) return;
        transfer = null;
        Update target = update;
        Task.execute(() -> {
            String error = validate(file, target);
            App.post(() -> {
                if (canceled) return;
                if (!TextUtils.isEmpty(error)) {
                    Path.clear(file);
                    if (retryFallback()) return;
                    downloading = false;
                    routes = null;
                    Notify.show(error);
                    dismiss();
                    return;
                }
                downloading = false;
                routes = null;
                install(file);
            });
        });
    }

    private void install(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(FileUtil.getShareUri(file), "application/vnd.android.package-archive");
            if (!App.get().getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                App.get().startActivity(intent);
                dismiss();
                return;
            }
            SpiderDebug.log("Updater", "no installer activity resolved for apk view intent");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        Notify.show(R.string.update_export_failed);
        copyAndOpen(Github.getApk(getFlavor()), GITHUB_RELEASE + "/" + getFlavor() + ".apk");
        dismiss();
    }

    private String validate(File file, Update update) {
        if (file == null || !file.exists() || file.length() <= 0) return ResUtil.getString(R.string.update_download_invalid);
        if (update != null && update.size > 0 && file.length() != update.size) return ResUtil.getString(R.string.update_download_incomplete);
        if (update != null && !TextUtils.isEmpty(update.sha256) && !update.sha256.equalsIgnoreCase(sha256(file))) return ResUtil.getString(R.string.update_download_checksum);
        if (!validatePackage(file, update)) return ResUtil.getString(R.string.update_download_identity);
        return "";
    }

    private boolean validatePackage(File file, Update update) {
        try {
            PackageManager manager = App.get().getPackageManager();
            PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (archive == null || !BuildConfig.APPLICATION_ID.equals(archive.packageName)) return false;
            // getPackageArchiveInfo frequently returns signingInfo == null for
            // NOT-installed packages on API 28+; fall back to GET_SIGNATURES,
            // which reliably parses signatures from an archive file.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && archive.signingInfo == null) {
                archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                if (archive == null) return false;
            }
            PackageInfo installed = manager.getPackageInfo(BuildConfig.APPLICATION_ID, Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
            long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? archive.getLongVersionCode() : archive.versionCode;
            if (update != null && update.code > 0 && archiveCode != update.code) return false;
            if (update != null && !TextUtils.isEmpty(update.versionName) && !update.versionName.equals(archive.versionName)) return false;
            return signaturesMatch(installed, archive);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean signaturesMatch(PackageInfo installed, PackageInfo archive) {
        Set<String> current = currentFingerprints(installed);
        if (current.isEmpty()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && archive.signingInfo != null) {
            if (current.equals(fingerprints(archive.signingInfo.getApkContentsSigners()))) return true;
            if (!archive.signingInfo.hasMultipleSigners()) return fingerprints(archive.signingInfo.getSigningCertificateHistory()).containsAll(current);
            return false;
        }
        return !current.isEmpty() && current.equals(fingerprints(archive.signatures));
    }

    private Set<String> currentFingerprints(PackageInfo installed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && installed.signingInfo != null) {
            Signature[] signers = installed.signingInfo.hasMultipleSigners() ? installed.signingInfo.getApkContentsSigners() : installed.signingInfo.getSigningCertificateHistory();
            return fingerprints(signers);
        }
        return fingerprints(installed.signatures);
    }

    private Set<String> fingerprints(Signature[] signatures) {
        Set<String> values = new HashSet<>();
        if (signatures == null) return values;
        for (Signature signature : signatures) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                values.add(Arrays.toString(digest.digest(signature.toByteArray())));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private String sha256(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) builder.append(String.format(Locale.ROOT, "%02x", value));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void copyAndOpen(String primary, String fallback) {
        // CNB raw refuses files >100 MiB (HTTP 413). If the primary mirror is
        // CNB and the APK is unavailable there, fall back to GitHub releases.
        String url = isCnb(primary) && !reachable(primary) ? fallback : primary;
        try {
            ClipboardManager cm = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("update", url));
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.get().startActivity(intent);
        } catch (Exception e) {
            Notify.show(ResUtil.getString(R.string.update_failed));
        }
    }

    private static boolean isCnb(String url) {
        return url != null && url.contains("cnb.cool");
    }

    private static boolean reachable(String url) {
        try {
            okhttp3.Request request = new okhttp3.Request.Builder().url(url).head().build();
            try (okhttp3.Response response = OkHttp.client(5000).newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        } finally {
            dialog = null;
        }
    }
}
