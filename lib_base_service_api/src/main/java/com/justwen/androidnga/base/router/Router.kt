package com.justwen.androidnga.base.router

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log

/**
 * 极简本地路由，替代 ARouter / TheRouter。
 *
 * <p>项目对路由的全部需求只有三件事：
 * <ol>
 *     <li>按 path 打开一个 Activity（12 条固定路由，没有 Fragment 路由、没有动态注册）；</li>
 *     <li>透传 string / int / parcelable 三种参数（数量固定，无需类型转换器）；</li>
 *     <li>跨模块按接口取服务实现（3 个服务）。</li>
 * </ol>
 * 因此不需要注解处理器、不需要编译期生成路由表、不需要运行时扫描 dex：
 * 路由表就是一张手工维护的 path -> Activity 全类名 映射，跳转直接 setClassName 生成 Intent，
 * 不反射、不实例化、不加载目标类。服务表同样是一次性的接口 -> 实现注册。
 *
 * <p>路由表的完整性由两处兜底：debug 包启动时用 Class.forName 校验每个条目是否真实存在，
 * 单元测试校验 path 覆盖了所有对外常量。
 */
object Router {

    private const val TAG = "Router"

    private var appContext: Context? = null

    private var debug = false

    /** path -> Activity 全类名，由 app 模块在启动时注入 */
    private val routes = HashMap<String, String>()

    @JvmStatic
    fun init(context: Context, routeTable: Map<String, String>, debug: Boolean = false) {
        appContext = context.applicationContext
        this.debug = debug
        routes.clear()
        routes.putAll(routeTable)
        if (debug) {
            verifyRouteTable(context, routeTable)
        }
    }

    private fun verifyRouteTable(context: Context, routeTable: Map<String, String>) {
        routeTable.forEach { (path, className) ->
            try {
                Class.forName(className, false, context.classLoader)
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "route target missing: $path -> $className", e)
            }
        }
    }

    @JvmStatic
    fun registerRoute(path: String, className: String) {
        routes[path] = className
    }

    @JvmStatic
    fun contains(path: String): Boolean = routes.containsKey(path)

    @JvmStatic
    fun build(path: String): Navigator = Navigator(path)

    /** 取跨模块服务实现，未注册时快速失败（避免后续 NPE 难以定位） */
    @JvmStatic
    fun <T : Any> get(type: Class<T>): T = ServiceLocator.get(type)

    internal fun findTarget(path: String): String? = routes[path]

    internal fun applicationContext(): Context = appContext
        ?: throw IllegalStateException("Router.init() must be called in Application.onCreate()")

    internal fun isDebug(): Boolean = debug
}

/**
 * 路由跳转构造器。保留 ARouter 时代的链式调用形态，业务侧改造量最小。
 */
class Navigator internal constructor(private val path: String) {

    private val arguments = Bundle()

    private var flags = 0

    fun withString(key: String, value: String?): Navigator {
        arguments.putString(key, value)
        return this
    }

    fun withInt(key: String, value: Int): Navigator {
        arguments.putInt(key, value)
        return this
    }

    fun withParcelable(key: String, value: Parcelable?): Navigator {
        arguments.putParcelable(key, value)
        return this
    }

    fun withFlags(flags: Int): Navigator {
        this.flags = this.flags or flags
        return this
    }

    fun createIntent(context: Context?): Intent? {
        val className = Router.findTarget(path)
        if (className == null) {
            reportMissingPath()
            return null
        }
        val host = context ?: Router.applicationContext()
        return Intent().apply {
            setClassName(host, className)
            putExtras(arguments)
            addFlags(flags)
        }
    }

    fun navigation(): Boolean = navigation(null, -1)

    fun navigation(context: Context?): Boolean = navigation(context, -1)

    fun navigation(context: Context?, requestCode: Int): Boolean {
        val intent = createIntent(context) ?: return false
        val host = context ?: Router.applicationContext()
        if (host !is Activity) {
            // 从非 Activity 上下文（Application、ViewModel）启动必须带 NEW_TASK
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (requestCode >= 0 && host is Activity) {
                host.startActivityForResult(intent, requestCode)
            } else {
                host.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Log.e("Router", "navigation failed: $path", e)
            false
        }
    }

    private fun reportMissingPath() {
        val message = "route path not registered: $path"
        if (Router.isDebug()) {
            throw IllegalStateException(message)
        } else {
            Log.e("Router", message)
        }
    }
}

/**
 * 跨模块服务表：接口 -> 实现，由 app 模块在启动时一次性注册。
 * 相比 ARouter 的 @Route / TheRouter 的 @ServiceProvider，少了注解处理器与生成代码，
 * 代价是新增服务要在这里补一行注册（本项目 3 个服务，成本可忽略）。
 */
object ServiceLocator {

    private val services = HashMap<Class<*>, Any>()

    @JvmStatic
    fun <T : Any> register(type: Class<T>, impl: T) {
        services[type] = impl
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T : Any> get(type: Class<T>): T {
        services[type]?.let { return it as T }
        throw IllegalStateException("service not registered: ${type.name}")
    }

    @JvmStatic
    fun contains(type: Class<*>): Boolean = services.containsKey(type)
}
