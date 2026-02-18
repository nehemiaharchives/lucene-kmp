---
name: progress
description: Update lucene-kmp progress documents and commit with GPG signature
---

## Purpose
Update `PROGRESS.md`, `PROGRESS2.md`, and `TODO.md` for `lucene-kmp`.

## Steps
1. `cd lucene-kmp`
2. Run `./progress.main.kts` and wait until it finishes.
3. Run `./progressv2.main.kts` and wait until it finishes.
4. Create a GPG-signed commit with message `Update progress`.

## Commit command
Use the signed commit flow:
- `git add PROGRESS.md PROGRESS2.md TODO.md`
- `export GPG_TTY=$(tty) && git commit -S -m "Update progress"`
- `git log --show-signature -1`
