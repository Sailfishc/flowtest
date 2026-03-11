package com.github.sailfishc.flowtest.v2.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureTraitTest {

    @Test
    void composeAppliesTraitsInDeclarationOrder() {
        Account account = new Account();

        FixtureTrait<Account> vip = FixtureTrait.mutate(new java.util.function.Consumer<Account>() {
            @Override
            public void accept(Account value) {
                value.status = "VIP";
                value.limit = 100;
            }
        });
        FixtureTrait<Account> overrideLimit = FixtureTrait.mutate(new java.util.function.Consumer<Account>() {
            @Override
            public void accept(Account value) {
                value.limit = 250;
            }
        });

        FixtureTrait.compose(vip, overrideLimit).apply(account, TraitContext.EMPTY);

        assertThat(account.status).isEqualTo("VIP");
        assertThat(account.limit).isEqualTo(250);
    }

    private static final class Account {
        private String status;
        private int limit;
    }
}
