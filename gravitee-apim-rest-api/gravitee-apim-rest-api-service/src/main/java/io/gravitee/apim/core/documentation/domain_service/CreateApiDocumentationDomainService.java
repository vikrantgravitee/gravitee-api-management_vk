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
package io.gravitee.apim.core.documentation.domain_service;

import io.gravitee.apim.core.audit.domain_service.AuditDomainService;
import io.gravitee.apim.core.audit.model.ApiAuditLogEntity;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.AuditProperties;
import io.gravitee.apim.core.audit.model.event.PageAuditEvent;
import io.gravitee.apim.core.documentation.crud_service.PageCrudService;
import io.gravitee.apim.core.documentation.crud_service.PageRevisionCrudService;
import io.gravitee.apim.core.documentation.model.Page;
import io.gravitee.apim.core.documentation.query_service.PageQueryService;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Map;

public class CreateApiDocumentationDomainService {

    private final PageCrudService pageCrudService;

    private final PageQueryService pageQueryService;
    private final PageRevisionCrudService pageRevisionCrudService;

    private final ApiDocumentationDomainService apiDocumentationDomainService;
    private final AuditDomainService auditDomainService;

    public CreateApiDocumentationDomainService(
        PageCrudService pageCrudService,
        PageQueryService pageQueryService,
        PageRevisionCrudService pageRevisionCrudService,
        ApiDocumentationDomainService apiDocumentationDomainService,
        AuditDomainService auditDomainService
    ) {
        this.pageCrudService = pageCrudService;
        this.pageQueryService = pageQueryService;
        this.pageRevisionCrudService = pageRevisionCrudService;
        this.apiDocumentationDomainService = apiDocumentationDomainService;
        this.auditDomainService = auditDomainService;
    }

    public Page createPage(Page page, AuditInfo auditInfo) {
        var createdPage = pageCrudService.createDocumentationPage(page);

        if (page.isMarkdown()) {
            pageRevisionCrudService.create(createdPage);
            // TODO: only markdown ==> add to index... is lucene necessary?
        }

        auditDomainService.createApiAuditLog(
            ApiAuditLogEntity
                .builder()
                .apiId(page.getReferenceId())
                .event(PageAuditEvent.PAGE_CREATED)
                .createdAt(page.getCreatedAt().toInstant().atZone(ZoneId.of("UTC")))
                .organizationId(auditInfo.organizationId())
                .environmentId(auditInfo.environmentId())
                .actor(auditInfo.actor())
                .properties(Map.of(AuditProperties.PAGE, page.getId()))
                .oldValue(null)
                .newValue(page)
                .build()
        );
        return createdPage;
    }

    public void validateNameIsUnique(Page page) {
        this.apiDocumentationDomainService.validateNameIsUnique(page.getReferenceId(), page.getParentId(), page.getName(), page.getType());
    }

    public void calculateOrder(Page page) {
        var lastPage = pageQueryService
            .searchByApiIdAndParentId(page.getReferenceId(), page.getParentId())
            .stream()
            .max(Comparator.comparingInt(Page::getOrder));
        var nextOrder = lastPage.map(value -> value.getOrder() + 1).orElse(0);

        page.setOrder(nextOrder);
    }
}
