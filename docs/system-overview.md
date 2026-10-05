# System Overview

Status as of 2026-10-02. This describes the system as it actually exists in the
repository today, not the target end-state — see `CLAUDE.md` for the intended
architecture and `docs/adr/` for the reasoning behind specific decisions.

## The three applications

| Application | Directory | State |
|---|---|---|
| PetClinic REST API | `spring-petclinic-rest` | Fully implemented |
| PetClinic AI Agent | `petclinic-ai-agent` | Empty Spring Boot scaffold, no endpoints or AI logic yet |
| PetClinic UI | `petclinic-ui` | Empty Vite/React scaffold, renders a static heading, no API calls yet |

Only `spring-petclinic-rest` does any real work today. The other two exist as
buildable skeletons so the monorepo layout and tooling are in place before
functionality is added (see [ADR-0001](adr/0001-use-a-monorepo.md)).

Per `CLAUDE.md`, the intended call directions once the other two are built out are:

```
petclinic-ui ──▶ spring-petclinic-rest
petclinic-ui ──▶ petclinic-ai-agent
petclinic-ai-agent ──▶ spring-petclinic-rest   (REST only — never the database)
```

Nothing currently violates this because `petclinic-ai-agent` and `petclinic-ui`
don't call anything yet. See [ADR-0002](adr/0002-separate-ai-agent-from-ui-and-rest-api.md)
for why the agent is a separate deployable.

## PetClinic REST API (`spring-petclinic-rest`)

Spring Boot 4.1.1, Java 25, Maven. Runs on port `9966` under context path
`/petclinic/`. Maven artifact id is `petclinic-api`; the directory itself was never
renamed and is still `spring-petclinic-rest`.

### Domain model

JPA entities under `model/`:

- **Owner** (extends `Person`) — has many **Pet**s (`cascade = ALL`, eager fetch)
- **Pet** (extends `NamedEntity`) — belongs to one **Owner**, has one **PetType**
  (`cascade = ALL`), has many **Visit**s (`cascade = ALL`, eager fetch)
- **Visit** — belongs to one **Pet**
- **PetType** — simple named lookup entity
- **Vet** (extends `Person`) — many-to-many with **Specialty**, eager fetch
- **Specialty** — simple named lookup entity
- **User** — has many **Role**s (`cascade = ALL`, eager fetch); backs HTTP Basic
  authentication when security is enabled

### Layering

```
Controllers (v1, v2) ──▶ Services ──▶ Repositories ──▶ JPA Entities
```

Each aggregate has its own service — `OwnerService`, `PetService`, `VisitService`,
`VetService`, `PetTypeService`, `SpecialtyService`, plus `UserService` for
authentication data. `PetService` depends on `PetTypeService` to resolve a pet's
type on save; that's the only service-to-service dependency. Controllers depend
only on the services they actually need (see the commit that split the former
`ClinicService` god object).

Repositories are Spring Data JPA interfaces (`Repository<T, Integer>`), with
hand-written overrides (`PetRepositoryImpl`, `VisitRepositoryImpl`, etc.) for
operations Spring Data can't generate — notably cascading pet/visit deletes via
raw JPQL.

### API surface

- **v1** (`rest/controller/v1`): full CRUD for owners, pets, pet types,
  specialties, vets, visits, and user creation. Response/request DTOs and the
  controller interfaces (`OwnersApi`, `PetsApi`, etc.) are generated from
  `src/main/resources/openapi.yml` via the OpenAPI Generator Maven plugin —
  controllers implement the generated interfaces rather than defining
  `@RequestMapping`s from scratch.
- **v2** (`rest/controller/v2`): currently just paginated listing —
  `GET /api/v2/owners` and `GET /api/v2/pets` — returning `Page<T>` wrapped in a
  page DTO. Not a full parallel CRUD surface.
- **Root** (`RootRestControllerV1`): `GET /` redirects to
  `/petclinic/swagger-ui/index.html`.

Entities are converted to/from DTOs by MapStruct-generated mappers
(`OwnerMapper`, `PetMapper`, etc. in `mapper/`).

### Authorization

Method-level `@PreAuthorize` annotations are already present on every v1
controller method (e.g. `hasRole(@roles.OWNER_ADMIN)`, roles defined in
`security/Roles.java`: `ROLE_OWNER_ADMIN`, `ROLE_VET_ADMIN`, `ROLE_ADMIN`), but
enforcement is **off by default** — `petclinic.security.enable=false` in
`application.properties` activates `DisableSecurityConfig`, which permits all
requests unauthenticated. Setting that property to `true` switches to
`BasicAuthenticationConfig`: HTTP Basic auth backed by `users`/`roles` tables
queried directly via JDBC (not through the JPA `User`/`Role` entities).

### Persistence

Spring Data JPA throughout. The active database is chosen by Spring profile
(`spring.profiles.active`, default `h2`); profile-specific config and
schema/seed SQL exist for `h2`, `hsqldb`, `mysql`, and `postgres`
(`src/main/resources/application-<profile>.properties`,
`src/main/resources/db/<profile>/`). In the default `h2` profile, schema and seed
data reload on every restart (`spring.sql.init.mode=always`) — convenient for
local dev, not representative of a persistent environment.

### Cross-cutting concerns

- **Error handling**: `ExceptionControllerAdvice` is a global `@ControllerAdvice`
  that maps validation errors, data integrity violations, and uncaught exceptions
  to RFC 7807 `ProblemDetail` responses.
- **Validation**: bean validation annotations on entities/DTOs, plus a custom
  `@PetAgeValidation` constraint.
- **Observability**: Spring Boot Actuator is on the classpath; `CallMonitoringAspect`
  is a JMX-exposed AOP aspect that counts and times calls into `@Repository` beans
  (`petclinic:type=CallMonitor`).
- **API docs**: springdoc-openapi serves interactive Swagger UI from the running
  app (reachable via the root redirect above).

### Build & packaging

Standard Maven build (`./mvnw`). A `jib-maven-plugin` target
(`mvn compile jib:dockerBuild`) builds a container image locally
(`<docker.image.prefix>/spring-petclinic-rest`); no image is currently published to
a registry.

## PetClinic AI Agent (`petclinic-ai-agent`)

Spring Boot 4.1.1 skeleton with `spring-boot-starter-web` and
`spring-boot-starter-actuator` only — no AI/LLM SDK dependency, no REST client
for calling `spring-petclinic-rest`, no endpoints beyond the default Spring Boot
application class. Exists to establish the module and its place in the monorepo
ahead of implementation.

## PetClinic UI (`petclinic-ui`)

Vite + React 19 + TypeScript skeleton. `App.tsx` renders a static "PetClinic"
heading; there is no routing, no API client, and no calls to either
`spring-petclinic-rest` or `petclinic-ai-agent` yet.

## Infrastructure

`CLAUDE.md` names the target AWS stack (ECS/Fargate, RDS, CloudFront/S3, Secrets
Manager, Parameter Store, CloudWatch), but no infrastructure-as-code exists yet —
the `infrastructure/` directory at the repo root is a placeholder for this.
Today, the only way to run any of the three applications is locally
(`./mvnw spring-boot:run` for the two Spring Boot apps, `npm run dev` for the UI).

## Known coupling / design notes

A few things worth knowing before building on top of this:

- `Owner`/`Pet`/`Visit` are bidirectionally linked with `CascadeType.ALL` and
  eager fetching in both directions — loading an `Owner` always pulls its full
  pet/visit graph, and deleting one cascades through the others automatically.
- `PetRepositoryImpl` hand-writes JPQL for cascading pet deletes, duplicating
  what the entity-level cascade is already supposed to do.
- `OwnerRestControllerV1`/`PetRestControllerV1` (v1) and `OwnerRestControllerV2`/
  `PetRestControllerV2` (v2) share the same `OwnerService`/`PetService`
  instances — there's no seam between API versions at the service layer.
