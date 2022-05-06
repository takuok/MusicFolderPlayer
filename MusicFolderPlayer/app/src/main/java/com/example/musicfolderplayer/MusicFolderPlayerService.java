package com.example.musicfolderplayer;

import java.io.File;
import java.util.Collections;
import java.util.List;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;

public class MusicFolderPlayerService extends Service {
	private static final String TAG = "MFP.Service";
	public static final String INTENT_ACTION = "MusicFolderPlayer:Action";
	public static final String COMMAND_PLAY_PAUSE = INTENT_ACTION + ":PlayPause2";
	public static final String COMMAND_NEXT = INTENT_ACTION + ":Next";
	public static final String COMMAND_PREV = INTENT_ACTION + ":Prev";
	public static final String COMMAND_STOP = INTENT_ACTION + ":Stop";
	protected static final String EXTRA_NOTIFY = "n";
	protected static final String EXTRA_DURATION = "d";
	protected static final String EXTRA_CURRENT = "c";
	protected static final String EXTRA_PATH = "p";
	protected static final String NOTIFY_POSITION = "position";
	protected static final String NOTIFY_PLAYING = "playing";
	protected static final String NOTIFY_PREPARE = "prepare";
	protected static final String NOTIFY_STOP = "stop";
	protected static final String NOTIFY_END = "end";
	private static final int FORGROUND_ID = 0x1;
	private final IBinder mBinder = new MyBinder();
	private Thread mPlayThread;
	private PlayList mPlayList = new PlayList();
	private boolean mIsPlaying;
	private File mPlayFile;
	private File mPlayFileNext;
	private MediaPlayer mMediaPlayer;
	protected boolean mIsNotify; // Activityに各種通知をするか
	private int mResumePos = -1; // 曲を一時停止した位置
	private int mDuration;
	private boolean mIsPause; // Activityが生きていてPauseのとき
	protected boolean mIsTermThread;
	private MyBroadcastReceiver mReceiver;
	private boolean mIsHeadphoneConnect;
	private ComponentName mComponentName;
	private NotificationCompat.Builder mNotificationBuilder;

	public class MyBinder extends Binder {
		MusicFolderPlayerService getService() {
			return MusicFolderPlayerService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand");

		String chID = "MFP";
		String chName = "MusicFolderPlayer";
		NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (mgr.getNotificationChannel(chID) == null) {
			NotificationChannel ch = new NotificationChannel(chID, chName, NotificationManager.IMPORTANCE_DEFAULT);
			ch.setDescription("起動中");
			mgr.createNotificationChannel(ch);
		}

		Intent t_intent = new Intent(this, MainActivity.class);
		t_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pintent = PendingIntent.getActivity(this, 0, t_intent, 0);

		Intent pauseIntent = new Intent(this, RemoteControllReceiver.class);
		pauseIntent.setAction(Intent.ACTION_MEDIA_BUTTON);
		pauseIntent.putExtra("keyCode", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
		PendingIntent pausePendingIntent = PendingIntent.getBroadcast(this, 1, pauseIntent, 0);

		Intent clearIntent = new Intent(this, ClearReceiver.class);
		PendingIntent clearPendingIntent = PendingIntent.getBroadcast(this, 2, clearIntent, 0);

		mNotificationBuilder = new NotificationCompat.Builder(this, chID).setContentIntent(pintent)
				.setSmallIcon(R.drawable.ic_launcher).setTicker("Start MusicFolderPlayerService")
				.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
				.addAction(android.R.drawable.ic_notification_clear_all, "Clear", clearPendingIntent)
				.setContentTitle("MusicFolderPlayer").setContentText("サービス起動中").setWhen(System.currentTimeMillis());

		startForeground(FORGROUND_ID, mNotificationBuilder.build());

		registerBroadcastReceiver();

		return Service.START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind");
		try {
			procOnBind();
		} catch (Exception e) {
			SdLog.put("onBind) " + e);
		}

		return mBinder;
	}

	@Override
	public void onRebind(Intent intent) {
		Log.i(TAG, "onRebind");
		try {
			procOnBind();
		} catch (Exception e) {
			SdLog.put("onRebind) " + e);
		}
	}

	private void procOnBind() {
		mIsNotify = true;
		if (mPlayFile != null && mResumePos >= 0) {
			mMediaPlayer = createMediaPlayer(mPlayFile);
			try {
				mMediaPlayer.seekTo(mResumePos);
				mIsPause = true;
			} catch (Exception e) {
				SdLog.put("seekTo) " + e);
			}
		}
		try {
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("procOnBind-interruptPlayThread) " + e);
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		boolean result = false;
		try {
			result = super.onUnbind(intent);
			Log.i(TAG, "onUnbind:" + result);
			if (mIsPause) {
				releaseMediaPlayer();
			}
		} catch (Exception e) {
			SdLog.put("onUnbind) " + e);
		}
		mIsNotify = false;
		mIsPause = false;
		return true;
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		mIsTermThread = true;
		try {
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("onDestroy-interruptPlayThread) " + e);
		}
	}

	private void startThread() {
		if (mPlayThread != null) {
			try {
				interruptPlayThread();
			} catch (Exception e) {
				SdLog.put("startThread-interruptPlayThread) " + e);
			}
			return;
		}
		mPlayThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!mIsTermThread) {
						threadProc();
						long t = 60 * 60 * 1000;
						if (mPlayFile != null && !mIsPause) {
							if (mIsNotify) {
								t = 1 * 1000;
							} else {
								int cur = mMediaPlayer.getCurrentPosition();
								if (mDuration <= 0) {
									mDuration = mMediaPlayer.getDuration();
								}
								if (mDuration <= 0) {
									t = 1 * 1000;
								} else {
									t = Math.max(1, Math.min(60 * 60 * 1000, mDuration - cur - 30 * 1000));
								}
							}
						}
						try {
							if (t > 1000) {
								Log.d(TAG, "sleep " + t);
							} else {
								Log.v(TAG, "sleep " + t);
							}
							long bef = System.currentTimeMillis();
							Thread.sleep(t);
							long aft = System.currentTimeMillis();
							if (aft - bef > t + 1000) {
								Log.w(TAG, "Long sleep...");
							}
						} catch (InterruptedException e) {
							Log.d(TAG, "interrupted");
						}
					}
					if (mMediaPlayer != null) {
						synchronized (mMediaPlayer) {
							if (mMediaPlayer.isPlaying()) {
								mMediaPlayer.stop();
							}
							releaseMediaPlayer();
						}
					}
				} catch (Exception e) {
					SdLog.put("run) " + e);
				}
				mPlayThread = null;
			}
		}, "PlayThread");
		mPlayThread.start();
	}

	private void threadProc() {
		try {
			if (mMediaPlayer == null) {
				// mPlayFile = null;
			}
			if (mPlayFile != null) {
				if (!mIsPause && mResumePos < 0 && mMediaPlayer != null) {
					// 再生中
					notifyPosition();
				}
			} else {
				// 次の曲を再生
				releaseMediaPlayer(); // 以前のを解放
				if (mResumePos < 0) {
					// 再開じゃなければ次の曲
					if (mPlayFileNext == null) {
						mPlayFile = mPlayList.next();
					} else {
						mPlayFile = mPlayFileNext;
						mPlayFileNext = null;
					}
				} else {
					mPlayFile = mPlayFileNext; // 再開用の情報を入れてある
				}
				if (mPlayFile == null) {
					// 再生する曲がない
					mIsPlaying = false;
					notifyEnd();
					return;
				}
				try {
					mMediaPlayer = createMediaPlayer(mPlayFile);
					synchronized (mMediaPlayer) {
						if (mResumePos >= 0) {
							// 再開するならシーク
							mMediaPlayer.seekTo(mResumePos);
						}
						mMediaPlayer.start();
					}
					notifyPlay(mPlayFile);
					notifyPosition();
					mResumePos = -1;
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (SecurityException e) {
					e.printStackTrace();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			SdLog.put("threadProc) " + e);
		}
	}

	private MediaPlayer createMediaPlayer(File f) {
		MediaPlayer mp = MediaPlayer.create(getApplicationContext(), Uri.fromFile(f));
		mp.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				Log.i(TAG, "onCompletion");
				mPlayFile = null;
				mDuration = 0;
				try {
					interruptPlayThread();
				} catch (Exception e) {
					SdLog.put("onCompletion-interruptPlayThread) " + e);
				}
			}
		});
		{
			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (mComponentName != null) {
				am.unregisterMediaButtonEventReceiver(mComponentName);
				mComponentName = null;
			}
			mComponentName = new ComponentName(this, RemoteControllReceiver.class);
			am.registerMediaButtonEventReceiver(mComponentName);
		}
		return mp;
	}

	private void interruptPlayThread() {
		if (mPlayThread != null) {
			synchronized (mPlayThread) {
				mPlayThread.interrupt();
			}
		}
	}

	/**
	 * サービスからの情報受信機の登録
	 */
	private void registerBroadcastReceiver() {
		try {
			mReceiver = new MyBroadcastReceiver();
			IntentFilter f = new IntentFilter();
			f.addAction(Intent.ACTION_HEADSET_PLUG);
			f.addAction(COMMAND_PLAY_PAUSE);
			f.addAction(COMMAND_NEXT);
			f.addAction(COMMAND_PREV);
			f.addAction(COMMAND_STOP);
			registerReceiver(mReceiver, f);
		} catch (Exception e) {
			SdLog.put("registerBroadcastReceiver) " + e);
		}
	}

	private class MyBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent == null) {
				return;
			}
			String act = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(act)) {
				if (intent.getIntExtra("state", 0) == 1) {
					// 接続時
					Log.v(TAG, "ヘッドフォン接続");
					mIsHeadphoneConnect = true;
				} else if (intent.getIntExtra("state", 0) == 0) {
					// 切断時
					if (mIsPlaying) {
						Log.d(TAG, "ヘッドフォン切断による再生停止");
						if (mIsHeadphoneConnect) {
							mIsHeadphoneConnect = false;
							try {
								stopPlay();
							} catch (Exception e) {
								SdLog.put("onReceive-stopPlay) " + e);
							}
						}
					}
				}
			} else if (COMMAND_PLAY_PAUSE.equals(act)) {
				if (mIsPlaying) {
					stopPlay();
				} else {
					startPlay();
				}
			} else if (COMMAND_NEXT.equals(act)) {
				fwSong();
			} else if (COMMAND_PREV.equals(act)) {
				rewSong();
			} else if (COMMAND_STOP.equals(act)) {
				if (mIsPlaying) {
					stopPlay();
				}
			}
		}
	}

	// ---------------------------------------------------------------------

	private void notifyPosition() {
		if (!mIsNotify) {
			return;
		}
		int c = mResumePos;
		int d = mDuration;
		if (mMediaPlayer == null) {
			if (c < 0) {
				// まだ再生していないので情報がない
				c = d = 0;
			}
		} else {
			synchronized (mMediaPlayer) {
				d = mDuration = mMediaPlayer.getDuration();
				if (c < 0) {
					c = mMediaPlayer.getCurrentPosition();
				}
			}
		}
		Intent intent = new Intent();
		intent.setAction(INTENT_ACTION);
		intent.putExtra(EXTRA_NOTIFY, NOTIFY_POSITION);
		intent.putExtra(EXTRA_DURATION, d / 1000);
		intent.putExtra(EXTRA_CURRENT, c / 1000);
		getBaseContext().sendBroadcast(intent);
	}

	private void notifyPrepare(File f) {
		if (!mIsNotify || f == null) {
			return;
		}
		Intent intent = new Intent();
		intent.setAction(INTENT_ACTION);
		intent.putExtra(EXTRA_NOTIFY, NOTIFY_PREPARE);
		intent.putExtra(EXTRA_PATH, f.getAbsolutePath());
		getBaseContext().sendBroadcast(intent);
	}

	private void notifyPlay(File f) {
		if (!mIsNotify || f == null) {
			return;
		}
		Intent intent = new Intent();
		intent.setAction(INTENT_ACTION);
		intent.putExtra(EXTRA_NOTIFY, NOTIFY_PLAYING);
		intent.putExtra(EXTRA_PATH, f.getAbsolutePath());
		getBaseContext().sendBroadcast(intent);

		mNotificationBuilder.setContentTitle(f.getParentFile().getName());
		mNotificationBuilder.setContentText(f.getName());
		startForeground(FORGROUND_ID, mNotificationBuilder.build());
	}

	private void notifyStop() {
		if (!mIsNotify) {
			return;
		}
		Intent intent = new Intent();
		intent.setAction(INTENT_ACTION);
		intent.putExtra(EXTRA_NOTIFY, NOTIFY_STOP);
		getBaseContext().sendBroadcast(intent);
	}

	private void notifyEnd() {
		if (!mIsNotify) {
			return;
		}
		Intent intent = new Intent();
		intent.setAction(INTENT_ACTION);
		intent.putExtra(EXTRA_NOTIFY, NOTIFY_END);
		getBaseContext().sendBroadcast(intent);
	}

	// ---------------------------------------------------------------------------

	public boolean requestNotifyPlayingInfo() {
		mIsNotify = true;
		if (!mIsPlaying && mResumePos < 0) {
			return false;
		}
		try {
			if (mIsPlaying) {
				notifyPlay(mPlayFile);
			} else {
				notifyPrepare(mPlayFile);
			}
			notifyPosition();
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("requestNotifyPlayingInfo) " + e);
		}
		return mIsPlaying;
	}

	public void setIsNotify(boolean isNotify) {
		mIsNotify = isNotify;
	}

	public void clearPlayList() {
		try {
			mPlayList.clear();
			mPlayFileNext = null;
			mResumePos = -1;
			mIsPause = false;
			if (!mIsPlaying) {
				mPlayFile = null;
				notifyEnd();
				releaseMediaPlayer();
			}
		} catch (Exception e) {
			SdLog.put("clearPlayList) " + e);
		}
	}

	private void releaseMediaPlayer() {
		if (mMediaPlayer == null) {
			return;
		}
		synchronized (mMediaPlayer) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		if (mComponentName != null) {
			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			am.unregisterMediaButtonEventReceiver(mComponentName);
			mComponentName = null;
		}
	}

	public void addToPlayList(List<File> list) {
		try {
			mPlayList.add(list);
		} catch (Exception e) {
			SdLog.put("addToPlayList) " + e);
		}
	}

	public void addToPlayList(File f) {
		try {
			mPlayList.add(f);
		} catch (Exception e) {
			SdLog.put("addToPlayList2) " + e);
		}
	}

	public boolean startPlay() {
		try {
			if (mResumePos < 0) {
				// 新規再生
				mPlayFile = null;
				mPlayFileNext = mPlayList.getCurrent();
				if (mPlayFileNext == null) {
					mPlayFileNext = mPlayList.top();
				}
				if (mPlayFileNext == null) {
					return false;
				}
				notifyPlay(mPlayFileNext);
				startThread();
			} else {
				// Pause状態からの復帰
				if (mMediaPlayer == null) {
					mPlayFileNext = mPlayList.getCurrent();
					mPlayFile = null;
					if (mPlayFileNext == null) {
						Log.w(TAG, "resume from pause failed");
						return false;
					}
				} else {
					synchronized (mMediaPlayer) {
						mResumePos = -1;
						mMediaPlayer.start();
						for (int i = 0; i < 100; i++) {
							if (mMediaPlayer.getCurrentPosition() > 0) {
								break;
							}
							try {
								Thread.sleep(10);
							} catch (InterruptedException e) {
							}
						}
						if (mPlayFile != null) {
							notifyPlay(mPlayFile);
						}
					}
				}
				mIsPause = false;
				startThread();
			}
			mIsPlaying = true;
		} catch (Exception e) {
			SdLog.put("startPlay) " + e);
		}

		return true;
	}

	public void stopPlay() {
		try {
			mIsPlaying = false;
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.pause();
					mResumePos = mMediaPlayer.getCurrentPosition();
					mIsPause = true;
				}
			}
			notifyStop();
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("stopPlay) " + e);
		}
	}

	public void seek(int pos, int max) {
		try {
			if (mMediaPlayer == null) {
				return;
			}
			if (mDuration <= 0 || max <= 0) {
				return;
			}
			int time = mDuration * pos / max;
			for (int i = 0; i < 100; i++) {
				mMediaPlayer.seekTo(time);
				if (Math.abs(mMediaPlayer.getCurrentPosition() - time) < 1000) {
					break;
				}
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		} catch (Exception e) {
			SdLog.put("seek) " + e);
		}
	}

	public void seekSec(int sec) {
		try {
			if (mMediaPlayer == null) {
				return;
			}
			if (mDuration <= 0 || sec == 0) {
				return;
			}
			int t = mMediaPlayer.getCurrentPosition() + (sec * 1000);
			t = Math.max(0, Math.min(mDuration, t));
			mMediaPlayer.seekTo(t);
		} catch (Exception e) {
			SdLog.put("seek) " + e);
		}
	}

	public boolean rewSong() {
		try {
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.stop();
				}
			} else {
				mResumePos = -1;
			}
			mPlayFileNext = mPlayList.prev();
			if (mPlayFileNext == null) {
				mPlayFileNext = mPlayList.next();
			}
			notifyPlay(mPlayFileNext);
			notifyPosition();
			mPlayFile = null;
			releaseMediaPlayer();
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("rewSong) " + e);
		}
		return true;
	}

	public boolean fwSong() {
		try {
			if (mPlayList.getNext() == null) {
				// 次の曲がない
				return false;
			}
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.stop();
				}
			} else {
				mResumePos = -1;
			}
			mPlayFile = null;
			releaseMediaPlayer();
			interruptPlayThread();
		} catch (Exception e) {
			SdLog.put("fwSong) " + e);
		}
		return true;
	}

	public PlayList getPlayList() {
		return mPlayList;
	}
}
