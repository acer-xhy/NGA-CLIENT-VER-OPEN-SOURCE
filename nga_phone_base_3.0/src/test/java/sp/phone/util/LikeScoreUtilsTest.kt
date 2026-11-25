package sp.phone.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 点赞显示值换算的纯逻辑测试。
 * 用例来自服务端真实返回（2026-11-25 抓取）。
 */
class LikeScoreUtilsTest {

    @Test
    fun parseUserState_support() {
        assertEquals(LikeScoreUtils.STATE_SUPPORT, LikeScoreUtils.parseUserState("你对这个帖子表示支持"))
    }

    @Test
    fun parseUserState_oppose() {
        assertEquals(LikeScoreUtils.STATE_OPPOSE, LikeScoreUtils.parseUserState("你对这个帖子表示反对"))
    }

    @Test
    fun parseUserState_cancelSupport() {
        assertEquals(LikeScoreUtils.STATE_NONE, LikeScoreUtils.parseUserState("你取消了对这个帖子的支持"))
    }

    @Test
    fun parseUserState_cancelOppose() {
        assertEquals(LikeScoreUtils.STATE_NONE, LikeScoreUtils.parseUserState("你取消了对这个帖子的反对"))
    }

    @Test
    fun parseUserState_unknown() {
        assertEquals(LikeScoreUtils.STATE_UNKNOWN, LikeScoreUtils.parseUserState("操作成功"))
        assertEquals(LikeScoreUtils.STATE_UNKNOWN, LikeScoreUtils.parseUserState(null))
        assertEquals(LikeScoreUtils.STATE_UNKNOWN, LikeScoreUtils.parseUserState(""))
    }

    /**
     * 中立 -> 赞：服务端 data.1 = +1，赞数 +1
     */
    @Test
    fun delta_neutralToSupport() {
        assertEquals(1, LikeScoreUtils.resolveScoreDelta(1, "你对这个帖子表示支持"))
    }

    /**
     * 赞 -> 取消：data.1 = -1，赞数 -1
     */
    @Test
    fun delta_supportToNone() {
        assertEquals(-1, LikeScoreUtils.resolveScoreDelta(-1, "你取消了对这个帖子的支持"))
    }

    /**
     * 中立 -> 踩：data.1 = -1，但赞数不变（踩数不展示）
     */
    @Test
    fun delta_neutralToOppose() {
        assertEquals(0, LikeScoreUtils.resolveScoreDelta(-1, "你对这个帖子表示反对"))
    }

    /**
     * 赞 -> 踩：data.1 = -2，赞数只 -1（关键回归点）
     */
    @Test
    fun delta_supportToOppose() {
        assertEquals(-1, LikeScoreUtils.resolveScoreDelta(-2, "你对这个帖子表示反对"))
    }

    /**
     * 踩 -> 赞：data.1 = +2，赞数只 +1
     */
    @Test
    fun delta_opposeToSupport() {
        assertEquals(1, LikeScoreUtils.resolveScoreDelta(2, "你对这个帖子表示支持"))
    }

    /**
     * 踩 -> 取消：data.1 = +1，赞数不变
     */
    @Test
    fun delta_opposeToNone() {
        assertEquals(0, LikeScoreUtils.resolveScoreDelta(1, "你取消了对这个帖子的反对"))
    }

    /**
     * 文案无法识别时退回服务端变化量，保证操作有反馈
     */
    @Test
    fun delta_unknownMessage_fallbackToUserValue() {
        assertEquals(1, LikeScoreUtils.resolveScoreDelta(1, "操作成功"))
        assertEquals(-1, LikeScoreUtils.resolveScoreDelta(-1, ""))
    }
}
