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
package io.gravitee.rest.api.management.v2.rest.resource.integration;

import static assertions.MAPIAssertions.assertThat;
import static io.gravitee.common.http.HttpStatusCode.FORBIDDEN_403;
import static io.gravitee.rest.api.management.v2.rest.resource.integration.IntegrationsResourceTest.INTEGRATION_PROVIDER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import fixtures.core.model.ApiFixtures;
import fixtures.core.model.IntegrationFixture;
import inmemory.ApiCrudServiceInMemory;
import inmemory.IntegrationCrudServiceInMemory;
import io.gravitee.apim.core.user.model.BaseUserEntity;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.rest.api.management.v2.rest.model.IngestionStatus;
import io.gravitee.rest.api.management.v2.rest.model.Integration;
import io.gravitee.rest.api.management.v2.rest.model.IntegrationIngestionResponse;
import io.gravitee.rest.api.management.v2.rest.resource.AbstractResourceTest;
import io.gravitee.rest.api.model.EnvironmentEntity;
import io.gravitee.rest.api.model.permissions.RolePermission;
import io.gravitee.rest.api.model.permissions.RolePermissionAction;
import io.gravitee.rest.api.model.settings.ApiPrimaryOwnerMode;
import io.gravitee.rest.api.service.common.GraviteeContext;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

public class IntegrationResourceTest extends AbstractResourceTest {

    @Autowired
    IntegrationCrudServiceInMemory integrationCrudServiceInMemory;

    @Autowired
    ApiCrudServiceInMemory apiCrudServiceInMemory;

    static final String ENVIRONMENT = "my-env";
    static final String INTEGRATION_ID = "integration-id";

    WebTarget target;

    @Override
    protected String contextPath() {
        return "/environments/" + ENVIRONMENT + "/integrations/" + INTEGRATION_ID;
    }

    @BeforeEach
    public void init() throws TechnicalException {
        target = rootTarget();

        EnvironmentEntity environmentEntity = EnvironmentEntity.builder().id(ENVIRONMENT).organizationId(ORGANIZATION).build();
        doReturn(environmentEntity).when(environmentService).findById(ENVIRONMENT);
        doReturn(environmentEntity).when(environmentService).findByOrgAndIdOrHrid(ORGANIZATION, ENVIRONMENT);

        GraviteeContext.setCurrentEnvironment(ENVIRONMENT);
        GraviteeContext.setCurrentOrganization(ORGANIZATION);

        roleQueryService.resetSystemRoles(ORGANIZATION);
        enableApiPrimaryOwnerMode(ENVIRONMENT, ApiPrimaryOwnerMode.USER);
        givenExistingUsers(
            List.of(BaseUserEntity.builder().id(USER_NAME).firstname("Jane").lastname("Doe").email("jane.doe@gravitee.io").build())
        );
    }

    @AfterEach
    public void tearDown() {
        super.tearDown();
        integrationCrudServiceInMemory.reset();
        GraviteeContext.cleanContext();
    }

    @Nested
    class GetIntegration {

        @Test
        public void should_get_integration() {
            //Given
            var integration = IntegrationFixture.anIntegration().withId(INTEGRATION_ID);
            integrationCrudServiceInMemory.initWith(List.of(integration));

            //When
            Response response = target.request().get();

            //Then
            assertThat(response)
                .hasStatus(HttpStatusCode.OK_200)
                .asEntity(Integration.class)
                .isEqualTo(
                    Integration
                        .builder()
                        .id(INTEGRATION_ID)
                        .name(integration.getName())
                        .description(integration.getDescription())
                        .provider(integration.getProvider())
                        .agentStatus(Integration.AgentStatusEnum.DISCONNECTED)
                        .build()
                );
        }

        @Test
        public void should_throw_error_when_integration_not_found() {
            //Given
            //When
            Response response = target.request().get();

            //Then
            assertThat(response).hasStatus(HttpStatusCode.NOT_FOUND_404);
        }

        @Test
        public void should_return_403_when_incorrect_permission() {
            when(
                permissionService.hasPermission(
                    eq(GraviteeContext.getExecutionContext()),
                    eq(RolePermission.ENVIRONMENT_INTEGRATION),
                    eq(ENVIRONMENT),
                    eq(RolePermissionAction.READ)
                )
            )
                .thenReturn(false);

            Response response = target.request().get();
            assertThat(response).hasStatus(HttpStatusCode.FORBIDDEN_403);
        }
    }

    @Nested
    class IngestApis {

        @BeforeEach
        void setUp() {
            target = rootTarget().path("_ingest");
        }

        @ParameterizedTest(name = "[{index}] {arguments}")
        @CsvSource(
            delimiterString = "|",
            useHeadersInDisplayName = true,
            textBlock = """
        ENVIRONMENT_INTEGRATION[READ] |  ENVIRONMENT_API[CREATE]
        false                  |  false
        true                   |  false
        false                  |  true
     """
        )
        public void should_get_error_if_user_does_not_have_correct_permissions(
            boolean environmentIntegrationRead,
            boolean environmentApiCreate
        ) {
            when(
                permissionService.hasPermission(
                    GraviteeContext.getExecutionContext(),
                    RolePermission.ENVIRONMENT_INTEGRATION,
                    ENVIRONMENT,
                    RolePermissionAction.READ
                )
            )
                .thenReturn(environmentIntegrationRead);
            when(
                permissionService.hasPermission(
                    GraviteeContext.getExecutionContext(),
                    RolePermission.ENVIRONMENT_API,
                    ENVIRONMENT,
                    RolePermissionAction.CREATE
                )
            )
                .thenReturn(environmentApiCreate);

            final Response response = target.request().post(null);

            assertThat(response).hasStatus(FORBIDDEN_403);
        }

        @Test
        public void should_throw_error_when_integration_not_found() {
            //When
            Response response = target.request().post(null);

            //Then
            assertThat(response).hasStatus(HttpStatusCode.NOT_FOUND_404);
        }

        @Test
        public void should_return_success_when_ingestion_succeed() {
            //Given
            integrationCrudServiceInMemory.initWith(List.of(IntegrationFixture.anIntegration().withId(INTEGRATION_ID)));

            //When
            Response response = target.request().post(null);

            //Then
            assertThat(response)
                .hasStatus(HttpStatusCode.OK_200)
                .asEntity(IntegrationIngestionResponse.class)
                .isEqualTo(IntegrationIngestionResponse.builder().status(IngestionStatus.SUCCESS).build());
        }
    }

    @Nested
    class UpdateIntegration {

        @Test
        public void should_update_integration() {
            //Given
            var updatedName = "updated-name";
            var updatedDescription = "updated-description";
            var integration = List.of(IntegrationFixture.anIntegration());
            integrationCrudServiceInMemory.initWith(integration);

            var updateIntegration = io.gravitee.rest.api.management.v2.rest.model.UpdateIntegration
                .builder()
                .name(updatedName)
                .description(updatedDescription)
                .build();

            //When
            Response response = target.request().put(Entity.json(updateIntegration));

            //Then
            assertThat(response)
                .hasStatus(HttpStatusCode.OK_200)
                .asEntity(Integration.class)
                .isEqualTo(
                    Integration
                        .builder()
                        .id(INTEGRATION_ID)
                        .name(updatedName)
                        .description(updatedDescription)
                        .provider(INTEGRATION_PROVIDER)
                        .agentStatus(Integration.AgentStatusEnum.DISCONNECTED)
                        .build()
                );
        }

        @Test
        public void should_throw_error_when_integration_to_update_not_found() {
            //Given
            var updatedName = "updated-name";
            var updatedDescription = "updated-description";

            var updateIntegration = io.gravitee.rest.api.management.v2.rest.model.UpdateIntegration
                .builder()
                .name(updatedName)
                .description(updatedDescription)
                .build();

            //When
            Response response = target.request().put(Entity.json(updateIntegration));

            //Then
            assertThat(response).hasStatus(HttpStatusCode.NOT_FOUND_404);
        }

        @Test
        public void should_return_403_when_incorrect_permission() {
            when(
                permissionService.hasPermission(
                    eq(GraviteeContext.getExecutionContext()),
                    eq(RolePermission.ENVIRONMENT_INTEGRATION),
                    eq(ENVIRONMENT),
                    eq(RolePermissionAction.UPDATE)
                )
            )
                .thenReturn(false);

            var updateIntegration = io.gravitee.rest.api.management.v2.rest.model.UpdateIntegration.builder().build();

            Response response = target.request().put(Entity.json(updateIntegration));

            assertThat(response).hasStatus(HttpStatusCode.FORBIDDEN_403);
        }

        @Test
        public void should_return_400_when_missing_name_to_update() {
            var updatedDescription = "updated-description";

            var updateIntegration = io.gravitee.rest.api.management.v2.rest.model.UpdateIntegration
                .builder()
                .description(updatedDescription)
                .build();

            Response response = target.request().put(Entity.json(updateIntegration));

            assertThat(response).hasStatus(HttpStatusCode.BAD_REQUEST_400);
        }

        @Test
        public void should_return_400_when_missing_body() {
            var updateIntegration = io.gravitee.rest.api.management.v2.rest.model.UpdateIntegration.builder().build();

            Response response = target.request().put(Entity.json(updateIntegration));

            assertThat(response).hasStatus(HttpStatusCode.BAD_REQUEST_400);
        }
    }

    @Nested
    class DeleteIntegration {

        @Test
        public void should_delete_integration() {
            //Given
            integrationCrudServiceInMemory.initWith(List.of(IntegrationFixture.anIntegration()));
            //When
            Response response = target.request().delete();

            //Then
            assertThat(response).hasStatus(HttpStatusCode.NO_CONTENT_204);
            assertThat(integrationCrudServiceInMemory.storage().size()).isEqualTo(0);
        }

        @Test
        public void should_return_404_when_integration_to_delete_not_found() {
            Response response = target.request().delete();

            assertThat(response).hasStatus(HttpStatusCode.NOT_FOUND_404);
        }

        @Test
        public void should_return_400_when_associated_federated_api_found() {
            integrationCrudServiceInMemory.initWith(List.of(IntegrationFixture.anIntegration()));
            apiCrudServiceInMemory.initWith(List.of(ApiFixtures.aFederatedApi()));

            Response response = target.request().delete();
            assertThat(response).hasStatus(HttpStatusCode.BAD_REQUEST_400);
        }

        @Test
        public void should_return_403_when_incorrect_permission() {
            when(
                permissionService.hasPermission(
                    eq(GraviteeContext.getExecutionContext()),
                    eq(RolePermission.ENVIRONMENT_INTEGRATION),
                    eq(ENVIRONMENT),
                    eq(RolePermissionAction.DELETE)
                )
            )
                .thenReturn(false);

            Response response = target.request().delete();

            assertThat(response).hasStatus(HttpStatusCode.FORBIDDEN_403);
        }
    }
}