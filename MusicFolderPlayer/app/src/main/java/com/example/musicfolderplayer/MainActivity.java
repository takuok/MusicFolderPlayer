package com.example.musicfolderplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
	private static final String TAG = "MFP.MainActivity";
	private MainActivity mThis = this;
	private DirListAdapter mAdapterDir;
	private PlayListAdapter mAdapterPlayList;

	private String mRootPath;

	private Button mBtnUp;
	private Button mBtnPlay;
	private Button mBtnStop;
	private ListView mListViewDir;
	private ListView mListViewPlayList;
	private String mCurPath;
	private boolean mIsPlaying;

	private TextView mTxtPath;
	private Button mBtnList;
	private Button mBtnClr;
	private Button mBtnRew;
	private Button mBtnFw;
	private Button mBtnRew5;
	private Button mBtnRew10;
	private Button mBtnRew30;
	private Button mBtnFw5;
	private Button mBtnFw30;
	private Button mBtnClose;
	private SeekBar mSeekBar;
	private TextView mTxtArtist;
	private TextView mTxtSong;
	private TextView mTxtAlbum;
	private TextView mTxtCurTime;
	private TextView mTxtDuration;
	private ViewGroup mLayPlayList;

	private static class ListPos {
		int topPos;
		int topY;
	}

	private Map<String, ListPos> mListPos = new HashMap<String, ListPos>();

	private MusicFolderPlayerService mService;

	private ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "onServiceDisconnected");
			mService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "onServiceConnected");
			mService = ((MusicFolderPlayerService.MyBinder) service).getService();
//			startService(new Intent(MainActivity.this, MusicFolderPlayerService.class));
			mIsPlaying = mService.requestNotifyPlayingInfo();
			setPlayStopBtnVisible();
		}
	};
	private MyBroadcastReceiver mReceiver;

	/**
	 * 生成時
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SdLog.put("MainActivity#OnCreate");
		setContentView(R.layout.activity_main);

		mAdapterDir = new DirListAdapter(getLayoutInflater());
		mAdapterPlayList = new PlayListAdapter(getLayoutInflater());

		if (Build.MODEL.startsWith("Nexus")) {
			mRootPath = "/storage/emulated/0/Music";
//		} else if (Build.MODEL.startsWith("ASUS")) {
//			mRootPath = "/sdcard/Music";
		} else {
			File file = Environment.getExternalStorageDirectory();
			mRootPath = file.getPath() + "/Music";
			// mRootPath = file.getPath() + "/external_sd/My Music";
		}

		init();

//		Handler h = new Handler();
//		h.postDelayed(
//		new Runnable() {
//			@Override
//			public void run() {
//				init();
//			}
//		}, 1000);
	}

	private void init() {
		int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
		if (permission != PackageManager.PERMISSION_GRANTED) {
			// We don't have permission so prompt the user
			String[] perms = {
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
			};
			ActivityCompat.requestPermissions(
					MainActivity.this,
					perms,
					1
			);
			return;
		}
		Intent intent = new Intent(this, MusicFolderPlayerService.class);
		startForegroundService(intent);

		initViews();

		File[] dirArr = getExternalFilesDirs( null );
		for (File dir: dirArr) {
			if (!Environment.isExternalStorageRemovable(dir)) continue;
			while (true) {
				String s = dir.getAbsolutePath();
				if (!s.contains("Android")) {
					mRootPath = s + "/Music";
					break;
				}
				dir = dir.getParentFile();
			}
		}

		File f = new File( mRootPath );
		if (!f.exists()) {
			Log.e(TAG, "Music folder not found!");
		}

		setCurPath(mRootPath);
		updateListView();

	}

	/**
	 * View関係の初期化
	 */
	private void initViews() {
		mListViewDir = (ListView) this.findViewById(R.id.LST_dir);
		mListViewPlayList = (ListView) this.findViewById(R.id.LST_playList);
		mLayPlayList = (ViewGroup) this.findViewById(R.id.LAY_playList);
		mLayPlayList.setVisibility(View.INVISIBLE);

		mTxtPath = (TextView) this.findViewById(R.id.TXT_path);
		mTxtArtist = (TextView) this.findViewById(R.id.TXT_artist);
		mTxtAlbum = (TextView) this.findViewById(R.id.TXT_album);
		mTxtSong = (TextView) this.findViewById(R.id.TXT_song);
		mTxtCurTime = (TextView) this.findViewById(R.id.TXT_curTime);
		mTxtDuration = (TextView) this.findViewById(R.id.TXT_duration);

		mSeekBar = (SeekBar) this.findViewById(R.id.SKB_play);

		mBtnUp = (Button) this.findViewById(R.id.BTN_up);
		mBtnPlay = (Button) this.findViewById(R.id.BTN_play);
		mBtnStop = (Button) this.findViewById(R.id.BTN_stop);
		mBtnList = (Button) this.findViewById(R.id.BTN_list);
		mBtnClr = (Button) this.findViewById(R.id.BTN_clr);
		mBtnRew = (Button) this.findViewById(R.id.BTN_rew);
		mBtnFw = (Button) this.findViewById(R.id.BTN_fw);
		mBtnRew5 = (Button) this.findViewById(R.id.BTN_rew5);
		mBtnRew10 = (Button) this.findViewById(R.id.BTN_rew10);
		mBtnRew30 = (Button) this.findViewById(R.id.BTN_rew30);
		mBtnFw5 = (Button) this.findViewById(R.id.BTN_fw5);
		mBtnFw30 = (Button) this.findViewById(R.id.BTN_fw30);
		mBtnClose = (Button) this.findViewById(R.id.BTN_close);
		setPlayingSongText(null);

		mBtnPlay.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startPlay();
			}
		});
		mBtnStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				stopPlay();
			}
		});
		mBtnUp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				File f = new File(mCurPath);
				String parent = f.getParent();
				if (parent.length() >= mRootPath.length()) {
					setCurPath(parent);
				}
			}
		});
		mBtnList.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mAdapterPlayList.setPlayList(mService.getPlayList());
				mLayPlayList.setVisibility(View.VISIBLE);
			}
		});
		mBtnClr.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.clearPlayList();
			}
		});
		mBtnRew.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.rewSong();
			}
		});
		mBtnFw.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.fwSong();
			}
		});
		mBtnRew5.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.seekSec(-5);
			}
		});
		mBtnRew10.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.seekSec(-10);
			}
		});
		mBtnRew30.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.seekSec(-30);
			}
		});
		mBtnFw5.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.seekSec(5);
			}
		});
		mBtnFw30.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mService.seekSec(30);
			}
		});
		mBtnClose.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mLayPlayList.setVisibility(View.GONE);
				mAdapterPlayList.setPlayList(null);
			}
		});

		setPlayStopBtnVisible();

		mListViewDir.setAdapter(mAdapterDir);
		mListViewDir.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				File f = mAdapterDir.getItem(position);
				if (f.isDirectory()) {
					setCurPath(f.getAbsolutePath());
				}
			}
		});

		mListViewPlayList.setAdapter(mAdapterPlayList);

		mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				mService.seek(seekBar.getProgress(), seekBar.getMax());
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}
		});
		mListViewDir.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				File f = mAdapterDir.getItem(position);
				if (f.isDirectory()) {
					mService.addToPlayList(f);
				} else {
					// 選択位置以降の全曲を追加
					List<File> ls = new ArrayList<File>();
					int n = mAdapterDir.getCount();
					for (int i = position; i < n; i++) {
						f = mAdapterDir.getItem(i);
						if (!f.isDirectory()) {
							ls.add(f);
						}
					}
					if (ls.isEmpty()) {
						// 曲が無い...
						return true;
					}
					mService.addToPlayList(ls);
				}
				if (!mIsPlaying) {
					startPlay();
				}
				return true;
			}
		});
	}

	@Override
	public void onPause() {
		Log.i(TAG, "onPause");
		if (mService != null) {
			mService.setIsNotify(false);
		}
		unregisterReceiver(mReceiver);
		mReceiver = null;
		try {
			unbindService(mServiceConnection);
		} catch (Exception e) {
			Log.w(TAG, "unbindService : " + e);
		}
		mService = null;
		super.onPause();
	}

	@Override
	public void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		if (mService != null) {
			mService.setIsNotify(true);
		}
		registerBroadcastReceiver();
		if (mService == null) {
			Intent intent = new Intent(getApplicationContext(), MusicFolderPlayerService.class);
			bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
		}
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		super.onDestroy();
	}

	private void setPlayStopBtnVisible() {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mBtnPlay.setVisibility((mIsPlaying) ? View.INVISIBLE : View.VISIBLE);
				mBtnStop.setVisibility((mIsPlaying) ? View.VISIBLE : View.INVISIBLE);
			}
		});
	}

	/**
	 * カレントパスの設定
	 * 
	 * @param parent
	 */
	private void setCurPath(String parent) {
		if (mCurPath != null) {
			// リストスクロール位置を保存
			ListPos lp = new ListPos();
			lp.topPos = mListViewDir.getFirstVisiblePosition();
			lp.topY = mListViewDir.getChildAt(0).getTop();
			mListPos.put(mCurPath, lp);
		}
		mCurPath = parent;
		int i2 = mCurPath.lastIndexOf('/');
		int i1 = mCurPath.lastIndexOf('/', i2 - 1);
		String s1 = mCurPath.substring(i1 + 1, i2);
		String s2 = mCurPath.substring(i2 + 1);
		mTxtPath.setText(s1 + "\n" + s2);
		updateListView();
	}

	/**
	 * ディレクトリ表示リストの更新
	 */
	private void updateListView() {
		int topPos = 0;
		int topY = 0;
		mAdapterDir.updateList(mCurPath);
		ListPos lp = mListPos.get(mCurPath);
		if (lp != null) {
			topPos = lp.topPos;
			topY = lp.topY;
		}
		mListViewDir.setSelectionFromTop(topPos, topY);
	}

	/**
	 * ファイルパス名から曲名を取得
	 * 
	 * @param path
	 * @return
	 */
	private CharSequence getSongNameFromPath(String path) {
		int i = path.lastIndexOf('/');
		if (i < 0) {
			return path;
		}
		return path.substring(i + 1);
	}

	/**
	 * 再生
	 * 
	 * @return
	 */
	private boolean startPlay() {
		if (!mService.startPlay()) {
			return false;
		}
		mIsPlaying = true;
		setPlayStopBtnVisible();
		return true;
	}

	/**
	 * 停止
	 */
	private void stopPlay() {
		mService.stopPlay();
		mIsPlaying = false;
		setPlayStopBtnVisible();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		stopService(new Intent(MainActivity.this, MusicFolderPlayerService.class));
		return true;
	}

	/**
	 * サービスからの情報受信機の登録
	 */
	private void registerBroadcastReceiver() {
		mReceiver = new MyBroadcastReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(MusicFolderPlayerService.INTENT_ACTION);
		registerReceiver(mReceiver, intentFilter);
	}

	/**
	 * サービスからの情報受信
	 */
	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent == null) {
				return;
			}
			if (!MusicFolderPlayerService.INTENT_ACTION.equals(intent.getAction())) {
				return;
			}
			String notify = intent.getStringExtra(MusicFolderPlayerService.EXTRA_NOTIFY);
			if (notify.equals(MusicFolderPlayerService.NOTIFY_POSITION)) {
				int c = intent.getIntExtra(MusicFolderPlayerService.EXTRA_CURRENT, 0);
				int d = intent.getIntExtra(MusicFolderPlayerService.EXTRA_DURATION, 0);
				setTimeText(c, d);
			} else if (notify.equals(MusicFolderPlayerService.NOTIFY_PREPARE)) {
				final String path = intent.getStringExtra(MusicFolderPlayerService.EXTRA_PATH);
				setPlayingSongText(path);
			} else if (notify.equals(MusicFolderPlayerService.NOTIFY_PLAYING)) {
				mIsPlaying = true;
				final String path = intent.getStringExtra(MusicFolderPlayerService.EXTRA_PATH);
				setPlayingSongText(path);
				setPlayStopBtnVisible();
			} else if (notify.equals(MusicFolderPlayerService.NOTIFY_STOP)) {
				mIsPlaying = false;
				setPlayStopBtnVisible();
			} else if (notify.equals(MusicFolderPlayerService.NOTIFY_END)) {
				mIsPlaying = false;
				setPlayingSongText(null);
				setTimeText(0, 0);
				setPlayStopBtnVisible();
			}
		}

		/**
		 * 再生時刻のテキスト設定
		 * 
		 * @param c
		 * @param d
		 */
		private void setTimeText(final int c, final int d) {
			mThis.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (d <= 0) {
						mTxtDuration.setText("---:--");
						mTxtCurTime.setText("---:--");
					} else {
						mTxtDuration.setText(String.format("%3d:%02d", d / 60, d % 60));
						mTxtCurTime.setText(String.format("%3d:%02d", c / 60, c % 60));
					}
					mSeekBar.setMax(d);
					mSeekBar.setProgress(c);
				}
			});
		}

	}

	/**
	 * 再生中の曲情報のテキスト設定
	 * 
	 * @param path
	 */
	private void setPlayingSongText(final String path) {
		if (path == null) {
			mThis.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mTxtSong.setText("--Song--");
					mTxtAlbum.setText("--Album--");
					mTxtArtist.setText("--Artist--");
					mIsPlaying = false;
				}
			});
			return;
		}
		int i = path.lastIndexOf('/');
		final String album = path.substring(path.lastIndexOf('/', i - 1) + 1, i);
		int n = mRootPath.length() + 1;
		final String artist = path.substring(n, path.indexOf('/', n));
		mThis.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mTxtSong.setText(getSongNameFromPath(path));
				mTxtAlbum.setText(album);
				mTxtArtist.setText(artist);
				mIsPlaying = true;
			}
		});
	}

	public void onClick(View v) {

	}
}
