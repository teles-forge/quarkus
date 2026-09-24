# Hibernate ORM auditing notes

This branch is just a checkpoint for the possible move of the auditing work into Quarkus core.

Related issue: quarkusio/quarkus#54724

The current prototype lives in `TelesNascimento/quarkus-panache-audit`. It already works at the Hibernate ORM level and does not depend on Panache for the actual value generation.

The direction being discussed with the maintainers is to move the feature into `quarkus-hibernate-orm` so regular JPA entities, Panache and Quarkus Data can all get the same behavior from ORM.

Do not treat the API below as final. We are still waiting for maintainer feedback.

## What already works

The current prototype has:

- `@CreatedBy`
- `@LastModifiedBy`
- Hibernate `BeforeExecutionGenerator` based value generation
- insert only generation for `@CreatedBy`
- insert and update generation for `@LastModifiedBy`
- plain JPA entity coverage
- Panache coverage
- a CDI abstraction called `AuditUserProvider`

The part that should not be moved as-is is the default implementation tied directly to `SecurityIdentity`.

## Current direction

The ORM side should stay generic.

A likely shape is:

`Hibernate generator -> CDI auditor resolver -> application provided implementation`

The important part is that `quarkus-hibernate-orm` should not need to depend directly on Quarkus Security just to resolve the current user.

`SecurityIdentity` can still be used by an application provided resolver, but it should not define the core auditing contract.

There is already a similar pattern in the Hibernate ORM extension for tenant resolution:

`Hibernate CurrentTenantIdentifierResolver -> Quarkus TenantResolver -> CDI bean`

That is worth using as a reference before introducing a new pattern.


## What Hibernate ORM already has

Hibernate ORM does not currently provide public `@CreatedBy` or `@LastModifiedBy` annotations.

What it does provide is the lower level value generation mechanism needed to build them.

There is a test in Hibernate ORM called `GeneratorTypeTest` that defines its own test-only `@CurrentUserGeneration` annotation and a `LoggedUserGenerator`. It uses `BeforeExecutionGenerator` to write one user on insert and another on update.

That class is not a reusable auditing feature. The annotation, current-user holder and generator all live inside the test. Searches of the Hibernate ORM source currently show no public `@CreatedBy`, `@LastModifiedBy` or `AuditorAware` equivalent.

The test is still important because it proves the generator mechanism is a supported way to implement this behavior.

Useful upstream reference:

`hibernate-core/src/test/java/org/hibernate/orm/test/mapping/generated/GeneratorTypeTest.java`

The test has existed for years in different forms. It was moved from the documentation test sources into `hibernate-core` in 2023 and migrated from the old `@GeneratorType` API to `@ValueGenerationType` and `BeforeExecutionGenerator` in 2024.

There is also an earlier Quarkus discussion in issue #53104 that matters here. Yoann pointed out that timestamps already exist in Hibernate ORM, while `CreatedBy` and `LastModifiedBy` still need a concept of the current user. He also said that if this feature lives in the Hibernate ORM extension, one possible direction would be to contribute the annotations and a current-user SPI upstream to Hibernate ORM. FroMage then suggested asking upstream ORM whether they want the feature.

As of this checkpoint there is no matching Hibernate ORM issue or public built-in feature for `CreatedBy` or `LastModifiedBy`.

This means the open design question is not whether Hibernate can generate the values. It can. The real question is where the reusable annotations and current-auditor contract should live.

## First implementation scope

Keep the first version small.

Move only the ORM specific auditing behavior:

- auditing annotations
- generators
- a minimal CDI contract for resolving the current auditor
- validation for the required resolver
- tests with regular Hibernate ORM entities

Avoid adding Security integration to the first version unless the maintainers explicitly ask for it.

Avoid adding Hibernate Reactive support in the same change. The current generator implementation is for Hibernate ORM and reactive should be discussed separately.

Avoid supporting multiple auditor types, multiple persistence unit specific resolvers or other extra API until there is a concrete requirement.

## Behavior to decide

The current prototype falls back to values such as `system`, `anonymous` and `unknown`.

That behavior probably should not move into core.

A safer core behavior would be to require an auditor resolver when `@CreatedBy` or `@LastModifiedBy` is used and fail with a clear message when none is available.

Do not implement this assumption blindly. Check the maintainer reply first.

## Likely Quarkus area

Start by looking under:

`extensions/hibernate-orm/runtime`

and:

`extensions/hibernate-orm/deployment`

Useful existing code to study:

- `HibernateCurrentTenantIdentifierResolver`
- `TenantResolver`
- `HibernateOrmCdiProcessor`
- `PersistenceUnitExtension`
- existing Hibernate ORM CDI integration tests

The new API name and package should stay open until the maintainers confirm the direction.

## Tests we will probably need

At minimum:

- plain `@Entity` with `@CreatedBy`
- plain `@Entity` with `@LastModifiedBy`
- insert sets created by
- insert sets last modified by
- update changes last modified by
- custom CDI auditor resolver is used
- missing resolver has defined behavior
- no dependency on Panache for the feature to work

A small Panache or Quarkus Data test can be added later if useful to prove the feature is inherited through ORM, but the core tests should exercise regular Hibernate ORM first.

## Before writing the real PR

Wait for feedback on the issue, especially from Luca Molteni and Yoann Rodiere.

If they agree with the CDI abstraction direction, build the smallest prototype that follows existing Hibernate ORM extension patterns.

If they suggest an existing SPI or a different integration point, use that instead of creating another abstraction.

The goal is not to preserve the current extension structure. The goal is to keep the useful Hibernate ORM part and fit it into Quarkus in the way the maintainers expect.
