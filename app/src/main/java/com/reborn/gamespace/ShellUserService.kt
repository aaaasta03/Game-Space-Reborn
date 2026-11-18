package com.reborn.gamespace

import android.os.RemoteException
import android.system.Os
import androidx.annotation.Keep

/** Shizuku UserService. Only fixed, read-only diagnostic commands are exposed. */
class ShellUserService : IGameSpaceService.Stub() {
    @Keep
    override fun destroy() {
        System.exit(0)
    }

    override fun exit() = destroy()

    @Throws(RemoteException::class)
    override fun runDiagnostic(diagnosticId: Int): String {
        return when (diagnosticId) {
            1 -> "Shizuku service identity\nUID=${Os.getuid()}\nPID=${Os.getpid()}\nExpected shell UID: 2000 (ADB mode)"
            2 -> readFixedCommand(arrayOf("/system/bin/cat", "/proc/meminfo"))
            3 -> readFixedCommand(arrayOf("/system/bin/getprop", "ro.build.fingerprint"))
            4 -> readFixedCommand(arrayOf("/system/bin/getprop", "ro.build.version.release"))
            // Read-only Android GameManager capability checks for Free Fire MAX.
            5 -> readFixedCommand(arrayOf("/system/bin/cmd", "game", "list-modes", "com.dts.freefiremax"))
            6 -> readFixedCommand(arrayOf("/system/bin/cmd", "game", "list-configs", "com.dts.freefiremax"))
            // Read-only device state; these commands do not modify policy or settings.
            7 -> readFixedCommand(arrayOf("/system/bin/dumpsys", "thermalservice"))
            8 -> readFixedCommand(arrayOf("/system/bin/dumpsys", "display"))
            9 -> readFixedCommand(arrayOf("/system/bin/dumpsys", "battery"))
            else -> "Unsupported diagnostic ID"
        }
    }

    private fun readFixedCommand(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            val trimmed = output.trim()
            val bounded = if (trimmed.length > 6000) {
                trimmed.take(6000) + "\n… output truncated by Game Space Reborn …"
            } else trimmed
            if (exit == 0) bounded.ifEmpty { "Command completed with no output." }
            else "Command exited with code $exit\n$bounded"
        } catch (e: Exception) {
            "Diagnostic failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
