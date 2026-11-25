package sp.phone.mvp.model;

import android.text.TextUtils;

import com.client.androidnga.core.parse.ForumParsekUtils;
import com.client.androidnga.core.parse.ThreadInfoAppParse;
import com.client.androidnga.core.parse.ThreadInfoHtmlParse;
import com.client.androidnga.core.parse.ThreadInfoParse;
import com.trello.rxlifecycle2.android.FragmentEvent;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import gov.anzong.androidnga.base.logger.Logger;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.http.OnHttpCallBack;
import gov.anzong.androidnga.service.HtmlConfigService;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import com.client.androidnga.core.data.model.ThreadInfo;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import sp.phone.mvp.contract.ArticleListContract;
import sp.phone.mvp.model.convert.ErrorConvertFactory;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.BaseSubscriber;
import gov.anzong.androidnga.common.util.NLog;

/**
 * 加载帖子内容
 * Created by Justwen on 2017/7/10.
 */

public class ArticleListModel extends BaseModel implements ArticleListContract.Model {

    private static final String TAG = ArticleListModel.class.getSimpleName();

    /**
     * 首次请求：__output=8 的 JSON 直出（原生数据，最快）。
     */
    private static final int ATTEMPT_JSON = 0;

    /**
     * 补救请求：JSON 直出被服务端截断时，改取网页（HTML），把网页数据还原成原生数据后再渲染。
     * 不做重试（截断是服务端行为，重试只会重复拿到同一段残体），也不做截断补齐（会漏数据）。
     */
    private static final int ATTEMPT_HTML = 1;

    private static final String HEADER_CONNECTION = "Connection";

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    private static final String HEADER_PRAGMA = "Pragma";

    private RetrofitService mService;

    public ArticleListModel() {
        mService = (RetrofitService) RetrofitHelper.getInstance().getService(RetrofitService.class);
    }

    public String getUrl(ArticleListParam param) {
        int page = param.page;
        int tid = param.tid;
        int pid = param.pid;
        int authorId = param.authorId;
        String url = getAvailableDomain() + "/read.php?" + "&page=" + page + "&__output=8&noprefix&v2";
        if (tid != 0) {
            url = url + "&tid=" + tid;
        }
        if (pid != 0) {
            url = url + "&pid=" + pid;
        }

        if (authorId != 0) {
            url = url + "&authorid=" + authorId;
        }

        return url;

    }

    @Override
    public void loadPage(ArticleListParam param, final OnHttpCallBack<ThreadInfo> callBack) {
        loadPage(param, null, callBack);
    }

    public void loadPageWithAppApi(ArticleListParam param, Map<String, String> header, OnHttpCallBack<ThreadInfo> callBack) {
        String appApiUrl = getAvailableDomain() + "/app_api.php?__lib=post&__act=list";
        Map<String, String> appApiFields = new HashMap<>();
        appApiFields.put("page", String.valueOf(param.page));
        if (param.tid != 0) {
            appApiFields.put("tid", String.valueOf(param.tid));
        }
        if (param.pid != 0) {
            appApiFields.put("pid", String.valueOf(param.pid));
        }
        if (param.authorId != 0) {
            appApiFields.put("authorid", String.valueOf(param.authorId));
        }
        mService.post(appApiUrl, header, appApiFields).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .compose(getLifecycleProvider().bindUntilEvent(FragmentEvent.DETACH))
                .map(s -> {
                    ThreadInfo data = ThreadInfoAppParse.parse(s, new HtmlConfigService());;
                    if (data == null) {
                        throw new ServerException("NGA后台抽风了，请尝试右上角菜单中的使用内置浏览器打开");
                    } else {
                        return data;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<>() {

                    @Override
                    public void onNext(@NonNull ThreadInfo threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        throwable.printStackTrace();
                        callBack.onError(throwable.getMessage(), throwable);
                    }
                });
    }

    @Override
    public void loadPage(ArticleListParam param, Map<String, String> header, OnHttpCallBack<ThreadInfo> callBack) {
        loadPageInternal(param, header, callBack, ATTEMPT_JSON);
    }

    /**
     * 网页解析的地址：与 JSON 直出同一个 read.php，只是不再要 __output=8（那是 JSON 直出的开关）。
     */
    public String getHtmlUrl(ArticleListParam param) {
        StringBuilder url = new StringBuilder(getAvailableDomain())
                .append("/read.php?&page=").append(param.page);
        if (param.tid != 0) {
            url.append("&tid=").append(param.tid);
        }
        if (param.pid != 0) {
            url.append("&pid=").append(param.pid);
        }
        if (param.authorId != 0) {
            url.append("&authorid=").append(param.authorId);
        }
        return url.toString();
    }

    private void loadPageInternal(final ArticleListParam param, final Map<String, String> header,
                                  final OnHttpCallBack<ThreadInfo> callBack, final int attempt) {
        final boolean viaHtml = attempt == ATTEMPT_HTML;
        final String url = viaHtml ? getHtmlUrl(param) : getUrl(param);
        final Map<String, String> requestHeader = buildRequestHeader(header, attempt);
        RetrofitService service = viaHtml
                ? RetrofitHelper.getInstance().getFreshConnectionService()
                : mService;
        service.get(url, requestHeader)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .compose(getLifecycleProvider().<String>bindUntilEvent(FragmentEvent.DETACH))
                .map(new Function<String, ThreadInfo>() {
                    @Override
                    public ThreadInfo apply(@NonNull String s) throws Exception {
                        long time = System.currentTimeMillis();
                        ThreadInfo data = null;
                        try {
                            data = viaHtml
                                    ? ThreadInfoHtmlParse.parse(s, new HtmlConfigService(), param.page, param.tid)
                                    : ThreadInfoParse.parse(s, new HtmlConfigService());
                        } catch (Throwable t) {
                            writeDiag(diagName(viaHtml ? "html_fail" : "fail", param.tid, attempt), url, s, t);
                        }
                        NLog.e(TAG, (viaHtml ? "html " : "json ") + "time = " + (System.currentTimeMillis() - time));
                        if (data == null) {
                            writeDiag(diagName(viaHtml ? "html_empty" : "empty", param.tid, attempt), url, s, null);
                            // JSON 直出可能带着服务端的错误提示（如帖子被删），这种不是截断，不走网页解析
                            String errorMsg = viaHtml ? null : ForumParsekUtils.parseErrorMsg(s);
                            if (errorMsg != null) {
                                throw new Exception(errorMsg);
                            }
                            throw new ServerException("NGA后台抽风了，请尝试右上角菜单中的使用内置浏览器打开");
                        }
                        writeDiag(diagName(viaHtml ? "html_ok" : "ok", param.tid, attempt), url, s, null);
                        return data;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .compose(getLifecycleProvider().<ThreadInfo>bindUntilEvent(FragmentEvent.DETACH))
                .subscribe(new BaseSubscriber<ThreadInfo>() {

                    @Override
                    public void onNext(@NonNull ThreadInfo threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        // 服务端 __output=8 JSON 直出会间歇性截断，且同一时刻网页是完整的：
                        // 不重试（重试只会拿到同一段残体）、不补齐（会丢数据），直接改走网页解析。
                        if (!viaHtml && throwable instanceof ServerException) {
                            writeDiag("html_" + param.tid + "_trigger", url, "switch to html", throwable);
                            loadPageInternal(param, header, callBack, ATTEMPT_HTML);
                            return;
                        }
                        callBack.onError(ErrorConvertFactory.getErrorMessage(throwable), throwable);
                    }
                });
    }

    private String diagName(String kind, int tid, int attempt) {
        return kind + "_" + tid + (attempt == 0 ? "" : "_a" + attempt);
    }

    /**
     * 按尝试序号构造请求头：网页补救请求强制新建连接并禁用缓存，避免复用那条已返回残体的连接。
     */
    private Map<String, String> buildRequestHeader(Map<String, String> baseHeader, int attempt) {
        Map<String, String> header = new HashMap<>();
        if (baseHeader != null) {
            header.putAll(baseHeader);
        }
        if (attempt > 0) {
            header.put(HEADER_CONNECTION, "close");
            header.put(HEADER_CACHE_CONTROL, "no-cache, no-store");
            header.put(HEADER_PRAGMA, "no-cache");
        }
        return header;
    }

    @Override
    public void cachePage(ArticleListParam param, String rawData) {

        if (TextUtils.isEmpty(param.topicInfo)) {
            ToastUtils.error("缓存失败！");
            return;
        }
        ThreadUtils.postOnSubThread(() -> {
            try {
                String path = ContextUtils.getContext().getFilesDir().getAbsolutePath() + "/cache/" + param.tid;
                File describeFile = new File(path, param.tid + ".json");
                FileUtils.write(describeFile, param.topicInfo);
                File rawDataFile = new File(path, param.page + ".json");
                FileUtils.write(rawDataFile, rawData);
                ToastUtils.success("缓存成功！");
            } catch (IOException e) {
                ToastUtils.error("缓存失败！");
                e.printStackTrace();
            }
        });
    }

    @Override
    public void loadCachePage(ArticleListParam param, OnHttpCallBack<ThreadInfo> callBack) {
        Observable.create((ObservableOnSubscribe<ThreadInfo>) emitter -> {
            String cachePath = ContextUtils.getContext().getFilesDir().getAbsolutePath()
                    + "/cache/" + param.tid + "/" + param.page + ".json";
            File cacheFile = new File(cachePath);
            String rawData = FileUtils.readFileToString(cacheFile);
            ThreadInfo threadData = getThreadInfo(rawData);
            if (threadData != null) {
                emitter.onNext(threadData);
            } else {
                emitter.onError(new Exception());
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<ThreadInfo>() {
                    @Override
                    public void onNext(ThreadInfo threadData) {
                        callBack.onSuccess(threadData);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callBack.onError("读取缓存失败！");
                    }
                });
    }

    /**
     * 诊断（样本收集期保留，攒够样本后再删）：把服务端返回原文与解析异常落盘，便于分析"为什么走 WebView 兜底"。
     * 数据写在应用外部目录 diag/ 下，可直接用 adb 读取；只保留最近 50 个 dump 文件。
     */
    private void writeDiag(String name, String url, String body, Throwable t) {
        try {
            File diagDir = new File(ContextUtils.getContext().getExternalFilesDir(null), "diag");
            if (!diagDir.exists() && !diagDir.mkdirs()) {
                return;
            }
            long now = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            sb.append("time=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date(now))).append('\n');
            sb.append("url=").append(url).append('\n');
            sb.append("len=").append(body == null ? -1 : body.length()).append('\n');
            if (t != null) {
                sb.append("ERR=").append(android.util.Log.getStackTraceString(t)).append('\n');
            }
            sb.append(body == null ? "null" : body);
            FileUtils.write(new File(diagDir, name + "_" + now + ".txt"), sb.toString());

            // 追加一行汇总，便于快速筛选是"截断"还是"服务端报错"
            String summary = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date(now))
                    + " " + name + " len=" + (body == null ? -1 : body.length())
                    + (t == null ? " OK" : " ERR " + t.getClass().getSimpleName() + ": " + t.getMessage())
                    + '\n';
            try (java.io.FileWriter writer = new java.io.FileWriter(new File(diagDir, "summary.log"), true)) {
                writer.write(summary);
            }

            File[] files = diagDir.listFiles((dir, n) -> n.endsWith(".txt"));
            if (files != null && files.length > 50) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < files.length - 50; i++) {
                    //noinspection ResultOfMethodCallIgnored
                    files[i].delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private ThreadInfo getThreadInfo(String js) {
        try {
            return ThreadInfoParse.parse(js, new HtmlConfigService());
        } catch (Exception e) {
            NLog.e(TAG, "can not parse :\n" + js);
            Logger.d(e);
        }
        return null;
    }

    public static class ServerException extends Exception {

        public ServerException(String message) {
            super(message);
        }
    }

}
