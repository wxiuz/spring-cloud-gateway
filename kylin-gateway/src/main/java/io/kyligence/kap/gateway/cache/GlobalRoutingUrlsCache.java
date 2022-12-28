package io.kyligence.kap.gateway.cache;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author xiuzhao.wu
 * @date 2022/12/16 14:47
 */
@Slf4j
@Scope(value = "singleton")
@Component
public class GlobalRoutingUrlsCache {

	private static final Set<String> URLS = Sets.newHashSet();

	private static final String DEFAULT_URLS = "GET:/kylin/api/projects/default_configs,GET:/kylin/api/projects," +
			"POST:/kylin/api/models/check_partition_desc";

	@Value("${kylin.gateway.ke.global-routing-urls:" + DEFAULT_URLS + "}")
	private String globalRoutingUrls;

	@PostConstruct
	public void init() {
		if (!StringUtils.hasText(globalRoutingUrls)) {
			this.globalRoutingUrls = DEFAULT_URLS;
		}
		URLS.addAll(StringUtils.commaDelimitedListToSet(this.globalRoutingUrls));
	}

	public boolean shouldGlobalRouting(ServerWebExchange exchange) {
		String path = exchange.getRequest().getPath().toString();
		if (!StringUtils.hasText(path)) {
			return false;
		}
		String method = exchange.getRequest().getMethodValue().toUpperCase();
		String url = Joiner.on(":").join(method, path);
		return URLS.contains(url);
	}
}
