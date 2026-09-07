# SmartFix Release & Recovery Guide

Deployment happens in a later sprint, but the team needs safe habits for `main` from
day one. This guide keeps the concept small: a **stable `main`** and safe recovery when
something breaks. It does not introduce an enterprise release process.

---

## 1. Stable `main` concept

- `main` is the **stable integration branch** — it should always build, test green, and
  be deployable.
- All changes reach `main` through **reviewed Pull Requests** with **green CI**
  (README §17, §24). No direct pushes.
- If `main` ever stops building or tests fail, it is a **team incident**, not an
  individual problem — fix it before layering more changes on top.

## 2. Sprint milestone tags/releases (later)

- At sprint milestones, tag the merged `main` so the team has a named, reproducible
  point:
  ```bash
  git checkout main && git pull origin main
  git tag -a v0.1.0 -m "Sprint 1 milestone"
  git push origin v0.1.0
  ```
- Tags are cheap and give you a "last known good" reference without creating a heavy
  release process.

## 3. Reverting a bad merge or commit

A merge on `main` turns out wrong (fails tests, breaks behaviour):

1. Stop further merges.
2. Find the bad commit/PR:
   ```bash
   git log --oneline -10
   ```
3. Create a **repair branch** and **revert** (never rewrite shared history):
   ```bash
   git checkout main
   git pull origin main
   git checkout -b fix/SCRUM-XX-revert-bad-merge
   git revert -m 1 <merge-commit-sha>    # for a merge commit, -m 1 keeps main's side
   ```
   or, for a normal commit:
   ```bash
   git revert <commit-sha>
   ```
4. Run `mvn test` and `mvn clean package`.
5. Open a PR for the revert, run CI, review, merge.
6. Communicate team-wide.

`git revert` adds a new commit that undoes the bad one — teammates pull normally and
history stays intact.

## 4. Recovery after a CI failure

A red CI build means `main` (or the branch) is not healthy. Check, in order: compile →
unit tests → dependency resolution → environment assumptions → Flyway migrations →
(later) Docker build. Do not merge a red build. If a merged commit broke CI, treat it
like a bad merge (§3) and revert quickly so teammates are not blocked.

## 5. How to restore working `main`

Best practice is to **revert forward** (§3): a new commit restoring a known-good state.
You can identify the last-known-good point with:

```bash
git log --oneline                 # find the last commit you know was green
git tag                           # sprint tags make this easy
git reflog                        # if you need to trace where HEAD has been
```

## 6. Finding the last-known-good commit

- Use CI history: the last commit where the pipeline was green.
- Use a sprint tag (§2).
- Local references: `git log --oneline -20`.

## 7. Why shared history must never be rewritten

`main` (and anything pushed) is shared: teammates and CI may already hold it. Rewriting
it (rebase, `reset`, force-push) makes their histories diverge and can silently erase
commits. Always prefer **`git revert`** for shared history, and reserve `reset`/force for
your own, unshared branch — and only with care (`docs/git-safety-guide.md` §2).

---

## Quick decision table

| Situation | Do | Avoid |
|---|---|---|
| Bad commit already on `main` | Revert forward via a repair PR | Rewriting/force-pushing `main` |
| Bad commit not yet pushed | `git reset --soft HEAD~1` or amend | Force-push |
| CI red on `main` | Revert to last-known-good, fix forward | Ignoring it / merging more on top |
| Need a stable reference point | Create a sprint tag | — |
| Panicking about lost work | `git reflog`, make a backup branch | Destructive commands |
