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

import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

// Database handler for images queued for processing.
// We have to do this to avoid losing images due to OOM, crashes, etc.
public class ImageQueue extends SQLiteOpenHelper {
	private static final String LOG_TAG = "LifeStream/ImageQueue";

	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "LifeStreamImageQueue";
	private static final String TABLE_QUEUE = "queue";

	private static final String KEY_ID = "id";
	private static final String KEY_NAME = "name";
	private static final String KEY_TIMESTAMP = "timestamp";
	private static final String KEY_QUEUESTAMP = "queuestamp";

	public class Image {
		public Image(int i, String p, int t, int q) {
			this.id = i;
			this.pathname = p;
			this.timestamp = t;
			this.queuestamp = q;
		}
		public int id;
		public String pathname;
		public int timestamp;
		public int queuestamp;
	}

	// This has to be done through the same object to get database locking, unfortunately.
	static Object s_lock = new Object();
	static ImageQueue s_global = null;
	static public ImageQueue GetSingleton(Context context) {
		synchronized(s_lock) {
			if (s_global == null)
				s_global = new ImageQueue(context.getApplicationContext());
			return s_global;
		}
	}

	public ImageQueue(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	// Creating Tables
	@Override
	public void onCreate(SQLiteDatabase db) {
		String CREATE_PROCESSED_TABLE = "CREATE TABLE " + TABLE_QUEUE + "("
			+ KEY_ID + " INTEGER PRIMARY KEY," + KEY_NAME + " TEXT,"
			+ KEY_TIMESTAMP + " INTEGER,"
			+ KEY_QUEUESTAMP + " INTEGER"
			+ ")";
		db.execSQL(CREATE_PROCESSED_TABLE);
	}
 
	// Upgrading database
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Drop older table if existed
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUEUE);

		// Create tables again
		onCreate(db);
	}

	// Add new image to be processed. This will silently ignore if the item is already on the queue.
	public void addToQueue(String imageName) {
		try {
			SQLiteDatabase db = this.getWritableDatabase();
			try {
				// Check if it's there already.
				Cursor cursor = db.query(TABLE_QUEUE,
					new String[] { KEY_NAME },
					KEY_NAME + "=?",
					new String[] { imageName },
					null, null, null, null);
				if (cursor.moveToFirst())
					return;

				// Go ahead and insert.
				ContentValues values = new ContentValues();
				values.put(KEY_NAME, imageName);
				values.put(KEY_TIMESTAMP, (int)(new Date().getTime() / 1000));
				values.put(KEY_QUEUESTAMP, (int)(new Date().getTime() / 1000));
				db.insert(TABLE_QUEUE, null, values);
			} finally {
				db.close();
			}
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during addToQueue", e);
		}
	}

	// Returns a set of image specs that need processing. They will not be
	// removed from the queue until markProcessed() has been called on each.
	public Image[] getItemsToProcess(int maxCount) {
		SQLiteDatabase db = null;
		try {
			db = this.getReadableDatabase();
			Cursor cursor = db.query(TABLE_QUEUE,
				new String[] { KEY_ID, KEY_NAME, KEY_TIMESTAMP, KEY_QUEUESTAMP },
				null, null,
				null, null, KEY_QUEUESTAMP, "" + maxCount);
			if (!cursor.moveToFirst())
				return null;

			Image[] rv = new Image[cursor.getCount()];
			for (int i=0; i<cursor.getCount(); ++ i) {
				rv[i] = new Image(
					cursor.getInt(0),
					cursor.getString(1),
					cursor.getInt(2),
					cursor.getInt(3)
				);
				cursor.moveToNext();
			}

			return rv;
		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "SQL exception during getItemToProcess", e);
			return null;
		} finally {
			if (db != null)
				db.close();
		}
	}

	// Pass in a string that was returned from getItemToProcess() above, and this
	// marks it as skipped. It will be sent to the end of the queue. You must do
	// this after each item you don't want to or can't process right now.
	public void markSkipped(int id) {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			ContentValues values = new ContentValues();
			values.put(KEY_QUEUESTAMP, (int)(new Date().getTime() / 1000));
			db.update(TABLE_QUEUE, values, KEY_ID + "=?", new String[] { "" + id });
		} finally {
			db.close();
		}
	}

	// Pass in a string that was returned from getItemToProcess() above, and this
	// marks it as completed, no longer on the queue. You must do this after each
	// item you've processed.
	public void markProcessed(int id) {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			db.delete(TABLE_QUEUE,
				KEY_ID + "=?",
				new String[] { ""+id });
		} finally {
			db.close();
		}
	}
}























