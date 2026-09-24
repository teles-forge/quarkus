package io.quarkus.hibernate.orm.runtime.audit;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.hibernate.orm.audit.CurrentAuditorProvider;

final class AuditingGenerationSupport {

    private AuditingGenerationSupport() {
    }

    static Object currentAuditorOrCurrentValue(Object currentValue) {
        InstanceHandle<CurrentAuditorProvider> provider = Arc.container().instance(CurrentAuditorProvider.class);
        if (!provider.isAvailable()) {
            return currentValue;
        }
        String currentAuditor = provider.get().currentAuditor();
        return currentAuditor != null ? currentAuditor : currentValue;
    }
}
