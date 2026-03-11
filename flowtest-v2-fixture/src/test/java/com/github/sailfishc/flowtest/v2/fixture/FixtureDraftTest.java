package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.TraitContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureDraftTest {

    @Test
    void materializeAppliesAllTraits() {
        FixtureSpec<Account> spec = new FixtureSpec<Account>(
            FixtureHandle.named(Account.class, "account"),
            Account.class,
            Arrays.<FixtureTrait<? super Account>>asList(
                FixtureTrait.mutate(new java.util.function.Consumer<Account>() {
                    @Override
                    public void accept(Account value) {
                        value.status = "ACTIVE";
                    }
                }),
                FixtureTrait.mutate(new java.util.function.Consumer<Account>() {
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

    @Test
    void materializeResolvesNamedFixtureByAlias() {
        FixtureSpec<Account> account = new FixtureSpec<Account>(
            FixtureHandle.named(Account.class, "account"),
            Account.class,
            Arrays.<FixtureTrait<? super Account>>asList(
                FixtureTrait.mutate(value -> value.limit = 99),
                FixtureTrait.mutate(value -> value.status = "ACTIVE")
            )
        );
        FixtureSpec<AccountAudit> audit = new FixtureSpec<AccountAudit>(
            FixtureHandle.named(AccountAudit.class, "audit"),
            AccountAudit.class,
            Arrays.<FixtureTrait<? super AccountAudit>>asList(
                new FixtureTrait<AccountAudit>() {
                    @Override
                    public void apply(AccountAudit target, TraitContext context) {
                        Account resolved = context.fixture("account", Account.class);
                        target.limitSnapshot = resolved.limit;
                        target.statusSnapshot = resolved.status;
                    }
                }
            )
        );

        Map<FixtureHandle<?>, Object> resolved = new FixtureMaterializer().materialize(Arrays.<FixtureSpec<?>>asList(account, audit));
        AccountAudit materialized = (AccountAudit) resolved.get(audit.getHandle());

        assertThat(materialized.limitSnapshot).isEqualTo(99);
        assertThat(materialized.statusSnapshot).isEqualTo("ACTIVE");
    }

    private static final class Account {
        private String status;
        private int limit;
    }

    private static final class AccountAudit {
        private String statusSnapshot;
        private int limitSnapshot;
    }
}
