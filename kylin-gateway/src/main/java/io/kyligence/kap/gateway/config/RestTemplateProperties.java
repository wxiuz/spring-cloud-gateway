package io.kyligence.kap.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author wuxiuzhao
 */
@Data
@Configuration
@ConfigurationProperties(prefix = RestTemplateProperties.PREFIX)
public class RestTemplateProperties {

	public static final String PREFIX = "kylin.gateway.rest-template";

	/**
	 * 连接超时时间
	 */
	private Integer connectTimeout = 2000;

	/**
	 * 请求超时时间
	 */
	private Integer readTimeout = 5000;

	/**
	 * 连接池配置
	 */
	private Pool pool = new Pool();

	@Data
	public static class Pool {
		/**
		 * 最大连接数
		 */
		private Integer maxTotal = 20;

		/**
		 * 每台主机最大分配连接数
		 */
		private Integer maxPerRoute = 2;
	}
}
