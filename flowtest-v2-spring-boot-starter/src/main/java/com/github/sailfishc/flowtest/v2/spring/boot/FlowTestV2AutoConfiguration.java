package com.github.sailfishc.flowtest.v2.spring.boot;

import com.github.sailfishc.flowtest.v2.fixture.DataFiller;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.FixtureMaterializer;
import com.github.sailfishc.flowtest.v2.fixture.InstancioDataFiller;
import com.github.sailfishc.flowtest.v2.fixture.NoOpDataFiller;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.FixtureAdapterRegistry;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.JdbcFixtureExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.FlowTestDataSourceRegistry;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.observe.rdbms.MultiDataSourceJdbcObservationExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Auto-configuration for the FlowTest V2 JDBC integration path.
 *
 * <p>Provides auto data filling via Instancio by default. Set
 * {@code flowtest.v2.data-filler=none} to disable auto-filling.</p>
 */
@Configuration
@ConditionalOnClass({ScenarioExecutor.class, DataSource.class})
@EnableConfigurationProperties(FlowTestV2Properties.class)
@ConditionalOnProperty(prefix = "flowtest.v2", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowTestV2AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    @ConditionalOnClass(name = "org.instancio.Instancio")
    @ConditionalOnProperty(prefix = "flowtest.v2", name = "data-filler", havingValue = "none", matchIfMissing = false)
    public DataFiller flowTestV2NoOpDataFiller() {
        return NoOpDataFiller.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    @ConditionalOnClass(name = "org.instancio.Instancio")
    public DataFiller flowTestV2InstancioDataFiller() {
        return new InstancioDataFiller();
    }

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    public DataFiller flowTestV2FallbackDataFiller() {
        return NoOpDataFiller.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public FixtureMaterializer flowTestV2FixtureMaterializer(DataFiller dataFiller) {
        return new FixtureMaterializer(dataFiller);
    }

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
    public FixtureExecutor flowTestV2FixtureExecutor(FlowTestDataSourceRegistry dataSourceRegistry,
                                                     FixtureAdapterRegistry fixtureAdapterRegistry,
                                                     FixtureMaterializer fixtureMaterializer,
                                                     JdbcObservationRegistry observationRegistry) {
        return new JdbcFixtureExecutor(dataSourceRegistry, fixtureAdapterRegistry, fixtureMaterializer, observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ObservationExecutor.class)
    public ObservationExecutor flowTestV2ObservationExecutor(FlowTestDataSourceRegistry dataSourceRegistry,
                                                             JdbcObservationRegistry observationRegistry) {
        return new MultiDataSourceJdbcObservationExecutor(dataSourceRegistry, observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowTestDataSourceRegistry flowTestV2DataSourceRegistry(Map<String, DataSource> dataSources,
                                                                   FlowTestV2Properties properties) {
        FlowTestDataSourceRegistry registry = new FlowTestDataSourceRegistry();
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            registry.register(entry.getKey(), entry.getValue());
        }
        if (properties.getDatasource().getDefaultName() != null && !properties.getDatasource().getDefaultName().trim().isEmpty()) {
            registry.defaultDataSource(properties.getDatasource().getDefaultName().trim());
        }
        for (FlowTestV2Properties.BindingProperties binding : properties.getDatasource().getBindings()) {
            FlowTestDataSourceRegistry.BindingBuilder builder = registry.bind(binding.getName());
            for (String table : binding.getTables()) {
                builder.table(table);
            }
            for (String pattern : binding.getPatterns()) {
                builder.pattern(pattern);
            }
            builder.register();
        }
        registry.validate();
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ScenarioExecutor flowTestV2ScenarioExecutor(FixtureExecutor fixtureExecutor,
                                                       ObservationExecutor observationExecutor) {
        return new ScenarioExecutor(fixtureExecutor, observationExecutor);
    }
}
