package org.springframework.cloud.gateway.support.msg;

import org.apache.commons.lang.StringUtils;

public class CnMessage extends Message {

	private static volatile CnMessage instance;

	public static CnMessage getInstance() {
		if (null == instance) {
			synchronized (CnMessage.class) {
				if (null == instance) {
					instance = new CnMessage();
				}
			}
		}

		return instance;
	}

	@Override
	public String getContext(String project, ErrorCode errorCode) {
		if (StringUtils.isNotEmpty(project)) {
			return formatContext(String.format(errorCode.cn, project), errorCode.code);
		}
		return formatContext(errorCode.cn, errorCode.code);
	}

	@Override
	public String getContext(ErrorCode errorCode) {
		return formatContext(errorCode.cn, errorCode.code);
	}
}
