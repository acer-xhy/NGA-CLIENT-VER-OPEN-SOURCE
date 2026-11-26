package com.justwen.androidnga.base.router

import android.content.Context
import com.justwen.androidnga.base.activity.ARouterConstants

/**
 * 登录态页面拦截。
 *
 * <p>原实现是 ARouter 的 {@code @Interceptor}（ActivityInterceptor），在跳转前判断
 * 目标 path 是否属于需要登录的页面。老框架（ARouter）的导航拦截器在新路由里没有对应形态，
 * 改为在目标页面 onCreate 里显式询问一次：
 * 需要登录但当前没有有效账号时，直接跳登录页并由调用方 finish 自身。
 *
 * <p>登录态检查由 app 模块在 Application 里注册（LoginGuard 本身处于底层模块，
 * 拿不到 UserManagerImpl）。
 */
object LoginGuard {

    private var loginChecker: (() -> Boolean)? = null

    private val gatedPaths = mutableSetOf<String>()

    fun setLoginChecker(checker: () -> Boolean) {
        loginChecker = checker
    }

    fun addGatedPaths(vararg paths: String) {
        gatedPaths.addAll(paths)
    }

    fun isLoggedIn(): Boolean = loginChecker?.invoke() ?: true

    fun isGated(path: String): Boolean = gatedPaths.contains(path)

    /**
     * @return true 表示已经被拦截（已拉起登录页），调用方应当 finish 自己。
     */
    fun intercept(context: Context, path: String): Boolean {
        if (!isLoggedIn() && gatedPaths.contains(path)) {
            Router.build(ARouterConstants.ACTIVITY_LOGIN).navigation(context)
            return true
        }
        return false
    }
}
