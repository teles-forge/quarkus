package io.quarkus.hibernate.orm.audit;

/**
 * Provides the current auditor used by {@link CreatedBy} and {@link LastModifiedBy}.
 * <p>
 * Implementations must be CDI beans. The provider is resolved when Hibernate ORM generates an auditing value,
 * so implementations may use request-scoped state.
 */
public interface CurrentAuditorProvider {

    /**
     * @return the current auditor, or {@code null} if no auditor can be determined
     */
    String currentAuditor();

}
