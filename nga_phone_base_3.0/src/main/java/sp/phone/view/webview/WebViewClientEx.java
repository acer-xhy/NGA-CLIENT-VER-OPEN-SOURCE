package sp.phone.view.webview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.justwen.androidnga.base.router.Router;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.ArticleListActivity;
import gov.anzong.androidnga.activity.TopicListActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.gallery.ImageZoomActivity;
import sp.phone.util.StringUtils;

public class WebViewClientEx extends WebViewClient {

    private List<String> mImgUrlList;


    private static final String NGA_USER_PROFILE_END = "&";

    /**
     * B 站 App 包名，命中视频链接时优先拉起它，未安装则回退浏览器。
     */
    private static final String BILIBILI_PACKAGE = "tv.danmaku.bili";

    /**
     * 从 B 站视频页链接中提取视频 id（BV 号或 av 号），用于拼 bilibili://video/{id}。
     */
    private static final Pattern BILIBILI_VIDEO_ID =
            Pattern.compile("(?:https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/(BV[0-9A-Za-z]{10}|av\\d+)",
                    Pattern.CASE_INSENSITIVE);

    private static final String[] SUFFIX_IMAGE = {
            ".gif", ".jpg", ".png", ".jpeg", ".bmp", ".webp"
    };

    private static final String NGA_READ = "/read.php?";

    private static final String NGA_THREAD = "/thread.php?";

    private static final String NGA_USER_PROFILE = "/nuke.php?func=ucp&username=";

    private static String[] sReadPrefix;

    private static String[] sThreadPrefix;

    private static String[] NGA_USER_PROFILE_START;

    static {
        String[] domains = ContextUtils.getContext().getResources().getStringArray(gov.anzong.androidnga.common.R.array.nga_domain_no_http);
        sThreadPrefix = new String[domains.length];
        sReadPrefix = new String[domains.length];
        NGA_USER_PROFILE_START = new String[domains.length];
        for (int i = 0; i < domains.length; i++) {
            sThreadPrefix[i] = domains[i] + NGA_THREAD;
            sReadPrefix[i] = domains[i] + NGA_READ;
            NGA_USER_PROFILE_START[i] = domains[i] + NGA_USER_PROFILE;
        }
    }

    public WebViewClientEx(Context context) {
        super();
    }

    public WebViewClientEx() {
        super();
    }

    public void setImgUrls(List<String> list) {
        mImgUrlList = list;
    }

    private boolean overrideProfileUrlLoading(Context context, String url) {
        for (String profileStart : NGA_USER_PROFILE_START)
            if (url.contains(profileStart)) {
                String data = StringUtils.getStringBetween(url, 0,
                        profileStart, NGA_USER_PROFILE_END).result;
                try {
                    data = URLDecoder.decode(data, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                if (!StringUtils.isEmpty(data)) {
                    Router.build(ARouterConstants.ACTIVITY_PROFILE)
                            .withString("mode", "username")
                            .withString("username", data)
                            .navigation(context);
                }
                return true;
            }
        return false;
    }

    /**
     * B 站视频链接优先用 B 站 App 打开；未安装或无法解析时返回 false，交给后续兜底（浏览器）。
     */
    private boolean overrideBilibiliUrlLoading(Context context, String url) {
        if (!url.contains("bilibili.com/video/") && !url.contains("b23.tv")) {
            return false;
        }
        String target;
        Matcher matcher = BILIBILI_VIDEO_ID.matcher(url);
        if (matcher.find()) {
            // 能拿到 BV/av 号：连同 App 一起打开对应视频页
            target = "bilibili://video/" + matcher.group(1);
        } else if (url.contains("b23.tv")) {
            // 短链没有视频 id，直接交给 B 站 App 解析（App 声明了 b23.tv 的 App Links）
            target = url.startsWith("http") ? url : "https://" + url;
        } else {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
        intent.setPackage(BILIBILI_PACKAGE);
        try {
            // 不要用 resolveActivity 预检：targetSdk 30+ 的包可见性限制会让它返回 null，
            // 从而误判为“B 站没装”并回退浏览器。startActivity 不受该限制，失败再兜底。
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Context context = view.getContext();

        if (!url.startsWith("http") && !url.startsWith("market")) {
            url = "http://" + url;
        }

        for (String read : sReadPrefix) {
            if (url.startsWith(read, "http://".length())
                    || url.startsWith(read, "https://".length())) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(url));
                intent.putExtra("fromreplyactivity", 1);
                intent.setClass(context, ArticleListActivity.class);
                context.startActivity(intent);
                return true;
            }
        }

        for (String thread : sThreadPrefix) {
            if (url.startsWith(thread, "http://".length())
                    || url.startsWith(thread, "https://".length())) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(url));
                intent.setClass(context, TopicListActivity.class);
                context.startActivity(intent);
                return true;
            }
        }

        for (String suffix : SUFFIX_IMAGE) {
            if (url.endsWith(suffix)) {
                Intent intent = new Intent();
                if (mImgUrlList == null) {
                    mImgUrlList = new ArrayList<>();
                    mImgUrlList.add(url);
                } else if (mImgUrlList.isEmpty()) {
                    mImgUrlList.add(url);
                }
                String[] urls = new String[mImgUrlList.size()];
                mImgUrlList.toArray(urls);
                intent.putExtra(ImageZoomActivity.KEY_GALLERY_URLS, urls);
                intent.putExtra(ImageZoomActivity.KEY_GALLERY_CUR_URL, url);
                intent.setClass(context, ImageZoomActivity.class);
                context.startActivity(intent);
                return true;
            }
        }

        if (overrideBilibiliUrlLoading(context, url)) {
            return true;
        }

        if (!overrideProfileUrlLoading(context, url)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        view.getSettings().setBlockNetworkImage(false);
        super.onPageFinished(view, url);
    }
}
