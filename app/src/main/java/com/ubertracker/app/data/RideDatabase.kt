package com.ubertracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Ride::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RideDatabase : RoomDatabase() {

    abstract fun rideDao(): RideDao

    companion object {
        @Volatile
        private var INSTANCE: RideDatabase? = null

        fun getDatabase(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "uber_expenses.db"
                )
                    .fallbackToDestructiveMigration() // VITAL: Wipes DB if version changes
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): RideDatabase {
            // Optional: Encrypt database with SQLCipher
            // Set to false if experiencing crashes - encryption requires SQLCipher native libraries
            val useEncryption = false // Temporarily disabled to avoid crashes

            return if (useEncryption) {
                val passphrase = SQLiteDatabase.getBytes("YourSecurePassphrase".toCharArray())
                val factory = SupportFactory(passphrase)

                Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "uber_expenses.db"
                )
                    .openHelperFactory(factory)
                    .addCallback(DatabaseCallback())
                    // WAL mode is enabled by default in Room 2.4.0+
                    //.addMigrations(MIGRATION_1_2)
                    .build()
            } else {
                Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "uber_expenses.db"
                )
                    .addCallback(DatabaseCallback())
                    // WAL mode is enabled by default in Room 2.4.0+
                    // Explicitly set it if needed (optional):
                    // .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    //.addMigrations(MIGRATION_1_2)
                    .build()
            }
        }

        // Database callback for initialization
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Perform any initialization here
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // WAL mode is enabled by default in Room 2.4.0+
                // No need to manually set PRAGMA journal_mode
            }
        }




    }
}