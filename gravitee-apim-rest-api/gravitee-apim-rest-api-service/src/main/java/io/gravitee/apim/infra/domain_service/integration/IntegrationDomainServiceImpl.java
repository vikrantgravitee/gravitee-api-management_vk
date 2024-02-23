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

package io.gravitee.apim.infra.domain_service.integration;

import static io.gravitee.apim.core.license.domain_service.GraviteeLicenseDomainService.APIM_INTEGRATION;

import io.gravitee.apim.core.api.model.Api;
import io.gravitee.apim.core.api.model.ApiMetadata;
import io.gravitee.apim.core.api.query_service.ApiMetadataQueryService;
import io.gravitee.apim.core.audit.model.AuditActor;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.event.SubscriptionAuditEvent;
import io.gravitee.apim.core.exception.NotFoundDomainException;
import io.gravitee.apim.core.exception.TechnicalDomainException;
import io.gravitee.apim.core.integration.crud_service.IntegrationCrudService;
import io.gravitee.apim.core.integration.domain_service.IntegrationDomainService;
import io.gravitee.apim.core.integration.model.AssetEntity;
import io.gravitee.apim.core.integration.model.IntegrationEntity;
import io.gravitee.apim.core.license.domain_service.GraviteeLicenseDomainService;
import io.gravitee.apim.core.plan.model.Plan;
import io.gravitee.apim.core.subscription.crud_service.SubscriptionCrudService;
import io.gravitee.apim.core.subscription.domain_service.AuditSubscriptionDomainService;
import io.gravitee.apim.core.subscription.domain_service.NotificationSubscriptionDomainService;
import io.gravitee.apim.core.subscription.model.SubscriptionEntity;
import io.gravitee.apim.infra.adapter.IntegrationAdapter;
import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.command.Batch;
import io.gravitee.exchange.api.command.BatchCommand;
import io.gravitee.exchange.api.command.BatchStatus;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.connector.ExchangeConnectorManager;
import io.gravitee.exchange.api.controller.ExchangeController;
import io.gravitee.exchange.connector.embedded.EmbeddedExchangeConnector;
import io.gravitee.exchange.controller.embedded.channel.EmbeddedChannel;
import io.gravitee.integration.api.command.fetch.FetchCommand;
import io.gravitee.integration.api.command.fetch.FetchCommandPayload;
import io.gravitee.integration.api.command.fetch.FetchReply;
import io.gravitee.integration.api.command.list.ListCommand;
import io.gravitee.integration.api.command.list.ListReply;
import io.gravitee.integration.api.command.subscribe.SubscribeCommand;
import io.gravitee.integration.api.command.subscribe.SubscribeCommandPayload;
import io.gravitee.integration.api.command.subscribe.SubscribeReply;
import io.gravitee.integration.api.model.Asset;
import io.gravitee.integration.api.model.Subscription;
import io.gravitee.integration.api.model.SubscriptionType;
import io.gravitee.integration.api.plugin.IntegrationProvider;
import io.gravitee.integration.api.plugin.IntegrationProviderFactory;
import io.gravitee.integration.connector.command.IntegrationConnectorCommandContext;
import io.gravitee.integration.connector.command.IntegrationConnectorCommandHandlersFactory;
import io.gravitee.plugin.integrationprovider.IntegrationProviderPluginManager;
import io.gravitee.rest.api.model.BaseApplicationEntity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @author Remi Baptiste (remi.baptiste at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Service
public class IntegrationDomainServiceImpl extends AbstractService<IntegrationDomainService> implements IntegrationDomainService {

    private final GraviteeLicenseDomainService graviteeLicenseDomainService;
    private final ExchangeConnectorManager exchangeConnectorManager;
    private final ExchangeController exchangeController;
    private final IntegrationConnectorCommandHandlersFactory connectorCommandHandlersFactory;
    private final IntegrationProviderPluginManager integrationProviderPluginManager;
    private final IntegrationCrudService integrationCrudService;
    private final SubscriptionCrudService subscriptionCrudService;

    private final NotificationSubscriptionDomainService notificationSubscriptionDomainService;

    private final AuditSubscriptionDomainService auditSubscriptionDomainService;

    private final ApiMetadataQueryService apiMetadataQueryService;
    private ExecutorService executorService;

    public IntegrationDomainServiceImpl(
        final GraviteeLicenseDomainService graviteeLicenseDomainService,
        final ExchangeConnectorManager exchangeConnectorManager,
        @Qualifier("integrationExchangeController") final ExchangeController exchangeController,
        final IntegrationConnectorCommandHandlersFactory connectorCommandHandlersFactory,
        final IntegrationProviderPluginManager integrationProviderPluginManager,
        final IntegrationCrudService integrationCrudService,
        final SubscriptionCrudService subscriptionCrudService,
        final NotificationSubscriptionDomainService notificationSubscriptionDomainService,
        final AuditSubscriptionDomainService auditSubscriptionDomainService,
        ApiMetadataQueryService apiMetadataQueryService
    ) {
        this.graviteeLicenseDomainService = graviteeLicenseDomainService;
        this.exchangeConnectorManager = exchangeConnectorManager;
        this.exchangeController = exchangeController;
        this.connectorCommandHandlersFactory = connectorCommandHandlersFactory;
        this.integrationProviderPluginManager = integrationProviderPluginManager;
        this.integrationCrudService = integrationCrudService;
        this.subscriptionCrudService = subscriptionCrudService;
        this.notificationSubscriptionDomainService = notificationSubscriptionDomainService;
        this.auditSubscriptionDomainService = auditSubscriptionDomainService;
        this.apiMetadataQueryService = apiMetadataQueryService;
    }

    // TODO To be removed when the license is up to date
    private final boolean FORCE_INTEGRATION = true;

    @Override
    public void doStart() throws Exception {
        super.doStart();
        if (graviteeLicenseDomainService.isFeatureEnabled(APIM_INTEGRATION) || FORCE_INTEGRATION) {
            exchangeController.start();

            integrationCrudService.findAll().forEach(this::startIntegration);
            log.info("Integrations started.");
        } else {
            log.warn("License doesn't contain Integrations feature.");
        }

        executorService =
            Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                new ThreadFactory() {
                    private final AtomicLong counter = new AtomicLong(0);

                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        return new Thread(r, "gio.integration-" + counter.getAndIncrement());
                    }
                }
            );
    }

    @Override
    public void startIntegration(IntegrationEntity integration) {
        if (integration.getDeploymentType() == IntegrationEntity.DeploymentType.EMBEDDED) {
            try {
                IntegrationProviderFactory<?> integrationProviderFactory = integrationProviderPluginManager.getIntegrationProviderFactory(
                    integration.getProvider().toLowerCase()
                );

                if (integrationProviderFactory == null) {
                    log.warn("Integration provider {} cannot be instantiated (no factory found). Skipped.", integration.getProvider());
                    return;
                }

                IntegrationProvider integrationProvider = integrationProviderFactory.createIntegrationProvider(
                    integration.getId(),
                    integration.getConfiguration()
                );

                if (integrationProvider == null) {
                    log.warn("Integration provider {} cannot be started. Skipped.", integration.getProvider());
                    return;
                }

                integrationProvider.start();

                IntegrationConnectorCommandContext integrationConnectorCommandContext = new IntegrationConnectorCommandContext(
                    integration.getProvider(),
                    integration.getId(),
                    integration.getEnvironmentId(),
                    integrationProvider
                );
                List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> connectorCommandHandlers =
                    connectorCommandHandlersFactory.buildCommandHandlers(integrationConnectorCommandContext);
                List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> commandAdapters =
                    connectorCommandHandlersFactory.buildCommandAdapters(integrationConnectorCommandContext);
                List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> replyAdapters =
                    connectorCommandHandlersFactory.buildReplyAdapters(integrationConnectorCommandContext);
                EmbeddedChannel embeddedChannel = EmbeddedChannel
                    .builder()
                    .targetId(integration.getId())
                    .commandHandlers(connectorCommandHandlers)
                    .commandAdapters(commandAdapters)
                    .replyAdapters(replyAdapters)
                    .build();
                exchangeController
                    .register(embeddedChannel)
                    .andThen(
                        exchangeConnectorManager.register(EmbeddedExchangeConnector.builder().connectorChannel(embeddedChannel).build())
                    )
                    .blockingAwait();
            } catch (Exception e) {
                log.warn("Unable to properly start the integration provider {}: {}. Skipped.", integration.getProvider(), e.getMessage());
            }
        }
    }

    @Override
    public Flowable<AssetEntity> getIntegrationAssets(IntegrationEntity integration) {
        ListCommand listCommand = new ListCommand();
        String targetId = integration.getDeploymentType() == IntegrationEntity.DeploymentType.EMBEDDED
            ? integration.getId()
            : integration.getRemoteId();
        return sendListCommand(listCommand, targetId)
            .flatMapPublisher(listReply -> {
                if (listReply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                    List<AssetEntity> assets = listReply.getPayload().assets().stream().map(IntegrationAdapter.INSTANCE::toEntity).toList();
                    return Flowable.fromIterable(assets);
                }
                return Flowable.empty();
            });
    }

    @Override
    public Flowable<AssetEntity> fetchAssets(IntegrationEntity integration, List<AssetEntity> assetsToImport) {
        List<Asset> assets = assetsToImport.stream().map(IntegrationAdapter.INSTANCE::toIntegrationModel).toList();

        FetchCommandPayload fetchCommandPayload = new FetchCommandPayload(assets);
        FetchCommand fetchCommand = new FetchCommand(fetchCommandPayload);
        String targetId = integration.getDeploymentType() == IntegrationEntity.DeploymentType.EMBEDDED
            ? integration.getId()
            : integration.getRemoteId();
        return sendFetchCommand(fetchCommand, targetId)
            .toFlowable()
            .flatMap(fetchReply -> {
                if (fetchReply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                    List<AssetEntity> fetchAssets = fetchReply
                        .getPayload()
                        .assets()
                        .stream()
                        .map(IntegrationAdapter.INSTANCE::toEntity)
                        .toList();
                    return Flowable.fromIterable(fetchAssets);
                }
                return Flowable.empty();
            });
    }

    @Override
    public Maybe<SubscriptionEntity> subscribe(
        String integrationId,
        String reason,
        Api api,
        SubscriptionEntity subscription,
        BaseApplicationEntity application,
        Plan plan,
        AuditInfo auditInfo
    ) {
        //Get integration
        ApiMetadata apiMetadata = apiMetadataQueryService.findApiMetadata(api.getId()).get("integration");
        if (apiMetadata == null || apiMetadata.getValue().isEmpty()) {
            throw new TechnicalDomainException("Integration id not found for Federated API " + api.getId());
        }
        var integration = integrationCrudService.findById(apiMetadata.getValue());

        //Find the external id of the asset using the Api
        var externalId = api.getDefinitionContext().getOrigin(); //TODO
        var asset = Asset.builder().id(externalId).build();

        //Create Subscription using data from SubscriptionEntity, Application and Plan
        Map<String, String> metadata = new HashMap<>();
        metadata.put("app_id", application.getId());
        Subscription subscriptionModel = Subscription
            .builder()
            .graviteeApiId(api.getId())
            .graviteeApplicationId(application.getId())
            .graviteeUserId(auditInfo.actor().userId())
            .graviteeSubscriptionId(subscription.getId())
            .graviteeEnvironmentId(auditInfo.environmentId())
            .graviteeOrganizationId(auditInfo.organizationId())
            .reason(reason)
            .type(SubscriptionType.API_KEY)
            .metadata(metadata)
            .build();
        SubscribeCommandPayload subscribeCommandPayload = new SubscribeCommandPayload(asset, subscriptionModel);
        SubscribeCommand subscribeCommand = new SubscribeCommand(subscribeCommandPayload);

        String targetId = integration.getDeploymentType() == IntegrationEntity.DeploymentType.EMBEDDED
            ? integration.getId()
            : integration.getRemoteId();

        return sendSubscribeCommand(subscribeCommand, targetId)
            .flatMap(subscribeReply -> {
                if (subscribeReply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                    var providerSubscription = subscribeReply.getPayload().subscription();

                    return Maybe.just(
                        SubscriptionEntity
                            .builder()
                            .apiId(providerSubscription.graviteeApiId())
                            .applicationId(providerSubscription.graviteeApplicationId())
                            .reasonMessage(reason)
                            .build()
                    );
                }
                return Maybe.empty();
            });
    }

    private Single<ListReply> sendListCommand(ListCommand listCommand, String integrationId) {
        return exchangeController
            .sendCommand(listCommand, integrationId)
            .cast(ListReply.class)
            .onErrorReturn(throwable -> new ListReply(listCommand.getId(), throwable.getMessage()));
    }

    private Single<FetchReply> sendFetchCommand(FetchCommand fetchCommand, String integrationId) {
        return exchangeController
            .sendCommand(fetchCommand, integrationId)
            .cast(FetchReply.class)
            .onErrorReturn(throwable -> new FetchReply(fetchCommand.getId(), throwable.getMessage()));
    }

    private Maybe<SubscribeReply> sendSubscribeCommand(SubscribeCommand subscribeCommand, String integrationId) {
        BatchCommand subscribeBatchCommand = BatchCommand.builder().command(subscribeCommand).build();
        Batch subscribeBatch = Batch.builder().batchCommands(List.of(subscribeBatchCommand)).targetId(integrationId).build();

        return exchangeController
            .executeBatch(subscribeBatch)
            .flatMapMaybe(batch -> {
                if (batch.status() == BatchStatus.SUCCEEDED) {
                    return Maybe.just(batch.batchCommands().get(0).reply());
                }
                //launch watch and return empty
                executorService.submit(() -> watchSubscribeCommand(batch.id(), subscribeCommand.getId()));
                return Maybe.empty();
            })
            .cast(SubscribeReply.class)
            .onErrorReturn(throwable -> new SubscribeReply(subscribeCommand.getId(), throwable.getMessage()));
    }

    private Disposable watchSubscribeCommand(String batchId, String subscribeCommandId) {
        return exchangeController
            .watchBatch(batchId)
            .subscribeOn(Schedulers.io())
            .map(batch -> batch.batchCommands().get(0).reply())
            .cast(SubscribeReply.class)
            .onErrorReturn(throwable -> new SubscribeReply(subscribeCommandId, throwable.getMessage()))
            .flatMapCompletable(this::notifySubscribeBatchStatus)
            .subscribe();
    }

    private Completable notifySubscribeBatchStatus(SubscribeReply reply) {
        return Completable.fromRunnable(() -> {
            //Check reply status and notify API Publisher
            log.info("Subscribe reply {}", reply);
            var subscription = reply.getPayload().subscription();

            //If we have a failure, we put back the subscription status to pending and notify the api publisher
            var subscriptionEntity = subscriptionCrudService.get(subscription.graviteeSubscriptionId());
            var auditInfo = AuditInfo
                .builder()
                .actor(AuditActor.builder().userId(subscription.graviteeUserId()).build())
                .environmentId(subscription.graviteeEnvironmentId())
                .organizationId(subscription.graviteeOrganizationId())
                .build();

            var acceptedSubscriptionEntity = subscriptionCrudService.update(
                subscriptionEntity.acceptBy(subscription.graviteeUserId(), null, null, subscriptionEntity.getReasonMessage())
            );

            notificationSubscriptionDomainService.triggerNotifications(auditInfo.organizationId(), acceptedSubscriptionEntity);
            auditSubscriptionDomainService.createAuditLog(
                subscriptionEntity,
                acceptedSubscriptionEntity,
                auditInfo,
                SubscriptionAuditEvent.SUBSCRIPTION_UPDATED
            );
        });
    }
}
