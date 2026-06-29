package com.iwadjp.pixeltagdrawer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * タグDB。launcher_apps / tags / app_tags を保持する。
 * version = 2 (launcher_apps に起動履歴 launchCount / lastLaunchedAt を追加)。
 */
@Database(
    entities = [
        LauncherAppEntity::class,
        TagEntity::class,
        AppTagCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PixelTagDrawerDatabase : RoomDatabase() {

    abstract fun launcherAppDao(): LauncherAppDao
    abstract fun tagDao(): TagDao
    abstract fun appTagDao(): AppTagDao

    companion object {
        private const val DB_NAME = "pixel_tag_drawer.db"

        /**
         * v1→v2: launcher_apps に起動履歴カラムを追加する。
         * 既存行は launchCount=0 / lastLaunchedAt=0。既存データは保持する。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE launcher_apps ADD COLUMN launchCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE launcher_apps ADD COLUMN lastLaunchedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: PixelTagDrawerDatabase? = null

        fun getInstance(context: Context): PixelTagDrawerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PixelTagDrawerDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
