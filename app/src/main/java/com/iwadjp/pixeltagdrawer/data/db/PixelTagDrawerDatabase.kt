package com.iwadjp.pixeltagdrawer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * タグDB。launcher_apps / tags / app_tags を保持する。
 * version = 3 (tags に displayLabel を追加し、sortOrder を表示順で backfill)。
 */
@Database(
    entities = [
        LauncherAppEntity::class,
        TagEntity::class,
        AppTagCrossRef::class,
    ],
    version = 3,
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

        /**
         * v2→v3: tags に displayLabel (nullable) を追加し、
         * sortOrder を現在の表示順 (sortOrder ASC, name COLLATE NOCASE ASC, tagId ASC)
         * のまま 0..n-1 に backfill する。既存データは保持する。
         * sortOrder 自身を更新するため、順位は一時テーブルに確定してから書き戻す。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN displayLabel TEXT")
                db.execSQL(
                    "CREATE TEMP TABLE _tag_order AS " +
                        "SELECT tagId FROM tags " +
                        "ORDER BY sortOrder ASC, name COLLATE NOCASE ASC, tagId ASC"
                )
                db.execSQL(
                    "UPDATE tags SET sortOrder = " +
                        "(SELECT _tag_order.rowid - 1 FROM _tag_order WHERE _tag_order.tagId = tags.tagId)"
                )
                db.execSQL("DROP TABLE _tag_order")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
