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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.app.Activity;

public class PathsActivity
	extends Activity
	implements OnClickListener
{
	Settings _settings;
	WatchedPaths _paths;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_paths);

		Button b = (Button)this.findViewById(R.id.btnBack);
		b.setOnClickListener(this);

		_settings = new Settings(this);
		_paths = new WatchedPaths(_settings);
		ListView list = (ListView)this.findViewById(R.id.listPaths);
		list.setAdapter(new TestAdapter(_paths));
	}

	class TestAdapter extends BaseAdapter implements ListAdapter {
		WatchedPaths _p;

		public TestAdapter(WatchedPaths p) {
			_p = p;
		}

		public int getCount() {
			return _p.getPaths().size();
		}

		public Object getItem(int pos) {
			return _p.getPaths().get(pos);
		}

		public long getItemId(int pos) {
			return pos;
		}

		public View getView(int pos, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater inflater = getLayoutInflater();
				convertView = inflater.inflate(android.R.layout.simple_list_item_2, null);
			}

			WatchedPaths.Path p = _p.getPaths().get(pos);
			TextView t = (TextView)convertView.findViewById(android.R.id.text1);
			t.setText(p.path);

			String secondText = p.fileWildcard;
			if (!p.extraWildcard.equals(""))
				secondText += ", " + p.extraWildcard;
			t = (TextView)convertView.findViewById(android.R.id.text2);
			t.setText(secondText);
			return convertView;
		}
	}

	public void onClick(View arg0) {
		this.finish();
	}
}
