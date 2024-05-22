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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.gravitee.apim.gateway.tests.sdk.utils.HttpClientUtils.extractHeaders;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractGatewayTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.policy.PolicyBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.policy.transformheaders.TransformHeadersPolicy;
import io.gravitee.policy.transformheaders.configuration.TransformHeadersPolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompositePolicyV4IntegrationTest extends AbstractGatewayTest {

    @Override
    public void configurePolicies(Map<String, PolicyPlugin> policies) {
        policies.putIfAbsent(
            "transform-headers",
            PolicyBuilder.build("transform-headers", TransformHeadersPolicy.class, TransformHeadersPolicyConfiguration.class)
        );
    }

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
    }

    @Test
    @DeployApi({"/apis/v4/http/environmentflows/api-composite.json"})
    void should_apply_composite_policy(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));

        httpClient
            .rxRequest(HttpMethod.GET, "/test")
            .flatMap(HttpClientRequest::rxSend)
            .test()
            .await()
            .assertComplete()
            .assertValue(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(extractHeaders(response))
                    .contains(
                        Map.entry("X-Request-Header-Outside-0", "Header Outside 0"),
                        Map.entry("X-Request-Header-Inside-0", "Header Inside 0"),
                        Map.entry("X-Request-Header-Inside-1", "Header Inside 1"),
                        Map.entry("X-Request-Header-Outside-1", "Header Outside 1")
                    );
                return true;
            })
            .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi({"/apis/v4/http/environmentflows/api-composite-conditional.json"})
    void should_apply_not_apply_composite_policy_because_of_condition(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));

        httpClient
               .rxRequest(HttpMethod.GET, "/test")
               .flatMap(req -> {
                   req.putHeader("execute-composite", "false");
                   return req.rxSend();
               })
               .test()
               .await()
               .assertComplete()
               .assertValue(response -> {
                   assertThat(response.statusCode()).isEqualTo(200);
                   assertThat(extractHeaders(response))
                          .contains(
                                 Map.entry("X-Request-Header-Outside-0", "Header Outside 0"),
                                 Map.entry("X-Request-Header-Outside-1", "Header Outside 1")
                          )
                          .doesNotContainKey("X-Request-Header-Inside-0")
                          .doesNotContainKey("X-Request-Header-Inside-1");
                   return true;
               })
               .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

    @Test
    @DeployApi({"/apis/v4/http/environmentflows/api-composite-conditional.json"})
    void should_apply_not_apply_conditional_policy_of_a_composite_policy(HttpClient httpClient) throws InterruptedException {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));

        httpClient
               .rxRequest(HttpMethod.GET, "/test")
               .flatMap(req -> {
                   req.putHeader("execute-composite", "yes");
                   req.putHeader("execute-composite-first-step", "no");
                   return req.rxSend();
               })
               .test()
               .await()
               .assertComplete()
               .assertValue(response -> {
                   assertThat(response.statusCode()).isEqualTo(200);
                   assertThat(extractHeaders(response))
                          .contains(
                                 Map.entry("X-Request-Header-Outside-0", "Header Outside 0"),
                                 Map.entry("X-Request-Header-Inside-1", "Header Inside 1"),
                                 Map.entry("X-Request-Header-Outside-1", "Header Outside 1")
                          )
                          .doesNotContainKey("X-Request-Header-Inside-0");
                   return true;
               })
               .assertNoErrors();

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/endpoint")));
    }

}
