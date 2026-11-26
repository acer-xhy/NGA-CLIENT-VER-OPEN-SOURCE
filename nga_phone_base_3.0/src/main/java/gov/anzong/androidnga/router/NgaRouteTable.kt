package gov.anzong.androidnga.router

import android.content.Context
import com.client.androidnga.base.api.service.IFilterManagerService
import com.justwen.androidnga.base.activity.ARouterConstants as BaseRouteConstants
import com.justwen.androidnga.base.router.Router
import com.justwen.androidnga.base.router.ServiceLocator
import com.justwen.androidnga.base.service.api.IThemeManagerService
import com.justwen.androidnga.base.service.api.IUserManagerService
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.activity.compose.filter.FilterManager
import gov.anzong.androidnga.arouter.ARouterConstants
import gov.anzong.androidnga.service.ThemeManagerService

/**
 * 全项目路由表与服务表。
 *
 * <p>这是自研路由（[Router]）唯一的“配置中心”：新增 Activity 路由或服务实现时只需在这里补一行。
 * 以前的 ARouter 通过 {@code @Route} 注解 + 编译期扫描生成路由表，本项目路由总数只有 12 条，
 * 用注解处理器换来的收益远小于它带来的构建成本（kapt/KSP + Gradle 插件 + 增量编译失效）。
 *
 * <p>路径常量仍沿用 [ARouterConstants]（名字保留，避免大面积改动），
 * 表的完整性由 {@code RouteTableTest} 与 debug 包启动时的 Class.forName 校验共同兜底。
 */
object NgaRouteTable {

    @JvmField
    val PATH_TO_ACTIVITY: Map<String, String> = linkedMapOf(
        // 账号
        ARouterConstants.ACTIVITY_LOGIN to "com.justwent.androidnga.bu.login.LoginActivity",
        ARouterConstants.ACTIVITY_PROFILE to "com.client.androidnga.account.profile.ProfileActivity",
        // 私信
        ARouterConstants.ACTIVITY_MESSAGE_LIST to "com.client.androidnga.message.list.MessageListActivity",
        ARouterConstants.ACTIVITY_MESSAGE_POST to "com.client.androidnga.message.post.MessagePostActivity",
        BaseRouteConstants.ACTIVITY_MESSAGE_DETAIL to "com.client.androidnga.message.detail.MessageDetailActivity",
        // 帖子
        ARouterConstants.ACTIVITY_TOPIC_LIST to "gov.anzong.androidnga.activity.TopicListActivity",
        ARouterConstants.ACTIVITY_TOPIC_CONTENT to "gov.anzong.androidnga.activity.ArticleListActivity",
        ARouterConstants.ACTIVITY_POST to "gov.anzong.androidnga.activity.PostActivity",
        // 搜索 / 通知
        ARouterConstants.ACTIVITY_SEARCH to "gov.anzong.androidnga.activity.compose.SearchActivity",
        ARouterConstants.ACTIVITY_NOTIFICATION to "gov.anzong.androidnga.activity.RecentNotificationActivity",
        // 通用
        BaseRouteConstants.ACTIVITY_FRAGMENT_TEMPLATE to "com.justwen.androidnga.ui.FragmentTemplateActivity",
        BaseRouteConstants.ACTIVITY_DEBUG to "com.justwen.androidnga.module.debug.DebugActivity",
    )

    @JvmStatic
    fun install(context: Context, debug: Boolean) {
        Router.init(context, PATH_TO_ACTIVITY, debug)
        installServices()
    }

    /**
     * 服务实现注册。放在启动期而不是用注解生成，是为了让“谁提供了哪个实现”一眼可见。
     */
    @JvmStatic
    fun installServices() {
        ServiceLocator.register(IThemeManagerService::class.java, ThemeManagerService())
        ServiceLocator.register(IUserManagerService::class.java, UserManager)
        ServiceLocator.register(IFilterManagerService::class.java, FilterManager)
    }
}
