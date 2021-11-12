package org.springframework.cloud.gateway.support.msg;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MessageUtils {
	public static final Set<Locale> SUPPORTED_LOCALES = Sets.newHashSet(Locale.US, Locale.UK, Locale.ENGLISH, Locale.CHINA, Locale.CHINESE);
	public static final Locale DEFAULT_LOCALE = Locale.US;
	public static final String DEFAULT_ERROR_CODE = "999";

	public static boolean isCn(String acceptLanguage) {
		Locale locale = parseLocale(acceptLanguage);
		return Locale.CHINESE.getLanguage().equals(locale.getLanguage());
	}

	public static Locale parseLocale(String acceptLanguage) {
		if (StringUtils.isBlank(acceptLanguage)) {
			return DEFAULT_LOCALE;
		}

		final List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(acceptLanguage);
		Locale result = Locale.lookup(languageRanges, SUPPORTED_LOCALES);
		return result != null ? result : DEFAULT_LOCALE;
	}

	public static boolean isFormatError(String message) {
		return message != null && message.startsWith("[GATEWAY-");
	}

}
