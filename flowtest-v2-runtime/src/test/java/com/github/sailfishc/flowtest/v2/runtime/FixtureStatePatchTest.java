package com.github.sailfishc.flowtest.v2.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureStatePatchTest {

    @Test
    void shouldResolveGetterMethodReferenceForSet() {
        FixtureStatePatch<TestUser> patch = FixtureStatePatch.of(TestUser.class)
            .set(TestUser::getBalance, 80L);

        assertThat(patch.getExpectedValues()).containsEntry("balance", 80L);
    }

    @Test
    void shouldResolveBooleanGetterMethodReferenceForIgnore() {
        FixtureStatePatch<TestUser> patch = FixtureStatePatch.of(TestUser.class)
            .ignore(TestUser::isDeleted);

        assertThat(patch.getIgnoredProperties()).containsExactly("deleted");
    }

    @Test
    void shouldRejectNonGetterLambda() {
        assertThatThrownBy(() -> FixtureStatePatch.of(TestUser.class)
            .set(user -> user.getName().trim(), "Alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only getter method references are supported");
    }

    static final class TestUser {
        private String name;
        private Long balance;
        private boolean deleted;

        public String getName() {
            return name;
        }

        public Long getBalance() {
            return balance;
        }

        public boolean isDeleted() {
            return deleted;
        }
    }
}
