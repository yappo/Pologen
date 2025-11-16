# YappoLogs2 Contents Generator

yap*POLOG*s contents *GEN*erator

# Usage

```
$ ./gradlew shadowJar
$ java -jar ./build/libs/Pologen-1.0-SNAPSHOT-all.jar path/to/config.toml
```

## Overview
Pologen turns a directory tree of Markdown posts into static HTML entry pages, a site index, and an RSS feed. The CLI expects the path to a TOML configuration file, and all configured paths are resolved relative to that file’s location.

## Configuration
Create a `config.toml` alongside your document root, for example:

```
documentRootPath = "htdocs"
blogTopUrl = "https://blog.example.com/"
documentBaseUrl = "https://blog.example.com"
feedXmlPath = "htdocs/feed.xml"
feedXmlUrl = "https://blog.example.com/feed.xml"
indexHtmlPath = "htdocs/index.html"
siteTitle = "Example Blog"
siteDescription = "Latest updates from Example"
siteLanguage = "en"
faviconUrl = "/favicon.png"
authorName = "@example"
authorUrl = "https://social.example.com/example"
authorIconUrl = "https://cdn.example.com/icon.png"
imageThumbWidth = 480
imageFullMaxWidth = 1920
imageScaleMethod = "quality"
imageJpegQuality = 0.9
```

- `documentRootPath` points to the directory containing your posts.
- `documentBaseUrl` and `blogTopUrl` supply absolute links for the generated HTML.
- `feedXmlPath`/`feedXmlUrl` and `indexHtmlPath` define where the feed and top-level index are written.
- `siteTitle`, `siteDescription`, and `siteLanguage` configure the text metadata injected into both HTML templates and RSS.
- `faviconUrl`, `authorName`, `authorUrl`, and `authorIconUrl` drive the header/footer branding, author credits, and avatar used on entry pages.
- `imageThumbWidth`, `imageFullMaxWidth`, `imageScaleMethod`, and `imageJpegQuality` govern thumbnail/full-size resizing and JPEG quality; supported `imageScaleMethod` values are `speed`, `balanced`, `quality`, and `ultra_quality`.

## Styling Defaults
The bundled templates include Tailwind CSS (via the CDN script) and daisyUI’s ready-made theme CSS by default. The markup sticks to standard Tailwind utility classes so you can swap in Flowbite, Bootstrap, or another framework in the future without rewriting the DOM structure. Upcoming releases will let you point the generator at your own `.kte` templates to fully customize the framework stack while still inheriting the configuration metadata described above.

## Image Handling
Markdown image syntax (`![alt](photo.jpg)`) now renders responsive figures: Pologen resolves the image relative to the post folder, emits `photo-full.jpg` and `photo-thumb.jpg` with the configured sizes, and injects Tailwind-ready HTML that links the thumbnail to the full asset. Both variants are generated as JPEGs using imgscalr, and assets live next to the post's `index.html`, so they deploy automatically alongside the rest of the entry directory.

## Content Layout
Each post resides in its own directory beneath `documentRootPath` and must contain an `index.md`. The first line is treated as the title using the `title: Your Title` format; the remainder is parsed with JetBrains Markdown and rendered into HTML. The generator maintains a `meta.toml` alongside each entry that tracks `publishDate`, `updateDate`, and a body digest. The file is created on first run and the digest is refreshed whenever the Markdown content changes.

## Generated Output
- A fully rendered `index.html` is emitted per entry directory, including metadata, author links, and embedded Markdown content.
- `indexHtmlPath` receives a landing page that lists up to 30 most recent entries (ordered lexicographically by directory), showing publication time in JST and a 140-character summary derived from the plain-text body.
- `feedXmlPath` is populated with an RSS 2.0 feed whose items link to `documentBaseUrl + entry.urlPath` and reuse the same summaries (properly HTML-escaped).

## Development & Testing
- `./gradlew build` compiles the Kotlin sources and run checks.
- `./gradlew test` executes the Kotest suites covering configuration parsing, Markdown ingestion, HTML generation, RSS output, and date handling.
- `./gradlew clean` removes build artefacts before regenerating outputs.
- When iterating locally, re-run `shadowJar` and invoke the jar with your config to update HTML and XML artifacts in place.
