package sp.phone.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Iterator;
import java.util.List;

import gov.anzong.androidnga.R;
import com.client.androidnga.core.data.model.ThreadPageInfo;
import sp.phone.theme.ThemeManager;
import sp.phone.util.StringUtils;

/**
 * Created by Justwen on 2018/3/23.
 */

public class ReplyListAdapter extends BaseAppendableAdapter<ThreadPageInfo, ReplyListAdapter.ViewHolder> {

    private boolean isNightMode;

    public ReplyListAdapter(Context context) {
        super(context);
        isNightMode = ThemeManager.getInstance().isNightMode();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(mLayoutInflater.inflate(R.layout.list_reply_ltem, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ThreadPageInfo pageInfo = getItem(position);
        ThreadPageInfo.ReplyInfo replyInfo = pageInfo.getReplyInfo();

        if (isNightMode) {
            holder.mActionTv.setTextColor(Color.parseColor("#FF128F80"));
            holder.mContentTv.setTextColor(Color.parseColor("#FF128F80"));
        }

        holder.mContentTv.setText(replyInfo.getContent());
        holder.mSubjectTv.setText(replyInfo.getSubject());
        holder.mPostDateTv.setText(StringUtils.timeStamp2Date2(replyInfo.getPostDate()));

        holder.itemView.setOnClickListener(mOnClickListener);
        holder.itemView.setTag(pageInfo);

    }


    @Override
    public void setData(List<ThreadPageInfo> dataList) {
        if (dataList == null) {
            super.setData(dataList);
        } else {
            checkData(dataList);
            super.appendData(dataList);
        }
    }

    private void checkData(List<ThreadPageInfo> dataList) {
        Iterator<ThreadPageInfo> iterator = dataList.iterator();
        while(iterator.hasNext()) {
            ThreadPageInfo pageInfo = iterator.next();
            if (pageInfo.getReplyInfo() == null) {
                iterator.remove();
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView mActionTv;

        public TextView mContentTv;

        public TextView mPostDateTv;

        public TextView mSubjectTv;

        public ViewHolder(View itemView) {
            super(itemView);
            mActionTv = itemView.findViewById(R.id.tv_action);
            mContentTv = itemView.findViewById(R.id.tv_content);
            mPostDateTv = itemView.findViewById(R.id.tv_time);
            mSubjectTv = itemView.findViewById(R.id.tv_topic);
        }
    }


}
