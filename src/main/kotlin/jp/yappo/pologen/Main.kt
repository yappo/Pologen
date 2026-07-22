package jp.yappo.pologen

import jp.yappo.pologen.application.BuildSiteUseCase
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

fun runCli(args: Array<String>, buildSite: (Path) -> Boolean = { BuildSiteUseCase().execute(it) }): Int {
    val configArgument = when {
        args.size == 1 && !args[0].startsWith("-") -> args[0]
        args.size == 2 && args[0] in setOf("-c", "--config") -> args[1]
        else -> {
            System.err.println("Usage: pologen [-c|--config] config.toml")
            return 2
        }
    }
    return runCatching {
        val configFile = Path.of(configArgument).toAbsolutePath().normalize()
        println("configuration file path: $configFile")
        if (buildSite(configFile)) 0 else 1
    }.getOrElse { error ->
        System.err.println("Failed to build site: ${error.message ?: error::class.simpleName}")
        1
    }
}
