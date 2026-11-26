package sp.phone.util;

import android.content.Context;

import com.justwen.androidnga.base.router.Router;
import com.justwen.androidnga.base.router.Navigator;

/**
 * 路由入口薄封装，底层是自研的 {@link com.justwen.androidnga.base.router.Router}：
 * path 到 Activity 的映射在启动时一次性注册，跳转直接 setClassName，无反射、无扫描。
 *
 * Created by Justwen on 2019/3/12.
 */
public class ARouterUtils {

    private ARouterUtils() {

    }

    public static Navigator build(String path) {
        return Router.build(path);
    }

    public static void navigation(String path) {
        navigation(null, path);
    }

    public static void navigation(Context context, String path) {
        if (context == null) {
            Router.build(path).navigation();
        } else {
            Router.build(path).navigation(context);
        }
    }

}
