package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeAssertion;

/**
 * Fluent DSL for declaring expectations on a specific fixture.
 *
 * <pre>{@code
 * .then(t -> t
 *     .fixture("user", TestUser.class, user -> user
 *         .before(u -> assertThat(u.getBalance()).isEqualTo(100L))
 *         .after(u -> assertThat(u.getBalance()).isEqualTo(80L))
 *         .afterMatches(FixtureStatePatch.of(TestUser.class).set(TestUser::getBalance, 80L))))
 * }</pre>
 */
public interface FixtureExpectationSpec<T> {

    FixtureExpectationSpec<T> before(FixtureAssertion<T> assertion);

    FixtureExpectationSpec<T> after(FixtureAssertion<T> assertion);

    FixtureExpectationSpec<T> change(FixtureChangeAssertion<T> assertion);

    FixtureExpectationSpec<T> afterMatches(FixtureStatePatch<T> patch);

    FixtureExpectationSpec<T> inspect(FixtureInspection<T> inspection);
}
