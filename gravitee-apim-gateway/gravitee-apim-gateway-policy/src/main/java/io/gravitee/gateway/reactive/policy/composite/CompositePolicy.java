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
package io.gravitee.gateway.reactive.policy.composite;

import io.gravitee.definition.model.ConditionSupplier;
import io.gravitee.gateway.reactive.api.context.ExecutionContext;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.gateway.reactive.core.condition.ExpressionLanguageConditionFilter;
import io.gravitee.gateway.reactive.policy.PolicyChain;
import io.reactivex.rxjava3.core.Completable;
import lombok.Setter;

/**
 * Policy allowing to execute a Shared Flow, which is a policy chain.
 */
public class CompositePolicy implements Policy, ConditionSupplier {

    public static final String POLICY_ID = "composite-policy";

    private final String id;
    private final String condition;
    private final ExpressionLanguageConditionFilter<CompositePolicy> conditionFilter;
    private final boolean conditionDefined;
    public final CompositePolicyConfiguration policyConfiguration;

    @Setter
    // For the POC, we manually set the policyChain when extracted from the API. In the future, get the chain from an "EnvironmentFlowManager"
    private PolicyChain policyChain;

    public CompositePolicy(
        String id,
        String condition,
        ExpressionLanguageConditionFilter<CompositePolicy> conditionFilter,
        CompositePolicyConfiguration policyConfiguration
    ) {
        this.id = id;
        // No refactoring has been done on condition, we just apply it as for conditional policies
        this.condition = condition;
        this.conditionFilter = conditionFilter;
        this.conditionDefined = condition != null && !condition.isBlank();
        this.policyConfiguration = policyConfiguration;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String getCondition() {
        return condition;
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        if (!conditionDefined) {
            return policyChain.execute((ExecutionContext) ctx);
        }

        return conditionFilter
            .filter(ctx, this)
            .flatMapCompletable(conditionalPolicy -> Completable.defer(() -> policyChain.execute((ExecutionContext) ctx)));
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        if (!conditionDefined) {
            return policyChain.execute((ExecutionContext) ctx);
        }

        return conditionFilter.filter(ctx, this).flatMapCompletable(conditionalPolicy -> policyChain.execute((ExecutionContext) ctx));
    }
}
