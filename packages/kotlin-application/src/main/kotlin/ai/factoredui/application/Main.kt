package ai.factoredui.application

import java.nio.file.Files
import kotlin.system.exitProcess

private fun exportSheetsCommand(args: List<String>) {
    val options = try {
        parseExportOptions(args)
    } catch (failure: UsageException) {
        System.err.println(failure.message)
        exitProcess(2)
    }
    exportSheets(options).forEach { println(it) }
}

private fun serveScreenCommand(args: List<String>) {
    val options = try {
        parseScreenOptions(args)
    } catch (failure: UsageException) {
        System.err.println(failure.message)
        exitProcess(2)
    }
    startScreenServer(options)
    println("screen http://${options.bind}:${options.port}/  season ${options.season}  screen ${options.screen}")
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "export-sheets") return exportSheetsCommand(args.drop(1))
    if (args.firstOrNull() == "serve-screen") return serveScreenCommand(args.drop(1))
    val options = try {
        parseOptions(args.toList())
    } catch (failure: UsageException) {
        System.err.println(failure.message)
        exitProcess(2)
    }
    val file = readSystemFile(Files.readString(options.system))
    startPageServer(options)
    println("system page http://${options.bind}:${options.port}/  system ${file.id}  runtime we ${runtimePin(options.runtime)}")
}
