package sp.phone.task;

import androidx.annotation.NonNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import sp.phone.http.OnSimpleHttpCallBack;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import sp.phone.rxjava.BaseSubscriber;

/**
 * 赞或者踩
 * Created by elrond on 2017/9/1.
 */

public class LikeTask {

    public static final int SUPPORT = 1;

    public static final int OPPOSE = -1;

    /**
     * 服务端要求当天已签到才允许点赞，未签到时返回 {"error":{"0":"本日没有签到"}}
     */
    private static final String CHECK_IN_URL = Utils.getNGAHost() + "nuke.php?__lib=check_in&__act=check_in&lite=js";

    /**
     * 点赞结果回调。
     * value 为服务端实际生效的值：1 表示本次是“支持”，-1 表示本次是“取消支持/反对”，
     * 界面上点赞数按该值做 ±1 即可与服务端保持一致。
     */
    public interface OnLikeResultListener {

        void onLikeSuccess(int pid, int value, String message);

        void onLikeFailed(int pid, String message);
    }

    private RetrofitService mService;

    private Map<String, String> mParamMap;

    public LikeTask() {
        mService = RetrofitHelper.getInstance().getService();
        mParamMap = new HashMap<>();
        mParamMap.put("__lib", "topic_recommend");
        mParamMap.put("__act", "add");
        mParamMap.put("raw", "3");
        mParamMap.put("pid", "0");
        mParamMap.put("__output", "8");
    }

    @Deprecated
    public void execute(int tid, int pid, int like, OnSimpleHttpCallBack<String> callBack) {
        execute(tid, pid, like, new OnLikeResultListener() {
            @Override
            public void onLikeSuccess(int resultPid, int value, String message) {
                callBack.onResult(message);
            }

            @Override
            public void onLikeFailed(int resultPid, String message) {
                callBack.onResult(message);
            }
        });
    }

    public void execute(int tid, int pid, int like, OnLikeResultListener listener) {
        execute(tid, pid, like, listener, true);
    }

    private void execute(int tid, int pid, int like, OnLikeResultListener listener, boolean allowCheckInRetry) {
        Map<String, String> map = new HashMap<>(mParamMap);
        map.put("value", String.valueOf(like));
        map.put("tid", String.valueOf(tid));
        map.put("pid", String.valueOf(pid));
        mService.post(map)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<String>() {
                    @Override
                    public void onNext(@NonNull String s) {
                        android.util.Log.i("LikeDiag", "resp pid=" + pid + " like=" + like + " body=" + s);
                        String successMessage = parseSuccessMessage(s);
                        if (successMessage != null) {
                            listener.onLikeSuccess(pid, parseSuccessValue(s, like), successMessage);
                            return;
                        }
                        String errorMessage = parseErrorMessage(s);
                        if (errorMessage == null) {
                            // 既不是成功结构也不是错误结构，说明不是可解析的服务端响应
                            listener.onLikeFailed(pid, ContextUtils.getString(R.string.network_error));
                            return;
                        }
                        if (allowCheckInRetry && errorMessage.contains("签到")) {
                            // 当天未签到导致被拒，先签到再重试一次点赞
                            checkInThenRetry(tid, pid, like, listener);
                            return;
                        }
                        // 把服务端原始提示透出，不再统一吞成“网络错误”
                        listener.onLikeFailed(pid, errorMessage);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        super.onError(throwable);
                        listener.onLikeFailed(pid, ContextUtils.getString(R.string.network_error));
                    }
                });
    }

    private void checkInThenRetry(int tid, int pid, int like, OnLikeResultListener listener) {
        mService.post(CHECK_IN_URL)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new BaseSubscriber<String>() {
                    @Override
                    public void onNext(@NonNull String s) {
                        if (s.contains("签到成功") || s.contains("今天已经签到")) {
                            PreferenceUtils.putData(PreferenceKey.CHECK_IN_LAST_TIME, System.currentTimeMillis());
                        }
                        execute(tid, pid, like, listener, false);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        super.onError(throwable);
                        listener.onLikeFailed(pid, ContextUtils.getString(R.string.network_error));
                    }
                });
    }

    private static String parseSuccessMessage(String body) {
        JSONObject data = parseSuccessData(body);
        return data == null ? null : data.getString("0");
    }

    /**
     * 取服务端本次实际生效的推荐值，取不到时退回本次请求的 like 值
     */
    private static int parseSuccessValue(String body, int like) {
        JSONObject data = parseSuccessData(body);
        if (data == null) {
            return like;
        }
        int value = data.getIntValue("1");
        return value == 0 ? like : value;
    }

    private static JSONObject parseSuccessData(String body) {
        JSONObject root = parseJson(body);
        return root == null ? null : root.getJSONObject("data");
    }

    private static String parseErrorMessage(String body) {
        JSONObject root = parseJson(body);
        if (root == null) {
            return null;
        }
        Object error = root.get("error");
        if (error instanceof JSONObject) {
            return ((JSONObject) error).getString("0");
        }
        if (error instanceof JSONArray) {
            JSONArray array = (JSONArray) error;
            return array.isEmpty() ? null : array.getString(0);
        }
        return null;
    }

    private static JSONObject parseJson(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(body);
        } catch (Exception e) {
            return null;
        }
    }
}

