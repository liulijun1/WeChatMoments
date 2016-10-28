package com.thoughtworks.wechat.moments;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;


public class FriendRefreshView extends ViewGroup implements OnDetectScrollListener{

    private ImageView mRainbowView;
    private ListView mContentView;

    private int sWidth;
    private int sHeight;

    private ViewDragHelper mDragHelper;

    private int currentTop;
    private int firstItem;
    private boolean bScrollDown = false;

    private boolean bDraging = false;

    private int rainbowMaxTop = 80;
    private int rainbowStickyTop = 80;
    private int rainbowStartTop = -120;
    private int rainbowRadius = 100;
    private int rainbowTop = - 120;
    private int rainbowRotateAngle = 0;

    private boolean bViewHelperSettling = false;

    private OnRefreshListener mRefreshLisenter;

    private AbsListView.OnScrollListener onScrollListener;
    private OnDetectScrollListener onDetectScrollListener;

    public enum State {
        NORMAL,
        REFRESHING,
        DRAGING
    }

    private State mState = State.NORMAL;

    public FriendRefreshView(Context context) {
        this(context, null);
    }

    public FriendRefreshView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FriendRefreshView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initHandler();
        initDragHelper();
        initListView();
        initRainbowView();
        setBackgroundColor(Color.parseColor("#000000"));
        onDetectScrollListener = this;
    }

    private void initHandler() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        if (rainbowTop > rainbowStartTop) {
                            rainbowTop -= 10;
                            requestLayout();
                            mHandler.sendEmptyMessageDelayed(0, 15);
                        }
                        break;
                    case 1:
                        if (rainbowTop <= rainbowStickyTop) {
                            if (rainbowTop < rainbowStickyTop) {
                                rainbowTop += 10;
                                if (rainbowTop > rainbowStickyTop) {
                                    rainbowTop = rainbowStickyTop;
                                }
                            }
                            mRainbowView.setRotation(rainbowRotateAngle -= 10);
                        }else {
                            mRainbowView.setRotation(rainbowRotateAngle += 10);
                        }

                        requestLayout();

                        mHandler.sendEmptyMessageDelayed(1, 15);
                        break;
                }
            }
        };
    }

    private void initDragHelper() {
        mDragHelper = ViewDragHelper.create(this, 1.0f, new ViewDragHelper.Callback() {
            @Override
            public boolean tryCaptureView(View view, int i) {
                return view == mContentView && !bViewHelperSettling;
            }

            @Override
            public int clampViewPositionHorizontal(View child, int left, int dx) {
                return 0;
            }

            @Override
            public int clampViewPositionVertical(View child, int top, int dy) {
                return top;
            }

            @Override
            public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
                super.onViewPositionChanged(changedView, left, top, dx, dy);
                if (changedView == mContentView) {
                    int lastContentTop = currentTop;
                    if (top >= 0) {
                        currentTop = top;
                    }else {
                        top = 0;
                    }
                    int lastTop = rainbowTop;
                    int rTop = top + rainbowStartTop;
                    if (rTop >= rainbowMaxTop) {
                        if (!isRefreshing()) {
                            rainbowRotateAngle += (currentTop - lastContentTop) * 2;
                            rTop = rainbowMaxTop;
                            rainbowTop = rTop;
                            mRainbowView.setRotation(rainbowRotateAngle);
                        }else {
                            rTop = rainbowMaxTop;
                            rainbowTop = rTop;
                        }

                    }else {
                        if (isRefreshing()) {
                            rainbowTop = rainbowStickyTop;
                        }else {
                            rainbowTop = rTop;
                            rainbowRotateAngle += (rainbowTop - lastTop) * 3;
                            mRainbowView.setRotation(rainbowRotateAngle);
                        }
                    }

                    requestLayout();

                }
            }

            @Override
            public void onViewReleased(View releasedChild, float xvel, float yvel) {
                super.onViewReleased(releasedChild, xvel, yvel);
                mDragHelper.settleCapturedViewAt(0, 0);
                ViewCompat.postInvalidateOnAnimation(FriendRefreshView.this);
                if (currentTop >= rainbowStickyTop) {
                    startRefresh();
                }

            }
        });


    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
            bViewHelperSettling = true;
        }else {
            bViewHelperSettling = false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mDragHelper.shouldInterceptTouchEvent(ev);
        return shouldIntercept();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mDragHelper.processTouchEvent(event);
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                mLastMotionY = 0;
                bDraging = false;
                bScrollDown = false;
                rainbowRotateAngle = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                int index = MotionEventCompat.getActionIndex(event);
                int pointerId = MotionEventCompat.getPointerId(event, index);
                if (shouldIntercept()) {
                    mDragHelper.captureChildView(mContentView, pointerId);
                }
                break;
        }
        return true;
    }

    private boolean shouldIntercept() {
        if (bDraging) return true;
        int childCount = mContentView.getChildCount();
        if (childCount > 0) {
            View firstChild = mContentView.getChildAt(0);
            if (firstChild.getTop() >= 0
                    && firstItem == 0 && currentTop == 0
                    && bScrollDown) {
                return true;
            }else return false;
        }else {
            return true;
        }
    }

    private boolean checkIsTop() {
        int childCount = mContentView.getChildCount();
        if (childCount > 0) {
            View firstChild = mContentView.getChildAt(0);
            if (firstChild.getTop() >= 0
                    && firstItem == 0 && currentTop == 0) {
                return true;
            }else return false;
        }else {
            return false;
        }
    }

    private void initRainbowView() {
        mRainbowView = new ImageView(getContext());
        mRainbowView.setImageResource(R.drawable.rainbow_ic);
        addView(mRainbowView);
    }

    private void initListView() {
        mContentView = new FriendRefreshListView(getContext());
        View mHeadViw = LayoutInflater.from(getContext()).inflate(R.layout.list_head_layout, null);
        mContentView.addHeaderView(mHeadViw);

        this.addView(mContentView);
        mContentView.setOnScrollListener(new AbsListView.OnScrollListener() {

            private int oldTop;
            private int oldFirstVisibleItem;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                firstItem = firstVisibleItem;
                if (onScrollListener != null) {
                    onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }

                if (onDetectScrollListener != null) {
                    onDetectedListScroll(view, firstVisibleItem);
                }
            }

            private void onDetectedListScroll(AbsListView absListView, int firstVisibleItem) {
                View view = absListView.getChildAt(0);
                int top = (view == null) ? 0 : view.getTop();

                if (firstVisibleItem == oldFirstVisibleItem) {
                    if (top > oldTop) {
                        onDetectScrollListener.onUpScrolling();
                    } else if (top < oldTop) {
                        onDetectScrollListener.onDownScrolling();
                    }
                } else {
                    if (firstVisibleItem < oldFirstVisibleItem) {
                        onDetectScrollListener.onUpScrolling();
                    } else {
                        onDetectScrollListener.onDownScrolling();
                    }
                }

                oldTop = top;
                oldFirstVisibleItem = firstVisibleItem;
            }

        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        sWidth = MeasureSpec.getSize(widthMeasureSpec);
        sHeight = MeasureSpec.getSize(heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        LayoutParams contentParams = (LayoutParams) mContentView.getLayoutParams();
        contentParams.left = 0;
        contentParams.top = 0;
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LayoutParams contentParams = (LayoutParams) mContentView.getLayoutParams();
        mContentView.layout(contentParams.left, currentTop,
                contentParams.left + sWidth, currentTop + sHeight);

        mRainbowView.layout(rainbowRadius, rainbowTop,
                rainbowRadius * 2 , rainbowTop + rainbowRadius);
    }

    @Override
    public void onUpScrolling() {
        bScrollDown = false;
    }

    @Override
    public void onDownScrolling() {
        bScrollDown = true;
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {

        public int left = 0;
        public int top = 0;

        public LayoutParams(Context arg0, AttributeSet arg1) {
            super(arg0, arg1);
        }

        public LayoutParams(int arg0, int arg1) {
            super(arg0, arg1);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams arg0) {
            super(arg0);
        }

    }

    @Override
    public android.view.ViewGroup.LayoutParams generateLayoutParams(
            AttributeSet attrs) {
        return new FriendRefreshView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected android.view.ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected android.view.ViewGroup.LayoutParams generateLayoutParams(
            android.view.ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return p instanceof FriendRefreshView.LayoutParams;
    }

    private float mLastMotionX;
    private float mLastMotionY;

    private class FriendRefreshListView extends ListView {



        public FriendRefreshListView(Context context) {
            this(context, null);
        }

        public FriendRefreshListView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public FriendRefreshListView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            setBackgroundColor(Color.parseColor("#ffffff"));
        }

        protected int mActivePointerId = INVALID_POINTER;

        private static final int INVALID_POINTER = -1;

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    int index = MotionEventCompat.getActionIndex(ev);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                    if (mActivePointerId == INVALID_POINTER)
                        break;
                    mLastMotionX = ev.getX();
                    mLastMotionY = ev.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    int indexMove = MotionEventCompat.getActionIndex(ev);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, indexMove);

                    if (mActivePointerId == INVALID_POINTER) {

                    }else {
                        final float y = ev.getY();
                        float dy = y - mLastMotionY;
                        if (checkIsTop() && dy >= 1.0f) {
                            bScrollDown = true;
                            bDraging = true;
                        }else {
                            bScrollDown = false;
                            bDraging = false;
                        }
                        mLastMotionX = y;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    mLastMotionY = 0;
                    break;
            }
            return super.onTouchEvent(ev);
        }
    }

    public void setAdapter(BaseAdapter adapter) {
        if (mContentView != null) {
            mContentView.setAdapter(adapter);
        }
    }

    public boolean isRefreshing() {
        return mState == State.REFRESHING;
    }


    Handler mHandler;
    public void startRefresh() {
        if (!isRefreshing()) {
            mHandler.removeMessages(0);
            mHandler.removeMessages(1);
            mHandler.sendEmptyMessage(1);
            mState = State.REFRESHING;
            invokeListner();
        }

    }

    private void invokeListner() {
        if (mRefreshLisenter != null) {
            mRefreshLisenter.onRefresh();
        }
    }

    public void stopRefresh() {
        mHandler.removeMessages(1);
        mHandler.sendEmptyMessage(0);
        mState = State.NORMAL;
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mRefreshLisenter = listener;
    }

    public interface OnRefreshListener {
        public void onRefresh();
    }
}
