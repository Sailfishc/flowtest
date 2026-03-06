package com.github.sailfishc.flowtest.v2.junit5;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceSnapshot;
import com.github.sailfishc.flowtest.v2.spec.RowKey;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTestV2ExtensionBuilderTest {

    @RegisterExtension
    static final FlowTestV2Extension FLOW = FlowTestV2Extension.builder()
        .observationExecutor(new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        )))
        .build();

    @Test
    void shouldResolveExecutorFromRegisteredExtension(ScenarioExecutor executor) throws Exception {
        ScenarioExecutionResult<String> result = FlowTestV2.scenario("junit5-builder")
            .watch(w -> w.table("orders"))
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(() -> "ok")
            .then(t -> t.expectNoException().inserted("orders", 1))
            .run();

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(result.getDiff().getChange("orders").getInsertedCount()).isEqualTo(1L);
        assertThat(executor).isNotNull();
    }

    private static ObservationSnapshot snapshot(String resourceName, RowSnapshot... rows) {
        return new ObservationSnapshot(Collections.singletonList(new ResourceSnapshot(resourceName, Arrays.asList(rows))));
    }

    private static RowSnapshot row(Long id, Object... kv) {
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("id", id);
        for (int i = 0; i < kv.length; i += 2) {
            columns.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new RowSnapshot(RowKey.of(id), columns);
    }

    private static final class RecordingObservationExecutor implements ObservationExecutor {

        private final List<ObservationSnapshot> snapshots;
        private int index;

        private RecordingObservationExecutor(List<ObservationSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public ObservationSnapshot capture(List<ObservationSpec> observations) {
            ObservationSnapshot snapshot = snapshots.get(index);
            index++;
            return snapshot;
        }

        @Override
        public void cleanup(List<ObservationSpec> observations, ObservationDiff diff, CleanupPolicy cleanupPolicy) {
            // no-op
        }
    }
}
