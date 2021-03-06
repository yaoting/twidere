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

package org.mariotaku.twidere.view;

import org.mariotaku.twidere.view.iface.IExtendedViewGroup;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class ExtendedFrameLayout extends FrameLayout implements IExtendedViewGroup {

	private TouchInterceptor mTouchInterceptor;
	private OnSizeChangedListener mOnSizeChangedListener;
	private int mAlpha = 0xFF;

	public ExtendedFrameLayout(final Context context) {
		super(context);
	}

	public ExtendedFrameLayout(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	public ExtendedFrameLayout(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public final boolean dispatchTouchEvent(final MotionEvent ev) {
		if (mTouchInterceptor != null) {
			mTouchInterceptor.dispatchTouchEvent(this, ev);
		}
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public final boolean onInterceptTouchEvent(final MotionEvent event) {
		if (mTouchInterceptor != null) {
			final boolean ret = mTouchInterceptor.onInterceptTouchEvent(this, event);
			if (ret) return true;
		}
		return super.onInterceptTouchEvent(event);
	}

	@Override
	public final boolean onTouchEvent(final MotionEvent event) {
		if (mTouchInterceptor != null) {
			final boolean ret = mTouchInterceptor.onTouchEvent(this, event);
			if (ret) return true;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void setAlpha(final int alpha) {
		mAlpha = alpha;
		invalidate();
	}

	@Override
	public final void setOnSizeChangedListener(final OnSizeChangedListener listener) {
		mOnSizeChangedListener = listener;
	}

	@Override
	public final void setTouchInterceptor(final TouchInterceptor listener) {
		mTouchInterceptor = listener;
	}

	@Override
	protected void dispatchDraw(final Canvas canvas) {
		try {
			canvas.saveLayerAlpha(null, mAlpha, Canvas.ALL_SAVE_FLAG);
			super.dispatchDraw(canvas);
			canvas.restore();
		} catch (final NullPointerException e) {
			super.dispatchDraw(canvas);
		}
	}

	@Override
	protected final void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (mOnSizeChangedListener != null) {
			mOnSizeChangedListener.onSizeChanged(this, w, h, oldw, oldh);
		}
	}

}
