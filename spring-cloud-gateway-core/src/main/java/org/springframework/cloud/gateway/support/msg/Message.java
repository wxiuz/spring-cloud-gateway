package org.springframework.cloud.gateway.support.msg;

import org.apache.commons.lang.StringUtils;

public class Message {

	private static volatile Message instance;

	public static Message getInstance() {
		if (null == instance) {
			synchronized (Message.class) {
				if (null == instance) {
					instance = new Message();
				}
			}
		}

		return instance;
	}

	public String formatContext(String context, String code) {
		return code + " " + context;
	}

	public String getContext(String project, ErrorCode errorCode) {
		if (StringUtils.isNotEmpty(project)) {
			return formatContext(String.format(errorCode.en, project), errorCode.code);
		}
		return errorCode.en;
	}

	public String getContext(ErrorCode errorCode) {
		return formatContext(errorCode.en, errorCode.code);
	}
}
