package com.example.musicfolderplayer;

import java.io.File;
import java.util.Locale;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class PlayListAdapter extends BaseAdapter {
	private PlayList mPlayList;
	private LayoutInflater mInflater;

	public PlayListAdapter(LayoutInflater inflater) {
		super();
		mInflater = inflater;
	}

	public void setPlayList(PlayList playList) {
		mPlayList = playList;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		if (mPlayList == null) {
			return 0;
		}
		return mPlayList.size();
	}

	@Override
	public File getItem(int position) {
		if (mPlayList == null) {
			return null;
		}
		return mPlayList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		if (mPlayList == null) {
			return 0;
		}
		return mPlayList.get(position).isDirectory() ? 0 : 1;
	}

	private static Typeface mTypefaceFile = Typeface.create(Typeface.SERIF,
			Typeface.ITALIC);

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		TextView tv;
		if (view == null) {
			view = mInflater.inflate(R.layout.playlist_row, null);
			tv = (TextView) view.findViewById(R.id.TXT_main);
			view.setTag(tv);
		} else {
			tv = (TextView) view.getTag();
		}
		int color = 0xFF202020;
		if (mPlayList.getPosition() == position) {
			color = 0xFF4040FF;
		}
		tv.setTextColor(color);
		String s = "";
		File f = mPlayList.get(position);
		if (f != null) {
			if (!f.isDirectory()) {
				tv.setTypeface(mTypefaceFile);
			}
			String name = f.getName();
			int len = name.length() - 4;
			if (name.toLowerCase(Locale.JAPANESE).lastIndexOf(".mp3") == len) {
				name = name.substring(0, len);
			}
			s += name;
		}
		tv.setText(s);

		return view;
	}

}
