const { expect, test } = require("@playwright/test")

const articleUrl = "http://127.0.0.1:4174/post/"

test("mobile layout uses the viewport without horizontal overflow", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(articleUrl)

    await expect(page.locator('meta[name="viewport"]')).toHaveAttribute(
        "content",
        "width=device-width,initial-scale=1",
    )
    await expect(page.getByTestId("entry-layout")).toHaveCSS("flex-direction", "column")
    const dimensions = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth,
    }))
    expect(dimensions.scrollWidth).toBe(dimensions.clientWidth)
})

test("desktop table of contents remains visible while scrolling", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto(articleUrl)

    const toc = page.getByTestId("toc-card")
    await expect(toc).toHaveCSS("position", "sticky")
    await page.evaluate(() => window.scrollTo(0, 700))
    await expect.poll(async () => Math.round((await toc.boundingBox()).y)).toBe(16)
})
