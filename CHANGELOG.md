# Changelog

## Unreleased

- Add safe incremental entry and image generation backed by SHA-256 metadata.
- Preserve malformed `meta.toml` files and report actionable errors.
- Decode WebP article images and emit correctly named JPEG artifacts.
- Generate unique TOC anchors from rendered headings and keep the TOC visible while scrolling.
- Bundle production CSS instead of loading Tailwind Play CDN at runtime.
- Add configuration validation, `-c` / `--config` CLI support, and CI on JDK 21.
- Update Kotlin to 2.2.21, Commons Text to 1.15.0, and jte to 3.2.3.

The application version remains `1.0-SNAPSHOT` until a stable release is intentionally prepared.
