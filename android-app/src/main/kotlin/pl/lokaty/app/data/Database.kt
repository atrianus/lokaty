package pl.lokaty.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** Ustawienia użytkownika (jeden wiersz). Kwoty jako tekst dziesiętny, nigdy Double. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val thresholdPln: String = "100.00",
    val capital: String = "10000.00",
    val horizonMonths: Int = 12,
    val maxLegs: Int = 3,
    val maxShown: Int = 4,
    val maxGapDays: Int = 7,
)

/** Zatwierdzona strategia (jedna aktywna). */
@Entity(tableName = "decision")
data class DecisionEntity(
    @PrimaryKey val id: Int = 1,
    val fingerprint: String,
    val description: String,
    val finalValue: String,
    val capital: String,
    val start: String,
    val horizonEnd: String,
    val lastNotifiedDifference: String?,
    val approvedAt: String,
)

/** Odrzucona strategia z wartością z chwili odrzucenia. */
@Entity(tableName = "rejected")
data class RejectedEntity(
    @PrimaryKey val fingerprint: String,
    val valueAtRejection: String,
    val rejectedAt: String,
)

/** Zawarta w banku noga strategii: wynik zapisany w chwili zawarcia (nie jest przeliczany). */
@Entity(tableName = "locked_leg")
data class LockedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val offerId: String,
    val start: String,
    val amount: String,
    val payout: String,
    val availableDate: String,
)

@Dao
interface AppDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Query("SELECT * FROM decision WHERE id = 1")
    suspend fun decision(): DecisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDecision(decision: DecisionEntity)

    @Query("DELETE FROM decision")
    suspend fun clearDecision()

    @Query("SELECT * FROM locked_leg ORDER BY availableDate, id")
    suspend fun lockedLegs(): List<LockedEntity>

    @Insert
    suspend fun addLocked(leg: LockedEntity)

    @Query("DELETE FROM locked_leg")
    suspend fun clearLocked()

    @Query("DELETE FROM rejected")
    suspend fun clearRejected()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Query("SELECT * FROM rejected")
    suspend fun rejected(): List<RejectedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun reject(rejected: RejectedEntity)
}

@Database(entities = [SettingsEntity::class, DecisionEntity::class, RejectedEntity::class, LockedEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lokaty.db")
                // Wersja testowa: przy zmianie schematu dane lokalne są kasowane (użytkownik może je zaimportować z eksportu).
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}

/** Wszystko w jednej transakcji: albo cała zmiana, albo nic. */
suspend fun AppDatabase.replaceAll(
    settings: SettingsEntity, decision: DecisionEntity?, locked: List<LockedEntity>, rejected: List<RejectedEntity>,
) = withTransaction {
    val d = dao()
    d.clearSettings(); d.clearDecision(); d.clearLocked(); d.clearRejected()
    d.saveSettings(settings)
    if (decision != null) d.saveDecision(decision)
    locked.forEach { d.addLocked(it.copy(id = 0)) }
    rejected.forEach { d.reject(it) }
}
