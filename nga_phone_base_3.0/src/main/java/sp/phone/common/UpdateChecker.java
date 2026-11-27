package sp.phone.common;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gov.anzong.androidnga.BuildConfig;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.util.ToastUtils;

/**
 * 检查本仓库 GitHub Releases 上的新版本。
 *
 * 清单 update.json 由发版流水线生成并上传到 Release，客户端走
 * /releases/latest/download/update.json 读取（普通下载通道），
 * 不走 api.github.com，因此没有每小时 60 次的限流；latest 也天然只指向正式发布版。
 */
public final class UpdateChecker {

    /**
     * 国内直连 GitHub 资产实测只有十几 KB/s，所以清单和安装包都准备了镜像前缀。
     * 这些只是缺省值，真正生效的是 update.json 里的 mirrors 字段，改服务端即可换线路。
     */
    public static final List<String> DEFAULT_MIRRORS = Arrays.asList(
            "https://gh-proxy.com/", "https://ghfast.top/", "https://gh-proxy.org/");

    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    private static final int MANIFEST_TIMEOUT_MS = 6000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean sChecking;

    private UpdateChecker() {
    }

    public static String manifestUrl() {
        return "https://github.com/" + BuildConfig.UPDATE_REPO + "/releases/latest/download/update.json";
    }

    public static String releasesUrl() {
        return "https://github.com/" + BuildConfig.UPDATE_REPO + "/releases";
    }

    /**
     * 启动后调用：24 小时内只自动检查一次，不打扰用户。
     */
    public static void checkIfDue(Activity activity) {
        if (activity == null || !BuildConfig.UPDATE_ENABLED) {
            return;
        }
        long last = PreferenceUtils.getData(PreferenceKey.KEY_UPDATE_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            return;
        }
        check(activity, false);
    }

    /**
     * @param manual 手动检查时，已是最新或失败都给提示
     */
    public static void check(final Activity activity, final boolean manual) {
        if (activity == null) {
            return;
        }
        if (!BuildConfig.UPDATE_ENABLED) {
            if (manual) {
                ToastUtils.showToast("当前构建不检查更新");
            }
            return;
        }
        if (sChecking) {
            if (manual) {
                ToastUtils.showToast("正在检查更新…");
            }
            return;
        }
        sChecking = true;
        if (manual) {
            ToastUtils.showToast("正在检查更新…");
        }
        final long startedAt = System.currentTimeMillis();
        EXECUTOR.execute(() -> {
            UpdateInfo info = null;
            try {
                info = fetchLatest();
            } catch (Throwable ignored) {
                // 网络问题按“检查失败”处理，不往上层抛
            }
            final UpdateInfo result = info;
            MAIN.post(() -> {
                sChecking = false;
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (result == null) {
                    if (manual) {
                        ToastUtils.showToast("检查更新失败，稍后重试或到发布页手动下载");
                        openUrl(activity, releasesUrl());
                    }
                    return;
                }
                // 只有真的拿到了清单才计一次检查，避免断网时反复重试
                PreferenceUtils.putData(PreferenceKey.KEY_UPDATE_LAST_CHECK, startedAt);
                if (!result.isUsable() || result.versionCode <= BuildConfig.VERSION_CODE) {
                    if (manual) {
                        ToastUtils.showToast("已是最新版本 " + BuildConfig.VERSION_NAME);
                    }
                    return;
                }
                if (result.prerelease) {
                    return;
                }
                long skipped = PreferenceUtils.getData(PreferenceKey.KEY_UPDATE_SKIP_VERSION, 0L);
                // 自动检查时尊重“稍后”，手动检查仍然提示
                if (!manual && skipped == result.versionCode) {
                    return;
                }
                showDialog(activity, result);
            });
        });
    }

    private static UpdateInfo fetchLatest() throws IOException {
        IOException lastError = null;
        for (String url : candidateUrls(manifestUrl())) {
            try {
                return UpdateInfo.parse(httpGet(url, MANIFEST_TIMEOUT_MS));
            } catch (IOException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = new IOException(e.getMessage() == null ? "parse error" : e.getMessage());
            }
        }
        throw lastError == null ? new IOException("没有可用的清单地址") : lastError;
    }

    private static List<String> candidateUrls(String directUrl) {
        List<String> urls = new ArrayList<>();
        urls.add(directUrl);
        for (String mirror : DEFAULT_MIRRORS) {
            urls.add(mirror + directUrl);
        }
        return urls;
    }

    static String httpGet(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "NGA-Android-UpdateChecker");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                // 清单只有 1~2 KB，设上限防止被异常响应撑爆内存
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > 512 * 1024) {
                        break;
                    }
                }
                return out.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void showDialog(final Activity activity, final UpdateInfo info) {
        String notes = info.chineseNotes(1500);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + info.versionName)
                .setMessage("当前版本 " + BuildConfig.VERSION_NAME + "\n\n" + notes)
                .setPositiveButton("下载并安装", (dialog, which) -> {
                    if (info.hasChecksum()) {
                        UpdateDownloader.start(activity, info);
                    } else {
                        ToastUtils.showToast("该版本未提供校验信息，转到发布页下载");
                        openUrl(activity, info.htmlUrl);
                    }
                })
                .setNeutralButton("查看发布页", (dialog, which) -> openUrl(activity, info.htmlUrl))
                .setNegativeButton("稍后", (dialog, which) ->
                        PreferenceUtils.putData(PreferenceKey.KEY_UPDATE_SKIP_VERSION, info.versionCode));
        builder.show();
    }

    static void openUrl(Activity activity, String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            ToastUtils.showToast("没有可以打开链接的应用");
        }
    }
}
