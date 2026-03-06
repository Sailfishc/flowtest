package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Context-based verification callback executed after diffing and before cleanup.
 */
public interface ScenarioVerification<R> {

    void verify(VerifyContext<R> context) throws Exception;
}
