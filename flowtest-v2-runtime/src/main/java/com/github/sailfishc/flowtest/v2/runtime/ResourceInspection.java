package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Scoped imperative assertion callback for a single observed resource.
 */
@FunctionalInterface
public interface ResourceInspection {

    void inspect(ResourceVerifyContext context) throws Exception;
}
