/*
 * This is the source code of Inkgram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Inkgram-specific configuration, separate from Telegram's SharedConfig.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages Inkgram-specific configuration settings.
 * Uses a dedicated SharedPreferences file ("inkgramconfig") to keep
 * Inkgram settings cleanly separated from upstream Telegram config.
 */
public class InkgramConfig {

    // UI Mode constants
    public static final int UI_MODE_CLASSIC = 0;  // Telegram UI optimized for e-ink
    public static final int UI_MODE_EINK = 1;     // Fully redesigned e-ink UI

    // Current UI mode (default: Classic)
    public static int uiMode = UI_MODE_CLASSIC;

    // E-Ink page-by-page scrolling (flip browsing) mode (default: true)
    public static boolean pageFlippingEnabled = true;

    private static boolean configLoaded = false;
    private static final Object sync = new Object();

    private static final String PREFS_NAME = "inkgramconfig";
    private static final String KEY_UI_MODE = "ui_mode";
    private static final String KEY_PAGE_FLIPPING = "page_flipping";

    /**
     * Load Inkgram configuration from SharedPreferences.
     * Safe to call multiple times; will only load once.
     */
    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded || ApplicationLoader.applicationContext == null) {
                return;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            uiMode = preferences.getInt(KEY_UI_MODE, UI_MODE_CLASSIC);
            pageFlippingEnabled = preferences.getBoolean(KEY_PAGE_FLIPPING, true);
            configLoaded = true;
        }
    }

    /**
     * Save current Inkgram configuration to SharedPreferences.
     */
    public static void saveConfig() {
        synchronized (sync) {
            if (ApplicationLoader.applicationContext == null) {
                return;
            }
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit();
            editor.putInt(KEY_UI_MODE, uiMode);
            editor.putBoolean(KEY_PAGE_FLIPPING, pageFlippingEnabled);
            editor.apply();
        }
    }

    /**
     * Set the UI mode and persist the change.
     * Broadcasts inkgramUiModeChanged notification via the global NotificationCenter.
     *
     * @param mode One of {@link #UI_MODE_CLASSIC} or {@link #UI_MODE_EINK}
     */
    public static void setUiMode(int mode) {
        if (uiMode == mode) {
            return;
        }
        uiMode = mode;
        saveConfig();
        NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.inkgramUiModeChanged, mode);
    }

    /**
     * Set page-by-page scrolling mode and persist the change.
     *
     * @param enabled true to enable page flipping, false for normal scrolling
     */
    public static void setPageFlippingEnabled(boolean enabled) {
        if (pageFlippingEnabled == enabled) {
            return;
        }
        pageFlippingEnabled = enabled;
        saveConfig();
    }

    /**
     * @return true if page-by-page scrolling is enabled
     */
    public static boolean isPageFlippingEnabled() {
        return pageFlippingEnabled;
    }

    /**
     * @return true if the current mode is E-Ink (redesigned UI)
     */
    public static boolean isEinkMode() {
        return uiMode == UI_MODE_EINK;
    }

    /**
     * @return true if the current mode is Classic (optimized Telegram UI)
     */
    public static boolean isClassicMode() {
        return uiMode == UI_MODE_CLASSIC;
    }
}
