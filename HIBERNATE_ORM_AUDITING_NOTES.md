# Hibernate ORM auditing notes

This branch is a checkpoint for the auditing discussion in quarkusio/quarkus#54724.

The current prototype is in `TelesNascimento/quarkus-panache-audit`.

It started as a Panache extension, but the actual implementation is already based on Hibernate ORM value generation and works with regular JPA entities too.

Do not treat the API below as decided. The maintainers are still discussing where this should live and how the current auditor should be resolved.

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
