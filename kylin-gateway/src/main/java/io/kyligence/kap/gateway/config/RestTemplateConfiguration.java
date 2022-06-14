package io.kyligence.kap.gateway.config;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author wuxiuzhao
 */
@Configuration
public class RestTemplateConfiguration {

	@Autowired
	private RestTemplateProperties restTemplateProperties;

	@Bean
	public RestTemplate generalRestTemplate() {
		// TODO https的处理，是否需要忽略掉证书问题，待验证
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(restTemplateProperties.getPool().getMaxTotal());
		connectionManager.setDefaultMaxPerRoute(restTemplateProperties.getPool().getMaxPerRoute());
		HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager)
				.setConnectionManagerShared(true).build();
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
		requestFactory.setConnectTimeout(restTemplateProperties.getConnectTimeout());
		requestFactory.setReadTimeout(restTemplateProperties.getReadTimeout());
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setRequestFactory(requestFactory);
		return restTemplate;
	}

}
