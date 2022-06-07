package io.kyligence.kap.gateway.cache;

import com.google.common.collect.ImmutableMap;
import com.netflix.loadbalancer.BaseLoadBalancer;
import io.kyligence.kap.gateway.filter.KylinLoadBalancer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author wuxiuzhao
 */
@Slf4j
@Scope(value = "singleton")
@Component
public class GlobalKylinBalancerCache {

	private ConcurrentMap<String, KylinLoadBalancer> kylinLoadBalancerMap = new ConcurrentHashMap<>();

	public void updateKylinBalancer(List<BaseLoadBalancer> newBalancer) {
		ConcurrentMap<String, KylinLoadBalancer> newKylinLoadBalancerMap = new ConcurrentHashMap<>(newBalancer.size());
		newBalancer.forEach(balancer -> {
			if (balancer instanceof KylinLoadBalancer) {
				KylinLoadBalancer kylinLoadBalancer = ((KylinLoadBalancer) balancer);
				newKylinLoadBalancerMap.put(kylinLoadBalancer.getServiceId(), kylinLoadBalancer);
			}
		});
		this.kylinLoadBalancerMap = newKylinLoadBalancerMap;
	}

	public Map<String, KylinLoadBalancer> getKylinBalancer() {
		return ImmutableMap.copyOf(this.kylinLoadBalancerMap);
	}
}
