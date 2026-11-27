package sp.phone.common;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import gov.anzong.androidnga.BuildConfig;
import gov.anzong.androidnga.util.ToastUtils;

/**
 * 在应用内下载新版本 APK，校验后调起系统安装器覆盖安装。
 *
 * 国内直连 GitHub 资产实测只有十几 KB/s，所以下载前先对 update.json 下发的镜像
 * （gh-proxy.com / ghfast.top 等）做 Range 探测选最快的一条，慢速会自动切换；
 * 因为走了第三方中转，安装前必须同时通过 SHA-256 和签名证书两道校验。
 */
public final class UpdateDownloader {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 探测单条通道：最多读 256 KB 或 1500 ms，超过 1 MB/s 直接采用，不再试后面的 */
    private static final long PROBE_BYTES = 256 * 1024;

    private static final int PROBE_TIMEOUT_MS = 1500;

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private static final int READ_TIMEOUT_MS = 30_000;

    /** 连续 8 秒低于 25 KB/s 就换通道 */
    private static final long SLOW_BPS = 25 * 1024;

    private static final long SLOW_WINDOW_MS = 8_000;

    private static volatile boolean sRunning;

    private static File sPendingApk;

    private UpdateDownloader() {
    }

    private interface ProgressCallback {
        void onProgress(long done, long total, long bytesPerSecond);
    }

    private static final class Task {
        volatile boolean cancelled;
    }

    public static void start(final Activity activity, final UpdateInfo info) {
        if (activity == null || info == null) {
            return;
        }
        if (sRunning) {
            ToastUtils.showToast("正在下载更新…");
            return;
        }
        if (!info.hasChecksum()) {
            ToastUtils.showToast("该版本未提供校验信息，转到发布页下载");
            UpdateChecker.openUrl(activity, info.htmlUrl);
            return;
        }

        File dir = new File(activity.getCacheDir(), "update");
        File target = new File(dir, safeName(info.apkName));
        if (target.isFile() && target.length() == info.apkSize) {
            try {
                verifyOrThrow(target, info, activity);
                install(activity, target);
                return;
            } catch (IOException e) {
                target.delete();
            }
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            ToastUtils.showToast("无法创建下载目录");
            return;
        }

        // 旧版本的安装包和残包都清掉，只保留本次下载
        File[] olds = dir.listFiles();
        if (olds != null) {
            for (File old : olds) {
                if (!old.equals(target)) {
                    old.delete();
                }
            }
        }
        final File part = new File(dir, target.getName() + ".part");

        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setMax(1000);
        final TextView text = new TextView(activity);
        int padding = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(bar);
        layout.addView(text);
        text.setText("正在连接…");

        final Task task = new Task();
        sRunning = true;
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("下载 " + info.versionName)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("取消", (d, which) -> task.cancelled = true)
                .show();

        new Thread(() -> {
            Throwable failure = null;
            boolean verified = false;
            try {
                downloadWithFallback(info, part, task, (done, total, bps) ->
                        MAIN.post(() -> updateProgress(bar, text, done, total, bps)));
                if (task.cancelled) {
                    throw new IOException("已取消");
                }
                if (part.length() != info.apkSize) {
                    throw new IOException("下载不完整");
                }
                if (!part.renameTo(target)) {
                    throw new IOException("无法保存安装包");
                }
                MAIN.post(() -> text.setText("正在校验安装包…"));
                verifyOrThrow(target, info, activity);
                verified = true;
            } catch (Throwable t) {
                failure = t;
                part.delete();
                target.delete();
            }
            final Throwable error = failure;
            final boolean ok = verified;
            MAIN.post(() -> {
                sRunning = false;
                try {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } catch (Throwable ignored) {
                }
                if (ok) {
                    install(activity, target);
                } else if (!task.cancelled && error != null) {
                    ToastUtils.showToast("下载失败：" + error.getMessage());
                }
            });
        }, "nga-update-download").start();
    }

    private static void updateProgress(ProgressBar bar, TextView text,
                                       long done, long total, long bps) {
        if (total > 0) {
            bar.setIndeterminate(false);
            bar.setProgress((int) (done * 1000 / total));
        }
        String speed = bps > 0 ? String.format(Locale.US, "%.1f MB/s", bps / 1048576f) : "…";
        String remain = "";
        if (bps > 0 && total > done) {
            remain = String.format(Locale.US, " · 剩余 %d 秒", (total - done) / bps);
        }
        text.setText(String.format(Locale.US, "%.1f / %.1f MB · %s%s",
                done / 1048576f, total / 1048576f, speed, remain));
    }

    private static void downloadWithFallback(UpdateInfo info, File part,
                                             Task task, ProgressCallback callback) throws IOException {
        List<String> candidates = new ArrayList<>();
        for (String mirror : info.mirrors) {
            candidates.add(mirror + info.apkUrl);
        }
        // 直链永远放最后兜底
        candidates.add(info.apkUrl);

        String fastest = pickFastest(candidates);
        List<String> ordered = new ArrayList<>();
        if (fastest != null) {
            ordered.add(fastest);
        }
        for (String candidate : candidates) {
            if (!candidate.equals(fastest)) {
                ordered.add(candidate);
            }
        }

        IOException lastError = null;
        for (String candidate : ordered) {
            if (task.cancelled) {
                throw new IOException("已取消");
            }
            try {
                downloadFrom(candidate, part, info.apkSize, task, callback);
                return;
            } catch (IOException e) {
                lastError = e;
                if (task.cancelled) {
                    throw e;
                }
            }
        }
        throw lastError == null ? new IOException("没有可用的下载地址") : lastError;
    }

    /**
     * 逐条发 Range 请求测速，取最快的一条；都失败时返回 null，按原顺序重试。
     */
    private static String pickFastest(List<String> candidates) {
        String best = null;
        long bestSpeed = -1;
        for (String candidate : candidates) {
            long speed = probe(candidate);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = candidate;
            }
            if (bestSpeed >= 1024 * 1024) {
                break;
            }
        }
        return bestSpeed > 0 ? best : null;
    }

    private static long probe(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(PROBE_TIMEOUT_MS);
            conn.setReadTimeout(PROBE_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            conn.setRequestProperty("User-Agent", "NGA-Android-Updater");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != 206) {
                return -1;
            }
            long start = System.currentTimeMillis();
            long read = 0;
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int n;
                while (read < PROBE_BYTES && (n = in.read(buffer)) > 0) {
                    read += n;
                    if (System.currentTimeMillis() - start >= PROBE_TIMEOUT_MS) {
                        break;
                    }
                }
            }
            long elapsed = Math.max(1, System.currentTimeMillis() - start);
            return read * 1000 / elapsed;
        } catch (Throwable t) {
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void downloadFrom(String url, File part, long totalSize,
                                     Task task, ProgressCallback callback) throws IOException {
        long existing = part.length();
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "NGA-Android-Updater");
            if (existing > 0) {
                conn.setRequestProperty("Range", "bytes=" + existing + "-");
            }
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                // 通道不支持断点续传，只能从头下
                existing = 0;
                part.delete();
            } else if (code != 206) {
                throw new IOException("HTTP " + code);
            }

            long total = totalSize > 0 ? totalSize : existing + conn.getContentLengthLong();
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(part, existing > 0);
            byte[] buffer = new byte[64 * 1024];
            long done = existing;
            long windowStart = System.currentTimeMillis();
            long windowBytes = 0;
            long slowStart = 0;
            try {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (task.cancelled) {
                        throw new IOException("已取消");
                    }
                    out.write(buffer, 0, read);
                    done += read;
                    windowBytes += read;
                    long now = System.currentTimeMillis();
                    if (now - windowStart >= 1000) {
                        long speed = windowBytes * 1000 / (now - windowStart);
                        callback.onProgress(done, total, speed);
                        windowStart = now;
                        windowBytes = 0;
                        // 剩余还多、速度却持续很慢，就换一条通道继续续传
                        if (speed < SLOW_BPS && total - done > 2 * 1024 * 1024) {
                            if (slowStart == 0) {
                                slowStart = now;
                            } else if (now - slowStart >= SLOW_WINDOW_MS) {
                                throw new IOException("下载通道过慢，切换中");
                            }
                        } else {
                            slowStart = 0;
                        }
                    }
                }
            } finally {
                try {
                    out.flush();
                } catch (IOException ignored) {
                }
                out.close();
                in.close();
            }
            if (total > 0 && part.length() != total) {
                throw new IOException("下载不完整");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void verifyOrThrow(File apk, UpdateInfo info, Activity activity) throws IOException {
        if (!sha256(apk).equalsIgnoreCase(info.sha256)) {
            throw new IOException("安装包校验失败（SHA-256 不匹配）");
        }
        if (!signatureMatches(activity, apk)) {
            throw new IOException("安装包签名与本机不一致");
        }
        PackageManager pm = activity.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null) {
            throw new IOException("无法读取安装包信息");
        }
        if (!archive.packageName.equals(activity.getPackageName())) {
            throw new IOException("安装包包名与当前应用不一致");
        }
        long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode() : archive.versionCode;
        if (archiveCode <= BuildConfig.VERSION_CODE) {
            throw new IOException("安装包版本不高于当前版本");
        }
    }

    private static boolean signatureMatches(Activity activity, File apk) {
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo installed = pm.getPackageInfo(activity.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (installed == null || archive == null
                    || installed.signingInfo == null || archive.signingInfo == null) {
                return false;
            }
            Signature[] installedSigners = installed.signingInfo.getApkContentsSigners();
            Signature[] archiveSigners = archive.signingInfo.getApkContentsSigners();
            if (installedSigners == null || archiveSigners == null
                    || installedSigners.length == 0 || archiveSigners.length == 0) {
                return false;
            }
            return Arrays.equals(installedSigners[0].toByteArray(), archiveSigners[0].toByteArray());
        } catch (Throwable t) {
            return false;
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("计算校验值失败");
        }
    }

    private static String safeName(String apkName) {
        String name = apkName == null || apkName.isEmpty() ? "nga_phone_update.apk" : apkName;
        return name.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    static void install(Activity activity, File apk) {
        if (activity == null || apk == null || !apk.isFile()) {
            return;
        }
        sPendingApk = apk;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("需要允许安装应用")
                    .setMessage("新版本已下载。请在接下来的设置里允许本应用「安装未知应用」，返回后会自动继续安装。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Throwable t) {
                            ToastUtils.showToast("请手动到系统设置里允许安装未知应用");
                        }
                    })
                    .setNegativeButton("稍后", null)
                    .show();
            return;
        }
        sPendingApk = null;
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName(), apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            ToastUtils.showToast("无法拉起安装程序，请到发布页手动下载");
        }
    }

    /**
     * 从「安装未知应用」设置页返回后自动继续安装，省掉用户再点一次检查更新。
     */
    public static void tryResumePendingInstall(Activity activity) {
        if (activity == null || sPendingApk == null || !sPendingApk.isFile()) {
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            return;
        }
        File apk = sPendingApk;
        sPendingApk = null;
        install(activity, apk);
    }
}
