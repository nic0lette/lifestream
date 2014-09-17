package net.kayateia.lifestream;

import android.app.Application;

/**
 * Application starts before the first activity (or when the app starts from a service,
 * broadcast, etc...) and anyone who has a context can call Context.getApplication()
 * so it makes it very convenient for holding onto values that are shared that don't
 * need to be, or shouldn't, be in shared preferences
 */
public class LifeStreamApplication extends Application {
	/*
	 * This is normally a *Bad Thing* to do; saving a reference to a Context in a static variable
	 * but this is the one time it's "okay"
	 * The reason it's bad is because it's a strong reference to a (say) Activity, which has
	 * strong references to Views, etc... Really bad.
	 * Here, the Application holding a strong reference to itself is okay, so long as it isn't
	 * held onto elsewhere - so don't take it and save it ;)
	 */
	private static LifeStreamApplication s_app;

	private Settings _settings;

	@Override
	public void onCreate() {
		super.onCreate();

		// Save a reference to ourself
		s_app = this;

		// Create the settings class
		_settings = new Settings(this);
	}

	// Make it easy to get a handle to us any time
	public static LifeStreamApplication GetApp() {
		return s_app;
	}

	public Settings GetSettings() {
		return _settings;
	}

	// Convenience method to get our GCM Sender ID
	public String GetGcmID() {
		return _settings.GCM_ID;
	}
}
