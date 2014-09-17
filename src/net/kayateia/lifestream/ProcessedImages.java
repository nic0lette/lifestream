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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.util.Log;

// Database handler for processed images.
// We have to do this to avoid media scanner stupidity. (And user
// meddling, I guess, but that's less common.)
//
// http://www.androidhive.info/2011/11/android-sqlite-database-tutorial/
public class ProcessedImages extends SQLiteOpenHelper {
	private static final String LOG_TAG = "LifeStream/ProcessedImages";

	private static final int DATABASE_VERSION = 3;
	private static final String DATABASE_NAME = "LifeStreamProcessedImages";
	private static final String TABLE_PROCESSED = "processed";

	public static final String KEY_ID = "id";
	public static final String KEY_NAME = "name";
	public static final String KEY_TIMESTAMP = "timestamp";
	public static final String KEY_THUMBNAIL = "thumbnail";
	public static final String KEY_FULLNAME = "fullname";

	// This has to be done through the same object to get database locking, unfortunately.
	static Object s_lock = new Object();
	static ProcessedImages s_global = null;
	static public ProcessedImages GetSingleton(Context context) {
		synchronized(s_lock) {
			if (s_global == null)
				s_global = new ProcessedImages(context.getApplicationContext());
			return s_global;
		}
	}

	public ProcessedImages(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	// Creating Tables
	@Override
	public void onCreate(SQLiteDatabase db) {
		String CREATE_PROCESSED_TABLE = "CREATE TABLE " + TABLE_PROCESSED + "("
			+ KEY_ID + " INTEGER PRIMARY KEY," + KEY_NAME + " TEXT,"
			+ KEY_TIMESTAMP + " INTEGER,"
			+ KEY_THUMBNAIL + " BLOB,"
			+ KEY_FULLNAME + " TEXT"
			+ ")";
		db.execSQL(CREATE_PROCESSED_TABLE);
	}
 
	// Upgrading database
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Do upgrade steps we know about; if oldVersion != newVersion by the end
		// of this, we don't know how.
		if (oldVersion == 1 && newVersion >= 2) {
			version1to2(db);
			oldVersion = 2;
		}
		if (oldVersion == 2 && newVersion == 3) {
			version2to3(db);
			oldVersion = 3;
		}

		if (oldVersion != newVersion) {
			// Drop older table if existed
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROCESSED);

			// Create tables again
			onCreate(db);
		}
	}
	void version1to2(SQLiteDatabase db) {
		// Added image thumbnail column, for recent images tab.
		db.execSQL("alter table " + TABLE_PROCESSED + " add column " + KEY_THUMBNAIL + " blob");
	}
	void version2to3(SQLiteDatabase db) {
		// Added full filename column, for recent images tab.
		db.execSQL("alter table " + TABLE_PROCESSED + " add column " + KEY_FULLNAME + " text");
	}

	// Get a cursor pointing to recently processed images. This should be used in
	// a strictly read-only manner, and its database must be disposed (i.e. startManagingCursor).
	public Cursor getCursorOfAll() {
		SQLiteDatabase db = null;
		try {
			db = this.getReadableDatabase();
			return db.rawQuery(
				"select " + KEY_ID + " as _id,"
					+ KEY_NAME + ","
					+ KEY_TIMESTAMP + ","
					+ KEY_THUMBNAIL + ","
					+ KEY_FULLNAME + " "
					+
				"from " + TABLE_PROCESSED + " " +
				"order by " + KEY_TIMESTAMP + " desc", null);
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during getCursorOfAll", e);
			if (db != null)
				db.close();
			return null;
		}
	}

	// Add an image to the list of those that have been processed.
	public void addProcessed(File imageName, Bitmap thumbnail) {
		try {
			SQLiteDatabase db = this.getWritableDatabase();
			try {
				ContentValues values = new ContentValues();
				values.put(KEY_NAME, imageName.getName());
				values.put(KEY_TIMESTAMP, (int)(new Date().getTime() / 1000));

				// Convert thumbnail to a byte array first, if needed.
				if (thumbnail != null) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					thumbnail.compress(Bitmap.CompressFormat.JPEG, 70, out);
					values.put(KEY_THUMBNAIL, out.toByteArray());
				}
				values.put(KEY_FULLNAME, imageName.getAbsolutePath());

				// Inserting Row
				db.insert(TABLE_PROCESSED, null, values);
			} finally {
				db.close();
			}

			// Go ahead and clean here too.
			clean();
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during addProcessed", e);
		}
	}

	// Returns true if we've ever processed this image.
	public boolean haveProcessed(String imageName) {
		SQLiteDatabase db = null;
		try {
			db = this.getReadableDatabase();
			Cursor cursor = db.query(TABLE_PROCESSED,
				new String[] { KEY_NAME, KEY_TIMESTAMP },
				KEY_NAME + "=?",
				new String[] { imageName },
				null, null, null, null);
			return cursor.moveToFirst();
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during haveProcessed", e);
			return false;
		} finally {
			if (db != null)
				db.close();
		}
	}

	// Returns the full image path if we can find it, and it's unique; otherwise null.
	public String getFullPath(String imageName) {
		SQLiteDatabase db = null;
		try {
			db = this.getReadableDatabase();
			Cursor cursor = db.query(TABLE_PROCESSED,
				new String[] { KEY_FULLNAME },
				KEY_NAME + "=?",
				new String[] { imageName },
				null, null, null, null);
			if (cursor.getCount() != 1)
				return null;
			cursor.moveToFirst();
			return cursor.getString(0);
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during getFullPath", e);
			return null;
		} finally {
			if (db != null)
				db.close();
		}
	}

	// Cleans out entries older than a week. If the media server reports
	// something more than a week old.. what the hell.. we'll just upload it again.
	public void clean() {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			// My date logic is so gangsta. 9_9
			long dateCutoff = new Date().getTime() / 1000 - (60*60*24*7);
			db.delete(TABLE_PROCESSED,
				KEY_TIMESTAMP + "< ?",
				new String[] { ""+dateCutoff });
		} finally {
			db.close();
		}
	}
}

