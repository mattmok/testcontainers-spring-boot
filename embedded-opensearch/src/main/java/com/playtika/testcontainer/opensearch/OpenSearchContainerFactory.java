package com.playtika.testcontainer.opensearch;

import com.playtika.testcontainer.common.utils.ContainerUtils;
import com.playtika.testcontainer.opensearch.rest.CreateIndex;
import com.playtika.testcontainer.opensearch.rest.WaitForGreenStatus;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

class OpenSearchContainerFactory {

    static OpensearchContainer create(OpenSearchProperties properties) {
        return new OpensearchContainer(ContainerUtils.getDockerImageName(properties))
                .withExposedPorts(properties.httpPort, properties.transportPort)
                .withEnv("cluster.name", properties.getClusterName())
                .withEnv("discovery.type", "single-node")
                .withEnv("ES_JAVA_OPTS", getJavaOpts(properties))
                .waitingFor(getCompositeWaitStrategy(properties));
    }

    private static String getJavaOpts(OpenSearchProperties properties) {
        return "-Xms" + properties.getClusterRamMb() + "m -Xmx" + properties.getClusterRamMb() + "m";
    }

    private static WaitStrategy getCompositeWaitStrategy(OpenSearchProperties properties) {
        WaitAllStrategy strategy = new WaitAllStrategy()
                .withStrategy(new HostPortWaitStrategy());
        properties.indices.forEach(index -> strategy.withStrategy(new CreateIndex(properties, index)));
        return strategy
                .withStrategy(new WaitForGreenStatus(properties))
                .withStartupTimeout(properties.getTimeoutDuration());
    }
}
