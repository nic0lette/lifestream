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
import java.io.IOException;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.nio.channels.*;

/**
 * @author niya
 * 
 */
public class Media {
	private final File mFile;
	private final String mType;

	public Media(File file, String type) {
		this.mFile = file;
		this.mType = type;
	}

	public File getFile() {
		return mFile;
	}

	public String getType() {
		return mType;
	}

	public static final String BASE_DIR = "Pictures/LifeStream";
	public static final String NO_MEDIA = ".nomedia";
	private static final String LOG_TAG = "LifeStream/Media";
	static public File InitStorage(Context context, String dirName, boolean createNoMedia) throws IOException {
		// First, let's get the path we're going to use to store things (and set
		// it up if it doesn't exist)
		final File sdCard = Environment.getExternalStorageDirectory();
		final File storagePath = new File(sdCard.toString().concat(File.separator).concat(dirName));

		if (!storagePath.exists()) {
			storagePath.mkdirs();
			if (!storagePath.exists()) {
				final Resources res = context.getResources();
				throw new IOException(res.getString(R.string.fail_cannot_create));
			}
		} else if (!storagePath.isDirectory()) {
			final Resources res = context.getResources();
			throw new IOException(res.getString(R.string.fail_exists));
		}

		if (createNoMedia) {
			final File noMedia = new File(storagePath.getAbsolutePath().concat(File.separator).concat(NO_MEDIA));
			if (!noMedia.exists()) {
				try {
					noMedia.createNewFile();
				} catch (final IOException ex) {
					Log.w(LOG_TAG, "Failed to create .nomedia file");
				}
			}
		}

		return storagePath;
	}

	static public File GetBaseStorage(Context context) {
		final File sdCard = Environment.getExternalStorageDirectory();
		final File storagePath = new File(sdCard.toString().concat(File.separator).concat(BASE_DIR));
		return storagePath;
	}

	static public boolean IsMediaMounted() {
		String mountState = Environment.getExternalStorageState();
		return mountState.equals(Environment.MEDIA_MOUNTED);
	}

	// Provide the equivalent of C# File.Copy for silly Java.
	static public void FileCopy(File fr, File to) {
		FileInputStream extraSourceStream = null;
		FileOutputStream extraDestinationStream = null;
		FileChannel extraSourceChannel = null, extraDestinationChannel = null;
		try {
			extraSourceStream = new FileInputStream(fr);
			extraSourceChannel = extraSourceStream.getChannel();
			extraDestinationStream = new FileOutputStream(to);
			extraDestinationChannel = extraDestinationStream.getChannel();
			extraDestinationChannel.transferFrom(extraSourceChannel, 0, extraSourceChannel.size());
		} catch (FileNotFoundException e) {
			Log.w(LOG_TAG, "Can't find file " + fr.getAbsolutePath() + "for copying");
		} catch (IOException e) {
			Log.w(LOG_TAG, "Can't copy file " + fr.getAbsolutePath(), e);
		} finally {
			try {
				if (extraSourceChannel != null)
					extraSourceChannel.close();
			} catch (IOException e) { }
			try {
				if (extraDestinationChannel != null)
					extraDestinationChannel.close();
			} catch (IOException e) { }
			try {
				if (extraSourceStream != null)
					extraSourceStream.close();
			} catch (IOException e) { }
			try {
				if (extraDestinationStream != null)
					extraDestinationStream.close();
			} catch (IOException e) { }
		}
		
	}
}
