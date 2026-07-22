const { defineConfig } = require("@playwright/test")

module.exports = defineConfig({
    testDir: "src/test/e2e",
    workers: 1,
    reporter: "line",
    use: {
        browserName: "chromium",
    },
    webServer: {
        command: "node src/test/e2e/serve-fixture.cjs",
        url: "http://127.0.0.1:4174/post/",
        reuseExistingServer: false,
        timeout: 30_000,
    },
})
