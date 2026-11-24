package com.webify.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WebProjectEntity::class, KeystoreEntity::class, BuildRecordEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WebifyDatabase : RoomDatabase() {
    abstract fun projectDao(): WebProjectDao
    abstract fun keystoreDao(): KeystoreDao
    abstract fun buildRecordDao(): BuildRecordDao
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): WebifyDatabase =
            Room.databaseBuilder(context.applicationContext, WebifyDatabase::class.java, "webify.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
