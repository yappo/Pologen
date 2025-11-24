package jp.yappo.pologen

/**
 * Shared helper for constructing a minimal but valid Configuration for tests.
 */
fun sampleConfiguration(): Configuration = Configuration(
    paths = PathsConfig(
        documentRoot = ".",
        indexHtml = "index.html",
        feedXml = "feed.xml",
    ),
    site = SiteConfig(
        blogTopUrl = "/",
        documentBaseUrl = "https://example.com",
        feedXmlUrl = "/feed.xml",
        title = "Example Site",
        description = "Example Description",
        language = "en",
        faviconUrl = "/favicon.png",
    ),
    author = AuthorConfig(
        name = "@example",
        url = "https://example.com/me",
        iconUrl = "https://example.com/me.png",
    ),
)
