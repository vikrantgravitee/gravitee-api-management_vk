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
package io.gravitee.definition.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fixtures.definition.ApiDefinitionFixtures;
import fixtures.definition.FlowFixtures;
import io.gravitee.definition.model.flow.Flow;
import io.gravitee.definition.model.flow.Step;
import io.gravitee.definition.model.plugins.resources.Resource;
import io.gravitee.definition.model.services.Services;
import io.gravitee.definition.model.services.dynamicproperty.DynamicPropertyService;
import io.gravitee.definition.model.services.healthcheck.HealthCheckService;
import io.gravitee.definition.model.v4.endpointgroup.Endpoint;
import io.gravitee.definition.model.v4.endpointgroup.EndpointGroup;
import io.gravitee.definition.model.v4.listener.entrypoint.Entrypoint;
import io.gravitee.definition.model.v4.listener.subscription.SubscriptionListener;
import io.gravitee.definition.model.v4.service.ApiServices;
import io.gravitee.definition.model.v4.service.Service;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiTest {

    @Test
    void getPluginsForApiV2() {
        Api api = ApiDefinitionFixtures.anApiV2();
        assertThat(api.getPlugins()).isEmpty();

        Api apiWithResource = ApiDefinitionFixtures.anApiV2();
        apiWithResource.setResources(List.of(Resource.builder().type("cache").build()));
        assertThat(apiWithResource.getPlugins()).containsExactly(new Plugin("resource", "cache"));

        Api apiWithFlows = ApiDefinitionFixtures.anApiV2();
        Flow flow = FlowFixtures.aFlowV2();
        flow.setPre(List.of(Step.builder().policy("policy-request-validation").build()));
        flow.setPost(List.of(Step.builder().policy("json-validation").build()));
        apiWithFlows.setFlows(List.of(flow));
        assertThat(apiWithFlows.getPlugins())
            .containsOnly(new Plugin("policy", "policy-request-validation"), new Plugin("policy", "json-validation"));

        Api apiWithPlans = ApiDefinitionFixtures.anApiV2();
        Plan plan = Plan.builder().flows(List.of(Flow.builder().pre(List.of(Step.builder().policy("json-xml").build())).build())).build();
        apiWithPlans.setPlans(List.of(plan));
        assertThat(apiWithPlans.getPlugins()).containsOnly(new Plugin("policy", "json-xml"));

        Api apiWithServices = ApiDefinitionFixtures.anApiV2();
        Services services = new Services();
        services.set(List.of(new HealthCheckService()));
        apiWithServices.setServices(services);
        assertThat(apiWithServices.getPlugins()).containsOnly(new Plugin("service", "health-check"));
    }

    @Test
    void getPluginsForApiV4() {
        io.gravitee.definition.model.v4.Api api = ApiDefinitionFixtures.anApiV4();
        assertThat(api.getPlugins()).isEmpty();

        io.gravitee.definition.model.v4.Api apiWithResource = ApiDefinitionFixtures.anApiV4();
        apiWithResource.setResources(List.of(io.gravitee.definition.model.v4.resource.Resource.builder().type("cache").build()));
        assertThat(apiWithResource.getPlugins()).containsExactly(new Plugin("resource", "cache"));

        io.gravitee.definition.model.v4.Api apiWithMessageFlows = ApiDefinitionFixtures.anApiV4();
        io.gravitee.definition.model.v4.flow.Flow flow = FlowFixtures.aMessageFlowV4();
        flow.setRequest(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("policy-request-validation").build()));
        flow.setResponse(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("json-validation").build()));
        flow.setPublish(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("policy-override-request-method").build()));
        flow.setSubscribe(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("transform-queryparams").build()));
        apiWithMessageFlows.setFlows(List.of(flow));
        assertThat(apiWithMessageFlows.getPlugins())
            .containsOnly(
                new Plugin("policy", "policy-request-validation"),
                new Plugin("policy", "json-validation"),
                new Plugin("policy", "policy-override-request-method"),
                new Plugin("policy", "transform-queryparams")
            );

        io.gravitee.definition.model.v4.Api apiWithProxyFlows = ApiDefinitionFixtures.anApiV4();
        io.gravitee.definition.model.v4.flow.Flow proxyFlow = FlowFixtures.aProxyFlowV4();
        proxyFlow.setRequest(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("policy-request-validation").build()));
        proxyFlow.setResponse(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("json-validation").build()));
        proxyFlow.setPublish(
            List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("policy-override-request-method").build())
        );
        proxyFlow.setSubscribe(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("transform-queryparams").build()));
        apiWithProxyFlows.setFlows(List.of(proxyFlow));
        assertThat(apiWithProxyFlows.getPlugins())
            .containsOnly(
                new Plugin("policy", "policy-request-validation"),
                new Plugin("policy", "json-validation"),
                new Plugin("policy", "policy-override-request-method"),
                new Plugin("policy", "transform-queryparams")
            );

        io.gravitee.definition.model.v4.Api apiWithPlans = ApiDefinitionFixtures.anApiV4();
        io.gravitee.definition.model.v4.plan.Plan plan = io.gravitee.definition.model.v4.plan.Plan
            .builder()
            .flows(
                List.of(
                    io.gravitee.definition.model.v4.flow.Flow
                        .builder()
                        .request(List.of(io.gravitee.definition.model.v4.flow.step.Step.builder().policy("json-xml").build()))
                        .build()
                )
            )
            .build();
        apiWithPlans.setPlans(List.of(plan));
        assertThat(apiWithPlans.getPlugins()).containsOnly(new Plugin("policy", "json-xml"));

        io.gravitee.definition.model.v4.Api apiWithServices = ApiDefinitionFixtures.anApiV4();
        ApiServices services = new ApiServices();
        Service dynamicPropertyService = new Service();
        dynamicPropertyService.setType("health-check");
        services.setDynamicProperty(dynamicPropertyService);
        apiWithServices.setServices(services);
        assertThat(apiWithServices.getPlugins()).containsOnly(new Plugin("service", "health-check"));

        io.gravitee.definition.model.v4.Api apiWithListeners = ApiDefinitionFixtures.anApiV4();
        apiWithListeners.setListeners(
            List.of(SubscriptionListener.builder().entrypoints(List.of(Entrypoint.builder().type("websocket").build())).build())
        );
        assertThat(apiWithListeners.getPlugins()).containsOnly(new Plugin("entrypoint-connector", "websocket"));

        io.gravitee.definition.model.v4.Api apiWithEndpoints = ApiDefinitionFixtures.anApiV4();
        apiWithEndpoints.setEndpointGroups(
            List.of(EndpointGroup.builder().endpoints(List.of(Endpoint.builder().type("mqtt5").build())).build())
        );
        assertThat(apiWithEndpoints.getPlugins()).containsOnly(new Plugin("endpoint-connector", "mqtt5"));
    }
}
