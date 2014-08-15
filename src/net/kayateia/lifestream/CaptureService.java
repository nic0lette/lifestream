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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
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

	private File _storagePath, _baseStoragePath;

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
			_baseStoragePath = Media.GetBaseStorage(this);
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
		Settings settings = new Settings(getBaseContext());
		String username = settings.getUserName();
		String authToken = settings.getAuthToken();
		if (username.isEmpty() || authToken.isEmpty() || !settings.getEnabled()) {
			Log.i(LOG_TAG, "Config is not set or not enabled: not doing anything.");
			return;
		}

		ProcessedImages db = ProcessedImages.GetSingleton(getBaseContext());

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
			if (wl != null)
				wl.acquire(WAKELOCK_TIMEOUT);
			needRelease = onCheckInner(wl);
		} finally {
			if (needRelease) {
				Log.v(LOG_TAG, "Release wakelock by default");
				if (wl != null && wl.isHeld())
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
		for (int i=0; i<images.length; ++i) {
			// Build the paths
			final File source = new File(images[i].pathname);
			final File destination = new File(_storagePath.getAbsolutePath().concat(File.separator).concat(source.getName()).concat(TEMP_EXTENSION));

			ScaleResult result = scaleOne(source, destination, images[i].timestamp);
			if (result.scaleSucceeded) {
				queue.markProcessed(images[i].id);

				// Add it to the database of things we've already processed.
				processed.addProcessed(source, result.thumbnail);

				didAny = true;
			} else
				queue.markSkipped(images[i].id);
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

		static public ScaleResult Success(Bitmap thumbnail) {
			ScaleResult rv = new ScaleResult();
			rv.scaleSucceeded = true;
			rv.thumbnail = thumbnail;
			return rv;
		}

		// True if we successfully scaled the image and submitted it for upload.
		public boolean scaleSucceeded;

		// Thumbnail of the scaled image, if applicable.
		public Bitmap thumbnail;
	}

	// Returns a thumbnail if we successfully scaled the image and submitted it for upload, or null otherwise.
	private ScaleResult scaleOne(File source, File destination, int timestamp) {
		// Only copy if the destination doesn't already exist
		if (destination.exists())
			return ScaleResult.Success();

		// Retrieve our auth token and user parameters. If they're not there, we can't do anything.
		Settings settings = new Settings(getBaseContext());
		String username = settings.getUserName();
		String authToken = settings.getAuthToken();
		if (username.isEmpty() || authToken.isEmpty() || !settings.getEnabled()) {
			Log.i(LOG_TAG, "Not copying " + source.getAbsolutePath() + ": Config is not set or disabled");
			return ScaleResult.Fail();
		}

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

		FileInputStream sourceStream = null;
		Bitmap image = null, thumbnail = null;
		try {
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

			// Read the image and scale it. Because loading the raw pixels is somewhat
			// likely to OOM us (or make it more likely, anyway) what we really want to do
			// here is to have the bitmap loader do the scaling for us.

			// Open once to get the image size.
			sourceStream = new FileInputStream(source);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(sourceStream, null, opts);
			sourceStream.close();

			// Also verify the orientation; Samsung phones like to set the EXIF flag
			// instead of rotating the pixels.
			ExifInterface exif = new ExifInterface(source.getAbsolutePath());
			int orientation = exif.getAttributeInt(
					ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_NORMAL);
			int rotate = 0;
			switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotate = 270;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotate = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotate = 90;
				break;
			}

			// This is the size we want to scale to. The largest dimension should not exceed this.
			final int largestSize = settings.getImageSize();
			if (settings.getHighQualityScale()) {
				// Figure out the target size.
				int width = -1, height = -1;
				if (opts.outWidth > largestSize || opts.outHeight > largestSize) {
					if (opts.outWidth > opts.outHeight) {
						width = largestSize;
						height = opts.outHeight * width / opts.outWidth;
					} else {
						height = largestSize;
						width = opts.outWidth * height / opts.outHeight;
					}
				}

				// This takes more RAM but it gives us more control over the size and a better quality scale.
				sourceStream = new FileInputStream(source);
				image = BitmapFactory.decodeStream(sourceStream, null, null);
				if (!checkImageLoad(source, image, "Image load failed: "))
					return ScaleResult.Fail();
				if (width != -1 && height != -1) {
					image = Bitmap.createScaledBitmap(image, width, height, true);
					if (!checkImageLoad(source, image, "Image scale failed: "))
						return ScaleResult.Fail();
				}
			} else {
				// Lower quality scale. This basically discards pixels while it's loading. It's much
				// cheaper in both RAM and CPU, but it has limitations about what sizes you can do,
				// and it's lower quality.
				int scale = 1;
				while ((opts.outWidth/scale) > largestSize || (opts.outHeight/scale) > largestSize)
					scale *= 2;

				// Re-open the file and scale it as we read it.
				sourceStream = new FileInputStream(source);
				opts = new BitmapFactory.Options();
				opts.inSampleSize = scale;
				image = BitmapFactory.decodeStream(sourceStream, null, opts);
				if (!checkImageLoad(source, image, "Image load failed: "))
					return ScaleResult.Fail();
			}

			// Do rotation if needed.
			if (rotate != 0) {
				Log.i(LOG_TAG, source.getAbsolutePath() + ": rotation by " + rotate);
				Matrix matrix = new Matrix();
				matrix.preRotate(rotate);
				image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
			}
			if (!checkImageLoad(source, image, "Image rotate failed: "))
				return ScaleResult.Fail();

			// Re-compress the data back into a JPEG in its new home.
			final FileOutputStream destStream = new FileOutputStream(destination);
			image.compress(Bitmap.CompressFormat.JPEG, 70, destStream);
			destStream.close();

			// Scale again to get a small thumbnail.
			if (opts.outWidth > THUMBNAIL_SIZE || opts.outHeight > THUMBNAIL_SIZE) {
				if (opts.outWidth > opts.outHeight)
					thumbnail = Bitmap.createScaledBitmap(image, THUMBNAIL_SIZE, opts.outHeight * THUMBNAIL_SIZE / opts.outWidth, true);
				else
					thumbnail = Bitmap.createScaledBitmap(image, opts.outWidth * THUMBNAIL_SIZE / opts.outHeight, THUMBNAIL_SIZE, true);
			}
		} catch (final FileNotFoundException ex) {
			if (settings.getVerbose()) {
				final Resources res = getResources();
				notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), res.getString(R.string.fail_copy));
			}
			Log.w(LOG_TAG, "Copy failed", ex);
		} catch (final IOException ex) {
			if (settings.getVerbose()) {
				final Resources res = getResources();
				notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), res.getString(R.string.fail_copy));
			}
			Log.w(LOG_TAG, "Copy failed", ex);
		} finally {
			if (sourceStream != null) {
				try {
					sourceStream.close();
				} catch (IOException e) {
				}
			}
			if (image != null)
				image.recycle();
		}

		// Remove the temp suffix so that it can actually be processed.
		String destName = destination.getAbsolutePath();
		String finalName = destName.substring(0, destName.length() - TEMP_EXTENSION.length());
		destination.renameTo(new File(finalName));

		// Copy any extra files as well.
		if (extras.length > 0) {
			for (File f : extras) {
				final File extraDest = new File(_storagePath.getAbsolutePath().concat(File.separator).concat(f.getName()).concat(TEMP_EXTENSION));
				Media.FileCopy(f, extraDest);
			}
		}

		return ScaleResult.Success(thumbnail);
	}

	boolean checkImageLoad(File source, Bitmap image, String message) {
		if (image == null) {
			final Resources res = getResources();
			notifyError(res.getString(R.string.fail_ticker), res.getString(R.string.fail_title), res.getString(R.string.fail_image_load, source.getName()));
			Log.e(LOG_TAG, message + source.getAbsolutePath());
			return false;
		} else
			return true;
	}
}
