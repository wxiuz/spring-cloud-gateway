package io.kyligence.kap.gateway.server;

import com.alibaba.fastjson.JSONObject;
import com.esotericsoftware.yamlbeans.YamlReader;
import io.kyligence.kap.gateway.bean.Response;
import io.kyligence.kap.gateway.config.MdxConfig;
import io.kyligence.kap.gateway.config.UpdateConfig;
import io.kyligence.kap.gateway.filter.MdxLoadBalancerClientFilter;
import io.kyligence.kap.gateway.health.MdxLoad;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileReader;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("api")
public class GatewayController {

	private static final String CONFIG_FILE = "spring.config.additional-location";

	public final static String RESP_SUC = "success";

	public final static String FAIL = "failed";

	@Autowired
	private MdxConfig mdxConfig;

	@GetMapping("gateway/admin/reload")
	public Response<String> reload() {
		log.info("http call url: api/gateway/admin/reload");
		try {
			String configPath = System.getProperty(CONFIG_FILE);
			YamlReader reader = new YamlReader(new FileReader(configPath));
			Object object = reader.read(Object.class);
			String jsonStr = JSONObject.toJSONString(object);
			UpdateConfig updateConfig = JSONObject.parseObject(jsonStr, UpdateConfig.class);
			mdxConfig.setProxyInfo(updateConfig.getMdx().getProxy());
			log.info("reload gateway config success!");
			return new Response<String>(Response.Status.SUCCESS)
					.data(RESP_SUC);
		} catch (Exception e) {
			log.error("reload gateway config catch error: " + e);
			Response<String> response = new Response<>(Response.Status.FAIL, FAIL);
			response.errorMsg("reload gateway config catch error: " + e);
			return response;
		}
	}

	@GetMapping("gateway/status/load")
	public Response<Map<String, MdxLoad.LoadInfo>> getLoad() {
		log.info("http call url: api/gateway/status/load");
		return new Response<>(MdxLoad.LOAD_INFO_MAP);
	}

	@GetMapping("gateway/status/route")
	public Response<Map<String, MdxLoadBalancerClientFilter.ServerInfo>> getRouteStatus() {
		log.info("http call url: api/gateway/status/route");
		return new Response<>(MdxLoadBalancerClientFilter.serverMap);
	}

	@GetMapping("gateway/health")
	public Response<String> checkHealth() {
		log.info("http call url: api/gateway/health");
		return new Response<>(RESP_SUC);
	}

}
