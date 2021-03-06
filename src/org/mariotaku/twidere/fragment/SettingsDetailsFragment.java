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

import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.activity.SettingsDetailsActivity;
import org.mariotaku.twidere.model.Panes;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsDetailsFragment extends PreferenceFragment implements Constants, Panes.Right {

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getPreferenceManager().setSharedPreferencesName(SHARED_PREFERENCES_NAME);
		final Bundle args = getArguments();
		final int resId = args != null ? args.getInt(INTENT_KEY_RESID) : 0;
		if (resId != 0) {
			addPreferencesFromResource(resId);
		}
		final Activity activity = getActivity();
		if (activity instanceof SettingsDetailsActivity) {
			activity.setTitle(getPreferenceScreen().getTitle());
		}
	}

}
