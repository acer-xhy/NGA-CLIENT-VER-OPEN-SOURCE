package sp.phone.util;

/**
 * 点赞/踩的显示值换算逻辑（纯 Java，便于单元测试，不依赖 Android 框架）。
 * <p>
 * 服务端 {@code nuke.php?__lib=topic_recommend} 成功响应形如：
 * {@code {"data":{"0":"你对这个帖子表示支持","1":2,"2":6}}}
 * 其中 {@code 1} 并不是赞数的变化，而是“操作人自身推荐状态”的变化量
 * （赞 +1、踩 -1、无 0 之间的差值），所以不能直接叠加到楼层赞数上。
 */
public final class LikeScoreUtils {

    /**
     * 无法识别文案时的状态标记
     */
    public static final int STATE_UNKNOWN = 99;

    /**
     * 未操作
     */
    public static final int STATE_NONE = 0;

    /**
     * 已赞
     */
    public static final int STATE_SUPPORT = 1;

    /**
     * 已踩
     */
    public static final int STATE_OPPOSE = -1;

    private LikeScoreUtils() {
    }

    /**
     * 解析服务端提示文案对应的推荐状态
     *
     * @param message 服务端 {@code data.0} 文案
     * @return {@link #STATE_SUPPORT}/{@link #STATE_OPPOSE}/{@link #STATE_NONE}，
     * 文案无法识别时返回 {@link #STATE_UNKNOWN}
     */
    public static int parseUserState(String message) {
        if (message == null || message.isEmpty()) {
            return STATE_UNKNOWN;
        }
        if (message.contains("取消")) {
            return STATE_NONE;
        }
        if (message.contains("反对")) {
            return STATE_OPPOSE;
        }
        if (message.contains("支持")) {
            return STATE_SUPPORT;
        }
        return STATE_UNKNOWN;
    }

    /**
     * 计算楼层显示赞数应变化多少
     *
     * @param userValue 服务端 {@code data.1}，本次自身推荐状态的变化量
     * @param message   服务端 {@code data.0} 文案
     * @return 显示赞数的增量（-1、0、+1）
     */
    public static int resolveScoreDelta(int userValue, String message) {
        int newState = parseUserState(message);
        if (newState == STATE_UNKNOWN) {
            // 文案不认识时退回服务端变化量，至少保证操作有反馈
            return userValue;
        }
        int oldState = newState - userValue;
        boolean wasSupported = oldState == STATE_SUPPORT;
        boolean isSupported = newState == STATE_SUPPORT;
        return (isSupported ? 1 : 0) - (wasSupported ? 1 : 0);
    }
}
