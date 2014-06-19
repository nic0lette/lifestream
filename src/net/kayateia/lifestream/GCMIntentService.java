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

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.AsyncTask;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService {
	private static final String LOG_TAG = "LifeStream/GCMIntentService";
	public final static String GCM_SENDER_ID = "<GCM Sender ID from Google>";

	public GCMIntentService() {
		// Defined by creating the API key.
		super(GCM_SENDER_ID);
	}

	@Override
	public void onRegistered(final Context context, final String regId) {
		// Called after a registration intent is received, passes the registration ID assigned by GCM to that device/application pair as parameter. Typically, you should send the regid to your server so it can use it to send messages to this device.
		Log.i(LOG_TAG, "GCM registered " + regId);

		// This is done inside a wake lock, so do it in a thread.
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... foo) {
				// Repeat the login process, this time with GCM ID.
				Settings settings = new Settings(context);
				settings.setGcmId(regId);
				settings.commit();
				String result = Network.DoLogin(context, settings.getUserName(), settings.getPassword());
				if (result == null)
					Log.e(LOG_TAG, "Re-register for GCM ID was not successful.");
				return null;
			}
		}.execute();
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		// Called after the device has been unregistered from GCM. Typically, you should send the regid to the server so it unregisters the device.
		Log.i(LOG_TAG, "GCM unregistered " + regId);
	}

	@Override
	public void onMessage(Context context, Intent intent) {
		// Called when your server sends a message to GCM, and GCM delivers it to the device. If the message has a payload, its contents are available as extras in the intent.
		Log.i(LOG_TAG, "GCM Message received");
		StreamService.Kick(context);
	}

	@Override
	public void onError(Context context, String errorId) {
		// Called when the device tries to register or unregister, but GCM returned an error. Typically, there is nothing to be done other than evaluating the error (returned by errorId) and trying to fix the problem.
		Log.e(LOG_TAG, "GCM Error: " + errorId);
	}
}
