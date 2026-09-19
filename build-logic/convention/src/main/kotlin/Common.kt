import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Common {
    /**
     * Minute-precision versionCode that fits in Android Int (max ~2.1e9).
     *
     * Raw yyMMddHHmm overflows (e.g. 2609191530 > Int.MAX_VALUE), so we pack:
     * (year-2020)*100_000_00 + MM*1_000_00 + dd*10_000 + HH*100 + mm
     * Example: 2026-09-19 15:30 → 609191530.
     * Same-hour double releases no longer collide.
     */
    fun getBuildVersionCode(): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"), Locale.ENGLISH)
        val yearOffset = cal.get(Calendar.YEAR) - 2020
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return yearOffset * 100_000_00 +
            month * 1_000_00 +
            day * 10_000 +
            hour * 100 +
            minute
    }

    private fun getCurrentDate(project: Project): String {
        val override = project.findProperty("buildVersionTimestamp") as String?
        if (!override.isNullOrBlank()) {
            return override
        }

        val sdf = SimpleDateFormat("MMddHHmm", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Taipei")
        return sdf.format(Date())
    }


    private fun getShortGitRevision(): String {
        val command = "git rev-parse --short HEAD"
        val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
        val process = processBuilder.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            output.trim()
        } else {
            "no_commit"
        }
    }

    fun getBuildVersionName(project: Project): String {
        return "${getShortGitRevision()}.${getCurrentDate(project)}"
    }
}

