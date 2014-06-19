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

import com.google.android.gcm.GCMRegistrar;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;

// http://android-er.blogspot.com/2012/06/create-actionbar-in-tab-navigation-mode.html
public class LifeStreamActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Start the service if it's not already started.
		MediaListenerService.Start(this);
		GCMRegistrar.checkDevice(this);
		GCMRegistrar.checkManifest(this);

		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Tab tabA = actionBar.newTab();
		tabA.setText("Config");
		tabA.setTabListener(new TabListener<TabConfig>(this, "Config", TabConfig.class));
		actionBar.addTab(tabA);

		Tab tabB = actionBar.newTab();
		tabB.setText("Recent Uploads");
		tabB.setTabListener(new TabListener<TabRecentImages>(this, "Recent Uploads", TabRecentImages.class));
		actionBar.addTab(tabB);

		if (savedInstanceState != null) {
			int savedIndex = savedInstanceState.getInt("SAVED_INDEX");
			getActionBar().setSelectedNavigationItem(savedIndex);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("SAVED_INDEX", getActionBar().getSelectedNavigationIndex());
	}

	public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
		private final Activity myActivity;
		private final String myTag;
		private final Class<T> myClass;

		public TabListener(Activity activity, String tag, Class<T> cls) {
			myActivity = activity;
			myTag = tag;
			myClass = cls;
		}

		public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
			Fragment fragment = myActivity.getFragmentManager().findFragmentByTag(myTag);

			// Check if the fragment is already initialized
			if (fragment == null) {
				// If not, instantiate and add it to the activity
				fragment = Fragment.instantiate(myActivity, myClass.getName());
				ft.add(android.R.id.content, fragment, myTag);
			} else {
				// If it exists, simply attach it in order to show it
				ft.attach(fragment);
			}
		}

		public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
			Fragment fragment = myActivity.getFragmentManager().findFragmentByTag(myTag);

			if (fragment != null) {
				// Detach the fragment, because another one is being attached
				ft.detach(fragment);
			}

		}

		public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) { }
	}
}
