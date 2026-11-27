package gov.anzong.androidnga.activity;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.justwen.androidnga.base.activity.ARouterConstants;
import com.justwen.androidnga.ui.fragment.WebViewFragment;

import gov.anzong.androidnga.BuildConfig;
import gov.anzong.androidnga.R;
import sp.phone.common.UpdateChecker;
import sp.phone.common.UpdateDownloader;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.fragment.dialog.VersionUpgradeDialogFragment;
import sp.phone.util.ARouterUtils;
import sp.phone.util.FunctionUtils;
import gov.anzong.androidnga.util.ToastUtils;

public class AboutActivity extends MaterialAboutActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.getInstance().applyAboutTheme(this);
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    /**
     * 库自带的布局只有 RecyclerView，而关于页主题是 NoActionBar，
     * 所以这里自己往 CoordinatorLayout 顶部补一个标题栏和返回按钮。
     */
    private void setupActionBar() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }
        View root = content.getChildAt(0);
        if (!(root instanceof CoordinatorLayout)) {
            return;
        }

        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) root;
        AppBarLayout appBarLayout = (AppBarLayout) getLayoutInflater()
                .inflate(R.layout.about_appbar, coordinatorLayout, false);
        Toolbar toolbar = appBarLayout.findViewById(R.id.toolbar);
        CharSequence title = getActivityTitle();
        toolbar.setTitle(title != null ? title : getString(R.string.title_about));
        coordinatorLayout.addView(appBarLayout, 0);

        RecyclerView recyclerView = findViewById(com.danielstone.materialaboutlibrary.R.id.mal_recyclerview);
        if (recyclerView != null) {
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) recyclerView.getLayoutParams();
            params.setBehavior(new AppBarLayout.ScrollingViewBehavior());
            recyclerView.setLayoutParams(params);
            final int originalBottomPadding = recyclerView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(coordinatorLayout, (view, windowInsets) -> {
                Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                appBarLayout.setPadding(0, statusBars.top, 0, 0);
                recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(), originalBottomPadding + navigationBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDownloader.tryResumePendingInstall(this);
    }

    @NonNull
    @Override
    protected MaterialAboutList getMaterialAboutList(@NonNull Context context) {
        return new MaterialAboutList(buildAppCard(), buildDevelopCard(), buildExtraCard());
    }

    private MaterialAboutCard buildAppCard() {
        MaterialAboutCard.Builder builder = new MaterialAboutCard.Builder();
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.start_title)
                .icon(R.mipmap.ic_launcher)
                .setOnClickAction(() -> new VersionUpgradeDialogFragment().show(getSupportFragmentManager(), null))
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("版本")
                .subText(BuildConfig.VERSION_NAME)
                .icon(R.drawable.ic_about)
                .setOnClickAction(() -> {
                    try {
                        String url = "market://details?id=" + getPackageName();
                        Intent intent = new Intent("android.intent.action.VIEW");
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        //FunctionUtils.openUrlByDefaultBrowser(AboutActivity.this, "https://www.coolapk.com/apk/gov.anzong.androidnga");
                    }
                })
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("License")
                .subText("GNU GPL v2,开放源代码许可")
                .setOnClickAction(() -> {
                    ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                            .withString("url", "file:///android_asset/OSLICENSE.TXT")
                            .withString("fragment", WebViewFragment.class.getName())
                            .navigation(this);
                })
                .icon(R.drawable.ic_license)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("检测更新")
                .setOnClickAction(() -> UpdateChecker.check(this, true))
                .icon(R.drawable.ic_update_24dp)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("清理更新缓存")
                .subText("当前线路：" + UpdateChecker.endpointLabel())
                .setOnClickAction(() -> {
                    UpdateChecker.clearCache(this);
                    ToastUtils.showToast("已清理更新缓存，线路记忆已重置");
                    recreate();
                })
                .icon(R.drawable.ic_clear_accent_24dp)
                .build());

        return builder.build();
    }

    private MaterialAboutCard buildDevelopCard() {
        MaterialAboutCard.Builder builder = new MaterialAboutCard.Builder();
        builder.title("开发团队");
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("代码")
                .subText("[@竹井詩織里]/[@cfan8]/[@jjimmys]\n[@Moandor]/[@Elrond]/[@Justwen]/[@xhy]")
                .setOnLongClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        ARouterUtils.navigation(ARouterConstants.ACTIVITY_DEBUG);
                    }
                })
                .icon(R.drawable.ic_code)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("美工")
                .subText("[@那个惩戒骑]/[@从来不卖萌]")
                .icon(R.drawable.ic_color_lens)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("Github")
                .subText("bug & 建议")
                .setOnClickAction(() -> FunctionUtils.openUrlByDefaultBrowser(AboutActivity.this, "https://github.com/acer-xhy/NGA-CLIENT-VER-OPEN-SOURCE/issues"))
                .icon(R.drawable.ic_github)
                .build());

        return builder.build();
    }


    private MaterialAboutCard buildExtraCard() {
        MaterialAboutCard.Builder builder = new MaterialAboutCard.Builder();
        builder.title("赞美片总!感谢[@force0119]");
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("客户端吐槽QQ群,欢迎加入捡肥皂")
                .subText("1065310118")
                .setOnClickAction(() -> FunctionUtils.copyToClipboard(AboutActivity.this, "1065310118"))
                .icon(R.drawable.ic_qq)
                .build());
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("客户端问题反馈群，请勿开车！")
                .subText("1077054628")
                .setOnClickAction(() -> FunctionUtils.copyToClipboard(AboutActivity.this, "1077054628"))
                .icon(R.drawable.ic_qq)
                .build());

        return builder.build();
    }

    @Nullable
    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.title_about);
    }
}
