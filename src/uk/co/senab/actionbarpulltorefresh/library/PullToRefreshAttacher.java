/*
 * Copyright 2013 Chris Banes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.co.senab.actionbarpulltorefresh.library;

import java.util.Set;
import java.util.WeakHashMap;

import org.mariotaku.twidere.R;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

/**
 * FIXME
 */
public class PullToRefreshAttacher implements View.OnTouchListener {

	/* Default configuration values */
	private static final int DEFAULT_HEADER_LAYOUT = R.layout.pull_refresh_default_header;
	private static final int DEFAULT_ANIM_HEADER_IN = R.anim.pull_refresh_fade_in;
	private static final int DEFAULT_ANIM_HEADER_OUT = R.anim.pull_refresh_fade_out;
	private static final float DEFAULT_REFRESH_SCROLL_DISTANCE = 0.5f;
	private static final boolean DEFAULT_REFRESH_ON_UP = false;
	private static final int DEFAULT_REFRESH_MINIMIZED_DELAY = 3 * 1000;
	private static final boolean DEFAULT_REFRESH_MINIMIZE = true;

	private static final boolean DEBUG = false;
	private static final String LOG_TAG = "PullToRefreshAttacher";

	/* Member Variables */

	private final EnvironmentDelegate mEnvironmentDelegate;
	private final HeaderTransformer mHeaderTransformer;

	private final View mHeaderView;
	private HeaderViewListener mHeaderViewListener;
	private final Animation mHeaderInAnimation, mHeaderOutAnimation;

	private final int mTouchSlop;
	private final float mRefreshScrollDistance;

	private int mInitialMotionY, mLastMotionY, mPullBeginY;
	private boolean mIsBeingDragged, mIsRefreshing, mIsHandlingTouchEvent;

	private final WeakHashMap<View, ViewParams> mRefreshableViews;
	private final WeakHashMap<View, OnTouchListener> mOnTouchListeners;

	private boolean mEnabled = true;
	private final boolean mRefreshOnUp;
	private final int mRefreshMinimizeDelay;
	private final boolean mRefreshMinimize;

	private final Handler mHandler = new Handler();

	private final Runnable mRefreshMinimizeRunnable = new Runnable() {
		@Override
		public void run() {
			mHeaderTransformer.onRefreshMinimized();

			if (mHeaderViewListener != null) {
				mHeaderViewListener.onStateChanged(mHeaderView, HeaderViewListener.STATE_MINIMIZED);
			}
		}
	};

	protected PullToRefreshAttacher(final Activity activity, Options options) {
		if (options == null) {
			Log.i(LOG_TAG, "Given null options so using default options.");
			options = new Options();
		}

		mRefreshableViews = new WeakHashMap<View, ViewParams>();
		mOnTouchListeners = new WeakHashMap<View, View.OnTouchListener>();

		// Copy necessary values from options
		mRefreshScrollDistance = options.refreshScrollDistance;
		mRefreshOnUp = options.refreshOnUp;
		mRefreshMinimizeDelay = options.refreshMinimizeDelay;
		mRefreshMinimize = options.refreshMinimize;

		// EnvironmentDelegate
		mEnvironmentDelegate = options.environmentDelegate != null ? options.environmentDelegate
				: createDefaultEnvironmentDelegate();

		// Header Transformer
		mHeaderTransformer = options.headerTransformer != null ? options.headerTransformer
				: createDefaultHeaderTransformer();

		// Create animations for use later
		mHeaderInAnimation = AnimationUtils.loadAnimation(activity, options.headerInAnimation);
		mHeaderOutAnimation = AnimationUtils.loadAnimation(activity, options.headerOutAnimation);
		if (mHeaderOutAnimation != null || mHeaderInAnimation != null) {
			final AnimationCallback callback = new AnimationCallback();
			if (mHeaderInAnimation != null) {
				mHeaderInAnimation.setAnimationListener(callback);
			}
			if (mHeaderOutAnimation != null) {
				mHeaderOutAnimation.setAnimationListener(callback);
			}
		}

		// Get touch slop for use later
		mTouchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

		// Get Window Decor View
		final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();

		// Check to see if there is already a Attacher view installed
		if (decorView.getChildCount() == 1 && decorView.getChildAt(0) instanceof DecorChildLayout)
			throw new IllegalStateException("View already installed to DecorView. This shouldn't happen.");

		// Create Header view and then add to Decor View
		mHeaderView = LayoutInflater.from(mEnvironmentDelegate.getContextForInflater(activity)).inflate(
				options.headerLayout, decorView, false);
		if (mHeaderView == null) throw new IllegalArgumentException("Must supply valid layout id for header.");
		mHeaderView.setVisibility(View.GONE);

		// Create DecorChildLayout which will move all of the system's decor
		// view's children + the
		// Header View to itself. See DecorChildLayout for more info.
		final DecorChildLayout decorContents = new DecorChildLayout(activity, decorView, mHeaderView);

		// Now add the DecorChildLayout to the decor view
		decorView.addView(decorContents, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

		// Notify transformer
		mHeaderTransformer.onViewCreated(activity, mHeaderView);
		// TODO Remove the follow deprecated method call before v1.0
		mHeaderTransformer.onViewCreated(mHeaderView);
	}

	/**
	 * Add a view which will be used to initiate refresh requests and a listener
	 * to be invoked when a refresh is started. This version of the method will
	 * try to find a handler for the view from the built-in view delegates.
	 * 
	 * @param view View which will be used to initiate refresh requests.
	 * @param refreshListener Listener to be invoked when a refresh is started.
	 */
	public void addRefreshableView(final View view, final OnRefreshListener refreshListener) {
		addRefreshableView(view, null, refreshListener);
	}

	/**
	 * Add a view which will be used to initiate refresh requests, along with a
	 * delegate which knows how to handle the given view, and a listener to be
	 * invoked when a refresh is started.
	 * 
	 * @param view View which will be used to initiate refresh requests.
	 * @param viewDelegate delegate which knows how to handle <code>view</code>.
	 * @param refreshListener Listener to be invoked when a refresh is started.
	 */
	public void addRefreshableView(final View view, final ViewDelegate viewDelegate,
			final OnRefreshListener refreshListener) {
		addRefreshableView(view, viewDelegate, refreshListener, true);
	}

	/**
	 * Clear all views which were previously used to initiate refresh requests.
	 */
	public void clearRefreshableViews() {
		final Set<View> views = mRefreshableViews.keySet();
		for (final View view : views) {
			view.setOnTouchListener(null);
		}
		mRefreshableViews.clear();
		mOnTouchListeners.clear();
	}

	/**
	 * @return The HeaderTransformer currently used by this Attacher.
	 */
	public HeaderTransformer getHeaderTransformer() {
		return mHeaderTransformer;
	}

	/**
	 * @return The Header View which is displayed when the user is pulling, or
	 *         we are refreshing.
	 */
	public final View getHeaderView() {
		return mHeaderView;
	}

	/**
	 * @return true if this PullToRefresh is currently enabled (defaults to
	 *         <code>true</code>)
	 */
	public boolean isEnabled() {
		return mEnabled;
	}

	/**
	 * @return true if this Attacher is currently in a refreshing state.
	 */
	public final boolean isRefreshing() {
		return mIsRefreshing;
	}

	/**
	 * This method should be called by your Activity's or Fragment's
	 * onConfigurationChanged method.
	 * 
	 * @param newConfig - The new configuration
	 */
	public void onConfigurationChanged(final Configuration newConfig) {
		mHeaderTransformer.onViewCreated(mHeaderView);
	}

	@Override
	public final boolean onTouch(final View view, final MotionEvent event) {
		if (!mIsHandlingTouchEvent && onInterceptTouchEvent(view, event)) {
			mIsHandlingTouchEvent = true;
		}

		if (mIsHandlingTouchEvent) {
			onTouchEvent(view, event);
		}
		final OnTouchListener listener = mOnTouchListeners.get(view);
		if (listener != null) return listener.onTouch(view, event);
		// Always return false as we only want to observe events
		return false;
	}

	/**
	 * Remove a view which was previously used to initiate refresh requests.
	 * 
	 * @param view - View which will be used to initiate refresh requests.
	 */
	public void removeRefreshableView(final View view) {
		if (mRefreshableViews.containsKey(view)) {
			mRefreshableViews.remove(view);
			view.setOnTouchListener(null);
		}
		mOnTouchListeners.remove(view);
	}

	/**
	 * Allows the enable/disable of this PullToRefreshAttacher. If disabled when
	 * refreshing then the UI is automatically reset.
	 * 
	 * @param enabled - Whether this PullToRefreshAttacher is enabled.
	 */
	public void setEnabled(final boolean enabled) {
		mEnabled = enabled;

		if (!enabled) {
			// If we're not enabled, reset any touch handling
			resetTouch();

			// If we're currently refreshing, reset the ptr UI
			if (mIsRefreshing) {
				reset(false);
			}
		}
	}

	/**
	 * Set a {@link HeaderViewListener} which is called when the visibility
	 * state of the Header View has changed.
	 * 
	 * @param listener
	 */
	public final void setHeaderViewListener(final HeaderViewListener listener) {
		mHeaderViewListener = listener;
	}

	public void setOnTouchListener(final View view, final OnTouchListener listener) {
		if (mRefreshableViews.containsKey(view)) {
			mOnTouchListeners.put(view, listener);
		}
	}

	/**
	 * Call this when your refresh is complete and this view should reset itself
	 * (header view will be hidden).
	 * 
	 * This is the equivalent of calling <code>setRefreshing(false)</code>.
	 */
	public final void setRefreshComplete() {
		setRefreshingInt(null, false, false);
	}

	/**
	 * Manually set this Attacher's refreshing state. The header will be
	 * displayed or hidden as requested.
	 * 
	 * @param refreshing - Whether the attacher should be in a refreshing state,
	 */
	public final void setRefreshing(final boolean refreshing) {
		setRefreshingInt(null, refreshing, false);
	}

	protected EnvironmentDelegate createDefaultEnvironmentDelegate() {
		return new EnvironmentDelegate();
	}

	protected HeaderTransformer createDefaultHeaderTransformer() {
		return new DefaultHeaderTransformer();
	}

	/**
	 * @param fromTouch - Whether this is being invoked from a touch event
	 * @return true if we're currently in a state where a refresh can be
	 *         started.
	 */
	private boolean canRefresh(final boolean fromTouch, final OnRefreshListener listener) {
		return !mIsRefreshing && (!fromTouch || listener != null);
	}

	private boolean checkScrollForRefresh(final View view) {
		if (mIsBeingDragged && mRefreshOnUp && view != null) {
			if (mLastMotionY - mPullBeginY >= getScrollNeededForRefresh(view)) {
				setRefreshingInt(view, true, true);
				return true;
			}
		}
		return false;
	}

	private OnRefreshListener getRefreshListenerForView(final View view) {
		if (view != null) {
			final ViewParams params = mRefreshableViews.get(view);
			if (params != null) return params.onRefreshListener;
		}
		return null;
	}

	private float getScrollNeededForRefresh(final View view) {
		return view.getHeight() * mRefreshScrollDistance;
	}

	private void reset(final boolean fromTouch) {
		// Update isRefreshing state
		mIsRefreshing = false;

		// Remove any minimize callbacks
		if (mRefreshMinimize) {
			mHandler.removeCallbacks(mRefreshMinimizeRunnable);
		}

		// Hide Header View
		hideHeaderView();
	}

	private void setRefreshingInt(final View view, final boolean refreshing, final boolean fromTouch) {
		if (DEBUG) {
			Log.d(LOG_TAG, "setRefreshingInt: " + refreshing);
		}
		// Check to see if we need to do anything
		if (mIsRefreshing == refreshing) return;

		resetTouch();

		if (refreshing && canRefresh(fromTouch, getRefreshListenerForView(view))) {
			startRefresh(view, fromTouch);
		} else {
			reset(fromTouch);
		}
	}

	private void startRefresh(final View view, final boolean fromTouch) {
		// Update isRefreshing state
		mIsRefreshing = true;

		// Call OnRefreshListener if this call has originated from a touch event
		if (fromTouch) {
			final OnRefreshListener listener = getRefreshListenerForView(view);
			if (listener != null) {
				listener.onRefreshStarted(view);
			}
		}

		// Call Transformer
		mHeaderTransformer.onRefreshStarted();

		// Show Header View
		showHeaderView();

		// Post a delay runnable to minimize the refresh header
		if (mRefreshMinimize) {
			mHandler.postDelayed(mRefreshMinimizeRunnable, mRefreshMinimizeDelay);
		}
	}

	/**
	 * Add a view which will be used to initiate refresh requests, along with a
	 * delegate which knows how to handle the given view, and a listener to be
	 * invoked when a refresh is started.
	 * 
	 * @param view View which will be used to initiate refresh requests.
	 * @param viewDelegate delegate which knows how to handle <code>view</code>.
	 * @param refreshListener Listener to be invoked when a refresh is started.
	 * @param setTouchListener Whether to set this as the
	 *            {@link android.view.View.OnTouchListener}.
	 */
	void addRefreshableView(final View view, ViewDelegate viewDelegate, final OnRefreshListener refreshListener,
			final boolean setTouchListener) {
		// Check to see if view is null
		if (view == null) {
			Log.i(LOG_TAG, "Refreshable View is null.");
			return;
		}

		if (refreshListener == null)
			throw new IllegalArgumentException("OnRefreshListener not given. Please provide one.");

		// ViewDelegate
		if (viewDelegate == null) {
			viewDelegate = InstanceCreationUtils.getBuiltInViewDelegate(view);
			if (viewDelegate == null) throw new IllegalArgumentException("No view handler found. Please provide one.");
		}

		// View to detect refreshes for
		mRefreshableViews.put(view, new ViewParams(viewDelegate, refreshListener));
		if (setTouchListener) {
			view.setOnTouchListener(this);
		}
	}

	void hideHeaderView() {
		if (mHeaderView.getVisibility() != View.GONE) {
			// Hide Header
			if (mHeaderOutAnimation != null) {
				// AnimationListener will call HeaderTransformer and
				// HeaderViewListener
				mHeaderView.startAnimation(mHeaderOutAnimation);
			} else {
				// As we're not animating, hide the header + call the header
				// transformer now
				mHeaderView.setVisibility(View.GONE);
				mHeaderTransformer.onReset();

				if (mHeaderViewListener != null) {
					mHeaderViewListener.onStateChanged(mHeaderView, HeaderViewListener.STATE_HIDDEN);
				}
			}
		}
	}

	final boolean onInterceptTouchEvent(final View view, final MotionEvent event) {
		if (DEBUG) {
			Log.d(LOG_TAG, "onInterceptTouchEvent: " + event.toString());
		}

		// If we're not enabled or currently refreshing don't handle any touch
		// events
		if (!isEnabled() || isRefreshing()) return false;

		final ViewParams params = mRefreshableViews.get(view);
		if (params == null) return false;

		switch (event.getAction()) {
			case MotionEvent.ACTION_MOVE: {
				// We're not currently being dragged so check to see if the user
				// has
				// scrolled enough
				if (!mIsBeingDragged && mInitialMotionY > 0) {
					final int y = (int) event.getY();
					final int yDiff = y - mInitialMotionY;

					if (yDiff > mTouchSlop) {
						mIsBeingDragged = true;
						onPullStarted(y);
					} else if (yDiff < -mTouchSlop) {
						resetTouch();
					}
				}
				break;
			}

			case MotionEvent.ACTION_DOWN: {
				// If we're already refreshing, ignore
				if (canRefresh(true, params.onRefreshListener) && params.viewDelegate.isScrolledToTop(view)) {
					mInitialMotionY = (int) event.getY();
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				resetTouch();
				break;
			}
		}

		return mIsBeingDragged;
	}

	void onPull(final View view, final int y) {
		if (DEBUG) {
			Log.d(LOG_TAG, "onPull");
		}

		final float pxScrollForRefresh = getScrollNeededForRefresh(view);
		final int scrollLength = y - mPullBeginY;

		if (scrollLength < pxScrollForRefresh) {
			mHeaderTransformer.onPulled(scrollLength / pxScrollForRefresh);
		} else {
			if (mRefreshOnUp) {
				mHeaderTransformer.onReleaseToRefresh();
			} else {
				setRefreshingInt(view, true, true);
			}
		}
	}

	void onPullEnded() {
		if (DEBUG) {
			Log.d(LOG_TAG, "onPullEnded");
		}
		if (!mIsRefreshing) {
			reset(true);
		}
	}

	void onPullStarted(final int y) {
		if (DEBUG) {
			Log.d(LOG_TAG, "onPullStarted");
		}
		showHeaderView();
		mPullBeginY = y;
	}

	final boolean onTouchEvent(final View view, final MotionEvent event) {
		if (DEBUG) {
			Log.d(LOG_TAG, "onTouchEvent: " + event.toString());
		}

		// If we're not enabled or currently refreshing don't handle any touch
		// events
		if (!isEnabled()) return false;

		final ViewParams params = mRefreshableViews.get(view);
		if (params == null) return false;

		switch (event.getAction()) {
			case MotionEvent.ACTION_MOVE: {
				// If we're already refreshing ignore it
				if (isRefreshing()) return false;

				final int y = (int) event.getY();

				if (mIsBeingDragged && y != mLastMotionY) {
					final int yDx = y - mLastMotionY;

					/**
					 * Check to see if the user is scrolling the right direction
					 * (down). We allow a small scroll up which is the check
					 * against negative touch slop.
					 */
					if (yDx >= -mTouchSlop) {
						onPull(view, y);
						// Only record the y motion if the user has scrolled
						// down.
						if (yDx > 0) {
							mLastMotionY = y;
						}
					} else {
						onPullEnded();
						resetTouch();
					}
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				checkScrollForRefresh(view);
				if (mIsBeingDragged) {
					onPullEnded();
				}
				resetTouch();
				break;
			}
		}

		return true;
	}

	void resetTouch() {
		mIsBeingDragged = false;
		mIsHandlingTouchEvent = false;
		mInitialMotionY = mLastMotionY = mPullBeginY = -1;
	}

	void showHeaderView() {
		if (mHeaderView.getVisibility() != View.VISIBLE) {
			// Show Header
			if (mHeaderInAnimation != null) {
				// AnimationListener will call HeaderViewListener
				mHeaderView.startAnimation(mHeaderInAnimation);
			} else {
				// Call HeaderViewListener now as we have no animation
				if (mHeaderViewListener != null) {
					mHeaderViewListener.onStateChanged(mHeaderView, HeaderViewListener.STATE_VISIBLE);
				}
			}
			mHeaderView.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Get a PullToRefreshAttacher for this Activity. If there is already a
	 * PullToRefreshAttacher attached to the Activity, the existing one is
	 * returned, otherwise a new instance is created. This version of the method
	 * will use default configuration options for everything.
	 * 
	 * @param activity Activity to attach to.
	 * @return PullToRefresh attached to the Activity.
	 */
	public static PullToRefreshAttacher get(final Activity activity) {
		return get(activity, new Options());
	}

	/**
	 * Get a PullToRefreshAttacher for this Activity. If there is already a
	 * PullToRefreshAttacher attached to the Activity, the existing one is
	 * returned, otherwise a new instance is created.
	 * 
	 * @param activity Activity to attach to.
	 * @param options Options used when creating the PullToRefreshAttacher.
	 * @return PullToRefresh attached to the Activity.
	 */
	public static PullToRefreshAttacher get(final Activity activity, final Options options) {
		return new PullToRefreshAttacher(activity, options);
	}

	/**
	 * FIXME
	 */
	public static class EnvironmentDelegate {

		/**
		 * @return Context which should be used for inflating the header layout
		 */
		public Context getContextForInflater(final Activity activity) {
			if (Build.VERSION.SDK_INT >= 14)
				return activity.getActionBar().getThemedContext();
			else
				return activity;
		}
	}

	public static abstract class HeaderTransformer {

		/**
		 * Called the user has pulled on the scrollable view.
		 * 
		 * @param percentagePulled - value between 0.0f and 1.0f depending on
		 *            how far the user has pulled.
		 */
		public void onPulled(final float percentagePulled) {
		}

		/**
		 * Called when the current refresh has taken longer than the time
		 * specified in {@link Options#refreshMinimizeDelay}.
		 */
		public void onRefreshMinimized() {
		}

		/**
		 * Called when a refresh has begun. Theoretically this call is similar
		 * to that provided from {@link OnRefreshListener} but is more suitable
		 * for header view updates.
		 */
		public void onRefreshStarted() {
		}

		/**
		 * Called when a refresh can be initiated when the user ends the touch
		 * event. This is only called when {@link Options#refreshOnUp} is set to
		 * true.
		 */
		public void onReleaseToRefresh() {
		}

		/**
		 * Called when the header should be reset. You should update any child
		 * views to reflect this.
		 * <p/>
		 * You should <strong>not</strong> change the visibility of the header
		 * view.
		 */
		public void onReset() {
		}

		/**
		 * Called whether the header view has been inflated from the resources
		 * defined in {@link Options#headerLayout}.
		 * 
		 * @param activity The {@link Activity} that the header view is attached
		 *            to.
		 * @param headerView The inflated header view.
		 */
		public void onViewCreated(final Activity activity, final View headerView) {
		}

		/**
		 * @deprecated This will be removed before v1.0. Override
		 *             {@link #onViewCreated(android.app.Activity, android.view.View)}
		 *             instead.
		 */
		@Deprecated
		public void onViewCreated(final View headerView) {
		}
	}

	public interface HeaderViewListener {
		/**
		 * The state when the header view is completely visible.
		 */
		public static int STATE_VISIBLE = 0;

		/**
		 * The state when the header view is minimized. By default this means
		 * that the progress bar is still visible, but the rest of the view is
		 * hidden, showing the Action Bar behind.
		 * <p/>
		 * This will not be called in header minimization is disabled.
		 */
		public static int STATE_MINIMIZED = 1;

		/**
		 * The state when the header view is completely hidden.
		 */
		public static int STATE_HIDDEN = 2;

		/**
		 * Called when the visibility state of the Header View has changed.
		 * 
		 * @param headerView HeaderView who's state has changed.
		 * @param state The new state. One of {@link #STATE_VISIBLE},
		 *            {@link #STATE_MINIMIZED} and {@link #STATE_HIDDEN}
		 */
		public void onStateChanged(View headerView, int state);
	}

	/**
	 * Simple Listener to listen for any callbacks to Refresh.
	 */
	public interface OnRefreshListener {
		/**
		 * Called when the user has initiated a refresh by pulling.
		 * 
		 * @param view - View which the user has started the refresh from.
		 */
		public void onRefreshStarted(View view);
	}

	public static class Options {

		/**
		 * EnvironmentDelegate instance which will be used. If null, we will
		 * create an instance of the default class.
		 */
		public EnvironmentDelegate environmentDelegate = null;

		/**
		 * The layout resource ID which should be inflated to be displayed above
		 * the Action Bar
		 */
		public int headerLayout = DEFAULT_HEADER_LAYOUT;

		/**
		 * The header transformer to be used to transfer the header view. If
		 * null, an instance of {@link DefaultHeaderTransformer} will be used.
		 */
		public HeaderTransformer headerTransformer = null;

		/**
		 * The anim resource ID which should be started when the header is being
		 * hidden.
		 */
		public int headerOutAnimation = DEFAULT_ANIM_HEADER_OUT;

		/**
		 * The anim resource ID which should be started when the header is being
		 * shown.
		 */
		public int headerInAnimation = DEFAULT_ANIM_HEADER_IN;

		/**
		 * The percentage of the refreshable view that needs to be scrolled
		 * before a refresh is initiated.
		 */
		public float refreshScrollDistance = DEFAULT_REFRESH_SCROLL_DISTANCE;

		/**
		 * Whether a refresh should only be initiated when the user has finished
		 * the touch event.
		 */
		public boolean refreshOnUp = DEFAULT_REFRESH_ON_UP;

		/**
		 * The delay after a refresh is started in which the header should be
		 * 'minimized'. By default, most of the header is faded out, leaving
		 * only the progress bar signifying that a refresh is taking place.
		 */
		public int refreshMinimizeDelay = DEFAULT_REFRESH_MINIMIZED_DELAY;

		/**
		 * Enable or disable the header 'minimization', which by default means
		 * that the majority of the header is hidden, leaving only the progress
		 * bar still showing.
		 * <p/>
		 * If set to true, the header will be minimized after the delay set in
		 * {@link #refreshMinimizeDelay}. If set to false then the whole header
		 * will be displayed until the refresh is finished.
		 */
		public boolean refreshMinimize = DEFAULT_REFRESH_MINIMIZE;
	}

	/**
	 * FIXME
	 */
	public static abstract class ViewDelegate {

		/**
		 * Allows you to provide support for View which do not have built-in
		 * support. In this method you should cast <code>view</code> to it's
		 * native class, and check if it is scrolled to the top.
		 * 
		 * @param view The view which has should be checked against.
		 * @return true if <code>view</code> is scrolled to the top.
		 */
		public abstract boolean isScrolledToTop(View view);
	}

	private class AnimationCallback implements Animation.AnimationListener {

		@Override
		public void onAnimationEnd(final Animation animation) {
			if (animation == mHeaderOutAnimation) {
				mHeaderView.setVisibility(View.GONE);
				mHeaderTransformer.onReset();
				if (mHeaderViewListener != null) {
					mHeaderViewListener.onStateChanged(mHeaderView, HeaderViewListener.STATE_HIDDEN);
				}
			} else if (animation == mHeaderInAnimation) {
				if (mHeaderViewListener != null) {
					mHeaderViewListener.onStateChanged(mHeaderView, HeaderViewListener.STATE_VISIBLE);
				}
			}
		}

		@Override
		public void onAnimationRepeat(final Animation animation) {
		}

		@Override
		public void onAnimationStart(final Animation animation) {
		}
	}

	private static final class ViewParams {
		final OnRefreshListener onRefreshListener;
		final ViewDelegate viewDelegate;

		ViewParams(final ViewDelegate _viewDelegate, final OnRefreshListener _onRefreshListener) {
			onRefreshListener = _onRefreshListener;
			viewDelegate = _viewDelegate;
		}
	}

	/**
	 * This class allows us to insert a layer in between the system decor view
	 * and the actual decor. (e.g. Action Bar views). This is needed so we can
	 * receive a call to fitSystemWindows(Rect) so we can adjust the header view
	 * to fit the system windows too.
	 */
	final static class DecorChildLayout extends FrameLayout {
		private final ViewGroup mHeaderViewWrapper;

		DecorChildLayout(final Context context, final ViewGroup systemDecorView, final View headerView) {
			super(context);

			// Move all children from decor view to here
			for (int i = 0, z = systemDecorView.getChildCount(); i < z; i++) {
				final View child = systemDecorView.getChildAt(i);
				systemDecorView.removeView(child);
				addView(child);
			}

			/**
			 * Wrap the Header View in a FrameLayout and add it to this view. It
			 * is wrapped so any inset changes do not affect the actual header
			 * view.
			 */
			mHeaderViewWrapper = new FrameLayout(context);
			mHeaderViewWrapper.addView(headerView);
			addView(mHeaderViewWrapper, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}

		@Override
		protected boolean fitSystemWindows(final Rect insets) {
			if (DEBUG) {
				Log.d(LOG_TAG, "fitSystemWindows: " + insets.toString());
			}

			// Adjust the Header View's padding to take the insets into account
			mHeaderViewWrapper.setPadding(insets.left, insets.top, insets.right, insets.bottom);

			// Call return super so that the rest of the
			return super.fitSystemWindows(insets);
		}
	}

}
