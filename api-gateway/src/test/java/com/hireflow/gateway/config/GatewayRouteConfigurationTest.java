package com.hireflow.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class GatewayRouteConfigurationTest {

    @Test
    void exposesAiServiceRouteConfiguration() {
        HireFlowServiceUris services = new HireFlowServiceUris(
                URI.create("http://auth"), URI.create("http://candidate"),
                URI.create("http://job"), URI.create("http://application"),
                URI.create("http://ai"));

        assertThat(services.aiServiceUri()).isEqualTo(URI.create("http://ai"));
        assertThat(GatewayRouteConfiguration.AI_PATH).isEqualTo("/api/ai/**");
    }
}
