package com.iwadjp.pixeltagdrawer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * タグDBの土台。launcher_apps / tags / app_tags を保持する。
 * version = 1、exportSchema は当面 false。
 * 今回は土台のみで、既存のアプリ一覧取得・表示はDBへ未接続。
 */
@Database(
    entities = [
        LauncherAppEntity::class,
        TagEntity::class,
        AppTagCrossRef::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PixelTagDrawerDatabase : RoomDatabase() {

    abstract fun launcherAppDao(): LauncherAppDao
    abstract fun tagDao(): TagDao
    abstract fun appTagDao(): AppTagDao

    companion object {
        private const val DB_NAME = "pixel_tag_drawer.db"

        @Volatile
        private var instance: PixelTagDrawerDatabase? = null

        fun getInstance(context: Context): PixelTagDrawerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PixelTagDrawerDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
