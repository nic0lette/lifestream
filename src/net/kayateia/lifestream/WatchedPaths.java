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
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Environment;
import android.util.Log;

public class WatchedPaths {
	final static String LOG_TAG = "LifeStream/WatchedPaths";

	ArrayList<Path>	_paths;
	Settings		_settings;

	public WatchedPaths(Settings settings) {
		_settings = settings;
		load();
	}

	public void load() {
		// For now, our stuff is all stored (perhaps foolishly) in Android prefs.
		String stored = _settings.getPaths();
		if (stored.equals("")) {
			// Nothing there -- set some sensible defaults.
			_paths = new ArrayList<Path>();
			_paths.add(new Path("/DCIM/Camera", "*.jpg", ""));
			_paths.add(new Path("/3DSteroidPro/Camera_org", "*.jpg", "*.txt"));
			_paths.add(new Path("/Pictures/HDR", "*.jpg", ""));
			_paths.add(new Path("/Pictures/HDRCamera", "*.jpg", ""));
			_paths.add(new Path("/Pictures/Screenshots", "*.png", ""));
			save();
		} else {
			try {
				JSONArray root = new JSONArray(stored);
				_paths = new ArrayList<Path>();
				for (int i=0; i<root.length(); ++i) {
					JSONObject obj = (JSONObject)root.get(i);
					_paths.add(new Path(
						obj.getString("path"),
						obj.getString("filewc"),
						obj.getString("extrawc")
					));
				}
			} catch (JSONException e) {
				Log.e(LOG_TAG, "Can't load watched paths", e);
			}
		}
	}

	public void save() {
		/*try {
			JSONArray root = new JSONArray();
			int i = 0;
			for (Path p : _paths) {
				JSONObject obj = new JSONObject();
				obj.put("path", p.path);
				obj.put("filewc", p.fileWildcard);
				obj.put("extrawc", p.extraWildcard);
				root.put(i, obj);
				++i;
			}
			_settings.setPaths(root.toString());
			_settings.commit();
		} catch (JSONException e) {
			Log.e(LOG_TAG, "Can't save watched paths", e);
		} */
	}

	public List<Path> getPaths() {
		return _paths;
	}

	public void addPath(Path p) {
		_paths.add(p);
	}

	// Returns null if the file is no good; otherwise returns a
	// list of other files that ought to go with it. (May be empty.)
	public File[] checkPath(File f) {
		// Get the absolute path, and make sure it's on the SD card.
		String absPath = f.getAbsolutePath();
		String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
		if (!absPath.startsWith(sdPath))
			return null;

		// Okay. Check it against our path prefixes.
		String pathPart = f.getParent().substring(sdPath.length());
		Path match = null;
		for (Path p : _paths) {
			if (p.path.equals(pathPart)) {
				match = p;
				break;
			}
		}
		if (match == null)
			return null;

		// Check wildcards if specified.
		if (!match.fileWildcard.equals("")) {
			String filePart = f.getName();
			String regex = "^" + match.fileWildcard
					.replace(".", "\\.")
					.replace("*", ".*") + "$";
			if (!filePart.matches(regex))
				return null;
		}

		// Finally, look for extra files if needed.
		if (!match.extraWildcard.equals("")) {
			final String regex = "^" + match.extraWildcard
					.replace(".", "\\.")
					.replace("*", ".*") + "$";
			return f.getParentFile().listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.matches(regex);
				}
			});
		} else
			return new File[0];
	}

	// Represents one watched path, where we will accept files from.
	public class Path {
		public String path, fileWildcard, extraWildcard;

		public Path(String path, String fileWildcard, String extraWildcard) {
			this.path = path;
			this.fileWildcard = fileWildcard;
			this.extraWildcard = extraWildcard;
		}
	}
}
