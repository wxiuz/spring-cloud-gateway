//package io.kyligence.kap.gateway.web.filter;
//
//import java.util.Base64;
//import java.util.Optional;
//import java.util.Set;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.util.AntPathMatcher;
//import org.springframework.util.StringUtils;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.web.server.WebFilter;
//import org.springframework.web.server.WebFilterChain;
//import reactor.core.publisher.Mono;
//
///**
// * @author xiuzhao.wu
// * @date 2023/3/22 17:17
// */
//public class ActuatorBasicAuthFilter implements WebFilter {
//
//	private static final String BASIC = "Basic ";
//
//	public static final String AUTHORIZATION = "Authorization";
//
//	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
//
//	private static final String HEADER_VALUE = "Basic realm=\"Realm\"";
//
//	private String username;
//
//	private String password;
//
//	private Set<String> authUrlPatterns;
//
//	public ActuatorBasicAuthFilter(String username, String password, Set<String> authUrlPatterns) {
//		this.username = username;
//		this.password = password;
//		this.authUrlPatterns = authUrlPatterns;
//	}
//
//	@Override
//	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//		final AntPathMatcher antPathMatcher = new AntPathMatcher();
//		final ServerHttpRequest request = exchange.getRequest();
//		final String uriPath = request.getURI().getPath();
//
//		final Optional<String> match = authUrlPatterns.stream()
//				.filter(pattern -> antPathMatcher.match(pattern, uriPath)).findAny();
//		if (!match.isPresent()) {
//			return chain.filter(exchange);
//		}
//
//		final String authorization = request.getHeaders().getFirst(AUTHORIZATION);
//
//		if (!StringUtils.hasText(authorization)) {
//			return commence(exchange);
//		}
//
//		if (!StringUtils.startsWithIgnoreCase(authorization, "basic ")) {
//			return commence(exchange);
//		}
//
//		final String credentials = (authorization.length() <= BASIC.length()) ? "" : authorization.substring(BASIC.length());
//		final String decoded = new String(base64Decode(credentials));
//		final String[] parts = decoded.split(":", 2);
//		if (parts.length != 2) {
//			return commence(exchange);
//		}
//
//		final String username = parts[0];
//		final String password = parts[1];
//		if (this.username.equalsIgnoreCase(username) && this.password.equals(password)) {
//			return chain.filter(exchange);
//		} else {
//			return commence(exchange);
//		}
//	}
//
//	private Mono<Void> commence(ServerWebExchange exchange) {
//		final ServerHttpResponse response = exchange.getResponse();
//		response.setStatusCode(HttpStatus.UNAUTHORIZED);
//		response.getHeaders().set(WWW_AUTHENTICATE, HEADER_VALUE);
//		return response.setComplete();
//	}
//
//	private byte[] base64Decode(String value) {
//		try {
//			return Base64.getDecoder().decode(value);
//		} catch (Exception ex) {
//			return new byte[0];
//		}
//	}
//}
