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

package io.gravitee.apim.core.subscription.use_case;

import io.gravitee.apim.core.api.domain_service.ApiTemplateDomainService;
import io.gravitee.apim.core.api.model.Api;
import io.gravitee.apim.core.api_key.domain_service.GenerateApiKeyDomainService;
import io.gravitee.apim.core.application.crud_service.ApplicationCrudService;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.event.SubscriptionAuditEvent;
import io.gravitee.apim.core.integration.domain_service.IntegrationDomainService;
import io.gravitee.apim.core.plan.crud_service.PlanCrudService;
import io.gravitee.apim.core.plan.model.Plan;
import io.gravitee.apim.core.subscription.crud_service.SubscriptionCrudService;
import io.gravitee.apim.core.subscription.domain_service.AuditSubscriptionDomainService;
import io.gravitee.apim.core.subscription.domain_service.NotificationSubscriptionDomainService;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.definition.model.DefinitionVersion;
import io.gravitee.definition.model.v4.plan.PlanSecurity;
import io.gravitee.rest.api.model.BaseApplicationEntity;
import io.gravitee.rest.api.model.v4.plan.PlanSecurityType;
import io.gravitee.rest.api.service.common.ExecutionContext;
import io.gravitee.rest.api.service.exceptions.PlanAlreadyClosedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionAlreadyProcessedException;
import io.gravitee.rest.api.service.exceptions.SubscriptionNotFoundException;
import io.reactivex.rxjava3.core.Single;
import java.time.ZonedDateTime;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AcceptSubscriptionUseCase {

    private final ApplicationCrudService applicationCrudService;
    private final PlanCrudService planCrudService;
    private final SubscriptionCrudService subscriptionCrudService;
    private final ApiTemplateDomainService apiTemplateDomainService;
    private final IntegrationDomainService integrationDomainService;
    private final NotificationSubscriptionDomainService notificationSubscriptionDomainService;
    private final AuditSubscriptionDomainService auditSubscriptionDomainService;
    private final GenerateApiKeyDomainService generateApiKeyDomainService;

    public AcceptSubscriptionUseCase(
        ApplicationCrudService applicationCrudService,
        PlanCrudService planCrudService,
        SubscriptionCrudService subscriptionCrudService,
        ApiTemplateDomainService apiTemplateDomainService,
        IntegrationDomainService integrationDomainService,
        NotificationSubscriptionDomainService notificationSubscriptionDomainService,
        AuditSubscriptionDomainService auditSubscriptionDomainService,
        GenerateApiKeyDomainService generateApiKeyDomainService
    ) {
        this.applicationCrudService = applicationCrudService;
        this.planCrudService = planCrudService;
        this.subscriptionCrudService = subscriptionCrudService;
        this.apiTemplateDomainService = apiTemplateDomainService;
        this.integrationDomainService = integrationDomainService;
        this.notificationSubscriptionDomainService = notificationSubscriptionDomainService;
        this.auditSubscriptionDomainService = auditSubscriptionDomainService;
        this.generateApiKeyDomainService = generateApiKeyDomainService;
    }

    public Output execute(Input input) {
        var auditInfo = input.auditInfo();
        var subscriptionEntity = subscriptionCrudService.get(input.subscriptionId());

        if (input.apiId != null && !subscriptionEntity.getApiId().equals(input.apiId)) {
            throw new SubscriptionNotFoundException(input.subscriptionId);
        }

        log.debug("Subscription {} accepted by {}", subscriptionEntity.getId(), auditInfo.actor().userId());

        if (subscriptionEntity.getStatus() != SubscriptionEntity.Status.PENDING) {
            throw new SubscriptionAlreadyProcessedException(subscriptionEntity.getId());
        }

        Plan plan = planCrudService.findById(subscriptionEntity.getPlanId());
        if (plan.isClosed()) {
            throw new PlanAlreadyClosedException(plan.getId());
        }

        final String apiId = plan.getApiId();

        final Api api = apiTemplateDomainService.findByIdForTemplates(apiId, auditInfo);

        var executionContext = new ExecutionContext(auditInfo.organizationId(), auditInfo.environmentId());
        BaseApplicationEntity application = applicationCrudService.findById(executionContext, subscriptionEntity.getApplicationId());

        PlanSecurity planSecurity = plan.getPlanSecurity();

        SubscriptionEntity acceptedSubscriptionEntity;
        // Check if API is a Federated one and call specific code to manage it ?
        if (api.getDefinitionVersion() == DefinitionVersion.FEDERATED) {
            //TODO Get integration id from API DefinitionContext
            var integrationId = api.getDefinitionContext().getOrigin();
            acceptedSubscriptionEntity =
                integrationDomainService
                    .subscribe(integrationId, input.reason(), api, subscriptionEntity, application, plan, auditInfo)
                    .map(subscription -> {
                        var updatedSubscriptionEntity = subscriptionCrudService.update(
                            subscriptionEntity.acceptBy(auditInfo.actor().userId(), null, null, input.reason())
                        );

                        notificationSubscriptionDomainService.triggerNotifications(auditInfo.organizationId(), updatedSubscriptionEntity);
                        auditSubscriptionDomainService.createAuditLog(
                            subscriptionEntity,
                            updatedSubscriptionEntity,
                            auditInfo,
                            SubscriptionAuditEvent.SUBSCRIPTION_UPDATED
                        );
                        return subscriptionEntity;
                    })
                    .switchIfEmpty(
                        //If we did not get a subscription then return a special status
                        //Do we need notifications and audit in this case ?
                        Single.fromCallable(() ->
                            subscriptionCrudService.update(subscriptionEntity.processByIntegration(auditInfo.actor().userId()))
                        )
                    )
                    .blockingGet();
        } else {
            if (planSecurity != null && PlanSecurityType.API_KEY == PlanSecurityType.valueOfLabel(planSecurity.getType())) {
                generateApiKeyDomainService.generate(auditInfo, application, subscriptionEntity, input.customKey());
            }
            acceptedSubscriptionEntity =
                subscriptionCrudService.update(
                    subscriptionEntity.acceptBy(auditInfo.actor().userId(), input.startingAt(), input.endingAt(), input.reason())
                );

            notificationSubscriptionDomainService.triggerNotifications(auditInfo.organizationId(), acceptedSubscriptionEntity);
            auditSubscriptionDomainService.createAuditLog(
                subscriptionEntity,
                acceptedSubscriptionEntity,
                auditInfo,
                SubscriptionAuditEvent.SUBSCRIPTION_UPDATED
            );
        }

        return new Output(acceptedSubscriptionEntity);
    }

    @Builder
    public record Input(
        String subscriptionId,
        String apiId,
        String customKey,
        ZonedDateTime startingAt,
        ZonedDateTime endingAt,
        String reason,
        AuditInfo auditInfo
    ) {}

    public record Output(SubscriptionEntity subscription) {}
}
