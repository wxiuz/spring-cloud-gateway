package io.kyligence.kap.gateway.route;

import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.cache.GlobalKylinBalancerCache;
import io.kyligence.kap.gateway.config.GlobalConfig;
import io.kyligence.kap.gateway.constant.KylinGatewayVersion;
import io.kyligence.kap.gateway.entity.KylinRouteRaw;
import io.kyligence.kap.gateway.entity.KylinRouteTable;
import io.kyligence.kap.gateway.event.KylinRefreshRoutesEvent;
import io.kyligence.kap.gateway.route.handler.RouteTableFilter;
import io.kyligence.kap.gateway.route.reader.IRouteTableReader;
import io.kyligence.kap.gateway.route.transformer.RouteTableTransformer;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.actuate.AbstractGatewayControllerEndpoint;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ConditionalOnProperty(name = "kylin.gateway.ke.version", havingValue = KylinGatewayVersion.KYLIN_4X)
@Component
public class KylinRefreshRouteTableScheduler implements ApplicationEventPublisherAware {

	private static final Logger logger = LoggerFactory.getLogger(KylinRefreshRouteTableScheduler.class);

	protected ApplicationEventPublisher publisher;

	private AbstractGatewayControllerEndpoint gatewayControllerEndpoint;

	private LoadBalancerClientFilter loadBalancerClientFilter;

	private IRouteTableReader routeTableReader;

	private final ScheduledExecutorService routeRefresher;

	@Autowired
	private GlobalConfig globalConfig;

	@Autowired
	private List<RouteTableFilter> routeTableFilterList;

	@Autowired
	private RouteTableTransformer routeTableTransformer;

	@Autowired
	private GlobalKylinBalancerCache kylinBalancerCache;

	public KylinRefreshRouteTableScheduler(IRouteTableReader routeTableReader,
										   AbstractGatewayControllerEndpoint gatewayControllerEndpoint,
										   LoadBalancerClientFilter loadBalancerClientFilter) {
		this.routeTableReader = routeTableReader;
		this.gatewayControllerEndpoint = gatewayControllerEndpoint;
		this.loadBalancerClientFilter = loadBalancerClientFilter;

		this.routeRefresher = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("RouteRefresher"));
	}

	@PostConstruct
	private void init() {
		this.run();
		routeRefresher.scheduleWithFixedDelay(this::run, globalConfig.getRouteRefreshIntervalSeconds(), globalConfig.getRouteRefreshIntervalSeconds(), TimeUnit.SECONDS);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	private synchronized void run() {
		try {
			List<KylinRouteRaw> rawRouteTable = routeTableReader.list();

			for (RouteTableFilter routeTableFilter : routeTableFilterList) {
				if (routeTableFilter.filter(rawRouteTable)) {
					if (CollectionUtils.isNotEmpty(routeTableFilter.getErrorMessage())) {
						routeTableFilter.getErrorMessage().forEach(logger::error);
					}
					return;
				}
			}

			logger.info("Start to update route table ...");

			KylinRouteTable routeTable = routeTableTransformer.convert(rawRouteTable);
			if (routeTable.isBroken()) {
				logger.error("Failed to convert rawRouteTable to kylinRouteTable!");
				return;
			}

			this.loadBalancerClientFilter.addResourceGroups(routeTable.getLoadBalancerList());

			globalConfig.getLastValidRawRouteTable().forEach(kylinRouteRaw ->
					gatewayControllerEndpoint.delete(String.valueOf(kylinRouteRaw.getId())).subscribe());

			routeTable.getRouteDefinitionList().forEach(routeDefinition ->
					gatewayControllerEndpoint.save(routeDefinition.getId(), routeDefinition).subscribe());

			Set<Server> serverSet = routeTable.getLoadBalancerList().stream()
					.flatMap(loadBalancer -> loadBalancer.getAllServers().stream()).collect(Collectors.toSet());
			this.publisher.publishEvent(new KylinRefreshRoutesEvent(this, serverSet));

			this.loadBalancerClientFilter.updateResourceGroups(
					routeTable.getLoadBalancerList(), globalConfig.getLastValidRawRouteTableMvcc().get());
			// 与Filter共用相同的Balancer
			this.kylinBalancerCache.updateKylinBalancer(routeTable.getLoadBalancerList());

			globalConfig.getLastValidRawRouteTableMvcc().incrementAndGet();
			globalConfig.setLastValidRawRouteTable(rawRouteTable);

			logger.info("Update route table is success ...");
		} catch (Exception e) {
			logger.error("Failed to get route table from {}!", routeTableReader.getClass(), e);
		}

	}

}
