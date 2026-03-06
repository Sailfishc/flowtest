package com.github.sailfishc.flowtest.v2.assertion;

import java.util.Objects;

/**
 * Custom assertion paired with the observed resource it targets.
 */
public final class ResourceChangeAssertionExpectation {

    private final String resourceName;
    private final ResourceChangeAssertion assertion;

    public ResourceChangeAssertionExpectation(String resourceName, ResourceChangeAssertion assertion) {
        this.resourceName = requireText(resourceName, "resourceName must not be blank");
        this.assertion = Objects.requireNonNull(assertion, "assertion must not be null");
    }

    public String getResourceName() {
        return resourceName;
    }

    public ResourceChangeAssertion getAssertion() {
        return assertion;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
