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
package io.gravitee.rest.api.management.v2.rest.resource.api;

import static io.gravitee.common.http.HttpStatusCode.FORBIDDEN_403;
import static io.gravitee.common.http.HttpStatusCode.NOT_FOUND_404;
import static io.gravitee.common.http.HttpStatusCode.OK_200;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fixtures.SubscriptionFixtures;
import inmemory.ApplicationCrudServiceInMemory;
import inmemory.InMemoryAlternative;
import inmemory.PlanCrudServiceInMemory;
import inmemory.SubscriptionCrudServiceInMemory;
import io.gravitee.apim.core.api.domain_service.ApiTemplateDomainService;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.rest.api.management.v2.rest.model.Error;
import io.gravitee.rest.api.management.v2.rest.model.Subscription;
import io.gravitee.rest.api.model.ApiKeyMode;
import io.gravitee.rest.api.model.BaseApplicationEntity;
import io.gravitee.rest.api.model.EnvironmentEntity;
import io.gravitee.rest.api.model.ProcessSubscriptionEntity;
import io.gravitee.rest.api.model.SubscriptionEntity;
import io.gravitee.rest.api.model.SubscriptionStatus;
import io.gravitee.rest.api.model.permissions.RolePermission;
import io.gravitee.rest.api.model.permissions.RolePermissionAction;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotFoundException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class ApiSubscriptionsResource_AcceptTest extends ApiSubscriptionsResourceTest {

    @Autowired
    private ApplicationCrudServiceInMemory applicationCrudServiceInMemory;

    @Autowired
    private PlanCrudServiceInMemory planCrudServiceInMemory;

    @Autowired
    private SubscriptionCrudServiceInMemory subscriptionCrudServiceInMemory;

    @Autowired
    private ApiTemplateDomainService apiTemplateDomainService;

    @BeforeEach
    public void setUp() {
        super.setUp();

        EnvironmentEntity environmentEntity = EnvironmentEntity.builder().id(ENVIRONMENT).organizationId(ORGANIZATION).build();
        doReturn(environmentEntity).when(environmentService).findById(ENVIRONMENT);
        doReturn(environmentEntity).when(environmentService).findByOrgAndIdOrHrid(ORGANIZATION, ENVIRONMENT);

        GraviteeContext.setCurrentEnvironment(ENVIRONMENT);
        GraviteeContext.setCurrentOrganization(ORGANIZATION);

        applicationCrudServiceInMemory.initWith(
            List.of(BaseApplicationEntity.builder().id(APPLICATION).apiKeyMode(ApiKeyMode.EXCLUSIVE).build())
        );
    }

    @AfterEach
    public void tearDown() {
        Stream
            .of(applicationCrudServiceInMemory, planCrudServiceInMemory, subscriptionCrudServiceInMemory)
            .forEach(InMemoryAlternative::reset);

        GraviteeContext.cleanContext();
    }

    @Override
    protected String contextPath() {
        return "/environments/" + ENVIRONMENT + "/apis/" + API + "/subscriptions/" + SUBSCRIPTION + "/_accept";
    }

    @Test
    public void should_return_404_if_not_found() {
        final Response response = rootTarget().request().post(Entity.json(SubscriptionFixtures.anAcceptSubscription()));
        assertEquals(NOT_FOUND_404, response.getStatus());

        var error = response.readEntity(Error.class);
        assertEquals(NOT_FOUND_404, (int) error.getHttpStatus());
        assertEquals("Subscription [" + SUBSCRIPTION + "] cannot be found.", error.getMessage());
    }

    @Test
    public void should_return_404_if_subscription_associated_to_another_api() {
        subscriptionCrudServiceInMemory.initWith(
            List.of(fixtures.core.model.SubscriptionFixtures.aSubscription().toBuilder().id(SUBSCRIPTION).apiId("ANOTHER-API").build())
        );

        final Response response = rootTarget().request().post(Entity.json(SubscriptionFixtures.anAcceptSubscription()));
        assertEquals(NOT_FOUND_404, response.getStatus());

        var error = response.readEntity(Error.class);
        assertEquals(NOT_FOUND_404, (int) error.getHttpStatus());
        assertEquals("Subscription [" + SUBSCRIPTION + "] cannot be found.", error.getMessage());
    }

    @Test
    public void should_return_403_if_incorrect_permissions() {
        when(
            permissionService.hasPermission(
                eq(GraviteeContext.getExecutionContext()),
                eq(RolePermission.API_SUBSCRIPTION),
                eq(API),
                eq(RolePermissionAction.UPDATE)
            )
        )
            .thenReturn(false);

        final Response response = rootTarget().request().post(Entity.json(SubscriptionFixtures.anAcceptSubscription()));
        assertEquals(FORBIDDEN_403, response.getStatus());

        var error = response.readEntity(Error.class);
        assertEquals(FORBIDDEN_403, (int) error.getHttpStatus());
        assertEquals("You do not have sufficient rights to access this resource", error.getMessage());
    }

    @Test
    public void should_accept_subscription() {
        final var acceptSubscription = SubscriptionFixtures.anAcceptSubscription();
        when(apiTemplateDomainService.findByIdForTemplates(anyString(), any(AuditInfo.class)))
            .thenReturn(fixtures.core.model.ApiFixtures.aProxyApiV4());

        planCrudServiceInMemory.initWith(List.of(fixtures.core.model.PlanFixtures.aPlanV4()));

        subscriptionCrudServiceInMemory.initWith(
            List.of(
                fixtures.core.model.SubscriptionFixtures
                    .aSubscription()
                    .toBuilder()
                    .id(SUBSCRIPTION)
                    .apiId(API)
                    .planId(PLAN)
                    .applicationId(APPLICATION)
                    .status(io.gravitee.apim.core.subscription.model.SubscriptionEntity.Status.PENDING)
                    .build()
            )
        );

        final Response response = rootTarget().request().post(Entity.json(acceptSubscription));
        assertEquals(OK_200, response.getStatus());

        final Subscription subscription = response.readEntity(Subscription.class);
        assertEquals(SUBSCRIPTION, subscription.getId());
    }
}
