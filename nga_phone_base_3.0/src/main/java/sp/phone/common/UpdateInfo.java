package sp.phone.common;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用内更新用的版本清单（Release 里上传的 update.json）数据模型。
 *
 * 新格式：
 * { tag_name, version_name, version_code, body, html_url, prerelease,
 *   apk: { name, size, sha256, url }, mirrors: [...] }
 *
 * 兼容只有 assets 数组的旧格式（tiger-git2022 的写法）。
 */
public class UpdateInfo {

    public String tagName = "";

    public String versionName = "";

    public long versionCode;

    public String body = "";

    public String htmlUrl = "";

    public String apkName = "";

    public long apkSize;

    public String sha256 = "";

    public String apkUrl = "";

    public boolean prerelease;

    public List<String> mirrors = new ArrayList<>();

    public static UpdateInfo parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        UpdateInfo info = new UpdateInfo();
        info.tagName = root.optString("tag_name", "");
        String rawName = root.optString("version_name", info.tagName);
        info.versionName = rawName.startsWith("v") || rawName.startsWith("V") ? rawName.substring(1) : rawName;
        info.versionCode = root.optLong("version_code", 0);
        info.body = root.optString("body", "");
        info.htmlUrl = root.optString("html_url", "");
        info.prerelease = root.optBoolean("prerelease", false);

        JSONObject apk = root.optJSONObject("apk");
        if (apk != null) {
            info.apkName = apk.optString("name", "");
            info.apkSize = apk.optLong("size", 0);
            info.sha256 = apk.optString("sha256", "").toLowerCase();
            info.apkUrl = apk.optString("url", "");
        } else {
            JSONArray assets = root.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) {
                        continue;
                    }
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        info.apkName = name;
                        info.apkUrl = asset.optString("browser_download_url", "");
                        break;
                    }
                }
            }
        }

        JSONArray mirrors = root.optJSONArray("mirrors");
        if (mirrors != null) {
            for (int i = 0; i < mirrors.length(); i++) {
                String mirror = mirrors.optString(i, "").trim();
                if (!mirror.isEmpty()) {
                    info.mirrors.add(mirror.endsWith("/") ? mirror : mirror + "/");
                }
            }
        }
        if (info.mirrors.isEmpty()) {
            info.mirrors.addAll(UpdateChecker.DEFAULT_MIRRORS);
        }
        return info;
    }

    /**
     * 有版本号且有下载地址才值得提示；sha256 缺失时仍可提示，只是不能应用内安装。
     */
    public boolean isUsable() {
        return versionCode > 0 && !apkUrl.isEmpty();
    }

    public boolean hasChecksum() {
        return apkSize > 0 && !sha256.isEmpty();
    }

    /**
     * 发布说明是中文在前、英文在 "---" 之后，弹窗只显示中文部分。
     */
    public String chineseNotes(int limit) {
        String notes = body == null ? "" : body;
        int separator = notes.indexOf("\n---");
        if (separator >= 0) {
            notes = notes.substring(0, separator);
        }
        notes = notes.trim();
        if (notes.length() > limit) {
            notes = notes.substring(0, limit) + "…";
        }
        return notes;
    }
}
