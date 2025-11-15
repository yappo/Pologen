package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneId

class DateTimeSpec : FunSpec({
    test("convertToRssDateTimeFormat converts JST to GMT correctly") {
        val jst = ZoneId.of("Asia/Tokyo")
        val gmt = ZoneId.of("GMT")
        val input = "2024-12-31 23:59:59"
        val result = convertToRssDateTimeFormat(input, jst, gmt)
        result shouldBe "Tue, 31 Dec 2024 14:59:59 GMT"
    }

    test("currentDateTimeInJST format is yyyy-MM-dd HH:mm:ss") {
        val now = currentDateTimeInJST()
        // simple format validation
        now.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) shouldBe true
    }
})
