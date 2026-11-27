package sp.phone.common;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** KEY_UPDATE_ENDPOINT 里的特殊值：GitHub 直连（没有镜像前缀） */
    private static final String ENDPOINT_DIRECT = "direct";

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

    /**
     * 取清单：记住过最快通道就直接用它；没有记录、或记录的通道这次不通，
     * 才并发竞速一轮，把最先成功返回的通道记为默认地址，下次检查只发一个请求。
     * <p>
     * 之前是串行“直链 → 镜像”，国内裸网直连 GitHub 是静默丢包，
     * 每次都要等满 6 秒超时才能轮到镜像；并发之后总耗时接近最快的那条线路。
     */
    private static UpdateInfo fetchLatest() throws IOException {
        String direct = manifestUrl();
        String endpoint = PreferenceUtils.getData(PreferenceKey.KEY_UPDATE_ENDPOINT, (String) null);
        if (!TextUtils.isEmpty(endpoint)) {
            try {
                return UpdateInfo.parse(httpGet(endpointUrl(endpoint, direct), MANIFEST_TIMEOUT_MS));
            } catch (Exception e) {
                // 换了网络或镜像挂了：丢掉记录，重新竞速
                PreferenceUtils.edit().remove(PreferenceKey.KEY_UPDATE_ENDPOINT).apply();
            }
        }
        return raceEndpoints(direct);
    }

    /** 并发尝试所有通道，第一个成功返回的即为最快，并记为默认地址 */
    private static UpdateInfo raceEndpoints(final String direct) throws IOException {
        List<String> endpoints = new ArrayList<>();
        endpoints.add(ENDPOINT_DIRECT);
        endpoints.addAll(DEFAULT_MIRRORS);

        ExecutorService pool = Executors.newFixedThreadPool(endpoints.size());
        CompletionService<RaceResult> service = new ExecutorCompletionService<>(pool);
        final AtomicBoolean settled = new AtomicBoolean(false);
        try {
            for (final String endpoint : endpoints) {
                service.submit(() -> {
                    if (settled.get()) {
                        return null;
                    }
                    long t0 = System.nanoTime();
                    try {
                        return new RaceResult(endpoint,
                                UpdateInfo.parse(httpGet(endpointUrl(endpoint, direct), MANIFEST_TIMEOUT_MS)),
                                ms(t0));
                    } catch (Throwable t) {
                        return new RaceResult(endpoint, null, ms(t0));
                    }
                });
            }
            IOException lastError = null;
            for (int i = 0; i < endpoints.size(); i++) {
                RaceResult result = service.take().get();
                if (result == null) {
                    continue;
                }
                if (result.info != null) {
                    if (settled.compareAndSet(false, true)) {
                        PreferenceUtils.putData(PreferenceKey.KEY_UPDATE_ENDPOINT, result.endpoint);
                    }
                    return result.info;
                }
                lastError = new IOException(result.endpoint + " 无响应");
            }
            throw lastError == null ? new IOException("没有可用的清单地址") : lastError;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("检查被取消");
        } catch (ExecutionException e) {
            throw new IOException(e.getCause() == null ? "检查失败" : String.valueOf(e.getCause()));
        } finally {
            settled.set(true);
            pool.shutdownNow();
        }
    }

    private static String endpointUrl(String endpoint, String directUrl) {
        return ENDPOINT_DIRECT.equals(endpoint) ? directUrl : endpoint + directUrl;
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1000000L;
    }

    /** 给界面显示当前记住的通道 */
    public static String endpointLabel() {
        String endpoint = PreferenceUtils.getData(PreferenceKey.KEY_UPDATE_ENDPOINT, (String) null);
        if (TextUtils.isEmpty(endpoint)) {
            return "未设置，检测时自动选最快线路";
        }
        if (ENDPOINT_DIRECT.equals(endpoint)) {
            return "GitHub 直连";
        }
        try {
            return new URL(endpoint).getHost();
        } catch (Exception e) {
            return endpoint;
        }
    }

    /** 清理更新相关缓存：默认通道、检查时间、跳过的版本，以及已下载的安装包 */
    public static void clearCache(Context context) {
        PreferenceUtils.edit()
                .remove(PreferenceKey.KEY_UPDATE_ENDPOINT)
                .remove(PreferenceKey.KEY_UPDATE_LAST_CHECK)
                .remove(PreferenceKey.KEY_UPDATE_SKIP_VERSION)
                .apply();
        deleteRecursively(new File(context.getCacheDir(), "update"));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class RaceResult {
        final String endpoint;
        final UpdateInfo info;
        final long elapsedMs;

        RaceResult(String endpoint, UpdateInfo info, long elapsedMs) {
            this.endpoint = endpoint;
            this.info = info;
            this.elapsedMs = elapsedMs;
        }
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
