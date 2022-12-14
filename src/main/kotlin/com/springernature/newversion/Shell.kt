package com.springernature.newversion

import com.lordcodes.turtle.ProcessCallbacks
import com.lordcodes.turtle.ProcessOutput
import com.lordcodes.turtle.ShellRunException
import com.lordcodes.turtle.shellRun
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.concurrent.thread

interface Shell {
    fun run(workingDirectory: File? = null, script: Script.() -> String): String
}

interface Script {
    fun command(
        command: String,
        arguments: List<String> = listOf()
    ): String
}

class TurtleShell : Shell {
    override fun run(workingDirectory: File?, script: Script.() -> String): String =
        TurtleScript(workingDirectory).script()
}

class TurtleScript(private val workingDirectory: File?) : Script {
    override fun command(command: String, arguments: List<String>): String {
        return shellRun(workingDirectory) {
            val commandDescription = "$command ${arguments.joinToString(" ") { """"$it"""" }}"
            LOG.debug("Shell: {}", commandDescription)
            val callback = LogAndCollectOutputs(commandDescription)
            val processOut = commandStreaming(command, arguments, callback)
            processOut.retrieveOutputOrThrow(callback.collectedStdOut(), callback.collectedStdErr())
        }
    }

    private fun ProcessOutput.retrieveOutputOrThrow(
        previouslyCollectedStdOut: String,
        previouslyCollectedStdErr: String
    ): String {
        val outputText = previouslyCollectedStdOut.trim()
        if (exitCode != 0) {
            val errorText = previouslyCollectedStdErr.trim()
            if (errorText.isNotEmpty()) {
                throw ShellRunException(exitCode, errorText)
            }
        }
        return outputText
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TurtleScript::class.java)
    }
}


private class LogAndCollectOutputs(private val commandDescription: String) : ProcessCallbacks {
    private val stdOut = Collections.synchronizedList(mutableListOf<String>())
    private val stdErr = Collections.synchronizedList(mutableListOf<String>())

    override fun onProcessStart(process: Process) {
        val logStdOut = thread(true, name = "logging-std-out($commandDescription)") {
            process.inputReader().useLines {
                it.forEach { line ->
                    LOG.info("Shell ({}): {}", commandDescription, line)
                    stdOut += line
                }
            }
        }
        val logStdErr = thread(true, name = "logging-std-err($commandDescription)") {
            process.errorReader().useLines {
                it.forEach { line ->
                    LOG.error("Shell ({}): {}", commandDescription, line)
                    stdErr += line
                }
            }
        }

        logStdOut.join()
        logStdErr.join()
    }

    fun collectedStdOut(): String = stdOut.joinToString("\n")
    fun collectedStdErr(): String = stdErr.joinToString("\n")

    companion object {
        private val LOG = LoggerFactory.getLogger(LogAndCollectOutputs::class.java)
    }
}
