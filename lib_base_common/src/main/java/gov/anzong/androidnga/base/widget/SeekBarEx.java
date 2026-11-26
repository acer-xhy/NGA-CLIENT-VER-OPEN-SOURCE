package gov.anzong.androidnga.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import gov.anzong.androidnga.common.R;

/**
 * 设置页用的尺寸调节条。
 *
 * <p>原实现继承自 com.zhouyou:signseekbar（support 时代产物，已停止维护，且旧实现靠反射去改
 * 私有字段 mThumbRadiusOnDragging）。这里按原控件的绘制语义重新实现：
 * 底轨 + 已选进度轨 + 圆点滑块 + 拖拽时跟随滑块的数值气泡 + 可选的分段刻度。
 * 只依赖 androidx.appcompat，颜色/尺寸全部走 XML 属性。
 *
 * <p>对外保留原来的 {@code getConfigBuilder().max().min().progress().sectionCount().build()}
 * 与 {@link OnProgressChangedListener} 回调形态，设置页的调用代码只需换 import。
 */
public class SeekBarEx extends AppCompatSeekBar {

    public interface OnProgressChangedListener {

        void onProgressChanged(SeekBarEx seekBar, int progress, float progressFloat, boolean fromUser);

        void getProgressOnActionUp(SeekBarEx seekBar, int progress, float progressFloat);

        void getProgressOnFinally(SeekBarEx seekBar, int progress, float progressFloat, boolean fromUser);
    }

    private static final int DEFAULT_TRACK_COLOR = 0xFFDCDCDC;
    private static final int DEFAULT_THUMB_COLOR = 0xFFFFFFFF;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSignTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTrackRect = new RectF();
    private final RectF mSignRect = new RectF();

    private int mMin;

    private int mMax = 100;

    private int mSectionCount;

    private float mTrackSize;

    private float mSecondTrackSize;

    private int mTrackColor = DEFAULT_TRACK_COLOR;

    private int mSecondTrackColor;

    private int mThumbColor = DEFAULT_THUMB_COLOR;

    private float mThumbRadius;

    private float mThumbRadiusOnDragging;

    private boolean mShowSign = true;

    private int mSignColor;

    private int mSignTextColor = 0xFFFFFFFF;

    private float mSignTextSize;

    private boolean mDragging;

    private float mProgressScale;

    private OnProgressChangedListener mOnProgressChangedListener;

    public SeekBarEx(Context context) {
        this(context, null);
    }

    public SeekBarEx(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.seekBarStyle);
    }

    public SeekBarEx(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final float density = getResources().getDisplayMetrics().density;
        mTrackSize = 3 * density;
        mSecondTrackSize = 3 * density;
        mThumbRadius = 8 * density;
        mThumbRadiusOnDragging = 10 * density;
        mSignTextSize = 12 * getResources().getDisplayMetrics().scaledDensity;
        mSecondTrackColor = 0xFF3F51B5;
        mSignColor = 0xFF3F51B5;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarEx, defStyleAttr, 0);
            mTrackColor = a.getColor(R.styleable.SeekBarEx_ssb_track_color, mTrackColor);
            mTrackSize = a.getDimension(R.styleable.SeekBarEx_ssb_track_size, mTrackSize);
            mSecondTrackColor = a.getColor(R.styleable.SeekBarEx_ssb_second_track_color, mSecondTrackColor);
            mSecondTrackSize = a.getDimension(R.styleable.SeekBarEx_ssb_second_track_size, mSecondTrackSize);
            mThumbColor = a.getColor(R.styleable.SeekBarEx_ssb_thumb_color, mThumbColor);
            mThumbRadius = a.getDimension(R.styleable.SeekBarEx_ssb_thumb_radius, mThumbRadius);
            mThumbRadiusOnDragging = a.getDimension(R.styleable.SeekBarEx_ssb_thumb_radius_on_dragging, mThumbRadiusOnDragging);
            mShowSign = a.getBoolean(R.styleable.SeekBarEx_ssb_show_sign, mShowSign);
            mSignColor = a.getColor(R.styleable.SeekBarEx_ssb_sign_color, mSignColor);
            mSignTextColor = a.getColor(R.styleable.SeekBarEx_ssb_sign_text_color, mSignTextColor);
            mSignTextSize = a.getDimension(R.styleable.SeekBarEx_ssb_sign_text_size, mSignTextSize);
            a.recycle();
        }

        mTrackPaint.setColor(mTrackColor);
        mProgressPaint.setColor(mSecondTrackColor);
        mThumbPaint.setColor(mThumbColor);
        mThumbStrokePaint.setStyle(Paint.Style.STROKE);
        mThumbStrokePaint.setColor(mSecondTrackColor);
        mThumbStrokePaint.setStrokeWidth(2 * density);
        mSignPaint.setColor(mSignColor);
        mSignTextPaint.setColor(mSignTextColor);
        mSignTextPaint.setTextSize(mSignTextSize);
        mSignTextPaint.setTextAlign(Paint.Align.CENTER);
        mSectionPaint.setColor(mTrackColor);
        mSectionPaint.setStrokeWidth(density);

        // 轨道与滑块全部自绘（onDraw 不调用 super），这里只关掉系统 track 的分裂显示
        setSplitTrack(false);
        super.setOnSeekBarChangeListener(mInternalListener);
    }

    public ConfigBuilder getConfigBuilder() {
        return new ConfigBuilder(this);
    }

    public void setOnProgressChangedListener(OnProgressChangedListener listener) {
        mOnProgressChangedListener = listener;
    }

    /**
     * 当前真实值（考虑了 min 偏移）。
     */
    public int getValue() {
        return mMin + super.getProgress();
    }

    public void setValue(int value) {
        super.setProgress(clamp(value) - mMin);
    }

    @Override
    public synchronized void setMax(int max) {
        mMax = max;
        applyRange();
    }

    private void applyRange() {
        int span = Math.max(1, mMax - mMin);
        super.setMax(span);
        setKeyProgressIncrement(1);
        if (super.getProgress() > span) {
            super.setProgress(span);
        }
        invalidate();
    }

    private int clamp(int value) {
        if (value < mMin) {
            return mMin;
        }
        return Math.min(value, mMax);
    }

    private float signHeight() {
        return mShowSign ? mSignTextSize * 1.6f + 6 * getResources().getDisplayMetrics().density : 0;
    }

    private float thumbRadiusNow() {
        return mDragging ? mThumbRadiusOnDragging : mThumbRadius;
    }

    private float contentPadding() {
        return Math.max(mThumbRadiusOnDragging, Math.max(mTrackSize, mSecondTrackSize) / 2f);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = (int) (200 * getResources().getDisplayMetrics().density);
        }
        int desiredHeight = (int) (getPaddingTop() + getPaddingBottom() + signHeight() + contentPadding() * 2);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : Math.max(desiredHeight, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        final float left = getPaddingLeft() + contentPadding();
        final float right = getWidth() - getPaddingRight() - contentPadding();
        if (right <= left) {
            return;
        }

        final float centerY = getHeight() - getPaddingBottom() - contentPadding();
        final int span = Math.max(1, mMax - mMin);
        final float thumbX = left + (right - left) * (super.getProgress() / (float) span);

        // 底轨
        mTrackRect.set(left, centerY - mTrackSize / 2f, right, centerY + mTrackSize / 2f);
        canvas.drawRoundRect(mTrackRect, mTrackSize / 2f, mTrackSize / 2f, mTrackPaint);

        // 已选进度轨
        if (thumbX > left) {
            mTrackRect.set(left, centerY - mSecondTrackSize / 2f, thumbX, centerY + mSecondTrackSize / 2f);
            canvas.drawRoundRect(mTrackRect, mSecondTrackSize / 2f, mSecondTrackSize / 2f, mProgressPaint);
        }

        // 分段刻度
        if (mSectionCount > 0) {
            for (int i = 0; i <= mSectionCount; i++) {
                float x = left + (right - left) * i / (float) mSectionCount;
                float half = Math.max(mTrackSize, mSecondTrackSize) / 2f + 2 * getResources().getDisplayMetrics().density;
                canvas.drawLine(x, centerY - half, x, centerY + half, mSectionPaint);
            }
        }

        // 滑块
        float radius = thumbRadiusNow();
        canvas.drawCircle(thumbX, centerY, radius, mThumbPaint);
        canvas.drawCircle(thumbX, centerY, radius, mThumbStrokePaint);

        // 拖拽气泡
        if (mShowSign && mDragging) {
            drawSign(canvas, thumbX, centerY - radius - signHeight() / 2f);
        }
    }

    private void drawSign(Canvas canvas, float anchorX, float centerY) {
        String text = String.valueOf(getValue());
        float textWidth = mSignTextPaint.measureText(text);
        float density = getResources().getDisplayMetrics().density;
        float halfHeight = mSignTextSize * 0.8f + 3 * density;
        float halfWidth = Math.max(textWidth / 2f + 10 * density, halfHeight);

        float signCenterX = Math.max(getPaddingLeft() + halfWidth, Math.min(anchorX, getWidth() - getPaddingRight() - halfWidth));

        mSignRect.set(signCenterX - halfWidth, centerY - halfHeight, signCenterX + halfWidth, centerY + halfHeight);
        canvas.drawRoundRect(mSignRect, halfHeight, halfHeight, mSignPaint);

        Paint.FontMetrics fm = mSignTextPaint.getFontMetrics();
        float baseline = centerY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, signCenterX, baseline, mSignTextPaint);
    }

    /**
     * 进度/拖拽事件统一从系统 OnSeekBarChangeListener 转出来。
     * SDK 37 里 AbsSeekBar 的 onProgressRefresh / onStartTrackingTouch 已不再是可覆写的
     * protected 方法，只能走监听器这一层。
     */
    private final SeekBar.OnSeekBarChangeListener mInternalListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mProgressScale = (mMax - mMin) <= 0 ? 0f : progress / (float) (mMax - mMin);
            if (mOnProgressChangedListener != null) {
                mOnProgressChangedListener.onProgressChanged(SeekBarEx.this, mMin + progress, mProgressScale, fromUser);
            }
            invalidate();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            invalidate();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            invalidate();
            if (mOnProgressChangedListener != null) {
                int value = getValue();
                mOnProgressChangedListener.getProgressOnActionUp(SeekBarEx.this, value, mProgressScale);
                mOnProgressChangedListener.getProgressOnFinally(SeekBarEx.this, value, mProgressScale, true);
            }
        }
    };

    public static class ConfigBuilder {

        private final SeekBarEx mSeekBar;

        private int mMin;

        private int mMax = 100;

        private float mProgress;

        private int mSectionCount;

        ConfigBuilder(SeekBarEx seekBar) {
            mSeekBar = seekBar;
        }

        public ConfigBuilder min(int min) {
            mMin = min;
            return this;
        }

        public ConfigBuilder max(int max) {
            mMax = max;
            return this;
        }

        public ConfigBuilder progress(float progress) {
            mProgress = progress;
            return this;
        }

        public ConfigBuilder sectionCount(int sectionCount) {
            mSectionCount = sectionCount;
            return this;
        }

        public void build() {
            mSeekBar.mMin = mMin;
            mSeekBar.mMax = Math.max(mMax, mMin + 1);
            mSeekBar.mSectionCount = Math.max(0, mSectionCount);
            mSeekBar.applyRange();
            mSeekBar.setValue(Math.round(mProgress));
        }
    }
}
