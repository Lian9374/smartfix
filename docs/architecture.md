# SmartFix Architecture Overview

This document explains the architectural foundation of the SmartFix repository.
It is aimed at the five team members and is kept intentionally concise and practical.

> Scope note: this describes the *engineering baseline*. Business functionality is
> implemented incrementally through Agile sprints after analysis and design, and may
> refine this architecture as the team learns.

---

## 1. Architectural style

SmartFix uses a **Modular Monolith** combined with a **Layered Architecture**.

- One Spring Boot application, one deployable unit, one primary relational database.
- Microservices, Kubernetes and distributed messaging are **intentionally not used**
  (see section 7 and ADR-001).
- The code is organised **primarily by business module**, and only secondarily by layer.

### Module-first packaging

Planned module boundaries under `com.smartfix`:

```
com.smartfix
├── common          # shared, module-independent building blocks (e.g. home page)
├── auth            # authentication & authorization
├── user            # users, roles, accounts
├── request         # maintenance requests, categories, feedback
├── workorder       # work orders, repair tracking, evidence
├── dispatch        # technician matching & assignment
├── sla             # SLA policies, deadlines, escalation
├── facility        # facilities, campus map, public status
├── notification    # in-app / email notifications
├── reporting       # operational reporting
├── announcement    # maintenance announcements
└── audit           # audit history
```

Only packages that actually contain code exist in the repository today (`common`,
`auth`, `user`). Empty directories are **not** created just to look complete; the
layout above is the *target* structure that stories will grow into.

### Layers within a module

Where a module needs the full stack, code is grouped into conventional layers:

```
<module>/
    controller/   # HTTP + view handling (thin)
    service/      # application / business coordination
    domain/       # business concepts (entities, enums, domain logic)
    repository/   # persistence
    dto/          # data transfer objects across boundaries
```

Not every layer must exist in every module. Add a layer only when real code needs it.

## 2. Dependency direction

The conceptual dependency flow is strictly one way:

```
Presentation (Controller / View)
        ↓
Application / Business Service
        ↓
Domain
        ↓
Repository / Persistence
```

Rules that keep coupling low and cohesion high:

- **Controllers are thin.** They translate HTTP to service calls and never contain
  business logic or touch the database directly.
- **Views contain no business logic.** Thymeleaf templates only render data.
- **Services coordinate** application and business behaviour.
- **Domain objects** carry business concepts and behaviour.
- **Repositories are persistence-oriented.**
- **DTOs** are used across presentation/application boundaries rather than exposing
  persistence entities directly (to be introduced from the first real story onward).

## 3. Relationship to ECB analysis

The module's analysis models map onto implementation concepts **conceptually** —
analysis names are not copied into package or class names.

| Analysis (ECB)      | Implementation                                |
|---------------------|-----------------------------------------------|
| Boundary            | Spring MVC Controller / View                  |
| Control             | Application / Business Service                |
| Entity              | Domain Entity                                 |
| Persistence         | Repository                                    |

There is deliberately **no** `boundary`/`control` package. ECB is a way of thinking;
implementation uses conventional Java architecture.

## 4. Separation of concerns

- **Business modules** separate concerns by *feature area* (each module owns its
  workflow concepts), which keeps related changes local and makes future design
  decisions (e.g. SLA rules, matching rules) testable in isolation.
- **Layers** separate concerns by *responsibility* (web, application, domain,
  persistence), enabling dependency inversion and easy mocking in tests.

## 5. Why microservices are intentionally NOT used

- The team is small (five members) and the domain is a single, cohesive workflow.
- Modular monolith boundaries provide the same development-time isolation without
  the operational cost of distributed systems (service discovery, networking,
  data consistency, deployment, observability).
- The proposal and module syllabus do not require distributed architecture.
- The team can keep focus on analysis & design, design patterns, testing and
  DevSecOps rather than infrastructure.

If a future sprint reveals a genuinely independent scaling or team boundary, a
module can be extracted to a service. The module-first layout keeps that option open
**without** paying the distributed cost today.

## 6. How this structure supports the project goals

- **Maintainability:** small modules + thin controllers + clear dependency direction
  make each change easy to reason about and review.
- **Extensibility:** new stories add code inside an existing module (or create a new
  module) following the same conventions. Future additions (Strategy for technician
  matching, SLA engine, notifications, campus map) have a clear home but are not
  pre-built.
- **Testability:** services can be unit-tested with Mockito in isolation; web layers
  with `@WebMvcTest`/MockMvc; repositories with an isolated test database. The H2
  test profile means `mvn test` needs no external database.
- **DevSecOps friendliness:** a single deployable JAR keeps the pipeline simple, and
  module boundaries give future static-analysis results a meaningful structure.

## 7. Functional module diagram vs implementation architecture

The proposal describes *functional* areas (request management, technician dispatch,
SLA monitoring, ...). Those areas are a **requirements view**, not a code structure.

This repository maps them to **implementation modules**. A functional area may map to
one module (`sla`) or be spread across several (`request` + `workorder` +
`dispatch`). The mapping will be confirmed during use-case and class design rather
than assumed now.

## 8. Current scaffold boundaries

Only the engineering baseline exists: application entry point, temporary security
baseline, minimal home page, configuration, database/Flyway infrastructure and
infrastructure tests. See `docs/decisions/ADR-001-architecture-baseline.md` and the
README for the explicit list of what is intentionally not yet implemented.
