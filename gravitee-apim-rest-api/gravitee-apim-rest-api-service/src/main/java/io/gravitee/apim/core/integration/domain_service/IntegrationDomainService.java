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

package io.gravitee.apim.core.integration.domain_service;

import io.gravitee.apim.core.api.model.Api;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.integration.model.AssetEntity;
import io.gravitee.apim.core.integration.model.IntegrationEntity;
import io.gravitee.apim.core.plan.model.Plan;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.rest.api.model.BaseApplicationEntity;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IntegrationDomainService extends LifecycleComponent<IntegrationDomainService> {
    void startIntegration(IntegrationEntity integration);

    Flowable<AssetEntity> getIntegrationAssets(IntegrationEntity integration);

    Flowable<AssetEntity> fetchAssets(IntegrationEntity integration, List<AssetEntity> assets);

    Maybe<SubscriptionEntity> subscribe(
        String integrationId,
        String reason,
        Api api,
        SubscriptionEntity subscription,
        BaseApplicationEntity application,
        Plan plan,
        AuditInfo auditInfo
    );
}
