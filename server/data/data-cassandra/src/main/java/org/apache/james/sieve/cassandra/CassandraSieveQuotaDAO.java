/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.sieve.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

public class CassandraSieveQuotaDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement selectClusterQuotaStatement;
    private final PreparedStatement selectSpaceUsedByUserStatement;
    private final PreparedStatement selectUserQuotaStatement;
    private final PreparedStatement updateClusterQuotaStatement;
    private final PreparedStatement updateUserQuotaStatement;
    private final PreparedStatement updateSpaceUsedStatement;
    private final PreparedStatement deleteClusterQuotaStatement;
    private final PreparedStatement deleteUserQuotaStatement;

    @Inject
    public CassandraSieveQuotaDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        selectClusterQuotaStatement = session.prepare(
            select(CassandraSieveClusterQuotaTable.VALUE)
                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME))));

        selectSpaceUsedByUserStatement = session.prepare(
            select(CassandraSieveSpaceTable.SPACE_USED)
                .from(CassandraSieveSpaceTable.TABLE_NAME)
                .where(eq(CassandraSieveSpaceTable.USER_NAME, bindMarker(CassandraSieveSpaceTable.USER_NAME))));

        selectUserQuotaStatement = session.prepare(
            select(CassandraSieveQuotaTable.QUOTA)
                .from(CassandraSieveQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME))));

        updateClusterQuotaStatement = session.prepare(
            update(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .with(set(CassandraSieveClusterQuotaTable.VALUE, bindMarker(CassandraSieveClusterQuotaTable.VALUE)))
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME))));

        updateSpaceUsedStatement = session.prepare(
            update(CassandraSieveSpaceTable.TABLE_NAME)
                .with(incr(CassandraSieveSpaceTable.SPACE_USED, bindMarker(CassandraSieveSpaceTable.SPACE_USED)))
                .where(eq(CassandraSieveSpaceTable.USER_NAME, bindMarker(CassandraSieveSpaceTable.USER_NAME))));

        updateUserQuotaStatement = session.prepare(
            update(CassandraSieveQuotaTable.TABLE_NAME)
                .with(set(CassandraSieveQuotaTable.QUOTA, bindMarker(CassandraSieveQuotaTable.QUOTA)))
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME))));

        deleteClusterQuotaStatement = session.prepare(
            delete()
                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveClusterQuotaTable.NAME, bindMarker(CassandraSieveClusterQuotaTable.NAME)))
                .ifExists());

        deleteUserQuotaStatement = session.prepare(
            delete()
                .from(CassandraSieveQuotaTable.TABLE_NAME)
                .where(eq(CassandraSieveQuotaTable.USER_NAME, bindMarker(CassandraSieveQuotaTable.USER_NAME)))
                .ifExists());
    }

    public CompletableFuture<Long> spaceUsedBy(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectSpaceUsedByUserStatement.bind()
                .setString(CassandraSieveSpaceTable.USER_NAME, user))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveSpaceTable.SPACE_USED))
                .orElse(0L));
    }

    public CompletableFuture<Void> updateSpaceUsed(String user, long spaceUsed) {
        return cassandraAsyncExecutor.executeVoid(
            updateSpaceUsedStatement.bind()
                .setLong(CassandraSieveSpaceTable.SPACE_USED, spaceUsed)
                .setString(CassandraSieveSpaceTable.USER_NAME, user));
    }

    public CompletableFuture<Optional<Long>> getQuota() {
        return cassandraAsyncExecutor.executeSingleRow(
            selectClusterQuotaStatement.bind()
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveClusterQuotaTable.VALUE)));
    }

    public CompletableFuture<Void> setQuota(long quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateClusterQuotaStatement.bind()
                .setLong(CassandraSieveClusterQuotaTable.VALUE, quota)
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    public CompletableFuture<Boolean> removeQuota() {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteClusterQuotaStatement.bind()
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    public CompletableFuture<Optional<Long>> getQuota(String user) {
        return cassandraAsyncExecutor.executeSingleRow(
            selectUserQuotaStatement.bind()
                .setString(CassandraSieveQuotaTable.USER_NAME, user))
            .thenApply(optional -> optional.map(row -> row.getLong(CassandraSieveQuotaTable.QUOTA)));
    }

    public CompletableFuture<Void> setQuota(String user, long quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateUserQuotaStatement.bind()
                .setLong(CassandraSieveQuotaTable.QUOTA, quota)
                .setString(CassandraSieveQuotaTable.USER_NAME, user));
    }

    public CompletableFuture<Boolean> removeQuota(String user)  {
        return cassandraAsyncExecutor.executeReturnApplied(
            deleteUserQuotaStatement.bind()
                .setString(CassandraSieveQuotaTable.USER_NAME, user));
    }

}
