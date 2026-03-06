package com.github.sailfishc.flowtest.v2.spring.boot;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.FixtureAdapterRegistry;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.JdbcFixtureExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Auto-configuration for the FlowTest V2 JDBC integration path.
 */
@Configuration
@ConditionalOnClass({ScenarioExecutor.class, DataSource.class})
@EnableConfigurationProperties(FlowTestV2Properties.class)
@ConditionalOnProperty(prefix = "flowtest.v2", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowTestV2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FixtureAdapterRegistry flowTestV2FixtureAdapterRegistry() {
        return new FixtureAdapterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcObservationRegistry flowTestV2ObservationRegistry() {
        return new JdbcObservationRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(FixtureExecutor.class)
    public FixtureExecutor flowTestV2FixtureExecutor(DataSource dataSource, FixtureAdapterRegistry fixtureAdapterRegistry) {
        return new JdbcFixtureExecutor(dataSource, fixtureAdapterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ObservationExecutor.class)
    public ObservationExecutor flowTestV2ObservationExecutor(DataSource dataSource, JdbcObservationRegistry observationRegistry) {
        return new JdbcObservationExecutor(dataSource, observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScenarioExecutor flowTestV2ScenarioExecutor(FixtureExecutor fixtureExecutor,
                                                       ObservationExecutor observationExecutor) {
        return new ScenarioExecutor(fixtureExecutor, observationExecutor);
    }
}
