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

package io.gravitee.apim.core.integration.use_case;

import io.gravitee.apim.core.api.domain_service.CreateFederatedApiDomainService;
import io.gravitee.apim.core.api.model.Api;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.documentation.domain_service.CreateApiDocumentationDomainService;
import io.gravitee.apim.core.documentation.domain_service.DocumentationValidationDomainService;
import io.gravitee.apim.core.documentation.model.Page;
import io.gravitee.apim.core.integration.crud_service.IntegrationCrudService;
import io.gravitee.apim.core.integration.domain_service.IntegrationDomainService;
import io.gravitee.apim.core.integration.model.AssetEntity;
import io.gravitee.apim.core.integration.model.IntegrationEntity;
import io.gravitee.definition.model.DefinitionVersion;
import io.gravitee.definition.model.federation.FederatedApiBuilder;
import io.gravitee.rest.api.model.NewPageEntity;
import io.gravitee.rest.api.model.PageType;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.common.UuidString;
import io.reactivex.rxjava3.core.Completable;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class IntegrationImportAssetsUseCase {

    private final IntegrationDomainService integrationDomainService;
    private final IntegrationCrudService integrationCrudService;
    private final CreateApiDocumentationDomainService createApiDocumentationDomainService;
    private final CreateFederatedApiDomainService createFederatedApiDomainService;
    private final DocumentationValidationDomainService documentationValidationDomainService;

    public IntegrationImportAssetsUseCase.Output execute(IntegrationImportAssetsUseCase.Input input) {
        var integrationId = input.integrationId();
        var auditInfo = input.auditInfo();

        var integration = integrationCrudService.findById(integrationId);

        return new Output(
            integrationDomainService.fetchAssets(integration, input.assets()).map(asset -> importApi(asset, auditInfo)).ignoreElements()
        );
    }

    @Builder
    public record Input(String integrationId, List<AssetEntity> assets, AuditInfo auditInfo) {}

    public record Output(Completable completable) {}

    private Api importApi(AssetEntity asset, AuditInfo auditInfo) {
        // Create API
        var api = Api
            .builder()
            .version(asset.version())
            .definitionVersion(DefinitionVersion.FEDERATED)
            .name(asset.name())
            .description(asset.description())
            .apiDefinitionFederated(
                FederatedApiBuilder
                    .aFederatedApi()
                    .apiVersion(asset.version())
                    .name(asset.name())
                    //.accessPoint(asset.getHost() + asset.getPath())
                    .build()
            )
            .build();

        //TODO manage context to save access point, runtime ...

        var createdApiEntity = createFederatedApiDomainService.create(api, auditInfo);

        // Create page
        asset
            .pages()
            .forEach(page -> {
                PageType pageType = PageType.valueOf(page.getPageType().name());
                createPage(createdApiEntity.getId(), asset.name(), page.getContent(), pageType, auditInfo);
            });

        log.info("API Imported {}", createdApiEntity.getId());
        return createdApiEntity;
    }

    private void createPage(String apiId, String apiName, String content, PageType pageType, AuditInfo auditInfo) {
        var pageToCreate = new Page();
        pageToCreate.setId(UuidString.generateRandom());
        pageToCreate.setReferenceId(apiId);
        pageToCreate.setReferenceType(Page.ReferenceType.API);
        pageToCreate.setType(Page.Type.valueOf(pageType.name()));
        pageToCreate.setContent(content);
        pageToCreate.setCreatedAt(new Date());
        pageToCreate.setUpdatedAt(pageToCreate.getCreatedAt());
        pageToCreate.setName(documentationValidationDomainService.sanitizeDocumentationName(apiName));

        if (pageToCreate.isMarkdown()) {
            this.documentationValidationDomainService.validateContentIsSafe(pageToCreate.getContent());
        }

        createApiDocumentationDomainService.validateNameIsUnique(pageToCreate);
        createApiDocumentationDomainService.calculateOrder(pageToCreate);

        createApiDocumentationDomainService.createPage(pageToCreate, auditInfo);
    }
}
