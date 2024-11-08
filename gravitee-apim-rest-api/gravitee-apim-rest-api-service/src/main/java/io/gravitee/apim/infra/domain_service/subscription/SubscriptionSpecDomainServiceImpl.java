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
package io.gravitee.apim.infra.domain_service.subscription;

import io.gravitee.apim.core.audit.domain_service.AuditDomainService;
import io.gravitee.apim.core.audit.model.ApiAuditLogEntity;
import io.gravitee.apim.core.audit.model.ApplicationAuditLogEntity;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.AuditProperties;
import io.gravitee.apim.core.audit.model.event.AuditEvent;
import io.gravitee.apim.core.audit.model.event.SubscriptionAuditEvent;
import io.gravitee.apim.core.subscription.crud_service.SubscriptionCrudService;
import io.gravitee.apim.core.subscription.domain_service.AcceptSubscriptionDomainService;
import io.gravitee.apim.core.subscription.domain_service.SubscriptionSpecDomainService;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.apim.core.subscription.model.crd.SubscriptionSpec;
import io.gravitee.apim.infra.adapter.SubscriptionAdapter;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotFoundException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Antoine CORDIER (antoine.cordier at graviteesource.com)
 * @author GraviteeSource Team
 */
@Service
@RequiredArgsConstructor
public class SubscriptionSpecDomainServiceImpl implements SubscriptionSpecDomainService {

    private final SubscriptionCrudService subscriptionCrudService;

    private final AcceptSubscriptionDomainService acceptService;

    private final AuditDomainService auditService;

    private final SubscriptionAdapter adapter;

    @Override
    public SubscriptionEntity createOrUpdate(AuditInfo auditInfo, SubscriptionSpec spec) {
        return find(spec.getId()).map((existing -> update(auditInfo, existing, spec))).orElseGet(() -> create(auditInfo, spec));
    }

    private SubscriptionEntity create(AuditInfo auditInfo, SubscriptionSpec spec) {
        var entity = adapter.fromSpec(spec);
        entity.setEnvironmentId(auditInfo.environmentId());
        entity.setCreatedAt(ZonedDateTime.now());
        entity.setProcessedAt(ZonedDateTime.now());
        entity.setStartingAt(spec.getStartingAt() == null ? ZonedDateTime.now() : spec.getStartingAt());
        entity.setSubscribedBy(auditInfo.actor().userId());
        entity.setProcessedBy(auditInfo.actor().userId());
        var subscription = subscriptionCrudService.create(entity);
        createAudit(null, entity, auditInfo, SubscriptionAuditEvent.SUBSCRIPTION_CREATED);
        return acceptService.autoAccept(subscription.getId(), spec.getStartingAt(), spec.getEndingAt(), "", "", auditInfo);
    }

    private SubscriptionEntity update(AuditInfo auditInfo, SubscriptionEntity existing, SubscriptionSpec spec) {
        var updated = existing.toBuilder().build();
        if (spec.getStatus() != existing.getStatus()) {
            updated.setStatus(spec.getStatus());
        }
        if (spec.getEndingAt() != existing.getEndingAt()) {
            updated.setEndingAt(spec.getEndingAt());
        }
        updated.setUpdatedAt(ZonedDateTime.now());
        createAudit(existing, updated, auditInfo, SubscriptionAuditEvent.SUBSCRIPTION_UPDATED);
        return subscriptionCrudService.update(updated);
    }

    private void createAudit(@Nullable SubscriptionEntity before, SubscriptionEntity after, AuditInfo auditInfo, AuditEvent event) {
        var then = ZonedDateTime.now();

        auditService.createApiAuditLog(
            ApiAuditLogEntity
                .builder()
                .actor(auditInfo.actor())
                .organizationId(auditInfo.organizationId())
                .environmentId(auditInfo.environmentId())
                .apiId(after.getApiId())
                .event(event)
                .oldValue(before)
                .newValue(after)
                .createdAt(then)
                .properties(Collections.singletonMap(AuditProperties.APPLICATION, after.getApplicationId()))
                .build()
        );

        auditService.createApplicationAuditLog(
            ApplicationAuditLogEntity
                .builder()
                .actor(auditInfo.actor())
                .organizationId(auditInfo.organizationId())
                .environmentId(auditInfo.environmentId())
                .applicationId(after.getApplicationId())
                .event(event)
                .oldValue(before)
                .newValue(after)
                .createdAt(then)
                .properties(Collections.singletonMap(AuditProperties.API, after.getApiId()))
                .build()
        );
    }

    private Optional<SubscriptionEntity> find(String id) {
        try {
            return Optional.ofNullable(subscriptionCrudService.get(id));
        } catch (SubscriptionNotFoundException e) {
            return Optional.empty();
        }
    }
}
