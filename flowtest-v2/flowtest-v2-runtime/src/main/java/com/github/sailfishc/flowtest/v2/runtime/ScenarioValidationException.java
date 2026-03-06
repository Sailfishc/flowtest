package com.github.sailfishc.flowtest.v2.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raised when a scenario is structurally invalid before execution starts.
 */
public final class ScenarioValidationException extends IllegalStateException {

    private final List<String> issues;

    public ScenarioValidationException(List<String> issues) {
        super(buildMessage(issues));
        this.issues = Collections.unmodifiableList(new ArrayList<String>(issues));
    }

    public List<String> getIssues() {
        return issues;
    }

    private static String buildMessage(List<String> issues) {
        return "Scenario definition is invalid: " + issues;
    }
}
