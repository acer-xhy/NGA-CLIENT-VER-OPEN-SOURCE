package sp.phone.task

import com.alibaba.fastjson2.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import sp.phone.util.LikeScoreUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Properties

/**
 * 点赞接口的登录态契约测试（会访问真实服务端）。
 * <p>
 * 登录态临时保存在 {@code src/test/resources/nga_test_account.properties}（已加入 .gitignore，不入库）：
 * <pre>
 * uid=你的数字 uid
 * cid=你的 ngaPassportCid
 * tid=测试帖子 id
 * pid=测试楼层 id（0 楼为 0）
 * </pre>
 * 文件不存在时测试自动跳过，不影响其它单元测试。
 * <p>
 * 测试会执行「点赞 -> 再点赞（还原）」两步，并在每一步前后读取帖子 JSON 对比赞数，
 * 结束时把楼层状态还原成测试前的样子。
 */
class LikeContractLiveTest {

    private val host = "https://bbs.nga.cn"

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 16; V2520A Build/BP2A.250605.031.A3_V000L1; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.135 Mobile Safari/537.36"

    private val accountFile: File
        get() {
            val moduleDir = File(System.getProperty("user.dir"))
            val candidates = listOf(
                File(moduleDir, "src/test/resources/nga_test_account.properties"),
                File(moduleDir, "nga_phone_base_3.0/src/test/resources/nga_test_account.properties"),
                File("src/test/resources/nga_test_account.properties")
            )
            return candidates.firstOrNull { it.isFile } ?: candidates.first()
        }

    private fun loadAccount(): Properties {
        val props = Properties()
        accountFile.inputStream().use { props.load(it) }
        return props
    }

    private fun cookieOf(props: Properties): String {
        return "ngaPassportUid=${props.getProperty("uid")}; ngaPassportCid=${props.getProperty("cid")}"
    }

    private fun request(urlStr: String, method: String, cookie: String, form: String? = null): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("X-User-Agent", "Nga_Official")
            conn.setRequestProperty("Referer", "https://bbs.nga.cn/read.php")
            if (form != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(form.toByteArray()) }
            }
            val bytes = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.readBytes() ?: ByteArray(0)
            return String(bytes, Charset.forName("GBK"))
        } finally {
            conn.disconnect()
        }
    }

    private fun postLike(cookie: String, tid: Int, pid: Int, value: Int): String {
        val form = "__lib=topic_recommend&__act=add&raw=3&pid=$pid&tid=$tid&value=$value&__output=8"
        return request("$host/nuke.php", "POST", cookie, form)
    }

    private fun fetchThread(cookie: String, tid: Int): String {
        return request("$host/read.php?tid=$tid&page=1&__output=8", "GET", cookie)
    }

    /**
     * 读取指定楼层的赞数
     */
    private fun scoreOf(threadJson: String, pid: Int): Int {
        val root = JSON.parseObject(threadJson)
        val rows = root.getJSONObject("data").getJSONObject("__R")
        return rows.getJSONObject(pid.toString()).getIntValue("score")
    }

    /**
     * 解析点赞响应：0=文案，1=自身推荐状态变化量
     */
    private fun likeResult(body: String): Pair<String, Int>? {
        val root = JSON.parseObject(body) ?: return null
        val data = root.getJSONObject("data") ?: return null
        return data.getString("0") to data.getIntValue("1")
    }

    @Test
    fun likeScoreDelta_matchesServerScoreChange() {
        assumeTrue("未找到登录态文件 ${accountFile.absolutePath}，跳过实网测试", accountFile.isFile)
        val props = loadAccount()
        val uid = props.getProperty("uid")?.toIntOrNull()
        val tid = props.getProperty("tid")?.toIntOrNull()
        val pid = props.getProperty("pid")?.toIntOrNull() ?: 0
        assumeTrue("登录态文件缺少 uid/tid", uid != null && tid != null)
        val cookie = cookieOf(props)

        val beforeJson = fetchThread(cookie, tid!!)
        val currentUid = JSON.parseObject(beforeJson).getJSONObject("data")
            .getJSONObject("__CU").getIntValue("uid")
        assertTrue("Cookie 已失效，抓到的 uid 与配置不一致", currentUid == uid)

        val scoreBefore = scoreOf(beforeJson, pid)

        // 第一步：点赞
        val firstBody = postLike(cookie, tid, pid, 1)
        val first = likeResult(firstBody)
        assumeTrue("服务端拒绝：$firstBody（可能未签到或该楼层不可操作）", first != null)
        val (firstMessage, firstValue) = first!!
        assertEquals(
            "服务端文案与预期不符：$firstMessage",
            LikeScoreUtils.resolveScoreDelta(firstValue, firstMessage),
            scoreOf(fetchThread(cookie, tid), pid) - scoreBefore
        )

        // 反推操作前的状态：旧状态 = 新状态 - 变化量
        val stateAfterLike = LikeScoreUtils.parseUserState(firstMessage)
        val stateBefore = stateAfterLike - firstValue

        // 第二步：按旧状态选择能还原的操作
        // 旧状态为“赞/踩”时发对应指令，旧状态为“无”时再发一次当前指令（开关式取消）
        val restoreValue = when (stateBefore) {
            LikeScoreUtils.STATE_SUPPORT -> 1
            LikeScoreUtils.STATE_OPPOSE -> -1
            else -> if (stateAfterLike == LikeScoreUtils.STATE_SUPPORT) 1 else -1
        }
        val secondBody = postLike(cookie, tid, pid, restoreValue)
        val second = likeResult(secondBody)
        assumeTrue("服务端拒绝第二次操作：$secondBody", second != null)
        val (secondMessage, _) = second!!

        assertEquals(
            "还原操作没有回到测试前状态：$secondMessage",
            stateBefore,
            LikeScoreUtils.parseUserState(secondMessage)
        )
        assertEquals(
            "还原后赞数应回到测试前",
            scoreBefore,
            scoreOf(fetchThread(cookie, tid), pid)
        )
    }
}
