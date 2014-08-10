package com.example.musicfolderplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayList {
	private List<File> mList = new ArrayList<File>();
	private int mCurIdx = -1;
	private boolean mIsRepeat;

	public synchronized void add(File f) {
		if (!f.isDirectory()) {
			mList.add(f);
			return;
		}
		List<File> folderList = new ArrayList<File>();
		List<File> songList = new ArrayList<File>();
		File[] ls = f.listFiles();
		for (int i = 0; i < ls.length; i++) {
			f = ls[i];
			if (f.isDirectory()) {
				folderList.add(f);
			} else if (f.getName().toLowerCase(Locale.JAPANESE).indexOf(".mp3") > 0) {
				songList.add(f);
			}
		}
		// ƒtƒHƒ‹ƒ_
		Collections.sort(folderList);
		for (File fd : folderList) {
			add(fd);
		}
		// ‹È
		Collections.sort(songList);
		mList.addAll(songList);
	}

	public synchronized File top() {
		mCurIdx = -1;
		return next();
	}

	public synchronized File next() {
		if (mList.isEmpty()) {
			return null;
		}
		int i = mCurIdx + 1;
		if (i >= mList.size()) {
			if (mIsRepeat) {
				i = 0;
			} else {
				mCurIdx = -2;
				return null;
			}
		}
		if (mCurIdx > -2) {
			mCurIdx = i;
		}
		if (i < 0)
			return null;

		return mList.get(i);
	}

	public synchronized File prev() {
		if (mList.isEmpty() || mCurIdx < 0) {
			return null;
		}
		mCurIdx--;
		if (mCurIdx < 0) {
			return null;
		}
		return mList.get(mCurIdx);
	}

	public synchronized File getCurrent() {
		return get(mCurIdx);
	}

	public synchronized File getNext() {
		return get(mCurIdx + 1);
	}

	public synchronized File getPrev() {
		return get(mCurIdx - 1);
	}

	public synchronized File get(int idx) {
		if (idx < 0 || idx >= mList.size()) {
			return null;
		}
		return mList.get(idx);
	}

	public synchronized void clear() {
		mList.clear();
		mCurIdx = -1;
	}

	public void add(List<File> list) {
		for (File f : list) {
			this.add(f);
		}
	}

	public int size() {
		return mList.size();
	}

	public int getPosition() {
		return mCurIdx;
	}

}
