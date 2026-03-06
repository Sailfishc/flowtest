package com.github.sailfishc.flowtest.v2.testng;

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
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Listeners(FlowTestV2Listener.class)
public class FlowTestV2ListenerTest implements ScenarioExecutorProvider {

    @FlowTestV2Executor
    private ScenarioExecutor executor;

    @Test
    public void shouldInjectExecutorIntoAnnotatedField() throws Exception {
        assertThat(executor).isNotNull();
        assertThat(FlowTestV2Listener.currentExecutor()).isSameAs(executor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("testng")
            .observe(o -> o.table("orders"))
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(() -> "ok")
            .then(t -> t.expectNoException())
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
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
