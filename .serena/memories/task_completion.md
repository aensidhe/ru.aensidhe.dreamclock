# Task Completion Checklist

All gates run from repo root:

**Before commit**:
1. Full gate: `./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt`
   - Must be all green
   - All tests pass
   - No lint/format violations
   - No detekt violations

**Commit checklist**:
1. Stage only the files you modified (explicit paths, not `git add -A`)
2. Commit with Conventional Commit format + `:robot:` marker if bot-authored
3. No `Co-Authored-By` trailer
4. Linear history — no merge commits

**PR/Integration**:
- Push to remote: `git push`
- Open PR for CI
- Once CI green: `git switch main && git merge --ff-only <branch> && git push`
