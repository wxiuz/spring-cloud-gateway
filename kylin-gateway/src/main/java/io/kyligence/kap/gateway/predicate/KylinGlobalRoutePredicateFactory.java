package io.kyligence.kap.gateway.predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.kyligence.kap.gateway.cache.GlobalRoutingUrlsCache;
import io.kyligence.kap.gateway.utils.UrlProjectUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECTS_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECT_FLAG;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECT_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECT_NO_RESOURCE_GROUP_EXCEPTION;

public class KylinGlobalRoutePredicateFactory extends AbstractRoutePredicateFactory<KylinGlobalRoutePredicateFactory.Config> {
	private static final Log log = LogFactory.getLog(KylinGlobalRoutePredicateFactory.class);

	private final List<HttpMessageReader<?>> messageReaders;

	private final Class inClass;

	@Autowired
	private GlobalRoutingUrlsCache globalRoutingUrlsCache;

	public KylinGlobalRoutePredicateFactory() {
		super(KylinGlobalRoutePredicateFactory.Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
		this.inClass = String.class;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Lists.newArrayList(PROJECTS_KEY);
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	private void setAttribute(ServerWebExchange exchange, String key, String value) {
		exchange.getAttributes().put(key, value);
	}

	private String readProjectFromCacheBody(String cacheBody) {
		if (StringUtils.isBlank(cacheBody)) {
			return null;
		}

		try {
			HashMap json = new ObjectMapper().readValue(cacheBody, HashMap.class);
			if (null == json) {
				return null;
			}

			Optional jsonKey = json.keySet().stream()
					.filter(key -> PROJECT_KEY.equalsIgnoreCase(key.toString()))
					.findFirst();

			if (jsonKey.isPresent()) {
				return (String) json.get(jsonKey.get());
			}
		} catch (Exception e) {
			log.error("Failed to read project from cache body!", e);
		}

		return null;
	}

	private String readProjectFromCacheBody(Object cacheBody) {
		Preconditions.checkNotNull(cacheBody);
		return readProjectFromCacheBody(cacheBody.toString());
	}

	private String getProjectFromProjectList(List<String> projects) {
		if (CollectionUtils.isEmpty(projects)) {
			return null;
		}

		if (StringUtils.isBlank(projects.get(0))) {
			return null;
		}

		return projects.get(0);
	}

	private Mono<Boolean> setProjectNoResourceGroupException(ServerWebExchange exchange, String project) {
		exchange.getAttributes().put(PROJECT_NO_RESOURCE_GROUP_EXCEPTION, "true");
		setAttribute(exchange, PROJECT_KEY, project);
		return Mono.just(true);
	}

	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(KylinGlobalRoutePredicateFactory.Config config) {
		return new AsyncPredicate<ServerWebExchange>() {
			@Override
			public Publisher<Boolean> apply(ServerWebExchange exchange) {
				String path = exchange.getRequest().getPath().toString();
				// 做KE老版本的特殊URL兼容问题
				if (globalRoutingUrlsCache.shouldGlobalRouting(path)) {
					return Mono.just(true);
				}

				// 有前面的规则做了项目解析，如果开启了资源组，但是没有任何一个项目信息，此时所有请求就会直接走到该处理
				if (Objects.nonNull(exchange.getAttribute(PROJECT_FLAG))) {
					// 没有解析出项目，此时走全局路由
					if (Objects.isNull(exchange.getAttribute(PROJECT_KEY))) {
						return Mono.just(true);
					}
					// 解析出了项目信息，但是没有匹配的规则，那么说明没有配置资源组，应该报没有资源组错误信息
					return setProjectNoResourceGroupException(exchange, exchange.getAttribute(PROJECT_KEY));
				}

				// 如果开启了资源组，但是没有任何项目配置了查询资源组，此时就会走到这一步，所有请求都先来到全局的路由
				//  第一步: 从request header中获取project信息，如果能获取到项目信息，那么说明没有配置资源组，直接报没有资源组错误
				String headerProject = getProjectFromProjectList(exchange.getRequest().getHeaders().get(PROJECT_KEY));
				if (Objects.nonNull(headerProject)) {
					return setProjectNoResourceGroupException(exchange, headerProject);
				}

				// 第二步：第二步: 从query parameter中获取project信息，如果能获取到项目信息，那说明没有配置资源组，直接报没有资源组错误
				String queryProject = getProjectFromProjectList(exchange.getRequest().getQueryParams().get(PROJECT_KEY));
				if (Objects.nonNull(queryProject)) {
					return setProjectNoResourceGroupException(exchange, queryProject);
				}

				// 第三步: 从request body中解析并获取project信息，如果解析不到，则从url path中解析
				return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange,
						serverHttpRequest -> ServerRequest
								.create(exchange.mutate().request(serverHttpRequest).build(), messageReaders)
								.bodyToMono(inClass)
								.defaultIfEmpty("")
								.map(objectValue -> {
									// 从 request body 中获取项目信息
									String project = readProjectFromCacheBody(objectValue);
									if (Objects.nonNull(project)) {
										setProjectNoResourceGroupException(exchange, project);
									} else {
										// 从 path 中获取 project 信息
										project = UrlProjectUtil.extractProjectFromUrlPath(exchange);
										if (Objects.nonNull(project)) {
											setProjectNoResourceGroupException(exchange, project);
										}
									}
									return Objects.isNull(project) ? "" : project;
								}).map(project -> true));
			}

			@Override
			public String toString() {
				return String.format("KylinGlobal: %s", Arrays.toString(config.getProjects().toArray()));
			}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(KylinGlobalRoutePredicateFactory.Config config) {
		throw new UnsupportedOperationException("KylinGlobalRoutePredicateFactory is only async.");
	}

	@Validated
	public static class Config {

		private List<String> projects = Lists.newArrayList();

		public List<String> getProjects() {
			return projects;
		}

		public void setProjects(List<String> projects) {
			this.projects = projects;
		}

	}
}
