/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.apim.integration.tests.http;

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployOrganization;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import java.util.Map;
import org.junit.jupiter.api.Nested;

/**
 * @author Benoit Bordigoni (benoit.bordigoni at graviteesource.com)
 * @author Gravitee Team
 */
class HttpRequestTimeoutV4IntegrationTest extends HttpRequestTimeoutV4EmulationIntegrationTest {

    @Nested
    @GatewayTest
    @DeployOrganization(
            organization = "/organizations/organization-add-header.json",
            apis = { "/apis/v4/http/api.json", "/apis/v4/http/api-latency.json" }
    )
    class OrganizationWithAddHeader extends HttpRequestTimeoutV4EmulationIntegrationTest.OrganizationWithAddHeader {

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        }
    }

    @Nested
    @GatewayTest
    @DeployOrganization(
            organization = "/organizations/organization-add-header-and-latency.json",
            apis = { "/apis/v4/http/api.json" }
    )
    class OrganizationWithAddHeaderAndLatency extends HttpRequestTimeoutV4EmulationIntegrationTest.OrganizationWithAddHeaderAndLatency {

        @Override
        public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
            entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
        }

        @Override
        public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
            endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
        }
    }
}
