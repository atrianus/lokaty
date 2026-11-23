package pl.lokaty.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pl.lokaty.app.data.Repository
import pl.lokaty.app.data.toSettings
import pl.lokaty.app.ui.money

/** Codzienna kontrola w tle: najlepsza strategia z bieżących ofert kontra zatwierdzona decyzja. Dane zostają na telefonie. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val decision = repo.decision() ?: return Result.success()
        val settings = repo.settings().toSettings()
        // Świeże oferty (jeśli jest sieć); przy błędzie zostają dane zapisane wcześniej.
        val snapshot = repo.refreshSnapshot().snapshot
        val base = repo.base(settings, repo.lockedLegs(), java.time.LocalDate.now())
        val check = repo.check(snapshot, decision, settings, base, repo.rejected())
        if (check.notify && check.comparison != null) {
            notify("Lepsza strategia: +${money(check.comparison!!.difference)}", "Otwórz aplikację, żeby zobaczyć propozycję.")
            repo.saveDecision(decision.copy(lastNotifiedDifference = check.comparison!!.difference.toPlainString()))
        }
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val granted = sdkBelow33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Strategie lokat", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1, n)
    }

    private val sdkBelow33: Boolean get() = android.os.Build.VERSION.SDK_INT < 33

    companion object {
        const val CHANNEL = "strategie"
    }
}
