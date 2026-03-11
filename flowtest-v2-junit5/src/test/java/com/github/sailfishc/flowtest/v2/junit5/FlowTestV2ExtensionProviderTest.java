package com.github.sailfishc.flowtest.v2.junit5;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@FlowTestV2Test
class FlowTestV2ExtensionProviderTest implements ScenarioExecutorProvider {

    @Test
    void shouldResolveExecutorFromProvider(ScenarioExecutor executor) throws Exception {
        ScenarioExecutionResult<String> result = FlowTestV2.scenario("junit5-provider")
            .observe(o -> o.table("orders"))
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(() -> "done")
            .then(t -> t.success())
            .run();

        assertThat(result.getResult()).isEqualTo("done");
        assertThat(executor).isNotNull();
    }

    @Override
    public ScenarioExecutor createScenarioExecutor() {
        return new ScenarioExecutor(new ObservationExecutor() {
            private final List<ObservationSnapshot> snapshots = Arrays.asList(
                new ObservationSnapshot(Collections.singletonList(new ResourceSnapshot("orders", Collections.emptyList()))),
                new ObservationSnapshot(Collections.singletonList(new ResourceSnapshot("orders", Collections.emptyList())))
            );
            private int index;

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
        });
    }
}
