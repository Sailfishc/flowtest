package com.github.sailfishc.flowtest.v2.assertion;

import java.util.Objects;

/**
 * Declarative change expectation for an observed resource.
 */
public final class ResourceChangeExpectation {

    private final String resourceName;
    private final Long expectedInserted;
    private final Long expectedDeleted;
    private final Long expectedModified;

    public ResourceChangeExpectation(String resourceName, Long expectedInserted, Long expectedDeleted, Long expectedModified) {
        this.resourceName = requireText(resourceName, "resourceName must not be blank");
        this.expectedInserted = expectedInserted;
        this.expectedDeleted = expectedDeleted;
        this.expectedModified = expectedModified;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Long getExpectedInserted() {
        return expectedInserted;
    }

    public Long getExpectedDeleted() {
        return expectedDeleted;
    }

    public Long getExpectedModified() {
        return expectedModified;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
