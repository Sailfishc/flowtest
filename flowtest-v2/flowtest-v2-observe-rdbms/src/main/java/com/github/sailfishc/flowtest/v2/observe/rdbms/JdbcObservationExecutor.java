package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.ResourceSnapshot;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteConditionOperator;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.RowKey;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC observation executor backed by explicit table registrations.
 */
public final class JdbcObservationExecutor implements ObservationExecutor {

    private final DataSource dataSource;
    private final JdbcObservationRegistry registry;

    public JdbcObservationExecutor(DataSource dataSource, JdbcObservationRegistry registry) {
        this.dataSource = dataSource;
        this.registry = registry;
    }

    @Override
    public ObservationSnapshot capture(List<ObservationSpec> observations) throws Exception {
        List<ResourceSnapshot> snapshots = new ArrayList<ResourceSnapshot>();
        for (ObservationSpec observation : observations) {
            snapshots.add(captureResource(observation));
        }
        return new ObservationSnapshot(snapshots);
    }

    @Override
    public void cleanup(List<ObservationSpec> observations, ObservationDiff diff, CleanupPolicy cleanupPolicy) throws Exception {
        if (cleanupPolicy == CleanupPolicy.DELETE_INSERTED) {
            executeCleanup(observations, diff, false);
            return;
        }
        if (cleanupPolicy == CleanupPolicy.RESTORE_BEFORE_IMAGE) {
            executeCleanup(observations, diff, true);
            return;
        }
        if (cleanupPolicy == CleanupPolicy.DELETE_FIXTURE) {
            return;
        }
        if (cleanupPolicy == CleanupPolicy.ROLLBACK) {
            throw new IllegalStateException("ROLLBACK cleanup requires an external transaction boundary");
        }
        throw new UnsupportedOperationException("Cleanup policy is not implemented yet: " + cleanupPolicy);
    }

    private ResourceSnapshot captureResource(ObservationSpec observation) throws Exception {
        JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(observation);
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = dataSource.getConnection();
            SqlStatement sql = buildSelect(resource.getIdentity(), observation.getRouteScope());
            statement = connection.prepareStatement(sql.getSql());
            bindParameters(statement, sql.getParameters());
            resultSet = statement.executeQuery();
            return new ResourceSnapshot(resource.getResourceName(), readRows(resultSet, resource.getIdentity()));
        } finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    private void executeCleanup(List<ObservationSpec> observations, ObservationDiff diff, boolean restoreBeforeImage) throws Exception {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (int i = observations.size() - 1; i >= 0; i--) {
                    ObservationSpec observation = observations.get(i);
                    ResourceChange change = diff.getChange(observation.getResourceName());
                    if (change != null && !change.getInsertedRows().isEmpty()) {
                        deleteInsertedRows(connection, observation, change.getInsertedRows());
                    }
                }
                if (restoreBeforeImage) {
                    restoreDeletedRows(connection, observations, diff);
                    restoreModifiedRows(connection, observations, diff);
                }
                connection.commit();
            } catch (Exception ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    private void deleteInsertedRows(Connection connection, ObservationSpec observation, List<RowSnapshot> rows) throws Exception {
        JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(observation);
        for (RowSnapshot row : rows) {
            executeStatement(connection, buildDelete(resource.getIdentity(), row, observation.getRouteScope()));
        }
    }

    private void restoreDeletedRows(Connection connection, List<ObservationSpec> observations, ObservationDiff diff) throws Exception {
        for (ObservationSpec observation : observations) {
            ResourceChange change = diff.getChange(observation.getResourceName());
            if (change == null || change.getDeletedRows().isEmpty()) {
                continue;
            }
            JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(observation);
            for (RowSnapshot row : change.getDeletedRows()) {
                executeStatement(connection, buildInsert(resource.getIdentity(), row));
            }
        }
    }

    private void restoreModifiedRows(Connection connection, List<ObservationSpec> observations, ObservationDiff diff) throws Exception {
        for (ObservationSpec observation : observations) {
            ResourceChange change = diff.getChange(observation.getResourceName());
            if (change == null || change.getModifiedRows().isEmpty()) {
                continue;
            }
            JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(observation);
            for (com.github.sailfishc.flowtest.v2.spec.ModifiedRow row : change.getModifiedRows()) {
                SqlStatement sql = buildUpdate(resource.getIdentity(), row.getBefore(), observation.getRouteScope());
                if (sql != null) {
                    executeStatement(connection, sql);
                }
            }
        }
    }

    private List<RowSnapshot> readRows(ResultSet resultSet, TableIdentity identity) throws SQLException {
        List<RowSnapshot> rows = new ArrayList<RowSnapshot>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        while (resultSet.next()) {
            Map<String, Object> columns = new LinkedHashMap<String, Object>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String columnLabel = metadata.getColumnLabel(i).toLowerCase(Locale.ENGLISH);
                columns.put(columnLabel, resultSet.getObject(i));
            }
            rows.add(new RowSnapshot(buildKey(identity, columns), columns));
        }
        return rows;
    }

    private RowKey buildKey(TableIdentity identity, Map<String, Object> columns) {
        List<Object> keyValues = new ArrayList<Object>();
        for (String keyColumn : identity.getKeyColumns()) {
            keyValues.add(columns.get(keyColumn));
        }
        return RowKey.of(keyValues.toArray(new Object[keyValues.size()]));
    }

    private SqlStatement buildSelect(TableIdentity identity, RouteScope routeScope) {
        StringBuilder sql = new StringBuilder("select * from ").append(identity.getTableName());
        List<Object> parameters = new ArrayList<Object>();
        appendRoute(sql, parameters, routeScope, false);
        return new SqlStatement(sql.toString(), parameters);
    }

    private SqlStatement buildDelete(TableIdentity identity, RowSnapshot row, RouteScope routeScope) {
        StringBuilder sql = new StringBuilder("delete from ").append(identity.getTableName()).append(" where ");
        List<Object> parameters = new ArrayList<Object>();
        appendKeyConditions(sql, parameters, identity, row);
        appendRoute(sql, parameters, routeScope, true);
        return new SqlStatement(sql.toString(), parameters);
    }

    private SqlStatement buildInsert(TableIdentity identity, RowSnapshot row) {
        StringBuilder sql = new StringBuilder("insert into ").append(identity.getTableName()).append(" (");
        StringBuilder values = new StringBuilder(" values (");
        List<Object> parameters = new ArrayList<Object>();
        int index = 0;
        for (Map.Entry<String, Object> entry : row.getColumns().entrySet()) {
            if (index > 0) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(entry.getKey());
            values.append("?");
            parameters.add(entry.getValue());
            index++;
        }
        sql.append(")").append(values).append(")");
        return new SqlStatement(sql.toString(), parameters);
    }

    private SqlStatement buildUpdate(TableIdentity identity, RowSnapshot row, RouteScope routeScope) {
        StringBuilder sql = new StringBuilder("update ").append(identity.getTableName()).append(" set ");
        List<Object> parameters = new ArrayList<Object>();
        int updatedColumnCount = 0;
        for (Map.Entry<String, Object> entry : row.getColumns().entrySet()) {
            if (identity.getKeyColumns().contains(entry.getKey())) {
                continue;
            }
            if (updatedColumnCount > 0) {
                sql.append(", ");
            }
            sql.append(entry.getKey()).append(" = ?");
            parameters.add(entry.getValue());
            updatedColumnCount++;
        }
        if (updatedColumnCount == 0) {
            return null;
        }
        sql.append(" where ");
        appendKeyConditions(sql, parameters, identity, row);
        appendRoute(sql, parameters, routeScope, true);
        return new SqlStatement(sql.toString(), parameters);
    }

    private void appendKeyConditions(StringBuilder sql, List<Object> parameters, TableIdentity identity, RowSnapshot row) {
        for (int i = 0; i < identity.getKeyColumns().size(); i++) {
            String keyColumn = identity.getKeyColumns().get(i);
            if (i > 0) {
                sql.append(" and ");
            }
            Object value = row.getColumn(keyColumn);
            if (value == null) {
                sql.append(keyColumn).append(" is null");
            } else {
                sql.append(keyColumn).append(" = ?");
                parameters.add(value);
            }
        }
    }

    private void appendRoute(StringBuilder sql, List<Object> parameters, RouteScope routeScope, boolean hasPredicate) {
        if (routeScope == null || routeScope.isEmpty()) {
            return;
        }
        if (!hasPredicate) {
            sql.append(" where ");
        } else {
            sql.append(" and ");
        }
        List<RouteCondition> conditions = routeScope.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            RouteCondition condition = conditions.get(i);
            if (i > 0) {
                sql.append(" and ");
            }
            if (condition.getOperator() != RouteConditionOperator.EQ) {
                throw new UnsupportedOperationException("Unsupported route operator: " + condition.getOperator());
            }
            if (condition.getValue() == null) {
                sql.append(condition.getColumnName()).append(" is null");
            } else {
                sql.append(condition.getColumnName()).append(" = ?");
                parameters.add(condition.getValue());
            }
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private void executeStatement(Connection connection, SqlStatement sql) throws Exception {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql.getSql());
            bindParameters(statement, sql.getParameters());
            statement.executeUpdate();
        } finally {
            closeQuietly(statement);
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // cleanup rollback failure is surfaced by the original exception
        }
    }

    private void closeQuietly(AutoCloseable closeable) throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    private static final class SqlStatement {

        private final String sql;
        private final List<Object> parameters;

        private SqlStatement(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }
}
