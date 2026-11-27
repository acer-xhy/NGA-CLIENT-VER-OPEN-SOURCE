package sp.phone.mvp.model.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 错误提示转换的纯逻辑测试。
 * 素材取自服务端真实返回形态：JSON __MESSAGE、410 错误页、权限提示页。
 */
class ErrorConvertFactoryTest {

    @Test
    fun describeServerText_searchNoResult() {
        val html = "<html><body><div class=\"tip\">搜索关键词 wx40217 中没有符合条件的结果</div></body></html>"
        assertEquals("没有符合条件的结果", ErrorConvertFactory.describeServerText(html))
    }

    @Test
    fun describeServerText_jsonMessage() {
        val json = "{\"data\":{\"__MESSAGE\":{\"1\":\"请先登录后再搜索<br/>\"}}}"
        assertEquals("请先登录后再搜索", ErrorConvertFactory.describeServerText(json))
    }

    @Test
    fun describeServerText_htmlPermission() {
        val html = "<html><body><div>你没有权限访问该版面</div></body></html>"
        assertEquals("你没有权限访问该版面", ErrorConvertFactory.describeServerText(html))
    }

    @Test
    fun describeServerText_withoutReadableTip() {
        assertNull(ErrorConvertFactory.describeServerText(null))
        assertNull(ErrorConvertFactory.describeServerText(""))
        assertNull(ErrorConvertFactory.describeServerText("{}"))
        assertNull(ErrorConvertFactory.describeServerText("HTTP 410 Gone"))
    }

    @Test
    fun describeHttpCode_search410() {
        assertEquals(
            "没有符合条件的结果",
            ErrorConvertFactory.describeHttpCode(410, "https://bbs.nga.cn/search.php?key=test")
        )
    }

    @Test
    fun describeHttpCode_read410() {
        assertEquals(
            "内容不存在或已被删除",
            ErrorConvertFactory.describeHttpCode(410, "https://bbs.nga.cn/read.php?tid=123")
        )
    }

    @Test
    fun describeHttpCode_permission() {
        assertEquals(
            "没有权限，请检查登录状态",
            ErrorConvertFactory.describeHttpCode(403, "https://bbs.nga.cn/thread.php?fid=-7")
        )
    }

    @Test
    fun describeHttpCode_serverBusy() {
        assertEquals(
            "NGA 服务器暂时不可用，请稍后再试",
            ErrorConvertFactory.describeHttpCode(502, "https://bbs.nga.cn/thread.php?fid=-7")
        )
    }

    @Test
    fun describeHttpCode_unknown() {
        assertEquals("服务器返回异常（HTTP 418）", ErrorConvertFactory.describeHttpCode(418, null))
    }
}
