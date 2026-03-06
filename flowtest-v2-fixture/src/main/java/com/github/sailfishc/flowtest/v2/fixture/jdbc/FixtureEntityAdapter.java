package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import java.sql.Connection;

/**
 * JDBC persistence adapter for one fixture entity type.
 */
public interface FixtureEntityAdapter<T> {

    Class<T> getEntityType();

    void insert(Connection connection, T entity) throws Exception;

    T reload(Connection connection, T entity) throws Exception;

    void delete(Connection connection, T entity) throws Exception;
}
