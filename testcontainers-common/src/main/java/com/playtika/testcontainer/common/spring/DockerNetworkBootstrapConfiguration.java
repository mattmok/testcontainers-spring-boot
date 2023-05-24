package com.playtika.testcontainer.common.spring;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;

@Slf4j
@Configuration
@AutoConfigureOrder(value = Ordered.HIGHEST_PRECEDENCE)
public class DockerNetworkBootstrapConfiguration {
    private static final String NETWORK_NAME = "testcontainers-spring-boot-network";

    @Bean(name = "network")
    public Network reusableNetwork() {
        String id = DockerClientFactory.instance().client().listNetworksCmd().exec().stream()
                .filter(network -> network.getName().equals(NETWORK_NAME)
                        && network.getLabels().equals(DockerClientFactory.DEFAULT_LABELS))
                .map(com.github.dockerjava.api.model.Network::getId)
                .findFirst()
                .orElseGet(() -> DockerClientFactory.instance().client().createNetworkCmd()
                        .withName(NETWORK_NAME)
                        .withCheckDuplicate(true)
                        .withAttachable(true)
                        .withLabels(DockerClientFactory.DEFAULT_LABELS)
                        .exec().getId());

        log.info("Use docker Network with reuse id={}", id);

        return new Network() {
            @Override
            public Statement apply(Statement statement, Description description) {
                return statement;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public void close() {
                // never close
            }
        };
    }
}
