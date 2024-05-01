/*
 * *
 *  * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.gravitee.plugin.endpoint.database.proxy;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.apache.commons.lang3.StringUtils;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ConnectorMode;
import io.gravitee.gateway.reactive.api.connector.endpoint.sync.EndpointSyncConnector;
import io.gravitee.gateway.reactive.api.context.ExecutionContext;
import io.gravitee.plugin.endpoint.database.proxy.client.DatabaseClientFactory;
import io.gravitee.plugin.endpoint.database.proxy.configuration.DatabaseProxyEndpointConnectorConfiguration;
import io.gravitee.plugin.endpoint.database.proxy.configuration.DatabaseProxyEndpointConnectorSharedConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class DatabaseProxyEndpointConnector extends EndpointSyncConnector {

    private String body;


    private static final String ENDPOINT_ID = "database";
    static final Set<ConnectorMode> SUPPORTED_MODES = Set.of(ConnectorMode.REQUEST_RESPONSE);

    protected final DatabaseProxyEndpointConnectorConfiguration configuration;
    protected final DatabaseProxyEndpointConnectorSharedConfiguration sharedConfiguration;
    private DatabaseClientFactory databaseClientFactory;

    private String resolvedTable;

    public DatabaseProxyEndpointConnector(
        DatabaseProxyEndpointConnectorConfiguration configuration,
        DatabaseProxyEndpointConnectorSharedConfiguration sharedConfiguration
    ) {
        this.configuration = configuration;
        this.sharedConfiguration = sharedConfiguration;
        this.databaseClientFactory = new DatabaseClientFactory();
        this.resolvedTable = resolveTable();
    }

    @Override
    public String id() {
        return ENDPOINT_ID;
    }

    @Override
    public Set<ConnectorMode> supportedModes() {
        return SUPPORTED_MODES;
    }

    @Override
    public Completable connect(final ExecutionContext ctx) {
        try {
            final Pool client = databaseClientFactory
                .getOrBuildJdbcClient(ctx, this.configuration, this.sharedConfiguration);

            return switch (ctx.request().method()) {
                case GET -> readData(client, ctx);
                case POST -> writeData(client, ctx);
                default -> readData(client, ctx); // Todo change
            };

        } catch (Exception e) {
            return Completable.error(e);
        }
    }

    private Completable readData(Pool client, ExecutionContext ctx) {
        String query = "SELECT * FROM " + this.resolvedTable;
        return client
            .rxGetConnection()
            .flatMap(conn -> {
                conn.begin();
                Single<Flowable<JsonObject>> output = conn.rxPrepare(query)
                    .map(preparedQuery ->
                        preparedQuery.createStream(50).toFlowable()
                    )
                    .map(rowFlowable -> rowFlowable.map(Row::toJson));
                conn.rxClose();
                return output;
            })
            .map(output -> output.map(JsonObject::toBuffer).map(Buffer::buffer))
            .doOnSuccess(ctx.response()::chunks)
            .ignoreElement();
    }

    private Completable writeData(Pool client, ExecutionContext ctx) {
        final String[] insertString = new String[1];
        Single<Disposable> myString = Single.just(
            ctx.request().body()
                .map(body -> io.vertx.rxjava3.core.buffer.Buffer.buffer(body.getNativeBuffer()))
                .map(io.vertx.rxjava3.core.buffer.Buffer::toJsonObject)
                .subscribe(json ->
                    insertString[0] = StringUtils.join(json.stream().map(Map.Entry::getValue).collect(Collectors.toList()), "', '"))
                );

        return client
            .withTransaction(conn -> {
                conn.begin();
                Maybe<RowSet<Row>> output = conn.query(
                        "INSERT INTO " + this.resolvedTable + " VALUES ('" + insertString[0] + "')"
                    )
                    .rxExecute()
                    .toMaybe();
                conn.rxClose();
                return output;
            })
            .ignoreElement();
    }

    private String resolveTable() {
        // Depending on database type we may need to prefix the table.
        // For postgres POC we just return the table name. Eventually there will be a switch statement here.
        // return String.format("%s.%s", configuration.getDatabase(), configuration.getTable());
        return this.configuration.getTable();
    }

}
