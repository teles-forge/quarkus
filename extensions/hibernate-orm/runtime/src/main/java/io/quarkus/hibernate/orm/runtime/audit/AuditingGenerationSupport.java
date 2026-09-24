package io.quarkus.hibernate.orm.runtime.audit;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.hibernate.orm.audit.CurrentAuditorProvider;

final class AuditingGenerationSupport {

    private AuditingGenerationSupport() {
    }

    static Object currentAuditorOrCurrentValue(Object currentValue) {
        InjectableInstance<CurrentAuditorProvider> providers = Arc.container().select(CurrentAuditorProvider.class);
        if (providers.isUnsatisfied()) {
            return currentValue;
        }
        if (providers.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple CurrentAuditorProvider beans were found. At most one provider can be active.");
        }
        try (InstanceHandle<CurrentAuditorProvider> handle = providers.getHandle()) {
            String currentAuditor = handle.get().currentAuditor();
            return currentAuditor != null ? currentAuditor : currentValue;
        }
    }
}
