package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

/**
 * 帖子分页用的 Tab 指示器。
 *
 * <p>原实现继承自 com.nshmura:recyclertablayout（support 时代产物，已停止维护，
 * 无 androidx 版本）。RecyclerTabLayout 的核心能力其实只有两点：
 * 1. 每个 tab 宽度均分（由 tabOnScreenLimit 控制，>0 时等于「一屏放 N 个」）；
 * 2. 与 ViewPager 双向联动。
 * 这两点 Material 的 TabLayout 原生就支持：{@link #MODE_FIXED} 会让所有 tab 均分整条宽度，
 * {@link #MODE_SCROLLABLE} 则是自然宽度 + 可滚动。因此这里改为继承 TabLayout，
 * 对外保留原来的 setTabOnScreenLimit / notifyDataSetChanged / setUpWithViewPager 三个方法，
 * 调用方无需改动。
 */
public class TabLayoutEx extends TabLayout {

    private int mTabOnScreenLimit;

    private ViewPager mViewPager;

    public TabLayoutEx(Context context) {
        this(context, null);
    }

    public TabLayoutEx(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabLayoutEx(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyTabMode();
    }

    /**
     * 兼容 RecyclerTabLayout 时代的调用名，等价于 {@link #setupWithViewPager(ViewPager)}。
     */
    public void setUpWithViewPager(ViewPager viewPager) {
        setupWithViewPager(viewPager);
    }

    @Override
    public void setupWithViewPager(@Nullable ViewPager viewPager) {
        mViewPager = viewPager;
        applyTabMode();
        super.setupWithViewPager(viewPager);
        applyTabMode();
    }

    /**
     * 页码数量变化后刷新 tab 文案。TabLayout 的文案来源于 PagerAdapter.getPageTitle，
     * 重新绑定一次 pager 即可重建全部 tab。
     */
    public void notifyDataSetChanged() {
        if (mViewPager != null) {
            setupWithViewPager(mViewPager);
        } else {
            applyTabMode();
        }
    }

    /**
     * @param tabLimit 大于 0 表示一屏固定显示这么多 tab（宽度均分）；0 表示不限制，按内容宽度滚动。
     */
    public void setTabOnScreenLimit(int tabLimit) {
        mTabOnScreenLimit = tabLimit;
        applyTabMode();
    }

    private void applyTabMode() {
        setTabMode(mTabOnScreenLimit > 0 ? MODE_FIXED : MODE_SCROLLABLE);
    }
}
