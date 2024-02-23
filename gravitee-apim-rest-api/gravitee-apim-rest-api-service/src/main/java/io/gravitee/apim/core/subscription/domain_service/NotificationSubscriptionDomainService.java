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

package io.gravitee.apim.core.subscription.domain_service;

import io.gravitee.apim.core.notification.domain_service.TriggerNotificationDomainService;
import io.gravitee.apim.core.notification.model.Recipient;
import io.gravitee.apim.core.notification.model.hook.ApiHookContext;
import io.gravitee.apim.core.notification.model.hook.ApplicationHookContext;
import io.gravitee.apim.core.notification.model.hook.SubscriptionAcceptedApiHookContext;
import io.gravitee.apim.core.notification.model.hook.SubscriptionAcceptedApplicationHookContext;
import io.gravitee.apim.core.notification.model.hook.SubscriptionRejectedApiHookContext;
import io.gravitee.apim.core.notification.model.hook.SubscriptionRejectedApplicationHookContext;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.apim.core.user.crud_service.UserCrudService;
import io.gravitee.apim.core.user.model.BaseUserEntity;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class NotificationSubscriptionDomainService {

    private final UserCrudService userCrudService;

    private final TriggerNotificationDomainService triggerNotificationDomainService;

    public void triggerNotifications(String organizationId, SubscriptionEntity processedSubscriptionEntity) {
        var subscriberEmail = userCrudService
            .findBaseUserById(processedSubscriptionEntity.getSubscribedBy())
            .map(BaseUserEntity::getEmail)
            .filter(email -> !email.isEmpty())
            .map(email -> new Recipient("EMAIL", email));

        var additionalRecipients = subscriberEmail.map(List::of).orElse(Collections.emptyList());

        triggerNotificationDomainService.triggerApiNotification(organizationId, createApiHookContext(processedSubscriptionEntity));
        triggerNotificationDomainService.triggerApplicationNotification(
            organizationId,
            createApplicationHookContext(processedSubscriptionEntity),
            additionalRecipients
        );
    }

    private ApiHookContext createApiHookContext(SubscriptionEntity processedSubscriptionEntity) {
        if (processedSubscriptionEntity.isAccepted()) {
            return new SubscriptionAcceptedApiHookContext(
                processedSubscriptionEntity.getApiId(),
                processedSubscriptionEntity.getApplicationId(),
                processedSubscriptionEntity.getPlanId(),
                processedSubscriptionEntity.getId()
            );
        }
        return new SubscriptionRejectedApiHookContext(
            processedSubscriptionEntity.getApiId(),
            processedSubscriptionEntity.getApplicationId(),
            processedSubscriptionEntity.getPlanId(),
            processedSubscriptionEntity.getId()
        );
    }

    private ApplicationHookContext createApplicationHookContext(SubscriptionEntity processedSubscriptionEntity) {
        if (processedSubscriptionEntity.isAccepted()) {
            return new SubscriptionAcceptedApplicationHookContext(
                processedSubscriptionEntity.getApplicationId(),
                processedSubscriptionEntity.getApiId(),
                processedSubscriptionEntity.getPlanId(),
                processedSubscriptionEntity.getId()
            );
        }
        return new SubscriptionRejectedApplicationHookContext(
            processedSubscriptionEntity.getApplicationId(),
            processedSubscriptionEntity.getApiId(),
            processedSubscriptionEntity.getPlanId(),
            processedSubscriptionEntity.getId()
        );
    }
}
