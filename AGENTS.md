# Repository Guidelines

## Project Structure & Module Organization
- Kotlin sources reside in `src/main/kotlin`; `Main.kt` orchestrates Markdown ingestion, HTML generation, and feed publishing.
- Test specs live in `src/test/kotlin`, grouped by feature (`*Spec` mirrors production responsibilities).
- Gradle outputs everything under `build/`; the shaded executable jar lands in `build/libs/`.

## Build, Test, and Development Commands
- `./gradlew build` compiles with Kotlin 2.1.10 and runs the default verification suite.
- `./gradlew test` executes the Kotest/JUnit5 specs; use it before pushing.
- `./gradlew shadowJar` assembles the distributable jar; run with `java -jar build/libs/Pologen-1.0-SNAPSHOT-all.jar <htdocsDir>`.
- `./gradlew clean` resets build artifacts when switching branches.

## Coding Style & Naming Conventions
- Follow standard Kotlin style: 4-space indentation, braces on the same line, and expression bodies for simple helpers.
- Use `UpperCamelCase` for classes/specs, `camelCase` for functions and locals, and `UPPER_SNAKE_CASE` for constants (see `DIGEST` in `Main.kt`).
- Keep serialization contracts in `@Serializable` data classes and colocate TOML schema updates with their usage.
- Prefer immutable `val` and top-level functions; document non-obvious logic with concise comments.

## Testing Guidelines
- Specs rely on Kotest’s `DescribeSpec`/`ShouldSpec` patterns; mirror production package names and suffix files with `Spec`.
- Cover edge cases around timezone handling, Markdown parsing, and HTML/RSS rendering when adding features.
- Run `./gradlew test` and, when touching CLI behavior, capture sample output to validate regression expectations.

## Commit & Pull Request Guidelines
- Use short, imperative commit messages (`Add RSS fallback`, `Fix config path lookup`) similar to existing history.
- Squash WIP changes locally; keep each commit buildable and scoped to a single concern.
- PRs must summarize intent, list functional changes, and note how you tested; link issues and attach relevant HTML/RSS diffs or screenshots.

## Configuration Tips
- The CLI expects a TOML config alongside the invocation; `documentRootPath`, `feedXmlPath`, and `indexHtmlPath` resolve relative to that file.
- After adding configuration keys, update `Configuration` in `Main.kt` and adjust sample configs or docs so downstream agents stay in sync.
