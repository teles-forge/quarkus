package io.quarkus.hibernate.orm.runtime.audit;

import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

public final class CreatedByGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        return AuditingGenerationSupport.currentAuditorOrCurrentValue(currentValue);
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public Class<?> getGeneratedType() {
        return String.class;
    }
}
