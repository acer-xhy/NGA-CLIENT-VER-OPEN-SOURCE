package sp.phone.view.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.R;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * 下拉刷新控件。
 *
 * <p>androidx 的 {@link SwipeRefreshLayout} 如果不显式设置颜色，会使用
 * {@code CircularProgressDrawable} 里硬编码的纯黑（0xFF000000），和 App 的主题色无关。
 * 这里在构造时把转圈颜色解析成当前主题的 colorPrimary，保证三套主题（棕/绿/黑）
 * 以及夜间模式切换后都跟着变。
 */
public class NgaSwipeRefreshLayout extends SwipeRefreshLayout {

    public NgaSwipeRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public NgaSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setColorSchemeColors(resolveThemeColor(R.attr.colorPrimary, Color.BLACK));
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(getContext(), value.resourceId);
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }
}
