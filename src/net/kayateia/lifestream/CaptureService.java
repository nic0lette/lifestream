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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * @author niya, kaya
 */
public class CaptureService extends Service {
	private static final String LOG_TAG = "LifeStream/CaptureService";
	private static final int NOTIFY_ID = 77;
	private static final int WAKELOCK_TIMEOUT = 30000;
	private static final int THUMBNAIL_SIZE = 200;

	public static final String UPLOAD_DIRECTORY_NAME = Media.BASE_DIR + "/upload";
	public static final String TEMP_EXTENSION = ".temp";

	private File _storagePath;

	// Manually kicks the capture service from elsewhere.
	static public void Kick(Context context) {
		final Intent captureServiceIntent = new Intent(context, CaptureService.class);
		context.startService(captureServiceIntent);
	}

	public CaptureService() {
		super();
	}

	/*
	 * Is there a reason to allow binding to this service?
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(LOG_TAG, "CaptureService kicked");
		checkForNewImages();

		return START_NOT_STICKY;
	}

	private boolean initStorage() {
		try {
			_storagePath = Media.InitStorage(getBaseContext(), UPLOAD_DIRECTORY_NAME, true);
		} catch (Exception e) {
			final Resources res = getResources();
			notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), e.getMessage());
		}

		return _storagePath != null;
	}

	private void notifyError(final String tickerText, final String title, final String text) {
		Notifications.NotifyError(getBaseContext(), NOTIFY_ID, false, tickerText, title, text);
	}

	private void checkForNewImages() {
		if (!Media.IsMediaMounted()) {
			Log.i(LOG_TAG, "External media isn't mounted, trying again later");
			return;
		}

		// If we're disabled or the config is empty, we can't even try to do anything anyway.
		final Settings settings = LifeStreamApplication.GetApp().GetSettings();
		final String username = settings.getUserName();
		final String authToken = settings.getAuthToken();
		if (username.isEmpty() || authToken.isEmpty() || !settings.getEnabled()) {
			Log.i(LOG_TAG, "Config is not set or not enabled: not doing anything.");
			return;
		}

		// We have to hold a wake lock here, or the user may lock the
		// phone right after a picture, preventing us from even determining
		// what it is, let alone scaling it. This may result in a weird
		// notification flood later, or pictures strangely being delayed.
		//
		// The wacky acquire/release cycle is unfortunately needed because
		// things happen through an AsyncTask. We assume a release is required
		// unless onChangeInner informs us (by returning false) that the
		// duty has been passed on to its AsyncTask. In theory, AsyncTask
		// should prevent it from being forgotten.
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

		boolean needRelease = true;
		try {
			// If we somehow don't release it within the timeout, do it anyway.
			Log.v(LOG_TAG, "Acquire wakelock");
			wl.acquire(WAKELOCK_TIMEOUT);
			needRelease = onCheckInner(wl);
		} finally {
			if (needRelease) {
				Log.v(LOG_TAG, "Release wakelock by default");
				if (wl.isHeld())
					wl.release();
			}
		}
	}

	private boolean onCheckInner(final PowerManager.WakeLock wl) {
		// Ensure we can actually back up the photo
		if (_storagePath == null) {
			if (!initStorage()) {
				return true;
			}
		}
		final ImageQueue queue = ImageQueue.GetSingleton(this);

		// Check to see if we actually have anything to get.
		final ImageQueue.Image[] images = queue.getItemsToProcess();
		if (images == null || images.length == 0)
			return true;

		// Do the scaling in a thread.
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... foo) {
				checkThreaded(queue, images);
				return null;
			}

			@Override
			protected void onPostExecute(Void foo) {
				Log.v(LOG_TAG, "Release wakelock by worker");
				if (wl != null && wl.isHeld())
					wl.release();
			}
		}.execute(new Void[] { null });
		return false;
	}

	private void checkThreaded(ImageQueue queue, ImageQueue.Image[] images) {
		ProcessedImages processed = ProcessedImages.GetSingleton(this);

		boolean didAny = false;
		for (final ImageQueue.Image image : images) {
			// Build the paths
			final File source = new File(image.pathname);
			final File destination = new File(_storagePath.getAbsolutePath().concat(File.separator).concat(source.getName()).concat(TEMP_EXTENSION));

			ScaleResult result = scaleOne(source, destination, image.timestamp);
			if (result.scaleSucceeded) {
				queue.markProcessed(image.id);

				// Add it to the database of things we've already processed.
				processed.addProcessed(source, result.image.getThumbnail());

				// Free the memory we're using
				result.image.recycle();

				didAny = true;
			} else
				queue.markSkipped(image.id);
		}

		if (didAny) {
			// Kick the streaming service to do the uploading work.
			UploadService.Kick(getBaseContext());
		}
	}

	static class ScaleResult {
		static public ScaleResult Fail() {
			ScaleResult rv = new ScaleResult();
			rv.scaleSucceeded = false;
			return rv;
		}

		static public ScaleResult Success() {
			ScaleResult rv = new ScaleResult();
			rv.scaleSucceeded = true;
			return rv;
		}

		static public ScaleResult Success(LifeStreamImage image) {
			ScaleResult rv = new ScaleResult();
			rv.scaleSucceeded = true;
			rv.image = image;
			return rv;
		}

		// True if we successfully scaled the image and submitted it for upload.
		public boolean scaleSucceeded;

		// Thumbnail of the scaled image, if applicable.
		public LifeStreamImage image;
	}

	// Returns a thumbnail if we successfully scaled the image and submitted it for upload, or null otherwise.
	private ScaleResult scaleOne(File source, File destination, int timestamp) {
		// Only copy if the destination doesn't already exist
		if (destination.exists())
			return ScaleResult.Success();

		// Get any "extras".
		WatchedPaths watched = new WatchedPaths(new Settings(this));
		final File[] extras = watched.checkPath(source);
		if (extras == null) {
			// This really shouldn't happen. It typically indicates a bug in MediaListener.
			Log.i(LOG_TAG, "Ignoring (BUGGED) path-excluded file " + source);
			return ScaleResult.Success();
		}

		// Stuff gets more CPU intensive from here. Try to lose
		// some thread priority to not hog.
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST);

		Log.i(LOG_TAG, "Scale image from " + source.getAbsolutePath() + " to " + destination.getAbsolutePath());

		// Give it a few moments to "settle".. sometimes it seems to give us the
		// notification before the image is actually finished writing or something.
		// FIXME: This hack shouldn't be necessary anymore when we split the media
		// observation from the photo capturing properly.
		try {
			int now = (int)(new Date().getTime() / 1000);
			int diff = now - timestamp;
			if (diff < 5)
				Thread.sleep((5 - diff) * 1000);
		} catch (Exception exc) {
			// We don't really care if we're interrupted.
		}

		final Settings settings = LifeStreamApplication.GetApp().GetSettings();
		final int scaleSize = settings.getImageSize();
		final boolean hqScale = settings.getHighQualityScale();
		final LifeStreamImage image = new LifeStreamImage(source, scaleSize, THUMBNAIL_SIZE, hqScale);

		// Success?
		if (image.getScaledImage() == null || image.getThumbnail() == null) {
			if (settings.getVerbose()) {
				final Resources res = getResources();
				notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), res.getString(R.string.fail_copy));
			}
			Log.w(LOG_TAG, "Copy failed: " + source);
			return ScaleResult.Fail();
		}

		// Re-compress the data back into a JPEG in its new home.
		try {
			final FileOutputStream destStream = new FileOutputStream(destination);
			image.getScaledImage().compress(Bitmap.CompressFormat.JPEG, 70, destStream);
			destStream.close();
		} catch (final IOException e) {
			if (settings.getVerbose()) {
				final Resources res = getResources();
				notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), res.getString(R.string.fail_copy));
			}
			Log.w(LOG_TAG, "Failed to write scaled image: " + destination, e);
			return ScaleResult.Fail();
		}

		// Remove the temp suffix so that it can actually be processed.
		String destName = destination.getAbsolutePath();
		String finalName = destName.substring(0, destName.length() - TEMP_EXTENSION.length());
		if (!destination.renameTo(new File(finalName))) {
			Log.w(LOG_TAG, String.format("Failed to rename image: %s -> %s", destination, finalName));
		}

		// Copy any extra files as well.
		if (extras.length > 0) {
			for (File f : extras) {
				final File extraDest = new File(_storagePath.getAbsolutePath().concat(File.separator).concat(f.getName()).concat(TEMP_EXTENSION));
				Media.FileCopy(f, extraDest);
			}
		}

		// Yay! It worked!
		return ScaleResult.Success(image);
	}
}
