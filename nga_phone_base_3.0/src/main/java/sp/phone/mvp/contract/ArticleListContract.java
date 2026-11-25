package sp.phone.mvp.contract;

import android.content.Intent;
import android.os.Bundle;

import java.util.Map;

import gov.anzong.androidnga.http.OnHttpCallBack;
import com.client.androidnga.core.data.model.ThreadInfo;
import com.client.androidnga.core.data.model.ThreadPostInfo;

import sp.phone.param.ArticleListParam;

/**
 *
 * @author Justwen
 * @date 2017/11/22
 */

public interface ArticleListContract {

    interface Presenter {

        void loadPage(ArticleListParam param);

        void banThisSB(ThreadPostInfo row);

        void postComment(ArticleListParam param, ThreadPostInfo row);

        void postSupportTask(int tid, int pid);

        void postOpposeTask(int tid, int pid);

        void quote(ArticleListParam param, ThreadPostInfo row);

        void cachePage();

        void loadCachePage();
    }

    interface View {

        void setRefreshing(boolean refreshing);

        boolean isRefreshing();

        void hideLoadingView();

        void setData(ThreadInfo data);

        void startPostActivity(Intent intent);

        void showPostCommentDialog(String prefix, Bundle bundle);

        /**
         * 点赞/踩成功后刷新对应楼层点赞数
         *
         * @param pid          楼层 id
         * @param userValue    服务端返回的“自己推荐状态的变化量”（+1 赞、-1 踩或取消、-2 赞转踩等）
         * @param message      服务端返回的提示文案，用于判断本次操作后的状态
         */
        void onLikeResult(int pid, int userValue, String message);

    }

    interface Model {

        void loadPage(ArticleListParam param, OnHttpCallBack<ThreadInfo> callBack);

        void loadPage(ArticleListParam param, Map<String, String> header, OnHttpCallBack<ThreadInfo> callBack);

        void cachePage(ArticleListParam param, String rawData);

        void loadCachePage(ArticleListParam param, OnHttpCallBack<ThreadInfo> callBack);
    }
}

