package com.flowtest.core.persistence;

import com.flowtest.core.fixture.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC-based implementation of EntityPersister.
 * Uses Spring JdbcTemplate for database operations.
 */
public class JdbcEntityPersister implements EntityPersister {

    private static final Logger log = LoggerFactory.getLogger(JdbcEntityPersister.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<Class<?>, EntityMetadata> metadataCache = new ConcurrentHashMap<>();

    public JdbcEntityPersister(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public JdbcEntityPersister(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public <T> Object persist(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        EntityMetadata metadata = getMetadata(entity.getClass());
        List<String> columns = metadata.getInsertColumns(entity);
        List<Object> values = metadata.getInsertValues(entity, columns);

        String sql = buildInsertSql(metadata.getTableName(), columns);
        log.debug("Executing INSERT: {} with values: {}", sql, values);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            return ps;
        }, keyHolder);

        // Get the generated key
        Number generatedKey = keyHolder.getKey();
        if (generatedKey != null) {
            // Set the ID back on the entity
            metadata.setId(entity, generatedKey);
            log.debug("Generated ID: {}", generatedKey);
            return generatedKey;
        }

        // If no key was generated, return the existing ID
        return metadata.getId(entity);
    }

    @Override
    public <T> List<Object> persistAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> ids = new ArrayList<>(entities.size());
        for (T entity : entities) {
            ids.add(persist(entity));
        }
        return ids;
    }

    @Override
    public <T> void delete(Class<T> entityClass, Object id) {
        EntityMetadata metadata = getMetadata(entityClass);
        String sql = "DELETE FROM " + metadata.getTableName() +
                     " WHERE " + metadata.getIdColumnName() + " = ?";
        log.debug("Executing DELETE: {} with id: {}", sql, id);
        jdbcTemplate.update(sql, id);
    }

    @Override
    public <T> void deleteAll(Class<T> entityClass, List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        EntityMetadata metadata = getMetadata(entityClass);

        // Use batch delete for efficiency
        if (ids.size() == 1) {
            delete(entityClass, ids.get(0));
            return;
        }

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + metadata.getTableName() +
                     " WHERE " + metadata.getIdColumnName() + " IN (" + placeholders + ")";
        log.debug("Executing batch DELETE: {} with ids: {}", sql, ids);
        jdbcTemplate.update(sql, ids.toArray());
    }

    @Override
    public <T> int deleteAllOfType(Class<T> entityClass) {
        EntityMetadata metadata = getMetadata(entityClass);
        String sql = "DELETE FROM " + metadata.getTableName();
        log.debug("Executing DELETE ALL: {}", sql);
        return jdbcTemplate.update(sql);
    }

    /**
     * Builds INSERT SQL statement.
     */
    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(String.join(", ", Collections.nCopies(columns.size(), "?")));
        sql.append(")");
        return sql.toString();
    }

    /**
     * Gets or creates metadata for an entity class.
     */
    private EntityMetadata getMetadata(Class<?> entityClass) {
        return metadataCache.computeIfAbsent(entityClass, EntityMetadata::new);
    }

    /**
     * Gets the underlying JdbcTemplate.
     */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
