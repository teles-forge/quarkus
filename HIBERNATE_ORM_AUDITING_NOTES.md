# Hibernate ORM auditing notes

This branch is a checkpoint for the auditing discussion in quarkusio/quarkus#54724.

The current prototype is in `TelesNascimento/quarkus-panache-audit`.

It started as a Panache extension, but the actual implementation is already based on Hibernate ORM value generation and works with regular JPA entities too.

Do not treat the API below as decided. The maintainers are still discussing where this should live and how the current auditor should be resolved.

## Prototype now on this branch

There is now a working-shape Quarkus-first prototype on this branch.

The current API names are only placeholders until the maintainers confirm them:

- `io.quarkus.hibernate.orm.audit.CreatedBy`
- `io.quarkus.hibernate.orm.audit.LastModifiedBy`
- `io.quarkus.hibernate.orm.audit.CurrentAuditorProvider`

The generators live under `io.quarkus.hibernate.orm.runtime.audit`.

The prototype follows the same runtime lookup style already used in Quarkus for context-dependent providers:

- the provider is a CDI bean
- it is looked up when the generated value is needed
- provider beans are marked unremovable because the lookup is programmatic
- no `SecurityIdentity` dependency is added to Hibernate ORM
- no provider means the current property value is kept
- a provider returning `null` also keeps the current property value
- multiple providers are treated as a configuration error
- auditing fields are currently limited to `String`
- `@CreatedBy` runs on insert
- `@LastModifiedBy` runs on insert and update

The deployment tests cover a CDI provider, insert/update behavior, preserving values when no auditor is available, and operation without any provider bean.

This is a prototype for review, not a claim that these API choices are final.

## What is already clear

The timestamp side is not part of this work.

Hibernate ORM already has `@CreationTimestamp` and `@UpdateTimestamp`.

The missing part is the actor side:

- `@CreatedBy`
- `@LastModifiedBy`

The current prototype uses:

- `@ValueGenerationType`
- `BeforeExecutionGenerator`
- `INSERT_ONLY` for created by
- `INSERT_AND_UPDATE` for last modified by
- a CDI-facing `AuditUserProvider`
- `SecurityIdentity` in the default provider

The generator approach is valid Hibernate ORM usage.

Hibernate ORM itself has a test called `GeneratorTypeTest` that defines a test-only current-user annotation and a `BeforeExecutionGenerator` that writes one user on insert and another on update.

That is not a built-in auditing feature. Hibernate ORM currently has no public `@CreatedBy`, `@LastModifiedBy` or `AuditorAware` equivalent.

Useful reference:

`hibernate-core/src/test/java/org/hibernate/orm/test/mapping/generated/GeneratorTypeTest.java`

## Earlier discussion that matters

There was already a Quarkus discussion about this in #53104 in March 2026.

Yoann pointed out that `CreatedDate` and `LastModifiedDate` already exist in Hibernate ORM, while `CreatedBy` and `LastModifiedBy` need a concept of current user that Hibernate ORM does not have.

FroMage then suggested that the feature could live in the Quarkus Hibernate ORM extension instead of Panache.

Yoann said that if it goes through the Hibernate ORM extension, one possible direction would be to contribute the annotations to Hibernate ORM itself and add an SPI for current-user retrieval.

He also noted that Hibernate ORM contributors might decide that the concept does not belong upstream.

FroMage suggested asking Hibernate ORM upstream first.

No built-in Hibernate ORM implementation or matching open issue was found at this checkpoint.

## Current September discussion

The current issue changed the framing because the prototype is no longer Panache-specific.

FroMage asked whether the feature should be added to `quarkus-hibernate-orm` proper.

Luca's first concern was still the current-user problem.

The reply on the issue proposed keeping the ORM side generic and resolving the current auditor through a small CDI abstraction, with `SecurityIdentity` being only one possible source.

That direction is consistent with the earlier discussion in #53104, but it is not approved yet.

We are still waiting for Luca and Yoann to confirm the direction.

## What is not decided yet

Do not lock these down before maintainer feedback:

- whether the annotations live in Quarkus or Hibernate ORM upstream
- the name of the current-auditor contract
- whether the auditor type is only `String` or generic
- whether the resolver is global or persistence-unit specific
- what happens when no resolver is available
- whether Quarkus Security provides a default integration
- package names
- Hibernate Reactive support

## Important CDI constraint

Do not assume the Hibernate generator itself can simply use constructor or field injection.

Hibernate ORM can instantiate generators through its `BeanContainer`, but Quarkus currently sets:

`hibernate.allow_extensions_in_cdi = false`

during metadata building.

Hibernate's `GeneratorBinder` only gets a `BeanContainer` for generator instantiation when that setting is enabled.

So a design like this should not be assumed to work in Quarkus:

`CreatedByGenerator -> @Inject CurrentAuditorResolver`

The current prototype avoids that by resolving the provider at runtime through ArC inside `generate()`.

That may still be a reasonable Quarkus-specific bridge, but it should be validated with the maintainers before making it the final design.

There is already a similar runtime bridge in the Hibernate ORM extension for tenant resolution:

`Hibernate CurrentTenantIdentifierResolver -> Quarkus TenantResolver -> CDI bean`

Useful Quarkus references:

- `HibernateCurrentTenantIdentifierResolver`
- `TenantResolver`
- `HibernateOrmCdiProcessor`
- `PersistenceUnitExtension`
- `FastBootMetadataBuilder`
- `QuarkusArcBeanContainer`

## Most likely implementation paths

### Path 1: keep the feature in Quarkus

If the maintainers confirm `quarkus-hibernate-orm` as the home, the first implementation should stay small:

- add the auditing annotations
- keep the Hibernate value generators
- add a small current-auditor contract
- resolve that contract through Quarkus CDI at runtime
- keep `quarkus-hibernate-orm` independent from `SecurityIdentity`
- cover regular JPA entities first

Security integration can be added separately if they want it.

### Path 2: split the work with Hibernate ORM upstream

If Yoann brings back the March direction, the work may need to be split:

Hibernate ORM:

- public auditing annotations
- current-user SPI
- generator integration

Quarkus:

- CDI integration for the SPI
- optional integration with Quarkus Security
- Quarkus-specific tests

This path would take longer because it depends on the Hibernate ORM release cycle.

Do not start the upstream split unless the maintainers explicitly choose it.


## What "current auditor" means

The current auditor should not be defined by Hibernate ORM itself.

For a normal secured Quarkus HTTP request, the most common source would probably be the authenticated principal from `SecurityIdentity`, for example the principal name.

But that is only one source.

An application may want the auditor to be:

- a username
- an employee or account id
- a service account
- a system user for scheduled jobs
- a value derived from tenant plus user
- something from another authentication mechanism

So the core contract should answer one question only:

`Who should be recorded as the actor for this persistence operation?`

The application or a Quarkus integration decides how to answer it.

For the first version, keep the contract small. If the maintainers accept a String-only API, a shape like this is enough conceptually:

`CurrentAuditorResolver -> String currentAuditor()`

Do not lock the exact name or return type until maintainer feedback.

A Quarkus Security adapter could later do:

`SecurityIdentity -> Principal -> name -> CurrentAuditorResolver`

That adapter should be separate from the ORM core contract.

## Remaining design problems besides finding the auditor

Resolving the current auditor is the main missing piece, but there are a few other behaviors that need an explicit answer before the API is final.

### No auditor available

Background jobs, startup code and unauthenticated operations may have no current user.

The core should not silently invent values like `unknown`, `system` or `anonymous` unless the maintainers explicitly want that behavior.

Possible policies are:

- fail when an auditing annotation is used and no auditor can be resolved
- allow no value and leave the field unchanged or null
- let the application provider decide its own fallback

This should be decided before the public API is fixed.

### Overwriting an existing value

For `@CreatedBy`, the expected behavior is probably to generate only on insert and not change the value later.

For `@LastModifiedBy`, the value should be generated on insert and update.

We should also decide whether manually assigned values are always overwritten on the relevant event or whether user-provided values can be preserved.

Hibernate's generator API has mutation semantics for this, so this should be tested explicitly.

### Auditor value type

The current prototype uses `String`.

A generic API could support UUIDs or domain-specific ids, but that adds API surface immediately.

Do not generalize this in the first implementation unless the maintainers ask for it.

### CDI scope and runtime context

The resolver must work when the actual persistence operation happens, not only when Hibernate metadata is built.

This matters because request-scoped security state may only exist at runtime.

The generator should therefore resolve the auditor at the time of `generate()`, or use another runtime-safe bridge chosen by the maintainers.

### Multiple persistence units

Do not assume a persistence-unit-specific auditor unless there is a real requirement.

One global resolver is simpler.

If the maintainers want different auditors per persistence unit, then `@PersistenceUnitExtension` or another qualifier-based mechanism may become relevant.

### Native image and build-time discovery

Any new annotation, generator or CDI contract must work with Quarkus build-time discovery and native image.

The Quarkus Hibernate ORM extension already scans `@ValueGenerationType` references through Jandex, which is a good sign, but the final implementation still needs a native/build-time-safe test path.

### Reactive

The current implementation uses synchronous Hibernate ORM `BeforeExecutionGenerator`.

Do not treat Hibernate Reactive as covered by this work.

If reactive support is wanted, it should be designed separately.

## Smallest useful first version

If the maintainers keep the feature in Quarkus, the smallest useful behavior is probably:

1. application provides one current-auditor resolver
2. `@CreatedBy` asks it on insert
3. `@LastModifiedBy` asks it on insert and update
4. the value is written before SQL execution
5. no direct dependency from Hibernate ORM integration to `SecurityIdentity`
6. behavior with no auditor is explicit and tested

That is enough to make the feature useful without deciding every possible application policy up front.

## Current prototype parts to keep

These are conceptually useful:

- `CreatedBy`
- `LastModifiedBy`
- `BeforeExecutionGenerator`
- event timing
- the separation between generator and user provider
- plain JPA coverage

## Current prototype parts not to copy blindly

The default `SecurityIdentity` implementation should not be moved into `quarkus-hibernate-orm` as-is.

The fallback values also need another look:

- `system`
- `anonymous`
- `unknown`

Those are application semantics, not obviously ORM semantics.

Do not decide the final missing-auditor behavior before the maintainer discussion is settled.

## Tests for a Quarkus-first prototype

If Path 1 is confirmed, start with regular Hibernate ORM tests:

- plain `@Entity`
- created by populated on insert
- last modified by populated on insert
- last modified by changed on update
- custom CDI resolver used at runtime
- behavior with no resolver explicitly tested
- no Panache dependency required

After that, add one small Panache or Quarkus Data test only if it helps prove the feature is inherited through ORM.

Do not make Panache the core test target.

## Before coding

Check the issue again first.

If Luca or Yoann confirm the CDI abstraction direction, implement the smallest prototype that follows existing Quarkus Hibernate ORM patterns.

If they point to an existing SPI or ask for an upstream Hibernate ORM change, change the plan before writing code.

The main open question is no longer whether Hibernate can generate the values. It can.

The open question is where the reusable annotations and current-auditor contract should live, and how Quarkus should bridge that contract to CDI.
