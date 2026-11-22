package pl.lokaty.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pl.lokaty.app.work.DailyCheckWorker
import java.util.concurrent.TimeUnit

class LokatyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Raz na dobę; KEEP nie resetuje terminu przy każdym uruchomieniu aplikacji.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
