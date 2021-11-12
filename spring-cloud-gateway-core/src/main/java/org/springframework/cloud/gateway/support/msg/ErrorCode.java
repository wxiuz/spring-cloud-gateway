package org.springframework.cloud.gateway.support.msg;

public enum ErrorCode {
	PROJECT_NO_RESOURCE_GROUP("MDX-03060004", "No resource group has been allocated to the project '%s' yet. Please contact your administrator to allocate a resource group for it."
	, "当前项目”%s”未绑定资源组，请联系管理员为其绑定资源组。"),
	PROJECT_NO_INSTANCE("MDX-03060003", "No available instance has been allocated to the project '%s' yet. Please contact your administrator to check the instance and retry."
	, "当前项目“%s”没有可用实例。请联系管理员检查实例状态后重试。"),
	NO_INSTANCE("MDX-03060002", "No available instance. Please contact your administrator to check the instance and retry.",
			"没有可用实例。请联系管理员检查实例状态后重试。"),
	SYSTEM_ERROR("MDX-03060001", "Internal Server Error", "服务器异常");


	public final String code;

	public final String en;

	public final String cn;

	ErrorCode(String code, String enMessage, String cnMessage) {
		this.code = code;
		this.en = enMessage;
		this.cn = cnMessage;
	}

}
