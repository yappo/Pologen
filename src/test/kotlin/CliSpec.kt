package jp.yappo.pologen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class CliSpec : FunSpec({
    test("CLI accepts positional and config option forms") {
        var received: Path? = null
        runCli(arrayOf("config.toml")) { received = it; true } shouldBe 0
        received?.fileName.toString() shouldBe "config.toml"

        runCli(arrayOf("--config", "other.toml")) { received = it; true } shouldBe 0
        received?.fileName.toString() shouldBe "other.toml"
    }

    test("CLI returns useful non-zero statuses") {
        runCli(emptyArray()) { true } shouldBe 2
        runCli(arrayOf("config.toml")) { false } shouldBe 1
        runCli(arrayOf("config.toml")) { error("broken") } shouldBe 1
    }
})
