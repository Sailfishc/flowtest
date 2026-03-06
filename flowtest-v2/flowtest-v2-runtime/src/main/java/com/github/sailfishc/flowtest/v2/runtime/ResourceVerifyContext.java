package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import java.util.List;

/**
 * Verification view over one observed resource change set.
 */
public interface ResourceVerifyContext {

    String name();

    long insertedCount();

    long deletedCount();

    long modifiedCount();

    List<RowSnapshot> insertedRows();

    List<RowSnapshot> deletedRows();

    List<ModifiedRow> modifiedRows();

    RowSnapshot insertedOne();

    RowSnapshot deletedOne();

    ModifiedRow modifiedOne();

    ResourceChange raw();
}
