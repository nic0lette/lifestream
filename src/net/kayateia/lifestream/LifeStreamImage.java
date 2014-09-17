package net.kayateia.lifestream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.util.Log;

import java.io.*;

/**
 * Class to take a file path and convert it to a scaled
 * and thumbnail image for use with LifeStream
 */
public class LifeStreamImage {
	private static final String LOG_TAG = "LifeStream/LifeStreamImage";

	public Bitmap getScaledImage() {
		return _scaled;
	}

	public Bitmap getThumbnail() {
		return _thumbnail;
	}

	/**
	 * Creates a LifeStream image set
	 * @param source File to create the image set from
	 * @param scaledSize Largest dimension for the scaled image
	 * @param thumbSize Largest dimension for the thumbnail
	 * @param useHqScale Whether to use the HQ scaling algorithm (more memory) or the lower quality algorithm
	 * @throws IOException
	 */
	public LifeStreamImage(final File source, final int scaledSize, final int thumbSize, final boolean useHqScale) {
		// Setup vars
		_scaledSize = scaledSize;
		_thumbSize = thumbSize;
		_useHq = useHqScale;

		// Try our best to get the exif information
		try {
			_exif = new ExifInterface(source.getAbsolutePath());
		} catch (final IOException e) {
			_exif = null;
		}

		// Created scaled versions
		scaleImage(source);
	}

	private int getRotation() {
		if (_exif == null) {
			// assume no rotation if there's no exif data
			return 0;
		}

		int orientation = _exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_270:
				return 270;
			case ExifInterface.ORIENTATION_ROTATE_180:
				return 180;
			case ExifInterface.ORIENTATION_ROTATE_90:
				return 90;
			default:
				return 0;
		}
	}

	/**
	 * Frees the memory backing the bitmaps used
	 */
	public void recycle() {
		if (_wasRecycled) {
			return;
		}

		if (_scaled != null) {
			_scaled.recycle();
			_scaled = null;
		}
		if (_thumbnail != null) {
			_thumbnail.recycle();
			_thumbnail = null;
		}
		_wasRecycled = true;
	}

	private Point getSize(final InputStream bitmapStream) {
		// Save the dimensions
		final BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(bitmapStream, null, opts);
		return new Point(opts.outWidth, opts.outHeight);
	}

	private void scaleImage(final File source) {

		// Get the size of the original image
		Point originalSize;
		try {
			final FileInputStream sizeStream = new FileInputStream(source);
			originalSize = getSize(sizeStream);
			sizeStream.close();
		} catch (final IOException e) {
			Log.w(LOG_TAG, "Failed to get the dimensions of file: " + source, e);
			return;
		}

		if (_useHq) {
			// Size to scale to
			final Point targetSize;

			// How do we scale it?
			if (originalSize.x > originalSize.y) {
				targetSize = new Point(_scaledSize,
						originalSize.y * _scaledSize / originalSize.x);
			} else {
				targetSize = new Point(originalSize.x * _scaledSize / originalSize.y,
						_scaledSize);
			}

			// Decode the original bitmap
			Bitmap original;
			try {
				final FileInputStream decodeStream = new FileInputStream(source);
				original = BitmapFactory.decodeStream(decodeStream, null, null);
				decodeStream.close();
			} catch (final IOException e) {
				Log.w(LOG_TAG, "Failed to decode image: " + source, e);
				return;
			}

			// Scale the image then free the memory used for the full size bitmap
			if (original != null) {
				_scaled = Bitmap.createScaledBitmap(original, targetSize.x, targetSize.y, true);
				original.recycle();
				original = null;
			}
		} else {
			// Can only scale based on powers of twos, so figure out which is the closest
			int scale = 1;
			while ((originalSize.x / scale) > _scaledSize || (originalSize.y / scale) > _scaledSize)
				scale *= 2;

			// Read and decode the scaled version
			final BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = scale;

			try {
				final FileInputStream decodeStream = new FileInputStream(source);
				_scaled = BitmapFactory.decodeStream(decodeStream, null, opts);
				decodeStream.close();
			} catch (final IOException e) {
				Log.w(LOG_TAG, "Failed to decode image: " + source, e);
			}
		}

		// To proceed we need an image
		if (_scaled == null) {
			return;
		}

		// Check for pending rotation
		final int rotation = getRotation();
		if (rotation != 0) {
			final Matrix matrix = new Matrix();
			matrix.preRotate(rotation);

			// Rotate
			final Bitmap rotated = Bitmap.createBitmap(_scaled, 0, 0,
					_scaled.getWidth(), _scaled.getHeight(), matrix, true);

			if (rotated != null) {
				// The rotated image is the proper scaled bitmap
				_scaled.recycle();
				_scaled = rotated;
			}
		}

		// Scale again to get a small thumbnail
		final Point thumbSize;
		if (originalSize.x > originalSize.y) {
			thumbSize = new Point(_thumbSize,
					_scaled.getHeight() * _thumbSize / _scaled.getWidth());
		} else {
			thumbSize = new Point(_scaled.getWidth() * _thumbSize / _scaled.getHeight(),
					_thumbSize);
		}

		// Finally, create the thumbnail
		_thumbnail = Bitmap.createScaledBitmap(_scaled, thumbSize.x, thumbSize.y, true);
	}

	private Bitmap _scaled;
	private Bitmap _thumbnail;

	private ExifInterface _exif;

	private int _scaledSize;
	private int _thumbSize;
	private boolean _useHq;

	private boolean _wasRecycled = false;
}
