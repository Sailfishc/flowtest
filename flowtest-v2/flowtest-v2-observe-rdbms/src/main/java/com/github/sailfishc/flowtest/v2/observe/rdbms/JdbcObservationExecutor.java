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
            for (int i = observations.size() - 1; i >= 0; i--) {
                ObservationSpec observation = observations.get(i);
                ResourceChange change = diff.getChange(observation.getResourceName());
                if (change != null && !change.getInsertedRows().isEmpty()) {
                    deleteInsertedRows(observation, change.getInsertedRows());
                }
            }
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

    private void deleteInsertedRows(ObservationSpec observation, List<RowSnapshot> rows) throws Exception {
        JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(observation);
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            for (RowSnapshot row : rows) {
                SqlStatement sql = buildDelete(resource.getIdentity(), row);
                PreparedStatement statement = null;
                try {
                    statement = connection.prepareStatement(sql.getSql());
                    bindParameters(statement, sql.getParameters());
                    statement.executeUpdate();
                } finally {
                    closeQuietly(statement);
                }
            }
        } finally {
            closeQuietly(connection);
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
        appendRoute(sql, parameters, routeScope);
        return new SqlStatement(sql.toString(), parameters);
    }

    private SqlStatement buildDelete(TableIdentity identity, RowSnapshot row) {
        StringBuilder sql = new StringBuilder("delete from ").append(identity.getTableName()).append(" where ");
        List<Object> parameters = new ArrayList<Object>();
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
        return new SqlStatement(sql.toString(), parameters);
    }

    private void appendRoute(StringBuilder sql, List<Object> parameters, RouteScope routeScope) {
        if (routeScope == null || routeScope.isEmpty()) {
            return;
        }
        sql.append(" where ");
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
