/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment;

import static org.mariotaku.twidere.util.Utils.scrollListToTop;

import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.fragment.iface.FragmentCallback;
import org.mariotaku.twidere.util.AsyncTwitterWrapper;
import org.mariotaku.twidere.util.MultiSelectManager;
import org.mariotaku.twidere.util.Utils;

import android.app.Activity;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

public class BaseListFragment extends ListFragment implements Constants, OnScrollListener {

	private boolean mActivityFirstCreated;
	private boolean mIsInstanceStateSaved;

	private boolean mReachedBottom, mNotReachedBottomBefore = true;

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (getActivity() == null || !isAdded() || isDetached()) return;
			final String action = intent.getAction();
			if ((BaseListFragment.this.getClass().getName() + SHUFFIX_SCROLL_TO_TOP).equals(action)) {
				scrollListToTop(getListView());
			}
		}
	};

	public final TwidereApplication getApplication() {
		return TwidereApplication.getInstance(getActivity());
	}

	public final ContentResolver getContentResolver() {
		final Activity activity = getActivity();
		if (activity != null) return activity.getContentResolver();
		return null;
	}

	public final MultiSelectManager getMultiSelectManager() {
		return getApplication() != null ? getApplication().getMultiSelectManager() : null;
	}

	public final SharedPreferences getSharedPreferences(final String name, final int mode) {
		final Activity activity = getActivity();
		if (activity != null) return activity.getSharedPreferences(name, mode);
		return null;
	}

	public final Object getSystemService(final String name) {
		final Activity activity = getActivity();
		if (activity != null) return activity.getSystemService(name);
		return null;
	}

	public final int getTabPosition() {
		final Bundle args = getArguments();
		return args != null ? args.getInt(INTENT_KEY_TAB_POSITION, -1) : -1;
	}

	public AsyncTwitterWrapper getTwitterWrapper() {
		return getApplication() != null ? getApplication().getTwitterWrapper() : null;
	}

	public void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.invalidateOptionsMenu();
	}

	public boolean isActivityFirstCreated() {
		return mActivityFirstCreated;
	}

	public boolean isInstanceStateSaved() {
		return mIsInstanceStateSaved;
	}

	public boolean isReachedBottom() {
		return mReachedBottom;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mIsInstanceStateSaved = savedInstanceState != null;
		final ListView lv = getListView();
		lv.setOnScrollListener(this);
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivityFirstCreated = true;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mActivityFirstCreated = true;
	}

	public void onPostStart() {
	}

	@Override
	public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount,
			final int totalItemCount) {
		final boolean reached = firstVisibleItem + visibleItemCount >= totalItemCount
				&& totalItemCount >= visibleItemCount;

		if (mReachedBottom != reached) {
			mReachedBottom = reached;
			if (mReachedBottom && mNotReachedBottomBefore) {
				mNotReachedBottomBefore = false;
				return;
			}
			if (mReachedBottom && getListAdapter().getCount() > visibleItemCount) {
				onReachedBottom();
			}
		}

	}

	@Override
	public void onScrollStateChanged(final AbsListView view, final int scrollState) {

	}

	@Override
	public void onStart() {
		super.onStart();
		final IntentFilter filter = new IntentFilter(getClass().getName() + SHUFFIX_SCROLL_TO_TOP);
		registerReceiver(mStateReceiver, filter);
		onPostStart();
	}

	@Override
	public void onStop() {
		mActivityFirstCreated = false;
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}

	public void registerReceiver(final BroadcastReceiver receiver, final IntentFilter filter) {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.registerReceiver(receiver, filter);
	}

	public void setProgressBarIndeterminateVisibility(final boolean visible) {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.setProgressBarIndeterminateVisibility(visible);
	}

	@Override
	public void setSelection(final int position) {
		Utils.scrollListToPosition(getListView(), position);
	}

	@Override
	public void setUserVisibleHint(final boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);
		final Activity activity = getActivity();
		if (activity instanceof FragmentCallback) {
			((FragmentCallback) activity).onSetUserVisibleHint(this, isVisibleToUser);
		}
	}

	public void unregisterReceiver(final BroadcastReceiver receiver) {
		final Activity activity = getActivity();
		if (activity == null) return;
		activity.unregisterReceiver(receiver);
	}

	protected void onReachedBottom() {

	}
}
