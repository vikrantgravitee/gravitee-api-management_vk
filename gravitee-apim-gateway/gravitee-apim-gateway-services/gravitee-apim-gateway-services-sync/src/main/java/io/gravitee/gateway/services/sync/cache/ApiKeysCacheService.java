/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.services.sync.cache;

import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.service.AbstractService;
import io.gravitee.definition.model.Plan;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.reactor.Reactable;
import io.gravitee.gateway.reactor.ReactorEvent;
import io.gravitee.gateway.services.sync.cache.handler.ApiKeysServiceHandler;
import io.gravitee.gateway.services.sync.cache.repository.ApiKeyRepositoryWrapper;
import io.gravitee.gateway.services.sync.cache.task.FullApiKeyRefresher;
import io.gravitee.gateway.services.sync.cache.task.IncrementalApiKeyRefresher;
import io.gravitee.gateway.services.sync.cache.task.Result;
import io.gravitee.node.api.cache.CacheManager;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.repository.management.api.ApiKeyRepository;
import io.gravitee.repository.management.api.SubscriptionRepository;
import io.vertx.ext.web.Router;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApiKeysCacheService extends AbstractService implements EventListener<ReactorEvent, Reactable> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiKeysCacheService.class);

    private static final String API_KEY_CACHE_NAME = "apikeys";

    @Value("${services.sync.bulk_items:100}")
    private int bulkItems;

    @Value("${services.sync.distributed:false}")
    private boolean distributed;

    private static final String PATH = "/apikeys";

    @Autowired
    private EventManager eventManager;

    @Autowired
    private CacheManager cacheManager;

    private ApiKeyRepository apiKeyRepository;

    private SubscriptionRepository subscriptionRepository;

    @Autowired
    @Qualifier("syncExecutor")
    private ThreadPoolExecutor executorService;

    @Autowired
    @Qualifier("managementRouter")
    private Router router;

    @Autowired
    private ClusterManager clusterManager;

    private final ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> scheduledFuture;

    private final Map<String, Set<String>> plansPerApi = new ConcurrentHashMap<>();

    public ApiKeysCacheService() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("gio.sync-apikeys-");
        // Ensure every execution is done before running next execution
        scheduler.setPoolSize(1);
        scheduler.initialize();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Overriding API key repository implementation with cached API Key repository");
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) (
            (ConfigurableApplicationContext) applicationContext.getParent()
        ).getBeanFactory();

        this.apiKeyRepository = beanFactory.getBean(ApiKeyRepository.class);
        LOGGER.debug("Current API key repository implementation is {}", apiKeyRepository.getClass().getName());

        this.subscriptionRepository = beanFactory.getBean(SubscriptionRepository.class);
        LOGGER.debug("Current subscription repository implementation is {}", subscriptionRepository.getClass().getName());

        String[] beanNames = beanFactory.getBeanNamesForType(ApiKeyRepository.class);
        String oldBeanName = beanNames[0];

        beanFactory.destroySingleton(oldBeanName);

        LOGGER.debug("Register API key repository implementation {}", ApiKeyRepositoryWrapper.class.getName());
        beanFactory.registerSingleton(
            ApiKeyRepository.class.getName(),
            new ApiKeyRepositoryWrapper(this.apiKeyRepository, new ApiKeysCache(cacheManager.getOrCreateCache(API_KEY_CACHE_NAME)))
        );

        LOGGER.info("Associate a new HTTP handler on {}", PATH);

        // Set API-keys handler
        ApiKeysServiceHandler apiKeysHandler = new ApiKeysServiceHandler(executorService);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(apiKeysHandler);
        router.get(PATH).produces(MediaType.APPLICATION_JSON).handler(apiKeysHandler);
    }

    public void startScheduler(int delay, TimeUnit unit) {
        scheduledFuture = scheduler.scheduleAtFixedRate(new ApiKeysTask(), Duration.ofMillis(unit.toMillis(delay)));
        eventManager.subscribeForEvents(this, ReactorEvent.class);
    }

    class ApiKeysTask extends TimerTask {

        private long lastRefreshAt = -1;

        @Override
        public void run() {
            if (clusterManager.isMasterNode() || (!clusterManager.isMasterNode() && !distributed)) {
                long nextLastRefreshAt = System.currentTimeMillis();

                // Merge all plans and split them into buckets
                final Set<String> plans = plansPerApi.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

                final AtomicInteger counter = new AtomicInteger();

                final Collection<List<String>> chunks = plans
                    .stream()
                    .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / bulkItems))
                    .values();

                // Run refreshers
                if (!chunks.isEmpty()) {
                    // Prepare tasks
                    final List<Callable<Result<Boolean>>> callables = chunks
                        .stream()
                        .map(
                            chunk -> {
                                IncrementalApiKeyRefresher refresher = new IncrementalApiKeyRefresher(
                                    lastRefreshAt,
                                    nextLastRefreshAt,
                                    chunk
                                );
                                refresher.setApiKeyRepository(apiKeyRepository);
                                refresher.setSubscriptionRepository(subscriptionRepository);
                                refresher.setCache(new ApiKeysCache(cacheManager.getOrCreateCache(API_KEY_CACHE_NAME)));

                                return refresher;
                            }
                        )
                        .collect(Collectors.toList());

                    // And run...
                    try {
                        List<Future<Result<Boolean>>> futures = executorService.invokeAll(callables);

                        boolean failure = futures
                            .stream()
                            .anyMatch(
                                resultFuture -> {
                                    try {
                                        return resultFuture.get().failed();
                                    } catch (Exception e) {
                                        LOGGER.error("Unexpected error while running the api-keys refresher", e);
                                    }

                                    return false;
                                }
                            );

                        // If there is no failure, move to the next period of time
                        if (!failure) {
                            lastRefreshAt = nextLastRefreshAt;
                        }
                    } catch (InterruptedException e) {
                        LOGGER.error("Unexpected error while running the api-keys refresher");
                        Thread.currentThread().interrupt();
                    }
                } else {
                    lastRefreshAt = nextLastRefreshAt;
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    protected String name() {
        return "API keys cache";
    }

    @Override
    public void onEvent(Event<ReactorEvent, Reactable> event) {
        switch (event.type()) {
            case DEPLOY:
                register((Api) event.content());
                break;
            case UNDEPLOY:
                unregister((Api) event.content());
                break;
            case UPDATE:
                unregister((Api) event.content());
                register((Api) event.content());
                break;
            default:
                // Nothing to do with unknown event type
                break;
        }
    }

    public void register(Api api) {
        register(Collections.singletonList(api));
    }

    public void register(List<Api> apis) {
        final Map<String, Api> apisById = apis.stream().collect(Collectors.toMap(io.gravitee.definition.model.Api::getId, api -> api));

        // Filters plans to update api_keys only for them
        final Map<String, Set<String>> plansByApi = apis
            .stream()
            .filter(Api::isEnabled)
            .map(
                api ->
                    new AbstractMap.SimpleEntry<>(
                        api.getId(),
                        api
                            .getPlans()
                            .stream()
                            .filter(
                                plan ->
                                    io.gravitee.repository.management.model.Plan.PlanSecurityType.API_KEY
                                        .name()
                                        .equalsIgnoreCase(plan.getSecurity())
                            )
                            .map(Plan::getId)
                            .collect(Collectors.toSet())
                    )
            )
            // Remove if no plan.
            .filter(e -> !e.getValue().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!plansByApi.isEmpty()) {
            final Set<String> planIds = plansByApi.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

            // If the node is not a master, we assume that the full refresh has been handle by another node
            if (clusterManager.isMasterNode() || (!clusterManager.isMasterNode() && !distributed)) {
                final FullApiKeyRefresher refresher = new FullApiKeyRefresher(planIds);
                refresher.setApiKeyRepository(apiKeyRepository);
                refresher.setSubscriptionRepository(subscriptionRepository);
                refresher.setCache(new ApiKeysCache(cacheManager.getOrCreateCache(API_KEY_CACHE_NAME)));

                CompletableFuture
                    .supplyAsync(refresher::call, executorService)
                    .whenComplete(
                        (result, throwable) -> {
                            if (throwable != null) {
                                // An error occurs, we must try to full refresh again
                                register(apis);
                            } else {
                                // Once we are sure that the initial full refresh is a success, we cn move the plans to an incremental refresh
                                if (result.succeeded()) {
                                    // Attach the plans to the global list
                                    plansPerApi.putAll(plansByApi);
                                } else {
                                    LOGGER.error(
                                        "An error occurs while doing a full api-keys refresh for APIs [{}]",
                                        apisById.keySet(),
                                        result.cause()
                                    );
                                    // If not, try to fully refresh again
                                    register(apis);
                                }
                            }
                        }
                    );
            } else {
                // Keep track of all the plans to ensure that, once the node is becoming a master node, we are able
                // to run incremental refresh for all the plans
                plansPerApi.putAll(plansByApi);
            }
        }
    }

    private void unregister(Api api) {
        plansPerApi.remove(api.getId());
    }
}
