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

    @Query("SELECT * FROM rejected")
    suspend fun rejected(): List<RejectedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun reject(rejected: RejectedEntity)
}

@Database(entities = [SettingsEntity::class, DecisionEntity::class, RejectedEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lokaty.db")
                .build().also { instance = it }
        }
    }
}
