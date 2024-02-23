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

package io.gravitee.apim.core.api_key.domain_service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Comparator.reverseOrder;

import io.gravitee.apim.core.api_key.crud_service.ApiKeyCrudService;
import io.gravitee.apim.core.api_key.model.ApiKeyEntity;
import io.gravitee.apim.core.api_key.query_service.ApiKeyQueryService;
import io.gravitee.apim.core.audit.domain_service.AuditDomainService;
import io.gravitee.apim.core.audit.model.ApiAuditLogEntity;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.AuditProperties;
import io.gravitee.apim.core.audit.model.event.ApiKeyAuditEvent;
import io.gravitee.apim.core.notification.domain_service.TriggerNotificationDomainService;
import io.gravitee.apim.core.subscription.crud_service.SubscriptionCrudService;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.repository.management.model.ApiKey;
import io.gravitee.rest.api.model.BaseApplicationEntity;
import io.gravitee.rest.api.service.common.UuidString;
import io.gravitee.rest.api.service.exceptions.ApiKeyAlreadyExistingException;
import io.gravitee.rest.api.service.exceptions.ApiKeyNotFoundException;
import io.gravitee.rest.api.service.exceptions.SubscriptionClosedException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GenerateApiKeyDomainService {

    private final ApiKeyCrudService apiKeyCrudService;
    private final ApiKeyQueryService apiKeyQueryService;
    private final SubscriptionCrudService subscriptionCrudService;
    private final AuditDomainService auditService;
    private final TriggerNotificationDomainService triggerNotificationDomainService;

    public GenerateApiKeyDomainService(
        ApiKeyCrudService apiKeyCrudService,
        ApiKeyQueryService apiKeyQueryService,
        SubscriptionCrudService subscriptionCrudService,
        AuditDomainService auditService,
        TriggerNotificationDomainService triggerNotificationDomainService
    ) {
        this.apiKeyCrudService = apiKeyCrudService;
        this.apiKeyQueryService = apiKeyQueryService;
        this.subscriptionCrudService = subscriptionCrudService;
        this.auditService = auditService;
        this.triggerNotificationDomainService = triggerNotificationDomainService;
    }

    public ApiKeyEntity generate(
        AuditInfo auditInfo,
        BaseApplicationEntity application,
        SubscriptionEntity subscription,
        String customApiKey
    ) {
        if (!application.hasApiKeySharedMode()) {
            return generate(auditInfo, subscription, customApiKey);
        }
        return findOrGenerate(auditInfo, application, subscription, customApiKey);
    }

    private ApiKeyEntity generate(AuditInfo auditInfo, SubscriptionEntity subscription, String customApiKey) {
        log.debug("Generate an API Key for subscription {}", subscription);

        ApiKeyEntity apiKey = generateForSubscription(subscription, customApiKey);
        apiKeyCrudService.create(apiKey);

        //TODO: Send a notification to the application owner

        // Audit
        createAuditLog(apiKey, subscription, auditInfo);

        return apiKey;
    }

    /**
     * Generate an {@link ApiKey} from a subscription. If no custom API Key, then generate a new one.
     *
     * @param subscription
     * @param customApiKey
     * @return An API Key
     */
    private ApiKeyEntity generateForSubscription(SubscriptionEntity subscription, String customApiKey) {
        if (Objects.nonNull(customApiKey) && !customApiKey.isEmpty() && !canCreate(customApiKey, subscription)) {
            throw new ApiKeyAlreadyExistingException();
        }

        var now = ZonedDateTime.now();
        if (subscription.getEndingAt() != null && subscription.getEndingAt().isBefore(now)) {
            throw new SubscriptionClosedException(subscription.getId());
        }

        var apiKey = ApiKeyEntity
            .builder()
            .id(UuidString.generateRandom())
            .applicationId(subscription.getApplicationId())
            .createdAt(now)
            .updatedAt(now)
            .key(Objects.nonNull(customApiKey) && !customApiKey.isEmpty() ? customApiKey : UUID.randomUUID().toString())
            .build();

        apiKey.setSubscriptions(List.of(subscription.getId()));

        // By default, the API Key will expire when subscription is closed
        apiKey.setExpireAt(subscription.getEndingAt());

        return apiKey;
    }

    public boolean canCreate(String apiKeyValue, SubscriptionEntity subscription) {
        return canCreate(apiKeyValue, subscription.getApiId(), subscription.getApplicationId());
    }

    public boolean canCreate(String apiKey, String apiId, String applicationId) {
        log.debug("Check if an API Key can be created for api {} and application {}", apiId, applicationId);

        return apiKeyQueryService.findByKeyAndApiId(apiKey, apiId).isEmpty();
    }

    private ApiKeyEntity findOrGenerate(
        AuditInfo auditInfo,
        BaseApplicationEntity application,
        SubscriptionEntity subscription,
        String customApiKey
    ) {
        return apiKeyQueryService
            .findByApplication(application.getId())
            .peek(apiKey -> addSubscription(apiKey, subscription))
            .max(comparing(ApiKeyEntity::isRevoked, reverseOrder()).thenComparing(ApiKeyEntity::getExpireAt, nullsLast(naturalOrder())))
            .orElseGet(() -> generate(auditInfo, subscription, customApiKey));
    }

    private void addSubscription(ApiKeyEntity apiKeyEntity, SubscriptionEntity subscription) {
        ApiKeyEntity apiKey = apiKeyQueryService.findById(apiKeyEntity.getId()).orElseThrow(ApiKeyNotFoundException::new);
        ArrayList<String> subscriptions = new ArrayList<>(apiKey.getSubscriptions());
        subscriptions.add(subscription.getId());
        apiKey.setSubscriptions(subscriptions);
        apiKey.setUpdatedAt(ZonedDateTime.now());
        apiKeyCrudService.update(apiKey);
    }

    private void createAuditLog(
        io.gravitee.apim.core.api_key.model.ApiKeyEntity createdApiKeyEntity,
        SubscriptionEntity subscription,
        AuditInfo auditInfo
    ) {
        auditService.createApiAuditLog(
            ApiAuditLogEntity
                .builder()
                .organizationId(auditInfo.organizationId())
                .environmentId(auditInfo.environmentId())
                .apiId(subscription.getApiId())
                .event(ApiKeyAuditEvent.APIKEY_CREATED)
                .actor(auditInfo.actor())
                .oldValue(null)
                .newValue(createdApiKeyEntity)
                .createdAt(ZonedDateTime.ofInstant(createdApiKeyEntity.getCreatedAt().toInstant(), ZoneId.systemDefault()))
                .properties(
                    Map.of(
                        AuditProperties.API_KEY,
                        createdApiKeyEntity.getKey(),
                        AuditProperties.API,
                        subscription.getApiId(),
                        AuditProperties.APPLICATION,
                        subscription.getApplicationId()
                    )
                )
                .build()
        );
    }
}
