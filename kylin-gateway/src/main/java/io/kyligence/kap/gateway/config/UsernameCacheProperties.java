package io.kyligence.kap.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author wuxiuzhao
 */
@Data
@Configuration
@ConfigurationProperties(prefix = UsernameCacheProperties.PREFIX)
public class UsernameCacheProperties {

	public static final String PREFIX = "kylin.gateway.username-cache";

	/**
	 * 缓存并发度
	 */
	private Integer concurrencyLevel = 10;

	/**
	 * 缓存失效时长(s)，默认30min
	 */
	private Long expire = 1800L;

	/**
	 * 最大缓存元素个数
	 */
	private Long maximumSize = 3000L;
}
