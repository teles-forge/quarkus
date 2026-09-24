package io.quarkus.hibernate.orm.auditing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.orm.audit.CreatedBy;
import io.quarkus.hibernate.orm.audit.CurrentAuditorProvider;
import io.quarkus.hibernate.orm.audit.LastModifiedBy;
import io.quarkus.test.QuarkusExtensionTest;

public class AuditingTest {

    private static final String AUDITOR_PROPERTY = "quarkus.hibernate-orm.test.current-auditor";

    @RegisterExtension
    static QuarkusExtensionTest runner = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(AuditedEntity.class, TestCurrentAuditorProvider.class))
            .withConfigurationResource("application.properties");

    @Inject
    EntityManager entityManager;

    @Inject
    UserTransaction transaction;

    @AfterEach
    void clearAuditor() {
        System.clearProperty(AUDITOR_PROPERTY);
    }

    @Test
    void shouldSetCreatedAndLastModifiedAuditor() throws Exception {
        System.setProperty(AUDITOR_PROPERTY, "alice");

        AuditedEntity entity = new AuditedEntity();
        entity.name = "first";

        transaction.begin();
        entityManager.persist(entity);
        transaction.commit();

        assertThat(entity.createdBy).isEqualTo("alice");
        assertThat(entity.lastModifiedBy).isEqualTo("alice");

        System.setProperty(AUDITOR_PROPERTY, "bob");

        transaction.begin();
        AuditedEntity managed = entityManager.find(AuditedEntity.class, entity.id);
        managed.name = "second";
        transaction.commit();

        transaction.begin();
        AuditedEntity reloaded = entityManager.find(AuditedEntity.class, entity.id);
        assertThat(reloaded.createdBy).isEqualTo("alice");
        assertThat(reloaded.lastModifiedBy).isEqualTo("bob");
        transaction.rollback();
    }

    @Test
    void shouldKeepCurrentValueWhenProviderHasNoAuditor() throws Exception {
        AuditedEntity entity = new AuditedEntity();
        entity.name = "first";
        entity.createdBy = "manual-created";
        entity.lastModifiedBy = "manual-modified";

        transaction.begin();
        entityManager.persist(entity);
        transaction.commit();

        assertThat(entity.createdBy).isEqualTo("manual-created");
        assertThat(entity.lastModifiedBy).isEqualTo("manual-modified");
    }

    @ApplicationScoped
    public static class TestCurrentAuditorProvider implements CurrentAuditorProvider {

        @Override
        public String currentAuditor() {
            return System.getProperty(AUDITOR_PROPERTY);
        }
    }

    @Entity
    public static class AuditedEntity {

        @Id
        @GeneratedValue
        public Long id;

        public String name;

        @CreatedBy
        public String createdBy;

        @LastModifiedBy
        public String lastModifiedBy;
    }
}
