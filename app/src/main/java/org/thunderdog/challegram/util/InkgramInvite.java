/*
 * Inkgram: localized "Invite Friends" message.
 */
package org.thunderdog.challegram.util;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Locale;

/**
 * Builds the invite text from resources matching the language selected inside the app,
 * rather than the system locale. This keeps the message localized even when the
 * in-app language differs from the device language.
 */
public final class InkgramInvite {
  public static final String WEBSITE = "https://inkgram.app";

  private InkgramInvite () { }

  public static String getText () {
    Resources resources = UI.getResources();
    String languageCode = getLanguageCode();
    if (languageCode == null || languageCode.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return resources.getString(R.string.InkgramInviteText, WEBSITE);
    }

    Configuration configuration = new Configuration(resources.getConfiguration());
    configuration.setLocale(localeFor(languageCode));
    return AppContext.get()
      .createConfigurationContext(configuration)
      .getResources()
      .getString(R.string.InkgramInviteText, WEBSITE);
  }

  private static String getLanguageCode () {
    TdApi.LanguagePackInfo language = Settings.instance().getLanguagePackInfo();
    String code = language.id;
    if (code != null && !code.isEmpty() && code.charAt(0) != 'X') {
      return code;
    }
    if (language.baseLanguagePackId != null && !language.baseLanguagePackId.isEmpty()) {
      return language.baseLanguagePackId;
    }
    if (language.pluralCode != null && !language.pluralCode.isEmpty()) {
      return language.pluralCode;
    }
    return code;
  }

  private static Locale localeFor (String languageCode) {
    String code = languageCode.replace('_', '-');
    String lowerCase = code.toLowerCase(Locale.ROOT);
    if (lowerCase.equals("zh") || lowerCase.startsWith("zh-")) {
      if (lowerCase.contains("hant") || lowerCase.contains("-tw") || lowerCase.contains("-hk") || lowerCase.contains("-mo")) {
        code = "zh-TW";
      } else {
        code = "zh-CN";
      }
    }
    int separator = code.indexOf('-');
    if (separator == -1) {
      return new Locale(code);
    }
    String language = code.substring(0, separator);
    String country = code.substring(separator + 1);
    int extra = country.indexOf('-');
    if (extra != -1) {
      country = country.substring(0, extra);
    }
    return new Locale(language, country.toUpperCase(Locale.ROOT));
  }
}
