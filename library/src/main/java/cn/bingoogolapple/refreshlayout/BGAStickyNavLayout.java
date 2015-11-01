package cn.bingoogolapple.refreshlayout;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.ScrollView;

import java.lang.reflect.Field;

import cn.bingoogolapple.refreshlayout.util.ScrollingUtil;

/**
 * 作者:王浩 邮件:bingoogolapple@gmail.com
 * 创建时间:15/10/28 上午2:32
 * 描述:
 */
public class BGAStickyNavLayout extends LinearLayout {
    private View mHeaderView;
    private View mNavView;
    private View mContentView;

    private View mDirectNormalView;
    private RecyclerView mDirectRecyclerView;
    private AbsListView mDirectAbsListView;
    private ScrollView mDirectScrollView;
    private WebView mDirectWebView;
    private ViewPager mDirectViewPager;

    private View mNestedContentView;
    private View mNestedNormalView;
    private RecyclerView mNestedRecyclerView;
    private AbsListView mNestedAbsListView;
    private ScrollView mNestedScrollView;
    private WebView mNestedWebView;

    private OverScroller mOverScroller;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMaximumVelocity;
    private int mMinimumVelocity;

    private boolean mIsInControl = true;

    private float mLastDispatchY;
    private float mLastTouchY;

    public BGARefreshLayout mRefreshLayout;

    /**
     * 是否已经设置内容控件滚动监听器
     */
    private boolean mIsInitedContentViewScrollListener = false;

    public BGAStickyNavLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);

        mOverScroller = new OverScroller(context);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
    }

    @Override
    public void setOrientation(int orientation) {
        if (VERTICAL == orientation) {
            super.setOrientation(VERTICAL);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        if (getChildCount() != 3) {
            throw new IllegalStateException(BGAStickyNavLayout.class.getSimpleName() + "必须有且只有三个子控件");
        }

        mHeaderView = getChildAt(0);
        mNavView = getChildAt(1);
        mContentView = getChildAt(2);

        if (mContentView instanceof AbsListView) {
            mDirectAbsListView = (AbsListView) mContentView;
        } else if (mContentView instanceof RecyclerView) {
            mDirectRecyclerView = (RecyclerView) mContentView;
        } else if (mContentView instanceof ScrollView) {
            mDirectScrollView = (ScrollView) mContentView;
        } else if (mContentView instanceof WebView) {
            mDirectWebView = (WebView) mContentView;
        } else if (mContentView instanceof ViewPager) {
            mDirectViewPager = (ViewPager) mContentView;
        } else {
            mDirectNormalView = mContentView;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mIsInitedContentViewScrollListener) {
            setDirectRecyclerViewOnScrollListener();
            setDirectAbsListViewOnScrollListener();
            mIsInitedContentViewScrollListener = true;
        }
    }

    private void setDirectRecyclerViewOnScrollListener() {
        if (mDirectRecyclerView != null) {
            mDirectRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if ((newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_SETTLING) && mRefreshLayout != null && mRefreshLayout.shouldHandleRecyclerViewLoadingMore(mDirectRecyclerView)) {
                        mRefreshLayout.beginLoadingMore();
                    }
                }
            });
        }
    }

    private void setDirectAbsListViewOnScrollListener() {
        if (mDirectAbsListView != null) {
            try {
                // 通过反射获取开发者自定义的滚动监听器，并将其替换成自己的滚动监听器，触发滚动时也要通知开发者自定义的滚动监听器（非侵入式，不让开发者继承特定的控件）
                // mAbsListView.getClass().getDeclaredField("mOnScrollListener")获取不到mOnScrollListener，必须通过AbsListView.class.getDeclaredField("mOnScrollListener")获取
                Field field = AbsListView.class.getDeclaredField("mOnScrollListener");
                field.setAccessible(true);
                // 开发者自定义的滚动监听器
                final AbsListView.OnScrollListener onScrollListener = (AbsListView.OnScrollListener) field.get(mDirectAbsListView);
                mDirectAbsListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                        if ((scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_FLING) && mRefreshLayout != null && mRefreshLayout.shouldHandleAbsListViewLoadingMore(mDirectAbsListView)) {
                            mRefreshLayout.beginLoadingMore();
                        }

                        if (onScrollListener != null) {
                            onScrollListener.onScrollStateChanged(absListView, scrollState);
                        }
                    }

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        if (onScrollListener != null) {
                            onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChild(mContentView, widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - getNavViewHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    public void computeScroll() {
        if (mOverScroller.computeScrollOffset()) {
            scrollTo(0, mOverScroller.getCurrY());
            invalidate();
        }
    }

    public void fling(int velocityY) {
        mOverScroller.fling(0, getScrollY(), 0, velocityY, 0, 0, 0, getHeaderViewHeight());
        invalidate();
    }

    @Override
    public void scrollTo(int x, int y) {
        if (y < 0) {
            y = 0;
        }

        int headerViewHeight = getHeaderViewHeight();
        if (y > headerViewHeight) {
            y = headerViewHeight;
        }

        if (y != getScrollY()) {
            super.scrollTo(x, y);
        }
    }

    /**
     * 获取头部视图高度，包括topMargin和bottomMargin
     *
     * @return
     */
    private int getHeaderViewHeight() {
        MarginLayoutParams layoutParams = (MarginLayoutParams) mHeaderView.getLayoutParams();
        return mHeaderView.getMeasuredHeight() + layoutParams.topMargin + layoutParams.bottomMargin;
    }

    /**
     * 获取导航视图的高度，包括topMargin和bottomMargin
     *
     * @return
     */
    private int getNavViewHeight() {
        MarginLayoutParams layoutParams = (MarginLayoutParams) mNavView.getLayoutParams();
        return mNavView.getMeasuredHeight() + layoutParams.topMargin + layoutParams.bottomMargin;
    }

    /**
     * 头部视图是否已经完全隐藏
     *
     * @return
     */
    private boolean isHeaderViewCompleteInvisible() {
        // 0表示x，1表示y
        int[] location = new int[2];
        getLocationOnScreen(location);
        int contentOnScreenTopY = location[1] + getPaddingTop();

        mNavView.getLocationOnScreen(location);
        MarginLayoutParams params = (MarginLayoutParams) mNavView.getLayoutParams();
        int navViewTopOnScreenY = location[1] - params.topMargin;

        if (navViewTopOnScreenY == contentOnScreenTopY) {
//            debug("头部视图完全隐藏  navViewTopOnScreenY = " + navViewTopOnScreenY + "   contentOnScreenTopY = " + contentOnScreenTopY);
            return true;
        } else {
//            debug("头部视图没有完全隐藏  navViewTopOnScreenY = " + navViewTopOnScreenY + "   contentOnScreenTopY = " + contentOnScreenTopY);
            return false;
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        float currentTouchY = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastDispatchY = currentTouchY;
                break;
            case MotionEvent.ACTION_MOVE:
                float differentY = currentTouchY - mLastDispatchY;
                mLastDispatchY = currentTouchY;
                if (isContentViewToTop() && isHeaderViewCompleteInvisible()) {
                    if (differentY >= 0 && !mIsInControl) {
                        mIsInControl = true;

                        return resetDispatchTouchEvent(ev);
                    }

                    if (differentY <= 0 && mIsInControl) {
                        mIsInControl = false;

                        return resetDispatchTouchEvent(ev);
                    }
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean resetDispatchTouchEvent(MotionEvent ev) {
        MotionEvent newEvent = MotionEvent.obtain(ev);

        ev.setAction(MotionEvent.ACTION_CANCEL);
        dispatchTouchEvent(ev);

        newEvent.setAction(MotionEvent.ACTION_DOWN);
        return dispatchTouchEvent(newEvent);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float currentTouchY = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchY = currentTouchY;
                break;
            case MotionEvent.ACTION_MOVE:
                float differentY = currentTouchY - mLastTouchY;
                if (Math.abs(differentY) > mTouchSlop) {
                    if (!isHeaderViewCompleteInvisible() || (isContentViewToTop() && isHeaderViewCompleteInvisible() && mIsInControl)) {
                        mLastTouchY = currentTouchY;
                        return true;
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(event);

        float currentTouchY = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mOverScroller.isFinished()) {
                    mOverScroller.abortAnimation();
                }

                mLastTouchY = currentTouchY;
                break;
            case MotionEvent.ACTION_MOVE:
                float differentY = currentTouchY - mLastTouchY;
                mLastTouchY = currentTouchY;
                if (Math.abs(differentY) > 0) {
                    scrollBy(0, (int) -differentY);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                recycleVelocityTracker();
                if (!mOverScroller.isFinished()) {
                    mOverScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int initialVelocity = (int) mVelocityTracker.getYVelocity();
                if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
                    fling(-initialVelocity);
                }
                recycleVelocityTracker();
                break;
        }
        return true;
    }

    public boolean isContentViewToTop() {
        if (ScrollingUtil.isScrollViewOrWebViewToTop(mDirectWebView)) {
            return true;
        }

        if (ScrollingUtil.isScrollViewOrWebViewToTop(mDirectScrollView)) {
            return true;
        }

        if (ScrollingUtil.isAbsListViewToTop(mDirectAbsListView)) {
            return true;
        }

        if (ScrollingUtil.isRecyclerViewToTop(mDirectRecyclerView)) {
            return true;
        }

        if (mDirectViewPager != null) {
            return isViewPagerContentViewToTop();
        }

        return false;
    }

    private boolean isViewPagerContentViewToTop() {
        regetNestedContentView();

        if (mDirectNormalView != null) {
            return true;
        }

        if (ScrollingUtil.isScrollViewOrWebViewToTop(mNestedWebView)) {
            return true;
        }

        if (ScrollingUtil.isScrollViewOrWebViewToTop(mNestedScrollView)) {
            return true;
        }

        if (ScrollingUtil.isAbsListViewToTop(mNestedAbsListView)) {
            return true;
        }

        if (ScrollingUtil.isRecyclerViewToTop(mNestedRecyclerView)) {
            return true;
        }

        return false;
    }

    /**
     * 重新获取嵌套的内容视图
     */
    private void regetNestedContentView() {
        int currentItem = mDirectViewPager.getCurrentItem();
        PagerAdapter adapter = mDirectViewPager.getAdapter();
        if (adapter instanceof FragmentPagerAdapter || adapter instanceof FragmentStatePagerAdapter) {
            Fragment item = (Fragment) adapter.instantiateItem(mDirectViewPager, currentItem);
            mNestedContentView = item.getView();

            // 清空之前的
            mNestedNormalView = null;
            mNestedAbsListView = null;
            mNestedRecyclerView = null;
            mNestedScrollView = null;
            mNestedWebView = null;

            if (mNestedContentView instanceof AbsListView) {
                mNestedAbsListView = (AbsListView) mNestedContentView;
                mNestedAbsListView.setOnScrollListener(mNestedLvOnScrollListener);
            } else if (mNestedContentView instanceof RecyclerView) {
                mNestedRecyclerView = (RecyclerView) mNestedContentView;
                mNestedRecyclerView.removeOnScrollListener(mNestedRvOnScrollListener);
                mNestedRecyclerView.addOnScrollListener(mNestedRvOnScrollListener);
            } else if (mNestedContentView instanceof ScrollView) {
                mNestedScrollView = (ScrollView) mNestedContentView;
            } else if (mNestedContentView instanceof WebView) {
                mNestedWebView = (WebView) mNestedContentView;
            } else {
                mNestedNormalView = mNestedContentView;
            }
        } else {
            throw new IllegalStateException(BGAStickyNavLayout.class.getSimpleName() + "的第三个子控件为ViewPager时，其adapter必须是FragmentPagerAdapter或者FragmentStatePagerAdapter");
        }
    }

    public void setRefreshLayout(BGARefreshLayout refreshLayout) {
        mRefreshLayout = refreshLayout;
    }

    private RecyclerView.OnScrollListener mNestedRvOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if ((newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_SETTLING) && mRefreshLayout != null && mRefreshLayout.shouldHandleRecyclerViewLoadingMore(mNestedRecyclerView)) {
                mRefreshLayout.beginLoadingMore();
            }
        }
    };

    private AbsListView.OnScrollListener mNestedLvOnScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView absListView, int scrollState) {
            if ((scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_FLING) && mRefreshLayout != null && mRefreshLayout.shouldHandleAbsListViewLoadingMore(mNestedAbsListView)) {
                mRefreshLayout.beginLoadingMore();
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        }
    };

    public boolean shouldHandleLoadingMore() {
        if (mRefreshLayout == null) {
            return false;
        }

        if (mDirectNormalView != null) {
            return true;
        }

        if (ScrollingUtil.isWebViewToBottom(mDirectWebView)) {
            return true;
        }

        if (ScrollingUtil.isScrollViewToBottom(mDirectScrollView)) {
            return true;
        }

        if (mDirectAbsListView != null) {
            return mRefreshLayout.shouldHandleAbsListViewLoadingMore(mDirectAbsListView);
        }

        if (mDirectRecyclerView != null) {
            return mRefreshLayout.shouldHandleRecyclerViewLoadingMore(mDirectRecyclerView);
        }

        if (mDirectViewPager != null) {
            regetNestedContentView();

            if (mNestedNormalView != null) {
                return true;
            }

            if (ScrollingUtil.isWebViewToBottom(mNestedWebView)) {
                return true;
            }

            if (ScrollingUtil.isScrollViewToBottom(mNestedScrollView)) {
                return true;
            }

            if (mNestedAbsListView != null) {
                return mRefreshLayout.shouldHandleAbsListViewLoadingMore(mNestedAbsListView);
            }

            if (mNestedRecyclerView != null) {
                return mRefreshLayout.shouldHandleRecyclerViewLoadingMore(mNestedRecyclerView);
            }
        }

        return false;
    }
}