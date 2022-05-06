package com.example.musicfolderplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class DirListAdapter extends BaseAdapter {
	private List<File> mList = new ArrayList<File>();
	private LayoutInflater mInflater;

	public DirListAdapter(LayoutInflater inflater) {
		super();
		mInflater = inflater;
	}

	static class FileComparator implements Comparator<File> {

		@Override
		public int compare(File f0, File f1) {
			return f0.getName().toLowerCase(Locale.JAPANESE)
					.compareTo(f1.getName().toLowerCase(Locale.JAPANESE));
		}

	}

	public void updateList(String path) {
		mList.clear();
		List<File> listSong = new ArrayList<File>();
		File dir = new File(path);
		File[] flist = dir.listFiles();
		for (int i = 0; i < flist.length; i++) {
			File f = flist[i];
			if (f.isDirectory()) {
				mList.add(flist[i]);
			} else {
				String name = f.getName();
				int len = name.length() - 4;
				if (name.toLowerCase(Locale.JAPANESE).lastIndexOf(".mp3") == len) {
					listSong.add(flist[i]);
				}
			}
		}
		FileComparator cmp = new FileComparator();
		Collections.sort(mList, cmp);
		Collections.sort(listSong, cmp);
		mList.addAll(listSong);
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return mList.size();
	}

	@Override
	public File getItem(int position) {
		return mList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		return mList.get(position).isDirectory() ? 0 : 1;
	}

	private static Typeface mTypefaceDir = Typeface.create(Typeface.SANS_SERIF,
			Typeface.BOLD);
	private static Typeface mTypefaceFile = Typeface.create(Typeface.SERIF,
			Typeface.ITALIC);

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		TextView tv;
		if (view == null) {
			view = mInflater.inflate(R.layout.dirlist_row, null);
			tv = (TextView) view.findViewById(R.id.TXT_main);
			view.setTag(tv);
		} else {
			tv = (TextView) view.getTag();
		}
		String s = "";
		File f = mList.get(position);
		if (f != null) {
			String name = f.getName();
			if (f.isDirectory()) {
				tv.setTypeface(mTypefaceDir);

			} else {
				s = "  ";
				tv.setTypeface(mTypefaceFile);
				int len = name.length() - 4;
				if (name.toLowerCase(Locale.JAPANESE).lastIndexOf(".mp3") == len) {
					name = name.substring(0, len);
				}
			}
			s += name;
		}
		tv.setText(s);

		return view;
	}

}
