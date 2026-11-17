package com.plumb5.monitor.database;

import android.content.Context;

import androidx.room.Database;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SupportFactory;

@Database(
        entities = {
                EventEntity.class,
                TransactionEntity.class
        },
        version = 5,
        exportSchema = false
)
public abstract class MonitoringDatabase extends RoomDatabase {

    public abstract EventDao eventDao();

    public abstract TransactionDao transactionDao();

    private static volatile MonitoringDatabase INSTANCE;

    public static MonitoringDatabase getInstance(Context context) {

        if (INSTANCE == null) {

            synchronized (MonitoringDatabase.class) {

                if (INSTANCE == null) {

                    Context appContext =
                            context.getApplicationContext();

                    SQLiteDatabase.loadLibs(appContext);

                    String key;

                    try {

                        key = androidx.security.crypto.MasterKeys
                                .getOrCreate(
                                        androidx.security.crypto.MasterKeys
                                                .AES256_GCM_SPEC
                                );

                    } catch (Exception e) {

                        key = "P5_FALLBACK_SECURE_KEY_"
                                + appContext.getPackageName();
                    }

                    byte[] passphrase =
                            key.getBytes();

                    SupportFactory factory =
                            new SupportFactory(passphrase);

                    Migration migration4to5 = new Migration(4, 5) {
                        @Override
                        public void migrate(@NonNull SupportSQLiteDatabase db) {
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `monitoring_transactions` (" +
                                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                            "`transactionId` TEXT NOT NULL, " +
                                            "`appId` TEXT, " +
                                            "`sessionId` TEXT, " +
                                            "`userId` TEXT, " +
                                            "`timestamp` INTEGER NOT NULL, " +
                                            "`transactionData` TEXT, " +
                                            "`retryCount` INTEGER NOT NULL DEFAULT 0)"
                            );

                            db.execSQL(
                                    "CREATE INDEX IF NOT EXISTS `index_monitoring_transactions_timestamp` " +
                                            "ON `monitoring_transactions` (`timestamp`)"
                            );
                            db.execSQL(
                                    "CREATE INDEX IF NOT EXISTS `index_monitoring_transactions_appId` " +
                                            "ON `monitoring_transactions` (`appId`)"
                            );
                            db.execSQL(
                                    "CREATE INDEX IF NOT EXISTS `index_monitoring_transactions_sessionId` " +
                                            "ON `monitoring_transactions` (`sessionId`)"
                            );
                        }
                    };

                    INSTANCE =
                            Room.databaseBuilder(
                                            appContext,
                                            MonitoringDatabase.class,
                                            "monitoring.db"
                                    )
                                    .openHelperFactory(factory)
                                    .addMigrations(migration4to5)
                                    .fallbackToDestructiveMigration()
                                    .allowMainThreadQueries()
                                    .build();
                }
            }
        }

        return INSTANCE;
    }
}