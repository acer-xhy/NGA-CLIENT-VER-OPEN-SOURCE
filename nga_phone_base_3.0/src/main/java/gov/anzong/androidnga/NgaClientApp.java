package gov.anzong.androidnga;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Process;
import android.webkit.WebView;

import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.cloud.CloudServerManager;
import com.justwen.androidnga.base.router.LoginGuard;
import com.justwent.androidnga.bu.UserManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.logger.Logger;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.common.util.ReflectUtils;
import gov.anzong.androidnga.db.AppDatabase;
import gov.anzong.androidnga.router.NgaRouteTable;
import sp.phone.common.UserManagerImpl;
import sp.phone.common.VersionUpgradeHelper;
import sp.phone.task.CheckInTask;

public class NgaClientApp extends Application {

    private static final String TAG = NgaClientApp.class.getSimpleName();

    private static boolean sNewVersion;

    @Override
    public void onCreate() {
        applyPortraitOnly();
        ContextUtils.setApplication(this);
        initLogger();
        PreferenceUtils.transfer(this);
        checkNewVersion();
        VersionUpgradeHelper.upgrade();
        AppDatabase.init(this);
        initCoreModule();
        initRouter();
        checkIn();
        super.onCreate();

        // fixWebViewMultiProcessException();
        CloudServerManager.init(this);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandlerProxy(Thread.getDefaultUncaughtExceptionHandler()));
    }

    private void initLogger() {
        Logger.setBuildDebugMode(BuildConfig.DEBUG);
        Logger.d(TAG, "app nga android start");
        NLog.setDebug(BuildConfig.DEBUG);
    }

    /**
     * 全应用强制竖屏，禁止跟随系统旋转成横屏。
     * 主机制：各模块 AndroidManifest 里每个 activity 都已声明 screenOrientation="portrait"；
     * 这里再兜一层运行时约束，保证后续新增的、或被三方清单合并进来的 Activity 同样锁定竖屏。
     */
    private void applyPortraitOnly() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void fixWebViewMultiProcessException() {
        try {
            File dataDir = getDataDir();
            File[] dirs = dataDir.listFiles();

            Object ppidObj = ReflectUtils.invokeMethod(Process.class, "myPpid");

            int ppid = ppidObj != null ? (int) ppidObj : Process.myPid();

            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.getName().contains("webview")) {
                        if (!dir.getName().contains("webview_" + ppid)) {
                            ThreadUtils.postOnSubThread(() -> {
                                try {
                                    FileUtils.deleteDirectory(dir);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                        }
                    }
                }
            }

            WebView.setDataDirectorySuffix(String.valueOf(ppid));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

    private void initRouter() {
        // 路由表 / 服务表一次性注册，取代原 ARouter 的注解扫描
        NgaRouteTable.install(this, BuildConfig.DEBUG);
        // 需要登录的页面统一在目标 Activity 里用 LoginGuard 拦截（替代原 ARouter 拦截器）
        LoginGuard.INSTANCE.setLoginChecker(() -> UserManagerImpl.getInstance().hasValidUser());
        LoginGuard.INSTANCE.addGatedPaths(ARouterConstants.ACTIVITY_NEED_LOGIN);
    }

    private void initCoreModule() {
        UserManagerImpl.getInstance().initialize(this);
        UserManager.INSTANCE.getActiveUser();
        RetrofitHelper.setCookieProvider(() -> UserManagerImpl.getInstance().getCookie());
//        // 注册crashHandler
//        CrashHandler.getInstance().init(this);
        initCoreModuleAsync();

    }

    private void initCoreModuleAsync() {
        ThreadUtils.postOnSubThread(new Runnable() {
            @Override
            public void run() {
                ForumBoardViewModel.INSTANCE.getBoardLiveData();
            }
        });
    }

    private void checkNewVersion() {
        int versionCode = PreferenceUtils.getData(PreferenceKey.VERSION_CODE, 0);
        if (BuildConfig.VERSION_CODE > versionCode) {
            PreferenceUtils.putData(PreferenceKey.PREVIOUS_VERSION_CODE, versionCode);
            PreferenceUtils.putData(PreferenceKey.VERSION_CODE, BuildConfig.VERSION_CODE);
            sNewVersion = true;
            PreferenceUtils.putData(PreferenceKey.KEY_WEBVIEW_DATA_INDEX, 0);
        }
    }

    private void checkIn() {
        CheckInTask.autoCheckIn(this);
    }

    public static boolean isNewVersion() {
        return sNewVersion;
    }

    public static void setNewVersion(boolean newVersion) {
        sNewVersion = newVersion;
    }

}
