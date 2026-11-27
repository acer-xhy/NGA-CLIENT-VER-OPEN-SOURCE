package sp.phone.mvp.model.convert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.UnknownHostException;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;
import okhttp3.ResponseBody;
import retrofit2.HttpException;

/**
 * Created by Justwen on 2017/11/23.
 *
 * 服务端返回的错误提示可能是 JSON（__MESSAGE）、HTML 页面（410/403 等错误页）或纯文本，
 * 这里统一转成用户能看懂的一句话，避免把 “HTTP 410 Gone” 这类原始文案直接弹给用户。
 */
public class ErrorConvertFactory {

    private static final String MESSAGE_NO_RESULT = "没有符合条件的结果";
    private static final String MESSAGE_NEED_LOGIN = "请重新登录";
    private static final String MESSAGE_LAST_PAGE = "已加载到最后一页";
    private static final String MESSAGE_NO_PERMISSION = "没有权限，请检查登录状态";
    private static final String MESSAGE_NOT_FOUND = "内容不存在或已被删除";
    private static final String MESSAGE_SERVER_BUSY = "NGA 服务器暂时不可用，请稍后再试";
    private static final String MESSAGE_GENERIC_FAIL = "加载失败，请稍后重试";

    /**
     * HTML 错误页里只取像提示语的行，避免把导航、页脚文字当成错误原因。
     */
    private static final String TIP_KEYWORDS = "错误|失败|权限|登录|删除|不存在|条件|结果|无法|禁止|限制|超时|繁忙|稍后|重试";

    public static String getErrorMessage(String js) {
        if (js == null || js.isEmpty()) {
            return ContextUtils.getString(R.string.network_error);
        }
        String described = describeServerText(js);
        return described != null ? described : MESSAGE_GENERIC_FAIL;
    }

    public static String getErrorMessage(Throwable throwable) {
        if (throwable instanceof UnknownHostException) {
            return ContextUtils.getString(R.string.network_error);
        }
        if (throwable instanceof HttpException) {
            HttpException exception = (HttpException) throwable;
            String described = describeServerText(readErrorBody(exception));
            if (described != null) {
                return described;
            }
            return describeHttpCode(exception.code(), requestUrl(exception));
        }
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? MESSAGE_GENERIC_FAIL : message;
    }

    /**
     * 从服务端返回的原文里提取可读提示：先看固定关键词，再按 JSON / HTML 两种形态兜底。
     */
    public static String describeServerText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.contains("中没有符合条件的结果")) {
            return MESSAGE_NO_RESULT;
        }
        if (raw.contains("未登录")) {
            return MESSAGE_NEED_LOGIN;
        }
        if (raw.contains("无此页")) {
            return MESSAGE_LAST_PAGE;
        }
        String jsonMessage = readJsonMessage(raw);
        if (jsonMessage != null) {
            return jsonMessage;
        }
        return readHtmlTip(raw);
    }

    /**
     * 服务端正文里没有可读提示时，按状态码给一个合理的说明。
     *
     * @param code HTTP 状态码
     * @param url  请求地址，410 需要区分“搜索无结果”和“内容已删除”
     */
    public static String describeHttpCode(int code, String url) {
        switch (code) {
            case 401:
            case 403:
                return MESSAGE_NO_PERMISSION;
            case 404:
                return MESSAGE_NOT_FOUND;
            case 410:
                return url != null && url.contains("search") ? MESSAGE_NO_RESULT : MESSAGE_NOT_FOUND;
            case 429:
                return "请求过于频繁，请稍后再试";
            case 500:
            case 502:
            case 503:
            case 504:
                return MESSAGE_SERVER_BUSY;
            default:
                return "服务器返回异常（HTTP " + code + "）";
        }
    }

    private static String readJsonMessage(String raw) {
        if (!raw.contains("__MESSAGE") && !raw.trim().startsWith("{")) {
            return null;
        }
        try {
            String json = raw;
            String prefix = "window.script_muti_get_var_store=";
            if (json.startsWith(prefix)) {
                json = json.substring(prefix.length());
            }
            JSONObject object = (JSONObject) JSON.parse(json);
            object = (JSONObject) object.get("data");
            if (object == null) {
                return null;
            }
            object = (JSONObject) object.get("__MESSAGE");
            if (object == null) {
                return null;
            }
            String message = object.getString("1");
            if (message == null) {
                return null;
            }
            message = message.replace("<br/>", " ").trim();
            return message.isEmpty() ? null : message;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readHtmlTip(String raw) {
        if (!raw.contains("<")) {
            return null;
        }
        String text = raw
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", "\n")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
        for (String line : text.split("\n")) {
            String tip = line.trim();
            if (tip.length() >= 2 && tip.length() <= 30 && tip.matches(".*[\\u4e00-\\u9fa5].*")
                    && tip.matches(".*(" + TIP_KEYWORDS + ").*")) {
                return tip;
            }
        }
        return null;
    }

    private static String readErrorBody(HttpException exception) {
        if (exception.response() == null) {
            return null;
        }
        ResponseBody body = exception.response().errorBody();
        if (body == null) {
            return null;
        }
        try {
            return body.string();
        } catch (IOException e) {
            return null;
        }
    }

    private static String requestUrl(HttpException exception) {
        if (exception.response() == null || exception.response().raw() == null) {
            return null;
        }
        return exception.response().raw().request().url().toString();
    }

}
