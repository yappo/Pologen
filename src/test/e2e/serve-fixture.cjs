const { execFileSync } = require("node:child_process")
const fs = require("node:fs")
const http = require("node:http")
const path = require("node:path")

const projectRoot = path.resolve(__dirname, "../../..")
const fixtureRoot = path.join(projectRoot, "build/e2e-site")
const documentRoot = path.join(fixtureRoot, "htdocs")
const entryRoot = path.join(documentRoot, "post")
const configPath = path.join(fixtureRoot, "config.toml")
const jarPath = path.join(projectRoot, "build/libs/Pologen-1.0-SNAPSHOT-all.jar")

fs.rmSync(fixtureRoot, { recursive: true, force: true })
fs.mkdirSync(entryRoot, { recursive: true })
fs.writeFileSync(
    configPath,
    `[paths]
documentRoot = "htdocs"
indexHtml = "htdocs/index.html"
feedXml = "htdocs/feed.xml"

[site]
blogTopUrl = "/"
documentBaseUrl = "http://127.0.0.1:4174"
feedXmlUrl = "http://127.0.0.1:4174/feed.xml"
title = "Responsive fixture"
description = "Responsive fixture"
language = "ja"
faviconUrl = "/favicon.ico"

[author]
name = "Fixture author"
url = "https://example.com/author"
iconUrl = "https://example.com/author.png"

[ogp]
enabled = false
`,
)
const longBody = Array.from(
    { length: 24 },
    (_, index) => `Paragraph ${index + 1}: responsive layout verification content.`,
).join("\n\n")
fs.writeFileSync(
    path.join(entryRoot, "index.md"),
    `title: Responsive article\n\n## First section\n\n${longBody}\n\n## Second section\n\n${longBody}\n`,
)

execFileSync("java", ["-jar", jarPath, configPath], {
    cwd: projectRoot,
    stdio: "inherit",
})

const server = http.createServer((request, response) => {
    const requestUrl = new URL(request.url, "http://127.0.0.1:4174")
    const relativePath = decodeURIComponent(requestUrl.pathname).replace(/^\/+/, "")
    let filePath = path.resolve(documentRoot, relativePath)
    if (!filePath.startsWith(`${documentRoot}${path.sep}`) && filePath !== documentRoot) {
        response.writeHead(403).end("Forbidden")
        return
    }
    if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
        filePath = path.join(filePath, "index.html")
    }
    if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
        response.writeHead(404).end("Not found")
        return
    }
    const contentType = filePath.endsWith(".css")
        ? "text/css; charset=utf-8"
        : filePath.endsWith(".js")
          ? "text/javascript; charset=utf-8"
          : filePath.endsWith(".xml")
            ? "application/xml; charset=utf-8"
            : "text/html; charset=utf-8"
    response.writeHead(200, { "Content-Type": contentType })
    fs.createReadStream(filePath).pipe(response)
})

server.listen(4174, "127.0.0.1")
