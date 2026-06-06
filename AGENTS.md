# Repository Guidelines

## Project Structure & Module Organization
- Kotlin sources reside in `src/main/kotlin/jp/yappo/pologen`; `Main.kt` is only the CLI entrypoint.
- `application` coordinates use cases and depends on ports in `application/port`; `domain` holds configuration/model/support types; `infrastructure` implements config loading, Markdown processing, image/OGP generation, and rendering.
- Test specs live in `src/test/kotlin`, grouped by feature (`*Spec` mirrors production responsibilities where practical).
- Gradle outputs everything under `build/`; the shaded executable jar lands in `build/libs/`.

## Build, Test, and Development Commands
- `./gradlew build` compiles with Kotlin 2.1.10 and runs the default verification suite.
- `./gradlew test` executes the Kotest/JUnit5 specs; use it before pushing.
- `./gradlew shadowJar` assembles the distributable jar; run with `java -jar build/libs/Pologen-1.0-SNAPSHOT-all.jar <config.toml>`.
- `./gradlew clean` resets build artifacts when switching branches.

## Coding Style & Naming Conventions
- Follow standard Kotlin style: 4-space indentation, braces on the same line, and expression bodies for simple helpers.
- Use `UpperCamelCase` for classes/specs, `camelCase` for functions and locals, and `UPPER_SNAKE_CASE` for constants.
- Keep serialization contracts in `@Serializable` data classes under `domain/config` and colocate TOML schema updates with loader/docs/tests changes.
- Prefer immutable `val` and top-level functions; document non-obvious logic with concise comments.
- Keep application use cases depending on ports instead of infrastructure classes when adding new cross-cutting behavior.

## Testing Guidelines
- Specs rely on Kotest/JUnit5; use the existing `FunSpec` style unless a feature already uses another Kotest style.
- Cover edge cases around timezone handling, Markdown parsing, metadata synchronization, OGP generation decisions, and HTML/RSS rendering when adding features.
- Run `./gradlew test` and, when touching CLI behavior, capture sample output to validate regression expectations.

## Commit & Pull Request Guidelines
- Use short, imperative commit messages (`Add RSS fallback`, `Fix config path lookup`) similar to existing history.
- Squash WIP changes locally; keep each commit buildable and scoped to a single concern.
- PRs must summarize intent, list functional changes, and note how you tested; link issues and attach relevant HTML/RSS diffs or screenshots.

## Configuration Tips
- The CLI expects a TOML config alongside the invocation; values under `[paths]` (`documentRoot`, `feedXml`, `indexHtml`) resolve relative to that file.
- After adding configuration keys, update `domain/config/Configuration.kt` and adjust README examples, tests, and any sample configs so downstream agents stay in sync.
