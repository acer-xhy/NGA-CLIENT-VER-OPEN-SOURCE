package com.justwent.androidnga.bu

import android.webkit.CookieManager
import gov.anzong.androidnga.base.util.ContextUtils

/**
 * App 账号与内置浏览器（WebView）cookie 之间的同步。
 *
 * 登录页固定走 ngabbs.com，而浏览默认域是 bbs.nga.cn，这是两个不同的注册域，
 * WebView 的 cookie 不会跨域共享。为了让网页版打开即可用（而不是新设备首次
 * 还要再登录一次），进入网页前把 App 已保存的账号 cookie 主动写进所有 NGA 域名；
 * 退出登录或切换账号时再把这些 cookie 清掉重写，避免网页版残留上一个账号。
 */
object NgaWebCookie {

    private const val SCHEME = "https://"

    private fun hosts(): Array<String> =
        ContextUtils.getContext().resources.getStringArray(
            gov.anzong.androidnga.common.R.array.nga_domain_no_http
        )

    /**
     * 把当前活动账号的 uid/cid 写进所有 NGA 域名。未登录时不做任何事。
     */
    fun sync() {
        val cookie = UserManager.getCookie()
        if (cookie.isEmpty()) {
            return
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        for (host in hosts()) {
            for (item in cookie.split(";")) {
                val pair = item.trim()
                if (pair.isNotEmpty()) {
                    cookieManager.setCookie("$SCHEME$host", "$pair; Path=/")
                }
            }
        }
        cookieManager.flush()
    }

    /**
     * 清空 NGA 各域下的 cookie。逐条置空并立刻过期，比 removeAllCookies 更克制，
     * 不会波及 WebView 里其它站点。
     */
    fun clear() {
        val cookieManager = CookieManager.getInstance()
        for (host in hosts()) {
            val current = cookieManager.getCookie("$SCHEME$host") ?: continue
            for (item in current.split(";")) {
                val name = item.substringBefore("=").trim()
                if (name.isEmpty()) {
                    continue
                }
                cookieManager.setCookie("$SCHEME$host", "$name=; Max-Age=0; Path=/")
                cookieManager.setCookie(
                    "$SCHEME$host",
                    "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                )
            }
        }
        cookieManager.flush()
    }
}
