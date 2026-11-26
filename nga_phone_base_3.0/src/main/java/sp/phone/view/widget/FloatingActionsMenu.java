package sp.phone.view.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import gov.anzong.androidnga.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 可展开的浮动操作菜单。
 *
 * <p>原实现是 com.getbase:floatingactionbutton（项目内 libs/floatingactionmenu.aar）。
 * 该库内部直接继承 {@code android.support.design.widget.CoordinatorLayout}，
 * 是关闭 Jetifier 的最后一块硬骨头。这里用 Material FAB 重写同样的交互：
 * 一个常驻的“加号”按钮 + 若干向上堆叠的操作按钮，点击加号展开/收起，滚动时由
 * {@code ScrollAwareFamBehavior} 整体移出屏幕。
 *
 * <p>只保留业务实际用到的能力：向上展开（EXPAND_UP）、标签在左/右两侧、collapse/expand/toggle。
 */
public class FloatingActionsMenu extends FrameLayout {

    public static final int EXPAND_UP = 0;
    public static final int EXPAND_DOWN = 1;
    public static final int EXPAND_LEFT = 2;
    public static final int EXPAND_RIGHT = 3;

    public static final int LABELS_ON_LEFT_SIDE = 0;
    public static final int LABELS_ON_RIGHT_SIDE = 1;

    private final FloatingActionButton mAddButton;

    private final List<FloatingActionButton> mActions = new ArrayList<>();

    private final List<TextView> mActionLabels = new ArrayList<>();

    private boolean mExpanded;

    private int mExpandDirection = EXPAND_UP;

    private int mLabelsPosition = LABELS_ON_LEFT_SIDE;

    private final int mGap;

    private final int mAddButtonSize;

    private final int mActionSize;

    public FloatingActionsMenu(Context context) {
        this(context, null);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mGap = dp(8);
        mAddButtonSize = dp(56);
        mActionSize = dp(40);
        setClipChildren(false);
        setClipToPadding(false);

        mAddButton = new FloatingActionButton(context);
        mAddButton.setSize(FloatingActionButton.SIZE_NORMAL);
        mAddButton.setImageResource(R.drawable.btn_ic_add);
        mAddButton.setContentDescription(getResources().getString(R.string.reply_thread));
        mAddButton.setOnClickListener(v -> toggle());
        super.addView(mAddButton);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (mAddButton != null && child != mAddButton && child instanceof FloatingActionButton) {
            FloatingActionButton action = (FloatingActionButton) child;
            TextView label = createLabel(action.getTitle());
            mActions.add(action);
            mActionLabels.add(label);
            super.addView(label, -1, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            super.addView(action, -1, new LayoutParams(mActionSize, mActionSize));
            action.setVisibility(INVISIBLE);
            return;
        }
        super.addView(child, index, params);
    }

    private TextView createLabel(CharSequence text) {
        TextView label = new TextView(getContext());
        label.setText(text == null ? "" : text);
        label.setTextSize(12);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC000000);
        background.setCornerRadius(dp(4));
        label.setBackground(background);
        label.setVisibility(INVISIBLE);
        return label;
    }

    public FloatingActionButton getAddFloatingActionButton() {
        return mAddButton;
    }

    /** 兼容原库：行为类用它计算移出屏幕的距离，本实现没有额外底部间距 */
    public int getAddButtonMarginBottom() {
        return 0;
    }

    public void setExpandDirection(int expandDirection, int labelsPosition) {
        // 业务只用到向上展开；其余方向保留常量以免调用方报错
        mExpandDirection = expandDirection;
        mLabelsPosition = labelsPosition;
        requestLayout();
        invalidate();
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void toggle() {
        if (mExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    public void expand() {
        if (mExpanded || mActions.isEmpty()) {
            return;
        }
        mExpanded = true;
        for (int i = 0; i < mActions.size(); i++) {
            FloatingActionButton action = mActions.get(i);
            TextView label = mActionLabels.get(i);
            action.setVisibility(VISIBLE);
            label.setVisibility(VISIBLE);
            action.setAlpha(0f);
            label.setAlpha(0f);
            float offset = mActionSize / 2f;
            action.setTranslationY(offset);
            label.setTranslationY(offset);
            ViewCompat.animate(action).alpha(1f).translationY(0f).setDuration(180).start();
            ViewCompat.animate(label).alpha(1f).translationY(0f).setDuration(220).start();
        }
    }

    public void collapse() {
        if (!mExpanded || mActions.isEmpty()) {
            return;
        }
        mExpanded = false;
        for (int i = 0; i < mActions.size(); i++) {
            final FloatingActionButton action = mActions.get(i);
            final TextView label = mActionLabels.get(i);
            float offset = mActionSize / 2f;
            ViewCompat.animate(action).alpha(0f).translationY(offset).setDuration(180)
                    .withEndAction(() -> action.setVisibility(INVISIBLE)).start();
            ViewCompat.animate(label).alpha(0f).translationY(offset).setDuration(180)
                    .withEndAction(() -> label.setVisibility(INVISIBLE)).start();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = mActions.size();
        int contentHeight = mAddButtonSize + count * (mActionSize + mGap)
                + getPaddingTop() + getPaddingBottom();

        int maxLabelWidth = 0;
        int labelHeight = 0;
        for (int i = 0; i < count; i++) {
            TextView label = mActionLabels.get(i);
            label.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            maxLabelWidth = Math.max(maxLabelWidth, label.getMeasuredWidth());
            labelHeight = Math.max(labelHeight, label.getMeasuredHeight());
        }

        int contentWidth = mAddButtonSize + (maxLabelWidth > 0 ? maxLabelWidth + mGap : 0)
                + getPaddingLeft() + getPaddingRight();
        if (labelHeight > mActionSize) {
            contentHeight += (labelHeight - mActionSize) * count;
        }

        mAddButton.measure(MeasureSpec.makeMeasureSpec(mAddButtonSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mAddButtonSize, MeasureSpec.EXACTLY));
        for (int i = 0; i < count; i++) {
            mActions.get(i).measure(MeasureSpec.makeMeasureSpec(mActionSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mActionSize, MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(
                resolveSize(contentWidth, widthMeasureSpec),
                resolveSize(contentHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int addLeft = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? width - getPaddingRight() - mAddButtonSize
                : getPaddingLeft();
        int addTop = height - getPaddingBottom() - mAddButtonSize;
        mAddButton.layout(addLeft, addTop, addLeft + mAddButtonSize, addTop + mAddButtonSize);

        int count = mActions.size();
        for (int i = 0; i < count; i++) {
            FloatingActionButton action = mActions.get(i);
            TextView label = mActionLabels.get(i);
            int actionTop = addTop - (i + 1) * (mActionSize + mGap);
            int actionLeft = addLeft + (mAddButtonSize - mActionSize) / 2;
            action.layout(actionLeft, actionTop, actionLeft + mActionSize, actionTop + mActionSize);

            int labelWidth = label.getMeasuredWidth();
            int labelHeight = label.getMeasuredHeight();
            int labelTop = actionTop + (mActionSize - labelHeight) / 2;
            if (mLabelsPosition == LABELS_ON_LEFT_SIDE) {
                int labelRight = actionLeft - mGap;
                label.layout(labelRight - labelWidth, labelTop, labelRight, labelTop + labelHeight);
            } else {
                int labelLeft = actionLeft + mActionSize + mGap;
                label.layout(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        if (mExpanded && event.getActionMasked() == MotionEvent.ACTION_UP) {
            for (FloatingActionButton action : mActions) {
                if (isInside(action, event)) {
                    // 点中操作按钮后自动收起，和原库行为一致；延后一帧避免吞掉 onClick
                    post(this::collapse);
                    break;
                }
            }
        }
        return handled;
    }

    private boolean isInside(View view, MotionEvent event) {
        return event.getX() >= view.getLeft() && event.getX() <= view.getRight()
                && event.getY() >= view.getTop() && event.getY() <= view.getBottom();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
