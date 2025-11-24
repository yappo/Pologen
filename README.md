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

[sidebar]
recentEntryCount = 10

[links]
"Docs" = "https://docs.example.com"
"Community Portal" = "https://community.example.com"
```

- `paths.documentRoot` points to the directory containing your posts, while `paths.indexHtml` and `paths.feedXml` define where the generated top page and RSS feed should be written (all relative to the configuration file).
- `site.blogTopUrl` and `site.documentBaseUrl` supply absolute links for the generated HTML; `site.feedXmlUrl`, `site.title`, `site.description`, `site.language`, and `site.faviconUrl` drive metadata injected into HTML and RSS.
- `author.*` configures the author info displayed on entry pages (name, profile link, and avatar).
- `assets.stylesheets` / `assets.scripts` let you append additional CSS/JS. They fall back to Tailwind + daisyUI + `/assets/pologen.js` when omitted.
- `images.*` governs thumbnail/full-size resizing and JPEG quality; `scaleMethod` accepts `speed`, `balanced`, `quality`, or `ultra_quality`.
- `sidebar.recentEntryCount` controls how many items appear in the “Recent posts” card; the `[links]` table is an insertion-order map rendered as external links in the sidebar (quote keys like `"Community Portal"` if they contain spaces).
- The `[ogp]` table enables/disables image generation and configures the canvas, colors, and optional font/author icon assets. If `enabled = false`, OGP rendering and meta tags are skipped entirely.

## Styling Defaults
The bundled templates include Tailwind CSS (via the CDN script `https://cdn.tailwindcss.com`) and daisyUI’s ready-made theme CSS (`https://cdn.jsdelivr.net/npm/daisyui@4.12.10/dist/full.min.css`) by default, plus the helper script at `/assets/pologen.js` (image overlay + TOC interactions). The markup sticks to standard Tailwind utility classes so you can swap in Flowbite, Bootstrap, or another framework in the future without rewriting the DOM structure. Upcoming releases will let you point the generator at your own `.kte` templates to fully customize the framework stack while still inheriting the configuration metadata described above.

## Image Handling
Markdown image syntax (`![alt](photo.jpg)`) now renders responsive figures: Pologen resolves the image relative to the post folder, emits `photo-full.jpg` and `photo-thumb.jpg` with the configured sizes, and injects Tailwind-ready HTML that links the thumbnail to the full asset. Both variants are generated as JPEGs using imgscalr, and assets live next to the post's `index.html`, so they deploy automatically alongside the rest of the entry directory.

## Custom Assets
If you need extra CSS or JS beyond the defaults, declare `[assets] stylesheets = ["..."]` or `scripts = ["..."]` in `config.toml`. The defaults already include Tailwind/daisyUI plus the image overlay helper (`/assets/pologen.js`), and your entries will load any additional assets you declare.

## Sharing
Entry pages include a share button that uses the Web Share API on iOS/Android, but still offers desktop-friendly controls (X.com intent link + copy-link helper). The share targets are built from an extensible list so additional services can be layered in later; for now the focus is on an X-compliant experience that respects X's posting requirements across browsers and devices.

## Content Layout
Each post resides in its own directory beneath `paths.documentRoot` and must contain an `index.md`. The first line is treated as the title using the `title: Your Title` format; the remainder is parsed with JetBrains Markdown and rendered into HTML. The generator maintains a `meta.toml` alongside each entry that tracks `publishDate`, `updateDate`, and a body digest. The file is created on first run and the digest is refreshed whenever the Markdown content changes.

### Markdown heading rules
- The top-of-page title is rendered as an `<h1>` from the `title:` line, so keep in-body headings at `##` (h2) or deeper. In-body `#` headings are intentionally excluded from TOC generation.

## Generated Output
- A fully rendered `index.html` is emitted per entry directory, including metadata, author links, and embedded Markdown content.
- `paths.indexHtml` receives a landing page that lists up to 30 most recent entries (ordered lexicographically by directory), showing publication time in JST and a 140-character summary derived from the plain-text body.
- `paths.feedXml` is populated with an RSS 2.0 feed whose items link to `site.documentBaseUrl + entry.urlPath` and reuse the same summaries (properly HTML-escaped).

## Development & Testing
- `./gradlew build` compiles the Kotlin sources and run checks.
- `./gradlew test` executes the Kotest suites covering configuration parsing, Markdown ingestion, HTML generation, RSS output, and date handling.
- `./gradlew clean` removes build artefacts before regenerating outputs.
- When iterating locally, re-run `shadowJar` and invoke the jar with your config to update HTML and XML artifacts in place.
