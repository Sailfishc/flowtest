package com.flowtest.spring.boot;

import com.flowtest.core.TestFlow;
import com.flowtest.core.fixture.AutoFiller;
import com.flowtest.core.fixture.DataFiller;
import com.flowtest.core.fixture.InstancioFiller;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.persistence.JdbcEntityPersister;
import com.flowtest.core.routing.DataSourceRoute;
import com.flowtest.core.routing.DataSourceRouter;
import com.flowtest.core.routing.RoutingEntityPersister;
import com.flowtest.core.routing.RoutingSnapshotEngine;
import com.flowtest.core.snapshot.SnapshotEngine;
import org.instancio.settings.Keys;
import org.instancio.settings.Settings;
import org.jeasy.random.EasyRandomParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.*;

/**
 * Auto-configuration for FlowTest framework.
 * Automatically creates and configures:
 * <ul>
 *   <li>{@link DataFiller} - for auto-generating test data (EasyRandom or Instancio)</li>
 *   <li>{@link EntityPersister} - for persisting test entities</li>
 *   <li>{@link SnapshotEngine} - for database change tracking</li>
 *   <li>{@link TestFlow} - main entry point for tests</li>
 * </ul>
 *
 * <p>When {@code flowtest.datasources} is configured, automatically creates
 * {@link DataSourceRouter}, {@link RoutingEntityPersister}, and {@link RoutingSnapshotEngine}
 * to support multi-datasource routing.
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@EnableConfigurationProperties(FlowTestProperties.class)
public class FlowTestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    @ConditionalOnProperty(name = "flowtest.data-filler", havingValue = "easyrandom")
    public DataFiller flowTestAutoFiller(FlowTestProperties properties) {
        EasyRandomParameters params = new EasyRandomParameters()
            .seed(properties.getSeed() != 0 ? properties.getSeed() : System.currentTimeMillis())
            .stringLengthRange(properties.getStringLengthMin(), properties.getStringLengthMax())
            .collectionSizeRange(properties.getCollectionSizeMin(), properties.getCollectionSizeMax())
            .randomizationDepth(properties.getRandomizationDepth())
            .scanClasspathForConcreteTypes(true)
            .overrideDefaultInitialization(false)
            .excludeField(field ->
                "id".equalsIgnoreCase(field.getName()) ||
                isIdAnnotated(field)
            );

        return new AutoFiller(params);
    }

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    public DataFiller flowTestInstancioFiller(FlowTestProperties properties) {
        Settings settings = Settings.create()
            .set(Keys.SEED, properties.getSeed() != 0 ? properties.getSeed() : System.currentTimeMillis())
            .set(Keys.STRING_MIN_LENGTH, properties.getStringLengthMin())
            .set(Keys.STRING_MAX_LENGTH, properties.getStringLengthMax())
            .set(Keys.COLLECTION_MIN_SIZE, properties.getCollectionSizeMin())
            .set(Keys.COLLECTION_MAX_SIZE, properties.getCollectionSizeMax())
            .set(Keys.MAX_DEPTH, properties.getRandomizationDepth())
            .set(Keys.JPA_ENABLED, true)
            .lock();

        return new InstancioFiller(settings);
    }

    @Bean
    @ConditionalOnMissingBean({EntityPersister.class, DataSourceRouter.class})
    public EntityPersister flowTestEntityPersister(DataSource dataSource) {
        return new JdbcEntityPersister(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean({SnapshotEngine.class, DataSourceRouter.class})
    public SnapshotEngine flowTestSnapshotEngine(DataSource dataSource, FlowTestProperties properties) {
        SnapshotEngine engine = new SnapshotEngine(dataSource);
        engine.setIdColumnName(properties.getIdColumnName());
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean
    public TestFlow testFlow(EntityPersister persister, DataFiller dataFiller, SnapshotEngine snapshotEngine) {
        return new TestFlow(persister, dataFiller, snapshotEngine);
    }

    /**
     * Checks if a field has @Id annotation (JPA).
     */
    private boolean isIdAnnotated(java.lang.reflect.Field field) {
        for (java.lang.annotation.Annotation ann : field.getAnnotations()) {
            String name = ann.annotationType().getName();
            if (name.equals("javax.persistence.Id") || name.equals("jakarta.persistence.Id")) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Multi-DataSource configuration — activated when flowtest.datasources is set
    // -----------------------------------------------------------------------

    @Configuration
    @Conditional(MultiDataSourceCondition.class)
    static class MultiDataSourceConfiguration {

        private static final Logger log = LoggerFactory.getLogger(MultiDataSourceConfiguration.class);

        @Bean
        public DataSourceRouter flowTestDataSourceRouter(
                FlowTestProperties properties,
                Map<String, DataSource> allDataSources) {

            Map<String, FlowTestProperties.DataSourceProperties> dsConfigs = properties.getDatasources();

            Map<String, DataSourceRoute> routes = new LinkedHashMap<String, DataSourceRoute>();
            Map<String, EntityPersister> persisters = new LinkedHashMap<String, EntityPersister>();
            Map<String, SnapshotEngine> engines = new LinkedHashMap<String, SnapshotEngine>();

            for (Map.Entry<String, FlowTestProperties.DataSourceProperties> entry : dsConfigs.entrySet()) {
                String dsName = entry.getKey();
                FlowTestProperties.DataSourceProperties dsProps = entry.getValue();

                DataSource dataSource = allDataSources.get(dsName);
                if (dataSource == null) {
                    log.warn("DataSource bean '{}' not found in application context, skipping", dsName);
                    continue;
                }

                // Build route
                Set<String> explicitTables = new LinkedHashSet<String>();
                if (dsProps.getTables() != null) {
                    explicitTables.addAll(dsProps.getTables());
                }
                DataSourceRoute route = new DataSourceRoute(dsName, explicitTables);
                routes.put(dsName, route);

                // Create per-datasource persister and engine
                JdbcEntityPersister persister = new JdbcEntityPersister(dataSource);
                persisters.put(dsName, persister);

                SnapshotEngine engine = new SnapshotEngine(dataSource);
                engine.setIdColumnName(properties.getIdColumnName());
                engines.put(dsName, engine);

                log.info("Configured FlowTest datasource '{}' with {} explicit tables",
                        dsName, explicitTables.size());
            }

            // Add a DEFAULT entry for any DataSource not explicitly configured
            // Use @Primary DataSource or first unmatched one
            for (Map.Entry<String, DataSource> dsEntry : allDataSources.entrySet()) {
                if (!dsConfigs.containsKey(dsEntry.getKey())) {
                    String defaultName = DataSourceRouter.DEFAULT;
                    if (!routes.containsKey(defaultName)) {
                        DataSource ds = dsEntry.getValue();
                        routes.put(defaultName, new DataSourceRoute(defaultName));
                        persisters.put(defaultName, new JdbcEntityPersister(ds));
                        SnapshotEngine engine = new SnapshotEngine(ds);
                        engine.setIdColumnName(properties.getIdColumnName());
                        engines.put(defaultName, engine);
                        log.info("Configured FlowTest DEFAULT datasource from bean '{}'", dsEntry.getKey());
                    }
                    break;
                }
            }

            return new DataSourceRouter(routes, persisters, engines);
        }

        @Bean
        public EntityPersister flowTestRoutingEntityPersister(DataSourceRouter router) {
            return new RoutingEntityPersister(router);
        }

        @Bean
        public SnapshotEngine flowTestRoutingSnapshotEngine(DataSourceRouter router) {
            return new RoutingSnapshotEngine(router);
        }
    }

    /**
     * Condition that matches when {@code flowtest.datasources} is configured.
     */
    static class MultiDataSourceCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ConditionMessage.Builder message = ConditionMessage.forCondition("FlowTest Multi-DataSource");
            boolean bound = Binder.get(context.getEnvironment())
                    .bind("flowtest.datasources", Bindable.mapOf(String.class, Object.class))
                    .isBound();
            if (bound) {
                return ConditionOutcome.match(message.found("property").items("flowtest.datasources"));
            }
            return ConditionOutcome.noMatch(message.didNotFind("property").items("flowtest.datasources"));
        }
    }
}
