package io.kyligence.kap.gateway.config;

import io.kyligence.kap.gateway.constant.KylinGatewayVersion;
import io.kyligence.kap.gateway.entity.KylinJdbcDataSource;
import io.kyligence.kap.gateway.persistent.FileDataSource;
import io.kyligence.kap.gateway.route.reader.FileRouteTableReader;
import io.kyligence.kap.gateway.route.reader.IRouteTableReader;
import io.kyligence.kap.gateway.route.reader.Kylin3XJdbcRouteTableReader;
import io.kyligence.kap.gateway.persistent.KylinJdbcTemplate;
import io.kyligence.kap.gateway.route.reader.KylinJdbcRouteTableReader;
import io.kyligence.kap.gateway.route.reader.MockRouteTableReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties
public class KylinRouteTableConfiguration {

	@Value(value = "${kylin.gateway.ke.version:4x}")
	private String version;

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "jdbc")
	@Import({DataSourceAutoConfiguration.class})
	static class EnableDataSourceAutoConfiguration {

	}

	@Bean
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "jdbc")
	@ConfigurationProperties(prefix = "kylin.gateway.datasource")
	public KylinJdbcDataSource kylinJdbcDataSource() {
		return new KylinJdbcDataSource();
	}

	@Bean
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "jdbc")
	public IRouteTableReader kylinRouteStore(KylinJdbcDataSource kylinJdbcDataSource, DataSource dataSource) {
		if (KylinGatewayVersion.KYLIN_3X.equals(version)) {
			return new Kylin3XJdbcRouteTableReader(
					new KylinJdbcTemplate(dataSource, kylinJdbcDataSource.getTableName()),
					kylinJdbcDataSource.getTableName());
		}

		return new KylinJdbcRouteTableReader(new KylinJdbcTemplate(dataSource, kylinJdbcDataSource.getTableName()),
				kylinJdbcDataSource.getTableName(), kylinJdbcDataSource.getClusterId());
	}

	@Bean
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "file")
	@ConfigurationProperties(prefix = "kylin.gateway.datasource")
	public FileDataSource fileDataSource() {
		return new FileDataSource();
	}

	@Bean
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "file")
	public IRouteTableReader fileRouteTableReader(FileDataSource fileDataSource) {
		return new FileRouteTableReader(fileDataSource);
	}

	@Bean
	@ConditionalOnProperty(name = "kylin.gateway.datasource.type", havingValue = "mock")
	public IRouteTableReader mockRouteTableReader() {
		return new MockRouteTableReader();
	}

}
