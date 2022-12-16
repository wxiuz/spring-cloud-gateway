package io.kyligence.kap.gateway.predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.cache.GlobalKylinBalancerCache;
import io.kyligence.kap.gateway.cache.GlobalRoutingUrlsCache;
import io.kyligence.kap.gateway.config.UsernameCacheProperties;
import io.kyligence.kap.gateway.filter.KylinLoadBalancer;
import io.kyligence.kap.gateway.utils.UrlProjectUtil;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.util.Base64Utils;
import org.springframework.util.DigestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECT_FLAG;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PROJECT_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.SERVICE_ID_KEY;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.USERNAME_KEY;

/**
 * <p>
 * 实现项目下用户级别的资源组路由
 *
 * @author wuxiuzhao
 */
public class KylinUserRoutePredicateFactory
		extends AbstractRoutePredicateFactory<KylinUserRoutePredicateFactory.Config> {

	private static final Log log = LogFactory.getLog(KylinUserRoutePredicateFactory.class);

	private static final String ACCEPT_KYLIN_PUBLIC = "application/vnd.apache.kylin-v4-public+json";

	private static final String ACCEPT_LANGUAGE_CN = "cn";

	private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

	private static final String AUTHENTICATION_URL = "%s://%s%s";

	private static final String COOKIE_SEPARATOR = "; ";

	private static final String SUCCESS_CODE = "000";

	private static final String COLON = ":";

	private static final String BASIC_AUTH_PREFIX = "Basic";

	private static final String SERVER_DEFAULT_HINT = "default";

	private final List<HttpMessageReader<?>> messageReaders;

	private final Class inClass;

	private Cache<String, String> cache;

	@Value("${kylin.gateway.ke.authentication-url:/kylin/api/user/authentication}")
	private String authenticationUrl;

	@Autowired
	@Qualifier("generalRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private GlobalKylinBalancerCache balancerCache;

	@Autowired
	private GlobalRoutingUrlsCache globalRoutingUrlsCache;

	@Autowired
	private UsernameCacheProperties usernameCacheProperties;

	public KylinUserRoutePredicateFactory() {
		super(Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
		this.inClass = String.class;
	}

	@PostConstruct
	public void init() {
		cache = CacheBuilder.newBuilder()
				.concurrencyLevel(usernameCacheProperties.getConcurrencyLevel())
				.maximumSize(usernameCacheProperties.getMaximumSize())
				.expireAfterAccess(usernameCacheProperties.getExpire(), TimeUnit.SECONDS)
				.build();
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Lists.newArrayList(PROJECT_KEY, USERNAME_KEY, SERVICE_ID_KEY);
	}

	private void setAttribute(ServerWebExchange exchange, String key, String value) {
		exchange.getAttributes().put(key, value);
	}

	private void setProject(ServerWebExchange exchange, String project) {
		setAttribute(exchange, PROJECT_KEY, project);
		setAttribute(exchange, PROJECT_FLAG, project);
	}

	private boolean testBasic(ServerWebExchange exchange, Config config) {
		String targetProject = exchange.getAttribute(PROJECT_KEY);
		if (!StringUtils.hasText(targetProject) || !targetProject.equalsIgnoreCase(config.getProject())) {
			return false;
		}

		String username = extractUsername(exchange, config);
		if (username == null) {
			return false;
		}

		Set<String> usernameSet = StringUtils.commaDelimitedListToSet(config.getUsername());
		// 如果匹配到了当前资源组，那么检查一下当前资源组下是否有存活的实例，如果没有，则走项目级别的默认资源组
		if (usernameSet.contains(username) && existsLiveServer(config)) {
			return true;
		}
		return false;
	}

	private boolean testProjectsAndMark(ServerWebExchange exchange, Config config, List<String> projects) {
		if (CollectionUtils.isEmpty(projects)) {
			return false;
		}
		setProject(exchange, projects.get(0));
		return testBasic(exchange, config);
	}

	private String readProjectFromCacheBody(String cacheBody) {
		if (!StringUtils.hasText(cacheBody)) {
			return null;
		}

		try {
			HashMap json = new ObjectMapper().readValue(cacheBody, HashMap.class);
			if (null == json) {
				return null;
			}
			Optional jsonKey = json.keySet().stream().filter(key -> PROJECT_KEY.equalsIgnoreCase(key.toString())).findFirst();
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

	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(Config config) {
		return new AsyncPredicate<ServerWebExchange>() {
			@Override
			public Publisher<Boolean> apply(ServerWebExchange exchange) {
				String path = exchange.getRequest().getPath().toString();
				// 做KE老版本的特殊 url 兼容问题
				if (globalRoutingUrlsCache.shouldGlobalRouting(path)) {
					return Mono.just(false);
				}

				// project_flag 不为 null，代表已经对项目信息已经解析过了，此处就不再继续对项目信息进行重复解析，直接使用结果
				if (Objects.nonNull(exchange.getAttribute(PROJECT_FLAG))) {
					return Mono.just(testBasic(exchange, config));
				}

				// 设置为空，代表已经做过项目信息解析，但是没有解析到项目
				exchange.getAttributes().put(PROJECT_FLAG, "");

				// 第一步: 从 request header 中获取 project 信息，验证一下有 project header 值为 null 的情况和没有 project header 情况
				List<String> headerProjects = exchange.getRequest().getHeaders().get(PROJECT_KEY);
				if (CollectionUtils.isNotEmpty(headerProjects)) {
					return Mono.just(testProjectsAndMark(exchange, config, headerProjects));
				}

				// 第二步: 从 query parameter 中获取 project 信息，验证一下有 project parameter 值为 null 的情况和没有 project parameter 情况
				List<String> queryProjects = exchange.getRequest().getQueryParams().get(PROJECT_KEY);
				if (CollectionUtils.isNotEmpty(queryProjects)) {
					return Mono.just(testProjectsAndMark(exchange, config, queryProjects));
				}

				// 第三步: 从 request body 中解析并获取 project 信息， 如果 request body 没有 project，则从 url path 中获取 project
				return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange,
						serverHttpRequest -> ServerRequest
								.create(exchange.mutate().request(serverHttpRequest).build(), messageReaders)
								.bodyToMono(inClass)
								.defaultIfEmpty("")
								.map(objectValue -> {
									// 从 request body 中取 project 信息
									String project = readProjectFromCacheBody(objectValue);
									if (Objects.nonNull(project)) {
										setProject(exchange, project);
									} else {
										// 从 url path 中获取 project 信息
										project = UrlProjectUtil.extractProjectFromUrlPath(exchange);
										if (Objects.nonNull(project)) {
											setProject(exchange, project);
										}
									}
									return Objects.isNull(project) ? "" : project;
								}).map(project -> testBasic(exchange, config)));

			}

			@Override
			public String toString() {
				return String.format("Projects: %s", config.getProject());
			}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(Config config) {
		throw new UnsupportedOperationException("KylinRoutePredicateFactory is only async.");
	}


	/**
	 * 获取当前请求的发起用户
	 *
	 * @param exchange
	 * @param config
	 * @return
	 */
	private String extractUsername(ServerWebExchange exchange, Config config) {
		// public api的调用方式通过Authorization传入用户信息
		String username = extractUsernameFromHeader(exchange);
		if (StringUtils.hasText(username)) {
			return username;
		}
		// 如果Authorization获取不到，则根据cookie信息从KE中获取
		return extractUsernameFromKe(exchange, config);
	}

	/**
	 * 从Authorization中获取用户信息
	 *
	 * @param exchange
	 * @return
	 */
	private String extractUsernameFromHeader(ServerWebExchange exchange) {
		List<String> headers = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
		log.info(String.format("Extract username from Authorization header , Authorization: [%s]",
				headers == null ? null : headers.toString()));
		if (CollectionUtils.isEmpty(headers)) {
			return null;
		}

		String username = null;
		for (String header : headers) {
			if (!StringUtils.hasText(header)) {
				continue;
			}

			header = header.trim();
			if (!StringUtils.startsWithIgnoreCase(header, BASIC_AUTH_PREFIX) ||
					header.equalsIgnoreCase(BASIC_AUTH_PREFIX)) {
				continue;
			}

			byte[] base64Token = header.substring(6).getBytes(StandardCharsets.UTF_8);
			byte[] decoded = Base64Utils.decode(base64Token);
			String token = new String(decoded, StandardCharsets.UTF_8);
			int delimIndex = token.indexOf(COLON);
			if (delimIndex == -1) {
				continue;
			}
			username = token.substring(0, delimIndex);
			break;
		}
		return username;
	}

	/**
	 * 页面调用方式通过cookie调用KE的http(s)://host:port/kylin/api/user/authentication接口
	 *
	 * @param exchange
	 * @param config
	 * @return
	 */
	private String extractUsernameFromKe(ServerWebExchange exchange, Config config) {
		MultiValueMap<String, HttpCookie> cookies = exchange.getRequest().getCookies();
		if (cookies == null || cookies.isEmpty()) {
			return null;
		}

		// sort后做md5
		List<String> cookieList = cookies.values().stream().flatMap(x -> x.stream())
				.map(HttpCookie::toString).distinct().sorted().collect(Collectors.toList());
		String cookiesStr = Joiner.on(COOKIE_SEPARATOR).join(cookieList);
		String cacheKey = DigestUtils.md5DigestAsHex(cookiesStr.getBytes(StandardCharsets.UTF_8));
		try {
			// 从本地缓存获取，如果本地缓存没有则请求KE获取
			return cache.get(cacheKey, () -> {
				String scheme = exchange.getRequest().getURI().getScheme();
				String username = requestUser(scheme, cookiesStr, config);
				log.info(String.format("Request username [%s] from ke server", username));
				return username;
			});
		} catch (Throwable e) {
			log.error("Request username from ke occur error", e);
			// ignore it and return null
			return null;
		}
	}

	/**
	 * 调用KE接口获取用户信息
	 *
	 * @param scheme
	 * @param cookies
	 * @param config
	 * @return
	 */
	private String requestUser(String scheme, String cookies, Config config) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add(HttpHeaders.ACCEPT, ACCEPT_KYLIN_PUBLIC);
		httpHeaders.add(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE_CN);
		httpHeaders.add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_UTF8);
		httpHeaders.add(HttpHeaders.COOKIE, cookies);

		Server server = chooseServer(config);
		if (server == null) {
			return null;
		}

		HttpEntity httpEntity = new HttpEntity(httpHeaders);
		String authUrl = String.format(AUTHENTICATION_URL, scheme, server.getId(), this.authenticationUrl);
		ResponseEntity<UserResponse> responseEntity = restTemplate.postForEntity(authUrl, httpEntity, UserResponse.class);
		if (responseEntity.getStatusCode().is2xxSuccessful()) {
			UserResponse body = responseEntity.getBody();
			if (SUCCESS_CODE.equals(body.getCode())) {
				return body.getData().getUsername();
			}
		}
		return null;
	}

	private Server chooseServer(Config config) {
		KylinLoadBalancer kylinLoadBalancer = balancerCache.getKylinBalancer().get(config.getServiceId());
		if (kylinLoadBalancer == null) {
			return null;
		}
		return kylinLoadBalancer.chooseServer(SERVER_DEFAULT_HINT);
	}

	private boolean existsLiveServer(Config config) {
		KylinLoadBalancer kylinLoadBalancer = balancerCache.getKylinBalancer().get(config.getServiceId());
		if (kylinLoadBalancer == null) {
			return false;
		}
		return CollectionUtils.isNotEmpty(kylinLoadBalancer.getReachableServers());
	}

	@Data
	public static class UserResponse {
		private String code;

		private User data;

		private String msg;
	}

	@Data
	public static class User {
		private String username;
	}

	@Data
	@Validated
	public static class Config {
		private String project = "";

		private String username = "";

		private String serviceId = "";
	}
}