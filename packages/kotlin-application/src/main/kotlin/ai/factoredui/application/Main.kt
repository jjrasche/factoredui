package ai.factoredui.application

import java.nio.file.Files
import kotlin.system.exitProcess

fun main(args: Array<String>) {
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
