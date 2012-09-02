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

package org.mariotaku.twidere.model;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
 
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.view.ExtendedRelativeLayout;

public class StatusViewHolder {

	public final ImageView profile_image, image_preview;
	public final TextView name, text, time, reply_retweet_status;
	private final View content, gap_indicator;
	private final ExtendedRelativeLayout status_content;
	public boolean show_as_gap;
	private boolean account_color_enabled;
	private float text_size;

	public StatusViewHolder(View view, Context context) {
		content = view;
		status_content = (ExtendedRelativeLayout) view.findViewById(R.id.status_content);
		gap_indicator = view.findViewById(R.id.list_gap_text);
		profile_image = (ImageView) view.findViewById(R.id.profile_image);
		image_preview = (ImageView) view.findViewById(R.id.image_preview);
		name = (TextView) view.findViewById(R.id.name);
		text = (TextView) view.findViewById(R.id.text);
		time = (TextView) view.findViewById(R.id.time);
		reply_retweet_status = (TextView) view.findViewById(R.id.reply_retweet_status);
	}

	public void setAccountColor(int color) {
		status_content.drawRight(account_color_enabled ? color : Color.TRANSPARENT);
	}

	public void setAccountColorEnabled(boolean enabled) {
		account_color_enabled = enabled;
		if (!enabled) {
			status_content.drawRight(Color.TRANSPARENT);
		}
	}
	
	public void setHighlightColor(int color) {
		status_content.drawBackground(color);
	}

	public void setSelected(boolean selected) {
		if (!show_as_gap) {
			content.setBackgroundResource(selected ? R.drawable.list_focused_holo : 0);
		} else {
			content.setBackgroundResource(0);
		}
	}

	public void setShowAsGap(boolean show_gap) {
		show_as_gap = show_gap;
		status_content.setVisibility(show_gap ? View.GONE : View.VISIBLE);
		gap_indicator.setVisibility(!show_gap ? View.GONE : View.VISIBLE);
	}

	public void setTextSize(float text_size) {
		if (this.text_size != text_size) {
			this.text_size = text_size;
			text.setTextSize(text_size);
			name.setTextSize(text_size * 1.05f);
			time.setTextSize(text_size * 0.65f);
			reply_retweet_status.setTextSize(text_size * 0.65f);
		}
	}

	public void setUserColor(int color) {
		status_content.drawLeft(color);
	}

}