package io.kyligence.kap.gateway.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.server.ServerWebExchange;

public class UrlProjectUtil {
	public static final String URL_PROJECTS_PREFIX = "/kylin/api/projects";

	public static final String URL_MODELS_PREFIX = "/kylin/api/models";

	private static final Pattern[] URL_PROJECT_PATTERNS = new Pattern[]{
			Pattern.compile("^/kylin/api/projects/([^/]+)"),
			Pattern.compile("^/kylin/api/models/([^/]+)")
	};

	private UrlProjectUtil() {

	}

	public static String extractProjectFromUrlPath(ServerWebExchange exchange) {
		String urlPath = exchange.getRequest().getPath().toString();
		if (StringUtils.isBlank(urlPath)
				|| !(urlPath.startsWith(URL_PROJECTS_PREFIX) || urlPath.startsWith(URL_MODELS_PREFIX))) {
			return null;
		}

		for (Pattern pattern : URL_PROJECT_PATTERNS) {
			Matcher matcher = pattern.matcher(urlPath);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}

		return null;
	}

}
