package com.justwen.androidnga.base.network.retrofit;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.webkit.WebSettings;

import com.client.androidnga.base.network.retrofit.JsonStringConvertFactory;

import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import gov.anzong.androidnga.base.logger.Logger;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.StringUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.ForumUtils;
import gov.anzong.androidnga.common.util.NLog;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * Created by Justwen on 2017/10/10.
 */

public class RetrofitHelper {

    private Retrofit mRetrofit;

    /**
     * 每次请求都新建连接的 RetrofitService（连接池空闲连接数为 0）。
     * 用于网页补偿请求：避免复用那条已经返回过残体的旧连接。
     */
    private RetrofitService mFreshConnectionService;

    private static final String URL_NGA_BASE_CC = "https://bbs.ngacn.cc/";

    private String mBaseUrl;

    private String mUserAgent = "";

    private static CookieProvider mCookieProvider;

    public interface CookieProvider {
        String getCookie();
    }

    private RetrofitHelper() {
        Context context = ContextUtils.getContext();
        SharedPreferences sp = context.getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE);
        mBaseUrl = ForumUtils.getAvailableDomain();
        mRetrofit = createRetrofit();

        sp.registerOnSharedPreferenceChangeListener((sp1, key) -> {
            if (key.equals(PreferenceKey.KEY_NGA_DOMAIN)) {
                mBaseUrl = ForumUtils.getAvailableDomain();
                mRetrofit = createRetrofit();
                mFreshConnectionService = null;
            }
        });

        mUserAgent = sp.getString(PreferenceKey.USER_AGENT, null);

        if (TextUtils.isEmpty(mUserAgent)) {
            mUserAgent = WebSettings.getDefaultUserAgent(context);
            PreferenceUtils.putData(PreferenceKey.USER_AGENT, mUserAgent);
        }

    }

    public static void setCookieProvider(CookieProvider cookieProvider) {
        mCookieProvider = cookieProvider;
    }
    public void setUserAgent(String userAgent) {
        mUserAgent = userAgent;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public Retrofit createRetrofit() {
        return createRetrofit(mBaseUrl, null);
    }

    public Retrofit createRetrofit(String baseUrl) {
        return createRetrofit(baseUrl, null);
    }

    public Retrofit createRetrofit(OkHttpClient.Builder builder) {
        return createRetrofit(mBaseUrl, builder);
    }

    public Retrofit createRetrofit(String baseUrl, OkHttpClient.Builder builder) {
        if (builder == null) {
            builder = createOkHttpClientBuilder();
        }
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JsonStringConvertFactory.INSTANCE)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(builder.build())
                .build();
    }

    public OkHttpClient.Builder createOkHttpClientBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(chain -> {
            Request original = chain.request();

            String cookie = original.header("Cookie");
            if (cookie == null && mCookieProvider != null) {
                cookie = mCookieProvider.getCookie();
            }
            Request request = original.newBuilder()
                    .header("Cookie", cookie)
                    .header("User-Agent", mUserAgent)
                    .header("X-User-Agent", "Nga_Official")
                    .method(original.method(), original.body())
                    .build();
            return chain.proceed(request);
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            try {
                if (request.method().equalsIgnoreCase("post")) {
                    String body = StringUtils.requestBody2String(request.body());
                    body = URLDecoder.decode(body, "utf-8");
                    if (body.contains("charset=gbk") || body.contains("charset=GBK")) {
                        request = request.newBuilder().post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=GBK"), body)).build();
                    }
                }
            } catch (Exception e) {
                NLog.e(e.getMessage());
            }
            return chain.proceed(request);
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Logger.d(request.toString());
            return chain.proceed(request);
        });
        // 临时诊断：记录最终发出的完整请求行/请求头，以及响应行/响应头（Cookie 只记录长度，不落明文）
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            long start = System.currentTimeMillis();
            try {
                Response response = chain.proceed(request);
                dumpNetwork(request, response, null, System.currentTimeMillis() - start);
                return response;
            } catch (Throwable t) {
                dumpNetwork(request, null, t, System.currentTimeMillis() - start);
                throw t;
            }
        });
        return builder;
    }

    /**
     * 诊断（样本收集期保留，攒够样本后再删）：把 App 真实发出的请求与收到的响应头落盘，
     * 用于和 PC 端同一帖的请求参数逐项对比。按要求保留完整明文（含 Cookie）。
     */
    private static void dumpNetwork(Request request, Response response, Throwable error, long cost) {
        try {
            java.io.File diagDir = new java.io.File(ContextUtils.getContext().getExternalFilesDir(null), "diag");
            if (!diagDir.exists() && !diagDir.mkdirs()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("time=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date())).append('\n');
            sb.append("cost=").append(cost).append("ms\n");
            sb.append("REQUEST ").append(request.method()).append(' ').append(request.url()).append('\n');
            for (String name : request.headers().names()) {
                String value = request.header(name);
                // 临时诊断：按用户要求保留完整明文（不再脱敏），便于与 PC 端逐字节比对
                sb.append("  ").append(name).append(": ").append(value).append('\n');
            }
            if (response != null) {
                sb.append("RESPONSE ").append(response.code()).append(' ').append(response.protocol())
                        .append(' ').append(response.message()).append('\n');
                for (String name : response.headers().names()) {
                    sb.append("  ").append(name).append(": ").append(response.header(name)).append('\n');
                }
                sb.append("  bodyContentLength=").append(response.body() == null ? -1 : response.body().contentLength()).append('\n');
            } else {
                sb.append("ERROR ").append(error).append('\n');
            }
            java.io.File file = new java.io.File(diagDir, "net_" + System.currentTimeMillis() + ".txt");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                out.write(sb.toString().getBytes("UTF-8"));
            }
            java.io.File[] files = diagDir.listFiles((dir, n) -> n.startsWith("net_") && n.endsWith(".txt"));
            if (files != null && files.length > 20) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < files.length - 20; i++) {
                    //noinspection ResultOfMethodCallIgnored
                    files[i].delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static RetrofitHelper getInstance() {
        return SingleTonHolder.sInstance;
    }

    public Object getService(Class<?> service) {
        return mRetrofit.create(service);
    }

    /**
     * 返回一个连接池空闲连接数为 0 的 RetrofitService：每个请求都会新建 TCP 连接。
     * 仅用于网页补偿请求，避免复用那条已经返回过残体的连接。
     */
    public RetrofitService getFreshConnectionService() {
        if (mFreshConnectionService == null) {
            OkHttpClient.Builder builder = createOkHttpClientBuilder();
            builder.connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS));
            mFreshConnectionService = createRetrofit(mBaseUrl, builder).create(RetrofitService.class);
        }
        return mFreshConnectionService;
    }

    public RetrofitService getService() {
        return mRetrofit.create(RetrofitService.class);
    }

    public RetrofitServiceKt getServiceKt() {
        return mRetrofit.create(RetrofitServiceKt.class);
    }

    public static RetrofitService getAuthCodeService() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RetrofitService.class);
    }

    public static RetrofitService getDefault() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addConverterFactory(JsonStringConvertFactory.INSTANCE)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RetrofitService.class);
    }

    private static class SingleTonHolder {

        static final RetrofitHelper sInstance = new RetrofitHelper();
    }
}
