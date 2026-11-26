package sp.phone.view.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import gov.anzong.androidnga.R;

/**
 * 浮动操作按钮。原实现来自 com.getbase:floatingactionbutton（support 时代，已停止维护），
 * 这里换成 Material FAB + 一个 {@code app:fab_title} 属性，其余能力（大小、图标、点击）沿用 Material。
 */
public class FloatingActionButton extends com.google.android.material.floatingactionbutton.FloatingActionButton {

    private CharSequence mTitle;

    public FloatingActionButton(@NonNull Context context) {
        this(context, null);
    }

    public FloatingActionButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.floatingActionButtonStyle);
    }

    public FloatingActionButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSize(SIZE_MINI);
        if (attrs != null) {
            android.content.res.TypedArray ta =
                    context.obtainStyledAttributes(attrs, R.styleable.FloatingActionButton);
            mTitle = ta.getText(R.styleable.FloatingActionButton_fab_title);
            ta.recycle();
        }
    }

    /** 展开时显示在按钮旁边的文字 */
    @Nullable
    public CharSequence getTitle() {
        return mTitle;
    }
}
