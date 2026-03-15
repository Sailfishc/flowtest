package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

import java.util.function.Consumer;

/**
 * Unified verification DSL. Supports both declarative expectations and imperative assertions.
 *
 * <pre>{@code
 * .then(t -> t
 *     .success()
 *     .returns(10L)
 *     .table("ft_order", order -> order
 *         .inserted(1)
 *         .inspect(ctx -> assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED")))
 *     .fixture("user", TestUser.class, user -> user
 *         .afterMatches(FixtureStatePatch.of(TestUser.class).set(TestUser::getBalance, 80L)))
 *     .inspect(ctx -> {
 *         // cross-resource assertions
 *     }))
 * }</pre>
 */
public interface ThenSpec<R> {

    // --- Outcome assertions ---

    /**
     * Assert that the action completed without exception.
     * This is the default if neither success() nor failure() is called.
     */
    ThenSpec<R> success();

    ThenSpec<R> failure(Class<? extends Throwable> exceptionType);

    ThenSpec<R> failureSatisfying(
        Class<? extends Throwable> exceptionType,
        Consumer<? super Throwable> assertion
    );

    // --- Result assertions ---

    ThenSpec<R> returns(R expected);

    ThenSpec<R> returnsSatisfying(ResultAssertion<? super R> assertion);

    // --- Resource assertions (block-scoped, auto-infers observation) ---

    ThenSpec<R> table(String tableName, Consumer<ResourceExpectationSpec> spec);

    ThenSpec<R> entity(Class<?> entityType, Consumer<ResourceExpectationSpec> spec);

    // --- Fixture assertions (alias-first, auto-infers observation) ---

    /**
     * Fixture assertion by type. Uses {@code type.getSimpleName()} as the default alias.
     *
     * <p><strong>Important:</strong> This shorthand is valid only when exactly one fixture of the
     * given type is declared in {@code given()} and its alias matches the default (simpleName).
     * If multiple fixtures of the same type exist, use {@link #fixture(String, Class, Consumer)}
     * with an explicit alias, or use the {@link FixtureHandle} returned from {@code given().fixture(...)}.
     *
     * <pre>{@code
     * .given(g -> g.fixture(User.class, withBalance(100L)))
     * .then(t -> t.fixture(User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L))))
     * }</pre>
     */
    <T> ThenSpec<R> fixture(Class<T> type, Consumer<FixtureExpectationSpec<T>> spec);

    /**
     * Fixture assertion by explicit handle. Preferred when multiple fixtures of the same type exist.
     */
    <T> ThenSpec<R> fixture(FixtureHandle<T> handle, Consumer<FixtureExpectationSpec<T>> spec);

    /**
     * Fixture assertion by alias and type. Use when multiple fixtures of the same type are declared.
     */
    <T> ThenSpec<R> fixture(String alias, Class<T> type, Consumer<FixtureExpectationSpec<T>> spec);

    // --- Global escape hatch ---

    /**
     * Imperative assertion with full context access.
     * Use for cross-resource / cross-fixture correlation assertions.
     * Resources accessed here must be explicitly declared via {@code observe(...)}.
     */
    ThenSpec<R> inspect(ScenarioInspection<R> inspection);
}
