# Contributing to Mealio

Thanks for taking a look. Mealio is a small, focused native Android client for
self-hosted Mealie. Contributions that keep it small and focused are the most
welcome.

## Ground rules

- **Keep the scope tight.** Mealio is a thin client for Mealie — not a calorie
  tracker, not a food database, no barcode scanner, no AI recommendations.
- **Prefer standard Android / Jetpack solutions** over new frameworks and heavy
  dependencies.
- **Don't break existing screens.** Behaviour-first changes with tests are
  preferred.
- Never commit secrets: no keystores, `keystore.properties`, tokens, `.env` or
  local machine paths (see `.gitignore`).

## Before opening a PR

Run the same checks CI runs:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Keep the tree green: unit tests pass, lint reports 0 errors.

## Commit style

Short imperative subject lines, e.g. `Compact the Today slot card`. Explain the
*why* in the body when it isn't obvious.

## Architecture

A short overview lives in [`AGENTS.md`](AGENTS.md): Compose UI + ViewModels,
pure `domain/` models, and a `data/` layer that isolates Mealie API quirks.
Significant decisions are recorded as ADRs in [`docs/adr/`](docs/adr/).

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT license](LICENSE).
