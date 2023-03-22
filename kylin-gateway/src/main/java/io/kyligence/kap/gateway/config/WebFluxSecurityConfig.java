package io.kyligence.kap.gateway.config;

import io.kyligence.kap.gateway.web.filter.ActuatorBasicAuthFilter;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;

/**
 * @author xiuzhao.wu
 * @date 2023/3/22 13:34
 */
@ConditionalOnProperty(name = "kylin.gateway.auth.enable", havingValue = "true")
@Configuration
public class WebFluxSecurityConfig {

	@Value("${kylin.gateway.auth.username:ADMIN}")
	private String username;

	@Value("${kylin.gateway.auth.password:KYLIN}")
	private String password;

	@Value("${kylin.gateway.auth.pattern:/actuator/**}")
	private String authPattern;

	@Order(Ordered.LOWEST_PRECEDENCE - 10000)
	@Bean
	public WebFilter securityFilter() {
		final Set<String> urlPatterns = StringUtils.commaDelimitedListToSet(this.authPattern);
		return new ActuatorBasicAuthFilter(username, password, urlPatterns);
	}
}
