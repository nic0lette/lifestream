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

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class TabRecentImages extends Fragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		_view = inflater.inflate(R.layout.recent_history, container, false);

		ProcessedImages images = ProcessedImages.GetSingleton(getActivity());
		final Cursor cursor = images.getCursorOfAll();
		if (cursor == null)
			return _view;

		// getActivity().startManagingCursor(cursor);
		ListView list = (ListView)_view.findViewById(R.id.listImages);
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
				cursor.moveToPosition(position);
				String fullName = cursor.getString(cursor.getColumnIndex(ProcessedImages.KEY_FULLNAME));
				if (fullName != null) {
					Intent intent = new Intent();
					intent.setAction(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.parse("file://" + fullName), "image/*");
					getActivity().startActivity(intent);
				}
			}
		});

		ImageCursorAdapter adapter = new ImageCursorAdapter(getActivity(), cursor);
		list.setAdapter(adapter);

		return _view;
	}

	public class ImageCursorAdapter extends CursorAdapter {
		public ImageCursorAdapter(Context context, Cursor c) {
			super(context, c, true);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ImageView img = (ImageView)view.findViewById(R.id.list_image);
			byte[] imgBlob = cursor.getBlob(cursor.getColumnIndex(ProcessedImages.KEY_THUMBNAIL));
			if (imgBlob != null) {
				Bitmap imgBmp = BitmapFactory.decodeByteArray(imgBlob, 0, imgBlob.length);
				img.setImageBitmap(imgBmp);
			} else {
				img.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
			}
			TextView t1 = (TextView)view.findViewById(R.id.filename);
			t1.setText(cursor.getString(cursor.getColumnIndex(ProcessedImages.KEY_NAME)));
			TextView t2 = (TextView)view.findViewById(R.id.timestamp);
			Date date = new Date(cursor.getInt(cursor.getColumnIndex(ProcessedImages.KEY_TIMESTAMP)) * (long)1000);
			t2.setText(SimpleDateFormat.getDateTimeInstance().format(date));
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			LayoutInflater inflater = LayoutInflater.from(context);
			View v = inflater.inflate(R.layout.image_list, parent, false);
			bindView(v, context, cursor);
			return v;
		}
	}

	View _view;
}
