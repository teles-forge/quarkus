package io.quarkus.hibernate.orm.audit;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.annotations.ValueGenerationType;

import io.quarkus.hibernate.orm.runtime.audit.LastModifiedByGenerator;

/**
 * Specifies that the annotated field or property should contain the auditor that last modified the entity.
 * <p>
 * The value is generated when the entity is inserted and whenever it is updated. The current auditor is provided by
 * a CDI {@link CurrentAuditorProvider}. If no provider is available, or if the provider cannot determine an auditor,
 * the current property value is left unchanged.
 */
@ValueGenerationType(generatedBy = LastModifiedByGenerator.class)
@Target({ FIELD, METHOD })
@Retention(RUNTIME)
@Documented
public @interface LastModifiedBy {
}
