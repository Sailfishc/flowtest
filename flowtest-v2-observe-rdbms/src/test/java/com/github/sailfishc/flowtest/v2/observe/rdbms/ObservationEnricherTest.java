package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureValueResolver;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationEnricherTest {

    private JdbcObservationRegistry registry;
    private FixtureValueResolver fixtures;
    private TestEntity testEntity;
    private FixtureHandle<TestEntity> handle;

    @BeforeEach
    void setUp() {
        registry = new JdbcObservationRegistry();
        testEntity = new TestEntity();
        testEntity.id = 1L;
        testEntity.bucket = "a";
        handle = FixtureHandle.named(TestEntity.class, "testHandle");

        Map<FixtureHandle<?>, Object> fixtureMap = new HashMap<FixtureHandle<?>, Object>();
        fixtureMap.put(handle, testEntity);
        fixtures = new MapFixtureValueResolver(fixtureMap);
    }

    @Test
    void shouldDeriveIdRouteScopeFromEntityPrimaryKey() {
        // Register entity with id as primary key
        registry.entity(TestEntity.class, "t_order", "id")
            .register();

        ObservationSpec observation = ObservationSpec.fixture(handle);
        List<ObservationSpec> observations = Collections.singletonList(observation);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(observations, fixtures, registry);

        ObservationSpec enrichedObs = enriched.get(0);

        // Verify RouteScope is derived from the registered identity
        assertThat(enrichedObs.getRouteScope().isEmpty()).isFalse();
        assertThat(enrichedObs.getRouteScope().getConditions()).hasSize(1);
        RouteCondition condition = enrichedObs.getRouteScope().getConditions().get(0);
        assertThat(condition.getColumnName()).isEqualTo("id");
        assertThat(condition.getValue()).isEqualTo(1L);
    }

    @Test
    void shouldDeriveBothTableRouteScopeAndIdRouteScopeForDynamicTable() {
        // Register entity as dynamic table by "bucket" property
        registry.entity(TestEntity.class, "t_order", "id")
            .dynamicByProperty("bucket")
            .register();

        ObservationSpec observation = ObservationSpec.fixture(handle);
        List<ObservationSpec> observations = Collections.singletonList(observation);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(observations, fixtures, registry);

        ObservationSpec enrichedObs = enriched.get(0);

        // Verify TableRouteScope is derived for physical table name resolution
        assertThat(enrichedObs.getTableRouteScope().isEmpty()).isFalse();
        assertThat(enrichedObs.getTableRouteScope().getValues()).hasSize(1);
        assertThat(enrichedObs.getTableRouteScope().getValues().get(0).getKey()).isEqualTo("bucket");
        assertThat(enrichedObs.getTableRouteScope().getValues().get(0).getValue()).isEqualTo("a");

        // Verify RouteScope is derived from the entity identity (NOT from bucket)
        assertThat(enrichedObs.getRouteScope().isEmpty()).isFalse();
        assertThat(enrichedObs.getRouteScope().getConditions()).hasSize(1);
        RouteCondition condition = enrichedObs.getRouteScope().getConditions().get(0);
        assertThat(condition.getColumnName()).isEqualTo("id");
        assertThat(condition.getValue()).isEqualTo(1L);
    }

    @Test
    void shouldNotEnrichWhenRouteScopeAlreadySet() {
        registry.entity(TestEntity.class, "t_order", "id")
            .dynamicByProperty("bucket")
            .register();

        // Create observation with pre-set RouteScope
        ObservationSpec observation = ObservationSpec.fixture(handle)
            .withRouteScope(RouteScope.of(RouteCondition.eq("custom_column", "custom_value")));
        List<ObservationSpec> observations = Collections.singletonList(observation);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(observations, fixtures, registry);

        // Should keep existing RouteScope (not overwritten)
        assertThat(enriched.get(0).getRouteScope().getConditions().get(0).getColumnName()).isEqualTo("custom_column");
    }

    @Test
    void shouldNotEnrichNonDynamicTableWithoutId() {
        // Register entity with id column but entity has null id
        TestEntity noIdEntity = new TestEntity();
        noIdEntity.id = null;  // No ID set
        FixtureHandle<TestEntity> noIdHandle = FixtureHandle.named(TestEntity.class, "noIdHandle");

        Map<FixtureHandle<?>, Object> fixtureMap = new HashMap<FixtureHandle<?>, Object>();
        fixtureMap.put(noIdHandle, noIdEntity);
        FixtureValueResolver noIdFixtures = new MapFixtureValueResolver(fixtureMap);

        registry.entity(TestEntity.class, "t_order", "id")
            .register();

        ObservationSpec observation = ObservationSpec.fixture(noIdHandle);
        List<ObservationSpec> observations = Collections.singletonList(observation);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(observations, noIdFixtures, registry);

        // Should return same instance (no ID to derive route from)
        assertThat(enriched).isSameAs(observations);
    }

    @Test
    void shouldDeriveCompositeIdentityRouteScopeUsingPropertyMappings() {
        testEntity.tenantId = 100L;

        registry.entity(TestEntity.class, "t_order", "tenant_id", "id")
            .column("tenantId", "tenant_id")
            .register();

        ObservationSpec observation = ObservationSpec.fixture(handle);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(
            Collections.singletonList(observation), fixtures, registry);

        assertThat(enriched.get(0).getRouteScope().getConditions())
            .extracting(RouteCondition::getColumnName, RouteCondition::getValue)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("tenant_id", 100L),
                org.assertj.core.groups.Tuple.tuple("id", 1L)
            );
    }

    @Test
    void shouldNotDerivePartialIdentityRouteScopeWhenAnyKeyValueIsMissing() {
        registry.entity(TestEntity.class, "t_order", "tenant_id", "id")
            .column("tenantId", "tenant_id")
            .register();

        ObservationSpec observation = ObservationSpec.fixture(handle);

        List<ObservationSpec> enriched = ObservationEnricher.enrichFixtureObservations(
            Collections.singletonList(observation), fixtures, registry);

        assertThat(enriched).containsExactly(observation);
        assertThat(enriched.get(0).getRouteScope().isEmpty()).isTrue();
    }

    // Test entity class
    public static class TestEntity {
        Long id;
        Long tenantId;
        String bucket;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    // Simple FixtureValueResolver backed by a Map
    @SuppressWarnings("unchecked")
    private static class MapFixtureValueResolver implements FixtureValueResolver {
        private final Map<FixtureHandle<?>, Object> fixtures;

        MapFixtureValueResolver(Map<FixtureHandle<?>, Object> fixtures) {
            this.fixtures = fixtures;
        }

        @Override
        public <T> T resolve(FixtureHandle<T> handle) {
            return (T) fixtures.get(handle);
        }
    }
}
