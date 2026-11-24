package com.webify.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WebProjectEntity::class, KeystoreEntity::class, BuildRecordEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WebifyDatabase : RoomDatabase() {
    abstract fun projectDao(): WebProjectDao
    abstract fun keystoreDao(): KeystoreDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun chatDao(): ChatDao

    companion object {
        /** v1 → v2: opsi WebView lanjutan. Data proyek lama dipertahankan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cols = listOf(
                    "desktopMode" to 0, "textZoomPercent" to 100, "forceDarkMode" to 0, "hideScrollbars" to 0,
                    "adBlockEnabled" to 0, "allowThirdPartyCookies" to 1, "safeBrowsing" to 1, "preventScreenshots" to 0,
                    "clearCacheOnStart" to 0, "keepScreenOn" to 0, "confirmExit" to 0, "disableTextSelection" to 0,
                    "autoplayMedia" to 1
                )
                cols.forEach { (name, def) ->
                    db.execSQL("ALTER TABLE web_projects ADD COLUMN `$name` INTEGER NOT NULL DEFAULT $def")
                }
            }
        }

        /** v2 → v3: navigasi native, shortcut, biometrik, plugin SDK, auto-increment. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val ints = listOf("autoIncrementVersionCode" to 1, "enableBiometricLock" to 0, "enableAboutDialog" to 1,
                    "fcmEnabled" to 0, "admobEnabled" to 0, "qrScannerEnabled" to 0)
                ints.forEach { (n, d) -> db.execSQL("ALTER TABLE web_projects ADD COLUMN `$n` INTEGER NOT NULL DEFAULT $d") }
                val texts = listOf("navStyle" to "NONE", "navItemsJson" to "", "shortcutsJson" to "", "privacyPolicyUrl" to "",
                    "admobAppId" to "", "admobBannerId" to "", "admobInterstitialId" to "", "extraDependencies" to "", "localLibraries" to "")
                texts.forEach { (n, d) -> db.execSQL("ALTER TABLE web_projects ADD COLUMN `$n` TEXT NOT NULL DEFAULT '$d'") }
                db.execSQL("ALTER TABLE web_projects ADD COLUMN `googleServicesJsonPath` TEXT")
            }
        }

        fun create(context: Context): WebifyDatabase =
            Room.databaseBuilder(context.applicationContext, WebifyDatabase::class.java, "webify.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
