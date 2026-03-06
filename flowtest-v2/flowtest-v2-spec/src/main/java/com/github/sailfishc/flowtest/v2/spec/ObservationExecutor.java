package com.github.sailfishc.flowtest.v2.spec;

import java.util.List;

/**
 * Captures resource snapshots and performs cleanup for observed resources.
 */
public interface ObservationExecutor {

    ObservationSnapshot capture(List<ObservationSpec> observations) throws Exception;

    void cleanup(List<ObservationSpec> observations, ObservationDiff diff, CleanupPolicy cleanupPolicy) throws Exception;
}
