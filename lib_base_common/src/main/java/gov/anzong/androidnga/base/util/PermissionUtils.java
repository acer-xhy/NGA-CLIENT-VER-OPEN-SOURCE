package gov.anzong.androidnga.base.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.vanniktech.rxpermission.Permission;
import com.vanniktech.rxpermission.RealRxPermission;
import com.vanniktech.rxpermission.RxPermission;

import java.util.List;

import gov.anzong.androidnga.rxjava.DefaultSubsriber;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Justwen on 2017/6/24.
 *
 * 权限请求统一入口。底层换成 androidx 版本实现（com.vanniktech:rxpermission），
 * 不再依赖 support 时代的 com.github.tbruyelle:rxpermissions。
 */
@SuppressLint("CheckResult")
public class PermissionUtils {

    private PermissionUtils() {

    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static RxPermission permission(Context context) {
        return RealRxPermission.getInstance(context);
    }

    public static void requestCombined(AppCompatActivity activity, @Nullable Consumer<Permission> consumer, String... permissions) {
        Consumer<Permission> target = createIfNull(consumer);
        permission(activity).requestEach(permissions)
                .toList()
                .map(list -> combine(permissions, list))
                .subscribe(target);
    }

    public static void requestEach(AppCompatActivity activity, @Nullable Consumer<Permission> consumer, String... permissions) {
        Consumer<Permission> target = createIfNull(consumer);
        permission(activity).requestEach(permissions).subscribe(target);
    }

    public static void request(FragmentActivity activity, @Nullable Observer<Boolean> consumer, String permission) {
        permission(activity).request(permission)
                .map(p -> p.state() == Permission.State.GRANTED)
                .toObservable()
                .subscribe(consumer == null ? new DefaultSubsriber<>() : consumer);
    }

    public static void requestCombined(Fragment fragment, @Nullable Consumer<Permission> consumer, String... permissions) {
        Consumer<Permission> target = createIfNull(consumer);
        permission(fragment.requireContext()).requestEach(permissions)
                .toList()
                .map(list -> combine(permissions, list))
                .subscribe(target);
    }

    public static void requestEach(Fragment fragment, @Nullable Consumer<Permission> consumer, String... permissions) {
        Consumer<Permission> target = createIfNull(consumer);
        permission(fragment.requireContext()).requestEach(permissions).subscribe(target);
    }

    public static void request(Fragment fragment, @Nullable Observer<Boolean> consumer, String permission) {
        permission(fragment.requireContext()).request(permission)
                .map(p -> p.state() == Permission.State.GRANTED)
                .toObservable()
                .subscribe(consumer == null ? new DefaultSubsriber<>() : consumer);
    }

    public static void requestAsync(Fragment fragment, @Nullable Observer<Boolean> consumer, String permission) {
        permission(fragment.requireContext()).request(permission)
                .map(p -> p.state() == Permission.State.GRANTED)
                .toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(createIfNull(consumer));
    }

    /**
     * 多权限合并：全部授予时产出一条 granted，否则产出一条 denied，
     * 与原先 requestEachCombined 的语义一致。
     */
    private static Permission combine(String[] permissions, List<Permission> list) {
        String name = permissions.length > 0 ? permissions[0] : "";
        boolean allGranted = !list.isEmpty();
        for (Permission p : list) {
            if (p.state() != Permission.State.GRANTED) {
                allGranted = false;
                break;
            }
        }
        return allGranted ? Permission.granted(name) : Permission.denied(name);
    }

    private static <T> Consumer<T> createIfNull(Consumer<T> consumer) {
        if (consumer == null) {
            consumer = t -> {

            };
        }
        return consumer;
    }

    private static <T> Observer<T> createIfNull(Observer<T> consumer) {
        if (consumer == null) {
            return new DefaultSubsriber<>();
        }
        return consumer;
    }
}
