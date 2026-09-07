# SmartFix Testing Guide

Tests are how the team proves behaviour stays correct while five people change code in
parallel. This guide explains the test levels used in this project, naming, and what to
mock.

> The repository is at the **scaffold** stage: the only tests are infrastructure tests
> (application context loads; home page renders). Everything below describes the levels
> you will use from the first real story onward.

---

## 1. Test levels used in this project

### Unit tests (JUnit 5 + Mockito)
- Test **one class in isolation**: a service, a domain rule, a calculation.
- Dependencies (other services, repositories) are **mocked**.
- Fast, focused, no database, no HTTP server.
- Future targets: technician assignment logic, SLA rules, request state transitions and
  **invalid** transitions, authorization-sensitive behaviour.

### Controller / web tests (MockMvc)
- Test the web layer: HTTP mapping, input validation, the right view/response, security.
- Spring Boot slices or `@AutoConfigureMockMvc` with the test profile.

### Repository / integration tests
- Test real persistence behaviour (queries, constraints, mappings).
- Typically against the isolated test database (H2 in PostgreSQL mode today), and —
  when PostgreSQL-specific behaviour matters — a real PostgreSQL via Testcontainers
  (introduced deliberately later; not present yet — README §29).

### Security tests (from Sprint 2)
- Verify routes are reachable only by the correct roles (REQUESTER / TECHNICIAN /
  ADMINISTRATOR) and that unauthenticated access is rejected where intended.

---

## 2. Where tests live and how they are named

- Mirror the main tree under `src/test/java`. A test for
  `com.smartfix.request.service.RequestService` lives at
  `com.smartfix.request.service.RequestServiceTests` (same package).
- Name test classes `<ClassUnderTest>Tests` and test methods as a sentence of expected
  behaviour, e.g.:
  - `requestWorkflowCannotReopenAfterClosed()`
  - `technicianAssignmentPrefersAvailableWithinServiceArea()`
  - `slaFlagsRequestOverdueWhenDeadlinePassed()`
- Add a `@DisplayName("…")` in plain language for readability.

Example shape:

```java
package com.smartfix.request.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestServiceTests {

    @Test
    @DisplayName("submission without a location is rejected")
    void submissionWithoutLocationIsRejected() {
        // arrange
        // act
        // assert
    }
}
```

## 3. Arrange–Act–Assert

Keep each test readable with a clear three-part structure:

```java
// arrange
var input = new RequestSubmissionInput("library", "broken light");
// act
var result = service.submit(input);
// assert
assertThat(result).isNotNull();
```

- One behaviour per test.
- Assert on the **result/effect**, not on implementation internals.

## 4. What to mock / what not to mock

| Element | Approach | Why |
|---|---|---|
| Repositories in service tests | **Mock** (Mockito) | Isolate the service's logic |
| Other modules' services | **Mock** | Test the unit under test, not collaborators |
| Domain objects | Use real objects (construct them) | They carry the rules you are asserting |
| Web layer | MockMvc over the test profile | Fast controller tests |
| Database behaviour | Integration tests (real/embedded DB), not mocks | A mock tells you nothing about SQL correctness |

Rule of thumb: mock **boundaries** (repositories, external services, notification
delivery), use real **domain** objects, and test **persistence** against a real/embedded
database rather than a mock.

## 5. Running tests

```bash
mvn test                              # full suite (isolated H2 profile; no DB needed)
mvn test -Dtest=RequestServiceTests   # one test class
mvn test -Dtest=RequestServiceTests#submissionWithoutLocationIsRejected   # one method
mvn clean package                     # full build + tests + jar
```

Tests use the **`test` Spring profile** (`src/test/resources/application-test.yml`),
which points at an in-memory H2 database in PostgreSQL mode. No per-developer database
configuration is needed to run tests.

## 6. Why tests belong with every business-rule change

The module syllabus and the team's DoD require behaviour to be proven, not just written.
For the areas most likely to have subtle rules, plan tests *as part of* the story:

- **Dispatch / technician assignment** — ranking, service-area filtering, availability,
  workload; edge cases and tie-breaking.
- **SLA** — deadline calculation, approaching/overdue boundaries, escalation triggers.
- **Request lifecycle** — allowed and **invalid** transitions (the "who closes a
  request?" decision will define the transition rules once resolved).
- **Work orders** — acceptance, repair-state updates, duplicate-assignment prevention.
- **Authorization-sensitive behaviour** — role-based access once authentication exists.

A business rule with no test is a rule waiting to break silently in a sprint.

## 7. Keeping the suite healthy

- Run the **full** suite before opening a PR and after resolving a merge conflict.
- Do **not** disable or delete tests to make CI green. A red test is information: either
  the code changed behaviour (update the test intentionally) or the code regressed (fix
  the code).
- If an existing test no longer reflects intended behaviour, change it **with the story**
  and say so in the PR — never silently.
