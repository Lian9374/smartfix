# ADR-001: Modular Monolith + Layered Architecture baseline

- **Status:** Accepted
- **Date:** 2026-09-06
- **Deciders:** SmartFix team (SWE5006)
- **Related:** `docs/architecture.md`, `README.md`

## Context

SmartFix is a campus facility maintenance and technician dispatch system being built
by a five-member team over ~10 weeks. The team needs an architecture that:

- supports a clean flow from user story → use case → ECB analysis → sequence diagram →
  class design → implementation → unit testing;
- keeps infrastructure manageable so the team can concentrate on analysis & design,
  design patterns, testing and DevSecOps;
- remains open to later extensions (authentication, SLA, technician matching,
  notifications, campus map, reporting) without premature implementation.

Options considered: modular monolith + layered architecture (chosen), microservices,
and a single-layer "everything in controllers" approach.

## Decision

Use a **modular monolith** (one Spring Boot application, one deployable unit, one
relational database) with a **layered architecture**, packaging Java source primarily
by **business module** (see `docs/architecture.md`). Each module uses layers
(controller / service / domain / repository / dto) only where required.

Microservices, Kubernetes, separate backend services and distributed messaging are
explicitly out of scope. The technology baseline is fixed as Java 21 + Spring Boot 3.x
+ Maven + Spring MVC + Thymeleaf + Spring Data JPA + Spring Security + PostgreSQL +
Flyway + JUnit 5/Mockito + Docker.

## Consequences

### Positive

- Simple local development and a simple CI pipeline.
- Module-first packaging isolates future design decisions (SLA, matching, lifecycle)
  into single places.
- Thin controllers + clear dependency direction keep the codebase reviewable and
  testable.
- A module can be extracted to a service later if a real boundary emerges, without
  paying distributed costs today.

### Negative / constraints

- We deliberately do **not** build distributed systems, so "scaling by service" is
  not available; acceptable for this domain and team size.
- Keeping layers disciplined (no business logic in controllers/views, no DB access
  from the web layer) requires consistent code-review discipline.
- Empty module directories are not created until real code needs them; the team must
  read the intended structure from the docs.

### Follow-ups

- Package layout to be re-validated at the first Sprint 2 story (authentication).
- The functional module diagram is a requirements view, not a code structure; the
  mapping is confirmed during use-case/class design.
