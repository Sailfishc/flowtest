package com.flowtest.spring.boot;

import com.flowtest.core.TestFlow;
import com.flowtest.core.fixture.AutoFiller;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.persistence.JdbcEntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import org.jeasy.random.EasyRandomParameters;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for FlowTest framework.
 * Automatically creates and configures:
 * <ul>
 *   <li>{@link AutoFiller} - for auto-generating test data</li>
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
    @ConditionalOnMissingBean
    public AutoFiller flowTestAutoFiller(FlowTestProperties properties) {
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
    public TestFlow testFlow(EntityPersister persister, AutoFiller autoFiller, SnapshotEngine snapshotEngine) {
        return new TestFlow(persister, autoFiller, snapshotEngine);
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
