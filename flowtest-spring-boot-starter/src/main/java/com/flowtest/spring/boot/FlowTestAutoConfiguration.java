package com.flowtest.spring.boot;

import com.flowtest.core.TestFlow;
import com.flowtest.core.fixture.AutoFiller;
import com.flowtest.core.fixture.DataFiller;
import com.flowtest.core.fixture.InstancioFiller;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.persistence.JdbcEntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import org.instancio.settings.Keys;
import org.instancio.settings.Settings;
import org.jeasy.random.EasyRandomParameters;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for FlowTest framework.
 * Automatically creates and configures:
 * <ul>
 *   <li>{@link DataFiller} - for auto-generating test data (EasyRandom or Instancio)</li>
 *   <li>{@link EntityPersister} - for persisting test entities</li>
 *   <li>{@link SnapshotEngine} - for database change tracking</li>
 *   <li>{@link TestFlow} - main entry point for tests</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@EnableConfigurationProperties(FlowTestProperties.class)
public class FlowTestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataFiller.class)
    @ConditionalOnProperty(name = "flowtest.data-filler", havingValue = "instancio")
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
    @ConditionalOnMissingBean(DataFiller.class)
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
    @ConditionalOnMissingBean
    public EntityPersister flowTestEntityPersister(DataSource dataSource) {
        return new JdbcEntityPersister(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
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
}
