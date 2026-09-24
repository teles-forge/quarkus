package io.quarkus.hibernate.orm.auditing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.orm.audit.CreatedBy;
import io.quarkus.hibernate.orm.audit.LastModifiedBy;
import io.quarkus.test.QuarkusExtensionTest;

public class AuditingWithoutProviderTest {

    @RegisterExtension
    static QuarkusExtensionTest runner = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar.addClass(AuditedEntity.class))
            .withConfigurationResource("application.properties");

    @Inject
    EntityManager entityManager;

    @Inject
    UserTransaction transaction;

    @Test
    void shouldLeaveAuditingValuesUnchangedWithoutProvider() throws Exception {
        AuditedEntity entity = new AuditedEntity();
        entity.createdBy = "manual-created";
        entity.lastModifiedBy = "manual-modified";

        transaction.begin();
        entityManager.persist(entity);
        transaction.commit();

        assertThat(entity.createdBy).isEqualTo("manual-created");
        assertThat(entity.lastModifiedBy).isEqualTo("manual-modified");
    }

    @Entity
    public static class AuditedEntity {

        @Id
        @GeneratedValue
        public Long id;

        @CreatedBy
        public String createdBy;

        @LastModifiedBy
        public String lastModifiedBy;
    }
}
