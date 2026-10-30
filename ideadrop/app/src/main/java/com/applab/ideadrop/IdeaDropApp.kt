package com.applab.ideadrop

import android.app.Application
import android.os.Build
import com.applab.ideadrop.data.AppDatabase
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.SettingsStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simple manual dependency container. */
class IdeaDropApp : Application() {
    lateinit var repository: NoteRepository private set
    lateinit var settings: SettingsStore private set

    /** Last crash report (saved on the previous run), shown on Home with a Share button. */
    val crashFile: File get() = File(filesDir, "last-crash.txt")

    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
        repository = NoteRepository(AppDatabase.create(this).noteDao())
        settings = SettingsStore(this)
    }

    /** Saves any crash to a file so it can be shared and fixed. Then lets Android close the app as usual. */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                crashFile.writeText(
                    "IdeaDrop crash report\n" +
                        "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
                        "App: $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n" + trace.take(12_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
