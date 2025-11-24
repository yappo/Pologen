package jp.yappo.pologen

import jp.yappo.pologen.application.BuildSiteUseCase
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: $ app config.toml")
        return
    }

    val configFile = Path.of(args[0]).toAbsolutePath().normalize()
    println("configuration file path: $configFile")
    val useCase = BuildSiteUseCase()
    useCase.execute(configFile)
}
