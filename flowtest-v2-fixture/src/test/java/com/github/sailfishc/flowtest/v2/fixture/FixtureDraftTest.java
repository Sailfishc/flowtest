package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.TraitContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureDraftTest {

    @Test
    void materializeAppliesAllTraits() {
        FixtureSpec<Account> spec = new FixtureSpec<Account>(
            FixtureHandle.named(Account.class, "account"),
            Account.class,
            Arrays.<FixtureTrait<? super Account>>asList(
                FixtureTrait.of(new java.util.function.Consumer<Account>() {
                    @Override
                    public void accept(Account value) {
                        value.status = "ACTIVE";
                    }
                }),
                FixtureTrait.of(new java.util.function.Consumer<Account>() {
                    @Override
                    public void accept(Account value) {
                        value.limit = 99;
                    }
                })
            )
        );

        Account materialized = new FixtureDraft<Account>(spec).materialize(new java.util.function.Supplier<Account>() {
            @Override
            public Account get() {
                return new Account();
            }
        }, TraitContext.EMPTY);

        assertThat(materialized.status).isEqualTo("ACTIVE");
        assertThat(materialized.limit).isEqualTo(99);
    }

    private static final class Account {
        private String status;
        private int limit;
    }
}
