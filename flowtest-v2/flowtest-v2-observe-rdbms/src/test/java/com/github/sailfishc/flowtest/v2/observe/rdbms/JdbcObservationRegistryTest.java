package com.github.sailfishc.flowtest.v2.observe.rdbms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcObservationRegistryTest {

    @Test
    void shouldRegisterAnnotatedEntityMapping() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry().registerEntity(AnnotatedUser.class);

        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(AnnotatedUser.class);
        assertThat(registration).isNotNull();
        assertThat(registration.getIdentity().getTableName()).isEqualTo("ft_user");
        assertThat(registration.getIdentity().getKeyColumns()).containsExactly("id");
        assertThat(registration.getPropertyColumns()).containsEntry("displayName", "display_name");
        assertThat(registration.getIgnoredProperties()).contains("transientFlag");
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class AnnotatedUser {

        private Long id;

        @JdbcColumn("display_name")
        private String displayName;

        @JdbcIgnore
        private String transientFlag;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTransientFlag() {
            return transientFlag;
        }

        public void setTransientFlag(String transientFlag) {
            this.transientFlag = transientFlag;
        }
    }
}
