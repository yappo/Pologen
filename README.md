# YappoLogs2 Contents Generator

yap*POLOG*s contents *GEN*erator

# Usage

```
$ ./gradlew shadowJar
$ java -jar ./build/libs/Pologen-1.0-SNAPSHOT-all.jar path/to/config.toml
$ java -jar ./build/libs/Pologen-1.0-SNAPSHOT-all.jar --config path/to/config.toml
```

## Overview
Pologen turns a directory tree of Markdown posts into static HTML entry pages, a site index, and an RSS feed. The CLI expects the path to a TOML configuration file, and all configured paths are resolved relative to that file’s location.

## Configuration
Create a `config.toml` alongside your document root, for example:

```
[paths]
documentRoot = "htdocs"
indexHtml = "htdocs/index.html"
feedXml = "htdocs/feed.xml"

[site]
blogTopUrl = "https://blog.example.com/"
documentBaseUrl = "https://blog.example.com"
feedXmlUrl = "https://blog.example.com/feed.xml"
title = "Example Blog"
description = "Latest updates from Example"
language = "en"
faviconUrl = "/favicon.png"

[author]
name = "@example"
url = "https://social.example.com/example"
iconUrl = "https://cdn.example.com/icon.png"

[images]
thumbWidth = 480
fullMaxWidth = 1920
scaleMethod = "quality"
jpegQuality = 0.9

[ogp]
enabled = true
width = 1200
height = 630
backgroundColor = "#101827"
titleColor = "#FFFFFF"
bodyColor = "#E5E7EB"
accentColor = "#F97316"
fontPath = "/absolute/or/relative/path/to/fontfile.ttf"
authorIconPath = "/absolute/or/relative/path/to/author_icon.png"

[assets]
stylesheets = ["/assets/custom.css"]
scripts = ["/assets/custom.js"]

[templates]
directory = "templates"

[archive]
enabled = true
output = "archive/index.html"
url = "/archive/"

[tags]
enabled = true
output = "tags"
url = "/tags/"
relatedEntryCount = 3

[sidebar]
recentEntryCount = 10

[links]
"Docs" = "https://docs.example.com"
"Community Portal" = "https://community.example.com"
```

- `paths.documentRoot` points to the directory containing your posts, while `paths.indexHtml` and `paths.feedXml` define where the generated top page and RSS feed should be written (all relative to the configuration file).
- `site.blogTopUrl` and `site.documentBaseUrl` supply absolute links for the generated HTML; `site.feedXmlUrl`, `site.title`, `site.description`, `site.language`, and `site.faviconUrl` drive metadata injected into HTML and RSS.
- `author.*` configures the author info displayed on entry pages (name, profile link, and avatar).
- `assets.stylesheets` / `assets.scripts` append additional CSS/JS after the bundled `/assets/pologen.css` and `/assets/pologen.js` defaults.
- `images.*` governs thumbnail/full-size resizing and JPEG quality; `scaleMethod` accepts `speed`, `balanced`, `quality`, `ultra_quality`, or `automatic`. Widths must be positive and `jpegQuality` must be between `0.0` and `1.0`.
- `sidebar.recentEntryCount` controls how many items appear in the “Recent posts” card; the `[links]` table is an insertion-order map rendered as external links in the sidebar (quote keys like `"Community Portal"` if they contain spaces).
- The `[ogp]` table enables/disables image generation and configures the canvas, colors, and optional font/author icon assets. If `enabled = false`, OGP rendering and meta tags are skipped entirely.
- `templates.directory` optionally selects a directory containing `entry.kte`, `index.kte`, and `feed.kte`. Relative paths resolve from `config.toml`; omitting the table keeps the bundled templates. Template files are trusted executable jte/Kotlin input.
- `[archive]` optionally emits a complete article archive grouped by month. `output` is relative to `paths.documentRoot`, while `url` is the public root-relative link shown on entry and index pages.
- `[tags]` optionally emits a tag index and one article list per tag. `output` is relative to `paths.documentRoot`, `url` is the public root-relative tag URL, and `relatedEntryCount` limits recommendations sharing the most tags.

## Styling Defaults
The bundled templates use a precompiled `/assets/pologen.css` containing the required Tailwind CSS, daisyUI, and Typography styles. The generator copies that stylesheet and `/assets/pologen.js` into the document root, so generated pages do not depend on Tailwind Play CDN at runtime. Custom assets are appended after these defaults.

To customize page structure, copy the bundled templates from `src/main/resources/templates` into the configured `templates.directory` and edit them together. `entry.kte`, `index.kte`, and `feed.kte` are always required; `archive.kte` is additionally required when the archive is enabled, and `tags.kte` plus `tag.kte` are required when tags are enabled. Generation fails before writing output when the directory or a required template is missing. Changing a custom template invalidates cached entry HTML.

## Image Handling
Markdown image syntax (`![alt](photo.jpg)`) renders responsive figures: Pologen resolves JPEG, PNG, GIF, and WebP input relative to the post folder, emits `photo-full.jpg` and `photo-thumb.jpg` with the configured sizes, and injects HTML that opens the full asset. The output is always encoded as JPEG, so the file extension matches its content. Unchanged image sources are reused using SHA-256 fingerprints stored in `meta.toml`.

## Custom Assets
If you need extra CSS or JS beyond the defaults, declare `[assets] stylesheets = ["..."]` or `scripts = ["..."]` in `config.toml`. Pologen keeps the bundled CSS and image-overlay/TOC helper and loads your assets afterwards.

## Sharing
Entry pages include an X share button that opens an X.com posting intent in a new tab. Web Share API and copy-link controls are not currently provided.

## Content Layout
Each post resides in its own directory beneath `paths.documentRoot` and must contain an `index.md`. Its first line contains the article title. Both the original plain-title form (`Your Title`) and the explicit `title: Your Title` form are supported, so existing posts do not need migration. An empty title stops generation with the offending path. The remainder is parsed with JetBrains Markdown and rendered into HTML. The generator maintains a `meta.toml` alongside each entry with publication/update dates, summaries, TOC data, source/configuration fingerprints, and generated-image fingerprints. The legacy `bodyMd5` key is retained for compatibility but contains a SHA-256 body digest. `updateDate` changes only when article source content or referenced image content changes; refreshing derived summaries, TOC data, templates, or cache metadata preserves it. An unreadable `meta.toml` is reported and never overwritten automatically.

When tags are used, put a comma-separated declaration directly on the second line. Duplicate names within an entry are removed while display spelling is preserved:

```markdown
title: Tagged article
tags: Kotlin, Static Site, 日本語

Article body starts here.
```

### Markdown heading rules
- The top-of-page title is rendered as an `<h1>` from the first line, so keep in-body headings at `##` (h2) or deeper. In-body `#` headings are intentionally excluded from TOC generation.
- TOC entries are derived from rendered h2/h3 elements, ignore headings inside code fences, and receive unique anchors when headings repeat. On larger screens the TOC remains visible while the article scrolls.

## Generated Output
- A fully rendered `index.html` is emitted per entry directory, including metadata, author links, and embedded Markdown content.
- `paths.indexHtml` receives a landing page that lists up to 30 most recent entries (ordered lexicographically by directory), showing publication time in JST and a 140-character summary derived from the plain-text body.
- `paths.feedXml` is populated with an RSS 2.0 feed whose items link to `site.documentBaseUrl + entry.urlPath` and reuse the same summaries (properly HTML-escaped).
- When enabled, `[archive]` writes every entry to a month-grouped archive page and adds an Archive link to entry and index headers.
- When enabled, `[tags]` writes `tags/index.html` plus URL-encoded per-tag directories, displays tag links on entries, and recommends other entries by shared-tag count.

## Development & Testing
- `./gradlew build` compiles the Kotlin sources and run checks.
- `./gradlew test` executes the Kotest suites covering configuration parsing, Markdown ingestion, HTML generation, RSS output, and date handling.
- `./gradlew clean` removes build artefacts before regenerating outputs.
- `npm ci && npm run build:css` regenerates the committed production stylesheet after template or style changes.
- `npm run test:e2e` builds the shaded jar and verifies mobile overflow and desktop sticky TOC behavior in Chromium.
- When iterating locally, re-run `shadowJar` and invoke the jar with your config to update HTML and XML artifacts in place.
