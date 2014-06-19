/**
    LifeStream - Instant Photo Sharing
    Copyright (C) 2014 Kayateia

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kayateia.lifestream;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * @author kaya
 *
 */
public class CheckAlarm extends BroadcastReceiver {
	private static final String LOG_TAG = "LifeStream/CheckAlarm";
	private static final int WAKELOCK_TIMEOUT = 1000;

	static public void SetAlarm(Context context) {
		AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(context, CheckAlarm.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
		am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 * 60, pi); // Millisec * Second * Minute
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

		try {
			if (wl != null)
				wl.acquire(WAKELOCK_TIMEOUT);
			Log.v(LOG_TAG, "CheckAlarm kick received");

			// Check for things that need to be compressed for transfer.
			CaptureService.Kick(context);

			// Don't do anything if there's no network.
			if (Network.IsActive(context)) {
				// Kick the streaming service to do the actual work.
				StreamService.Kick(context);

				// Kick the upload service too, to catch any stragglers.
				UploadService.Kick(context);
			}
		} finally {
			Log.v(LOG_TAG, "Release wakelock by default");
			if (wl != null && wl.isHeld())
				wl.release();
		}
	}
}
