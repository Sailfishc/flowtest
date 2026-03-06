package com.github.sailfishc.flowtest.v2.observe.rdbms;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowTestDataSourceRegistryTest {

    @Test
    void resolvesExactThenPatternThenDefault() {
        JdbcDataSource orderDs = dataSource("order");
        JdbcDataSource accountDs = dataSource("account");

        FlowTestDataSourceRegistry registry = new FlowTestDataSourceRegistry()
            .register("orderDs", orderDs)
            .register("accountDs", accountDs)
            .bind("orderDs").table("ft_order").pattern("t_order_*").register()
            .defaultDataSource("accountDs");

        assertThat(registry.requireDataSource("ft_order")).isSameAs(orderDs);
        assertThat(registry.requireDataSource("t_order_202603")).isSameAs(orderDs);
        assertThat(registry.requireDataSource("ft_account")).isSameAs(accountDs);
    }

    @Test
    void failsWhenMultiplePatternsMatchTheSameTable() {
        FlowTestDataSourceRegistry registry = new FlowTestDataSourceRegistry()
            .register("orderDs", dataSource("order"))
            .register("archiveDs", dataSource("archive"))
            .bind("orderDs").pattern("t_order_*").register()
            .bind("archiveDs").pattern("t_*").register();

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                registry.requireDataSource("t_order_202603");
            }
        }).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Multiple FlowTest data source bindings matched table");
    }

    @Test
    void validatesReferencedDataSourceNames() {
        FlowTestDataSourceRegistry registry = new FlowTestDataSourceRegistry()
            .register("orderDs", dataSource("order"))
            .bind("accountDs").table("ft_account").register();

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                registry.validate();
            }
        }).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No DataSource bean registered");
    }

    private JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
