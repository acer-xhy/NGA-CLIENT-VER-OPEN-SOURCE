package gov.anzong.androidnga.router

import com.justwen.androidnga.base.activity.ARouterConstants as BaseRouteConstants
import com.justwen.androidnga.base.router.LoginGuard
import gov.anzong.androidnga.arouter.ARouterConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地路由表的完整性校验。
 *
 * <p>自研路由用一张手工维护的 path -> Activity 全类名 表替代注解扫描，
 * 唯一代价是“新增/改名时可能忘记同步”。这个测试就是那道闸门：
 * 每个对外暴露的路由常量都必须在表里有条目，且不能重复、不能指到非法类名。
 */
class RouteTableTest {

    private val expectedPaths = setOf(
        ARouterConstants.ACTIVITY_LOGIN,
        ARouterConstants.ACTIVITY_PROFILE,
        ARouterConstants.ACTIVITY_MESSAGE_LIST,
        ARouterConstants.ACTIVITY_MESSAGE_POST,
        BaseRouteConstants.ACTIVITY_MESSAGE_DETAIL,
        ARouterConstants.ACTIVITY_TOPIC_LIST,
        ARouterConstants.ACTIVITY_TOPIC_CONTENT,
        ARouterConstants.ACTIVITY_POST,
        ARouterConstants.ACTIVITY_SEARCH,
        ARouterConstants.ACTIVITY_NOTIFICATION,
        BaseRouteConstants.ACTIVITY_FRAGMENT_TEMPLATE,
        BaseRouteConstants.ACTIVITY_DEBUG,
    )

    @Test
    fun routeTableCoversEveryPath() {
        assertEquals(
            "路由表与常量不一致，缺失: ${expectedPaths - NgaRouteTable.PATH_TO_ACTIVITY.keys}",
            expectedPaths,
            NgaRouteTable.PATH_TO_ACTIVITY.keys,
        )
    }

    @Test
    fun routeTargetsAreValidClassNames() {
        NgaRouteTable.PATH_TO_ACTIVITY.forEach { (path, className) ->
            assertTrue("$path 的目标类名为空", className.isNotBlank())
            assertTrue("$path 的目标类名含非法字符: $className", className.matches(Regex("[\\w.$]+")))
            assertTrue("$path 的目标类名缺少包名: $className", className.contains('.'))
        }
    }

    @Test
    fun routeTargetsAreUnique() {
        val duplicated = NgaRouteTable.PATH_TO_ACTIVITY.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
        assertTrue("重复的目标类: $duplicated", duplicated.isEmpty())
    }

    @Test
    fun routePathsUseSlashPrefix() {
        NgaRouteTable.PATH_TO_ACTIVITY.keys.forEach {
            assertTrue("path 必须以 / 开头: $it", it.startsWith("/"))
        }
    }

    @Test
    fun loginGuardDefaultAllowsLogin() {
        // 没有注册登录态检查时（启动早期或单测环境）不做拦截，避免把自己锁在门外
        assertTrue(LoginGuard.isLoggedIn())
        assertFalse(LoginGuard.isGated(ARouterConstants.ACTIVITY_LOGIN))
    }
}
