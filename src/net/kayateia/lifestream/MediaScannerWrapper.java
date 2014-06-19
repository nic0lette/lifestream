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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

/**
 * @author PeterKnego, kaya
 * http://stackoverflow.com/questions/4157724/dynamically-add-pictures-to-gallery-widget
 *
 * This should only be used once.
 */
public class MediaScannerWrapper implements MediaScannerConnection.MediaScannerConnectionClient {
	static final String LOG_TAG = "LifeStream/MediaScannerWrapper";
	private MediaScannerConnection _connection;
	private List<String> _paths;
	private HashMap<String,String> _messages;

	// filePath - where to scan;
	// mime type of media to scan i.e. "image/jpeg".
	// use "*/*" for any media
	public MediaScannerWrapper(Context ctx) {
		_connection = new MediaScannerConnection(ctx, this);
		_paths = new ArrayList<String>();
		_messages = new HashMap<String,String>();
	}

	public void addFile(String fn, String message) {
		_paths.add(fn);
		_messages.put(fn, message);
	}

	public boolean scannedAny() {
		return !_paths.isEmpty();
	}

	// do the scanning
	public void scan() {
		_connection.connect();
	}

	// start the scan when scanner is ready
	public void onMediaScannerConnected() {
		for (String p : _paths) {
			_connection.scanFile(p, getMime(p));
			Log.w("MediaScannerWrapper", "media file submitted: " + p);
		}
	}

	String getMime(String fn) {
		int i = fn.lastIndexOf('.');
		if (i > 0) {
			String extension = fn.substring(i+1);
			if (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg"))
				return "image/jpeg";
			else if (extension.equalsIgnoreCase("png"))
				return "image/png";
		}
		return "image/*";
	}

	protected void scanned(String path, Uri uri) {
	}

	public void onScanCompleted(String path, Uri uri) {
		// when scan is completes, update media file tags
		Log.w("MediaScannerWrapper", "media file scanned: " + path + " - " + uri.toString());
		scanned(path, uri);
	}

	public String getMessage(String path) {
		if (_messages.containsKey(path))
			return _messages.get(path);
		else
			return "Itsa stuff!";
	}
}
