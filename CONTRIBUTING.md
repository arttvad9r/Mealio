# Contributing to Mealio

Thanks for taking a look. Mealio is a small, focused native Android client for
self-hosted Mealie. Contributions that keep it small and focused are the most
welcome.

## Getting in touch

- **Issues** are welcome for bug reports and feature requests.
- **Pull requests** are welcome.
- For **larger changes**, please open an issue or discussion first so we can
  agree on the direction before you write a lot of code.

## Ground rules

- **Keep the architecture simple.** Prefer direct calls and hand-rolled
  dependencies over new abstractions, wrappers or service layers.
- **Avoid unnecessary dependencies.** Prefer standard Android / Jetpack
  solutions over new frameworks and heavy libraries.
- **Don't break existing screens**, and keep the UI consistent with the existing
  Mealio design.
- **Contributions must work against a self-hosted Mealie server** — that is the
  only backend Mealio targets.
- Mealio is a thin client for Mealie: not a calorie tracker, no separate food
  database, no barcode scanner, no manual food entry, no AI recommendations.
- **Never commit secrets** or signing material: no keystores,
  `keystore.properties`, tokens, `.env` files or local machine paths (see
  `.gitignore`).

## Before opening a PR

Run the same checks CI runs and keep the tree green:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Unit tests must pass and lint must report 0 errors.

## Commit style

Short imperative subject lines, e.g. `Compact the Today slot card`. Explain the
*why* in the body when it isn't obvious.

## Architecture

A short overview lives in [`AGENTS.md`](AGENTS.md): Compose UI + ViewModels,
pure `domain/` models, and a `data/` layer that isolates Mealie API quirks.
Significant decisions are recorded as ADRs in [`docs/adr/`](docs/adr/) — useful
if you are touching the connection layer, DI or token storage.

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT license](LICENSE).
