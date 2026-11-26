package sp.phone.view.behavior;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListener;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import sp.phone.view.widget.FloatingActionsMenu;

/**
 * 列表滚动时自动收起 / 移出浮动操作菜单，停止滚动时恢复。
 *
 * <p>原实现继承 com.getbase:floatingactionbutton 自带的 Behavior（依赖 support design 库）。
 * 这里直接继承 {@link CoordinatorLayout.Behavior}，回调语义与原来一致，
 * 调用方（ArticleTabFragment / TopicListFragment）用到的 animateIn / animateOut 保留。
 */
public class ScrollAwareFamBehavior extends CoordinatorLayout.Behavior<FloatingActionsMenu> {

    private static final Interpolator INTERPOLATOR = new FastOutSlowInInterpolator();

    private static final int SCROLL_AXIS_VERTICAL = 2;

    private boolean mIsAnimatingOut = false;

    private boolean mIsAnimationIn = false;

    private boolean mIsShown = true;

    public ScrollAwareFamBehavior() {
        super();
    }

    public ScrollAwareFamBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                       @NonNull FloatingActionsMenu child,
                                       @NonNull View directTargetChild, @NonNull View target,
                                       int nestedScrollAxes) {
        return nestedScrollAxes == SCROLL_AXIS_VERTICAL;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                       @NonNull FloatingActionsMenu child,
                                       @NonNull View directTargetChild, @NonNull View target,
                                       int axes, int type) {
        return nestedScrollAxes(axes);
    }

    private boolean nestedScrollAxes(int axes) {
        return axes == SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                               @NonNull FloatingActionsMenu child, @NonNull View target,
                               int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        handleScroll(child, dyConsumed);
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                               @NonNull FloatingActionsMenu child, @NonNull View target,
                               int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed,
                               int type) {
        handleScroll(child, dyConsumed);
    }

    private void handleScroll(FloatingActionsMenu child, int dyConsumed) {
        if (dyConsumed > 10 && !mIsAnimatingOut && mIsShown) {
            child.collapse();
            animateOut(child);
        } else if (dyConsumed < -10 && !mIsAnimationIn && !mIsShown) {
            animateIn(child);
        }
    }

    public void animateOut(@NonNull FloatingActionsMenu button) {
        if (mIsAnimatingOut || !mIsShown) {
            return;
        }
        int height = button.getAddFloatingActionButton().getHeight() + button.getAddButtonMarginBottom();
        ViewCompat.animate(button).translationY((float) height).setInterpolator(INTERPOLATOR)
                .setListener(new ViewPropertyAnimatorListener() {
                    @Override
                    public void onAnimationStart(@NonNull View view) {
                        mIsAnimatingOut = true;
                    }

                    @Override
                    public void onAnimationCancel(@NonNull View view) {
                        mIsAnimatingOut = false;
                    }

                    @Override
                    public void onAnimationEnd(@NonNull View view) {
                        mIsAnimatingOut = false;
                        mIsShown = false;
                    }
                }).start();
    }

    public void animateIn(@Nullable FloatingActionsMenu button) {
        if (button == null || mIsAnimationIn || mIsShown) {
            return;
        }
        button.setVisibility(View.VISIBLE);
        ViewCompat.animate(button).translationY(0.0F).setInterpolator(INTERPOLATOR)
                .setListener(new ViewPropertyAnimatorListener() {
                    @Override
                    public void onAnimationStart(@NonNull View view) {
                        mIsAnimationIn = true;
                    }

                    @Override
                    public void onAnimationEnd(@NonNull View view) {
                        mIsShown = true;
                        mIsAnimationIn = false;
                    }

                    @Override
                    public void onAnimationCancel(@NonNull View view) {
                        mIsAnimationIn = false;
                    }
                }).start();
    }
}
