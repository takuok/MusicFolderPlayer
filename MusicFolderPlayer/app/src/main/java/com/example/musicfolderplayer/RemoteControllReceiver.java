package com.example.musicfolderplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class RemoteControllReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			int keyCode = 0;
			KeyEvent keyEvent = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (keyEvent != null) {
				if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
					keyCode = keyEvent.getKeyCode();
				}
			} else {
				keyCode = intent.getIntExtra("keyCode", 0);
			}
			if (keyCode == 0) return;
			SdLog.put("key:" + keyCode);
			String act = null;
			switch (keyCode) {
			case KeyEvent.KEYCODE_MEDIA_STOP:
				act = MusicFolderPlayerService.COMMAND_STOP;
				break;
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY:
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				act = MusicFolderPlayerService.COMMAND_PLAY_PAUSE;
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				act = MusicFolderPlayerService.COMMAND_NEXT;
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				act = MusicFolderPlayerService.COMMAND_PREV;
				break;

			}
			if (act != null) {
				context.sendBroadcast(new Intent(act));
			}
		}
	}

}
