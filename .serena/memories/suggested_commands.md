# Suggested Commands

All run from repo root with `./gradlew`.

**Testing**:
- `:app:testDebugUnitTest` — unit tests (JUnit5)
- `:app:testDebugUnitTest --tests 'PatternHere'` — run specific test class/method

**Build**:
- `:app:assembleDebug` — debug build

**Linting & Analysis**:
- `:app:ktlintCheck` — check Kotlin style
- `:app:ktlintFormat` — auto-format Kotlin
- `:app:detekt` — static analysis

**Full Gate** (all checks before commit):
```
./gradlew :app:testDebugUnitTest :app:assembleDebug ktlintCheck detekt
```

**Git**:
- Linear history; no interactive rebase in this environment
- Feature branches → `git rebase main`, push, PR, then merge with `--ff-only`
- Commit conventions: Conventional Commits with `:robot:` emoji marker for bot commits (e.g., `feat: :robot: add feature`)
