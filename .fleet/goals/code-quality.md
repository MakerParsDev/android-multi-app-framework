# Goal: Code Quality, Refactoring & Technical Debt

Continuously inspect Kotlin source code across `app/`, `core/`, `feature/`, and `performance/` modules to eliminate dead code, concurrency issues, and lifecycle bugs.

## Focus Areas
1. **Kotlin & Coroutine Safety**: Correct usage of `viewModelScope`, lifecycle-aware coroutines, state collection (`repeatOnLifecycle`), and job cancellation.
2. **Compose State & Performance**: Detect unstable state usage, redundant recompositions, or missing `remember` keys in Jetpack Compose UI.
3. **Dead Code & Duplication**: Safely remove unused resources, obsolete compatibility shims, unused internal functions, and redundant constants.
4. **Static Quality Compliance**: Address Detekt and ktlint warnings proactively before they accumulate into baselines.

## Verification Commands
- `./gradlew staticQualityCheck`
- `./gradlew detekt`
- `./gradlew ktlintCheck`

## Rules
- Keep changes localized to small, focused PRs.
- Avoid large-scale stylistic or aesthetic formatting rewrites.
