# SmartFix Git Safety Guide — How Not to Destroy the Repository

This guide is about protecting shared history and your own uncommitted work. Read it
before you run any *destructive* Git command. The golden rule:

> **Never rewrite history that other people have already pulled.** Prefer `git revert`.

The repository uses `main` as the stable branch and short-lived
`feature/…` / `fix/…` branches merged through Pull Requests.

---

## 1. Safe normal operations (use these freely)

| Command | What it does |
|---|---|
| `git status` | Show current branch and changes |
| `git log --oneline -10` | Recent commits |
| `git diff` | Unstaged changes |
| `git diff --staged` | Staged changes |
| `git add <file>` | Stage a file |
| `git commit -m "message"` | Commit staged changes |
| `git pull origin main` | Fetch + merge latest `main` into current branch |
| `git push -u origin <branch>` | Push a new branch and track it |
| `git branch -d <branch>` | Delete a **merged** local branch (safe) |
| `git restore --staged <file>` | Unstage a file, keep the change |
| `git stash` / `git stash pop` | Temporarily set work aside and bring it back |

Use `git restore --staged` (not `git reset`) to unstage — it is safer and clearer.

---

## 2. Dangerous operations (warn the reader)

These can permanently destroy **uncommitted** or **shared** work. Treat each as a
last resort and understand exactly what it deletes first.

| Command | Danger |
|---|---|
| `git reset --hard [<commit>]` | **Discards uncommitted changes and moves your branch.** Anything you had not committed is gone. Never use casually; never against a shared branch. |
| `git clean -fd` | **Deletes untracked files/folders** (build output, config files you forgot to commit). `-x` even removes git-ignored files. |
| `git push --force` / `git push --force-with-lease` | **Rewrites shared history.** A plain force-push can erase teammates' commits. Forbid on `main`; avoid elsewhere. If ever needed, use `--force-with-lease` and only on an unshared branch. |
| `git reset --hard origin/main` | Moves your branch and throws away local commits — you will lose work not yet on the remote. |
| `git checkout -- <file>` / `git restore <file>` | Discards uncommitted changes **to that file** — irreversible. |
| `git branch -D <branch>` | Force-deletes a branch **even if not merged** — its commits may become unreachable. |

### If you are tempted to force-push or hard-reset
1. Stop.
2. Make a **backup branch** first: `git branch backup/my-work` (cheap and safe).
3. Ask: is this commit already shared? If yes → use `git revert` instead (see §5).
4. Only then consider the destructive command, and tell the team.

---

## 3. Shared-history risks

Anything you have **pushed** is shared. Teammates may have already pulled it.

- **Do not rebase, reset or force-push a shared branch.** Rewriting it makes teammates'
  histories diverge and can silently lose their commits.
- If a commit is wrong but already shared, **add a new commit that undoes it**
  (`git revert`) rather than rewriting the past.
- `main` is protected: no force-push, no direct development, no deletion (README §24).

---

## 4. Recovering from mistakes — safe paths

### "I accidentally committed a file I should not have (e.g. a secret)"

```bash
# If not yet pushed:
git reset --soft HEAD~1        # undo the commit, keep changes staged
git restore --staged <file>    # unstage the unwanted file
git add <the right files>
git commit -m "..."            # commit again without the file
```

If it **was pushed** and it is a **secret**, removal is not enough — the secret is in
history. Rotate/revoke it and notify the team (README §21). For history cleanup, only
proceed with team agreement (tools like `git filter-repo`) — this is a bigger operation.

### "I want to undo the latest local commit but keep my work"

```bash
git reset --soft HEAD~1     # safe: only moves the branch pointer, work stays staged
```

### "I want to undo a commit that is already shared"

```bash
git revert <commit-sha>     # creates a NEW commit that undoes it - safe for shared history
git push origin <branch>
```

`git revert` does not rewrite history; teammates can pull normally.

### "I made a mess in my working tree and want a clean copy of one file"

```bash
git restore <file>          # discards uncommitted changes to that ONE file only
```

Only run `git clean -fd` when you are certain untracked files (e.g. stray files) are
disposable — it is irreversible.

---

## 5. Revert vs reset — which to use

| Situation | Use |
|---|---|
| Commit not yet pushed | `git reset --soft HEAD~1` (undo, keep changes) or amend |
| Commit already pushed/shared | `git revert <sha>` — never rewrite |
| Want to discard uncommitted file changes | `git restore <file>` |
| Branch pointer mess, nothing shared | ask first, backup branch first |

When in doubt: **make a backup branch, then ask a teammate.** A few minutes of caution
beats an hour of recovery.

---

## 6. Branch deletion

- Delete a merged branch safely: `git branch -d <branch>`.
- `git branch -D` force-deletes **unmerged** branches — only if you are sure the commits
  are safe (merged via PR elsewhere, or backed up).
- After a PR merges, also delete the remote branch on GitHub (the UI offers this).

---

## 7. Conflict handling

- Conflicts happen when two branches change the same lines. They are normal.
- Resolve by editing files to the **correct combined result** (see README §23 and
  `docs/development-guide.md` §7), then `git add` and `git commit`.
- **Special care:** `pom.xml`, Flyway migrations, security configuration and central
  `application*.yml`. These conflicts usually mean two people changed shared setup —
  get a second pair of eyes.

---

## 8. Accidental situations (quick answers)

**"I accidentally worked on `main`."**
```bash
git checkout -b feature/SCRUM-XX-what-i-am-doing   # move your work to a branch
git push -u origin feature/SCRUM-XX-what-i-am-doing
```
Then `main` is clean and you can switch back. Do not commit to `main`.

**"I accidentally committed a file that shouldn't be tracked."**
Follow §4 (unstage/remove). Add it to `.gitignore` if it is a recurring local file.

**"My branch is behind `main`."**
```bash
git checkout main && git pull origin main
git checkout feature/SCRUM-XX-short-description
git merge main
```

**"I think I destroyed something — I don't know where my work is."**
```bash
git reflog          # a journal of where HEAD has been - find your lost commit
git branch backup/lost-work <sha>   # recreate a branch pointing at it
```
Do **not** run more destructive commands while panicking. `git reflog` is your friend.

---

## 9. What you should NEVER do in this repository

- `git push --force` (especially to `main`).
- `git reset --hard` out of frustration.
- `git clean -fd` without checking what it removes.
- Rewriting a shared migration or deleting it to fix a local DB.
- Committing secrets, `.env`, `target/` or IDE files.
- Coding or committing directly on `main`.

When unsure, ask. The repository is the team's shared asset — protecting it is everyone's
job.
