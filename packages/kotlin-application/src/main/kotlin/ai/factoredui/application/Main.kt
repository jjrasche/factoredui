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

fun main(args: Array<String>) {
    if (args.firstOrNull() == "export-sheets") return exportSheetsCommand(args.drop(1))
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
