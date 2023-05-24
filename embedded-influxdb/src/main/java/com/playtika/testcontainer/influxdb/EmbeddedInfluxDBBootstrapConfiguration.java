package com.playtika.testcontainer.influxdb;

import com.playtika.testcontainer.common.spring.DockerPresenceBootstrapConfiguration;
import com.playtika.testcontainer.common.utils.ContainerUtils;
import com.playtika.testcontainer.toxiproxy.condition.ConditionalOnToxiProxyEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.playtika.testcontainer.common.utils.ContainerUtils.configureCommonsAndStart;
import static com.playtika.testcontainer.influxdb.InfluxDBProperties.EMBEDDED_INFLUX_DB;

@Slf4j
@Configuration
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@AutoConfigureAfter(DockerPresenceBootstrapConfiguration.class)
@ConditionalOnProperty(name = "embedded.influxdb.enabled", matchIfMissing = true)
@EnableConfigurationProperties(InfluxDBProperties.class)
public class EmbeddedInfluxDBBootstrapConfiguration {
    private static final String INFLUXDB_NETWORK_ALIAS = "influxdb.testcontainer.docker";

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "influxdb")
    ToxiproxyContainer.ContainerProxy influxdbContainerProxy(ToxiproxyContainer toxiproxyContainer,
                                                             @Qualifier(EMBEDDED_INFLUX_DB) ConcreteInfluxDbContainer influxdb,
                                                             InfluxDBProperties properties,
                                                             ConfigurableEnvironment environment) {
        ToxiproxyContainer.ContainerProxy proxy = toxiproxyContainer.getProxy(influxdb, properties.getPort());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.influxdb.toxiproxy.host", proxy.getContainerIpAddress());
        map.put("embedded.influxdb.toxiproxy.port", proxy.getProxyPort());
        map.put("embedded.influxdb.toxiproxy.proxyName", proxy.getName());

        MapPropertySource propertySource = new MapPropertySource("embeddedInfluxDBToxiproxyInfo", map);
        environment.getPropertySources().addFirst(propertySource);
        log.info("Started InfluxDB ToxiProxy connection details {}", map);

        return proxy;
    }

    @Bean(name = EMBEDDED_INFLUX_DB, destroyMethod = "stop")
    public ConcreteInfluxDbContainer influxdb(ConfigurableEnvironment environment,
                                              InfluxDBProperties properties,
                                              Network network) {
        ConcreteInfluxDbContainer influxDBContainer = new ConcreteInfluxDbContainer(ContainerUtils.getDockerImageName(properties));
        influxDBContainer
                .withAdmin(properties.getAdminUser())
                .withAdminPassword(properties.getAdminPassword())
                .withAuthEnabled(properties.isEnableHttpAuth())
                .withUsername(properties.getUser())
                .withPassword(properties.getPassword())
                .withDatabase(properties.getDatabase())
                .withExposedPorts(properties.getPort())
                .withNetwork(network)
                .withNetworkAliases(INFLUXDB_NETWORK_ALIAS);

        influxDBContainer.waitingFor(getInfluxWaitStrategy(properties.getUser(), properties.getPassword()));

        influxDBContainer = (ConcreteInfluxDbContainer) configureCommonsAndStart(influxDBContainer, properties, log);
        registerInfluxEnvironment(influxDBContainer, environment, properties);
        return influxDBContainer;
    }

    private void registerInfluxEnvironment(ConcreteInfluxDbContainer influx,
                                           ConfigurableEnvironment environment,
                                           InfluxDBProperties properties) {
        Integer mappedPort = influx.getMappedPort(properties.getPort());
        String host = influx.getHost();

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.influxdb.port", mappedPort);
        map.put("embedded.influxdb.host", host);
        map.put("embedded.influxdb.database", properties.getDatabase());
        map.put("embedded.influxdb.user", properties.getUser());
        map.put("embedded.influxdb.password", properties.getPassword());

        String influxDBURL = "http://{}:{}";
        log.info("Started InfluxDB server. Connection details: {}, " +
                "HTTP connection url: " + influxDBURL, map, host, mappedPort);

        MapPropertySource propertySource = new MapPropertySource("embeddedInfluxDBInfo", map);
        environment.getPropertySources().addFirst(propertySource);
    }

    private static class ConcreteInfluxDbContainer extends InfluxDBContainer<ConcreteInfluxDbContainer> {
        ConcreteInfluxDbContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
            addExposedPort(INFLUXDB_PORT);
        }
    }

    private WaitAllStrategy getInfluxWaitStrategy(String user, String password) {
        return new WaitAllStrategy()
                .withStrategy(Wait.forHttp("/ping")
                        .withBasicCredentials(user, password)
                        .forStatusCode(204))
                .withStrategy(Wait.forListeningPort());
    }
}
