package com.iwadjp.pixeltagdrawer.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MIGRATION_3_4 (app_tags の孤児行削除) の単体テスト。
 * Room の schema exportは使わず (exportSchema=false)、実際のテーブル定義に相当する
 * 最小限のSQLで v3 相当のテーブルを作り、実際の Migration オブジェクトを直接実行して検証する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration3To4Test {

    private fun openHelper(): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE tags (tagId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT NOT NULL, sortOrder INTEGER NOT NULL, displayLabel TEXT)",
                    )
                    db.execSQL(
                        "CREATE TABLE app_tags (packageName TEXT NOT NULL, className TEXT NOT NULL, " +
                            "tagId INTEGER NOT NULL, PRIMARY KEY(packageName, className, tagId))",
                    )
                    db.execSQL(
                        "CREATE TABLE launcher_apps (packageName TEXT NOT NULL, className TEXT NOT NULL, " +
                            "label TEXT NOT NULL, isInstalled INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, " +
                            "launchCount INTEGER NOT NULL, lastLaunchedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(packageName, className))",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // このテストでは呼ばれない (migrate() を直接実行するため)
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @Test
    fun `migration removes only orphan app_tags rows`() {
        val helper = openHelper()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO tags (tagId, name, sortOrder, displayLabel) VALUES (1, 'work', 0, NULL)")
        db.execSQL("INSERT INTO tags (tagId, name, sortOrder, displayLabel) VALUES (2, 'hobby', 1, NULL)")
        db.execSQL(
            "INSERT INTO launcher_apps VALUES ('com.example.app', 'com.example.app.Main', " +
                "'Example', 1, 100, 3, 200)",
        )

        // valid: tagId=1,2 は tags に存在する
        db.execSQL("INSERT INTO app_tags VALUES ('com.example.app', 'com.example.app.Main', 1)")
        db.execSQL("INSERT INTO app_tags VALUES ('com.example.app', 'com.example.app.Main', 2)")
        // orphan: tagId=8 は tags に存在しない (削除済みタグの残骸を模擬)
        db.execSQL(
            "INSERT INTO app_tags VALUES " +
                "('jp.co.rakuten.kc.rakutencardapp.android', " +
                "'jp.co.rakuten.kc.rakutencardapp.android.common.view.MainActivity', 8)",
        )

        PixelTagDrawerDatabase.MIGRATION_3_4.migrate(db)

        val remaining = mutableListOf<Long>()
        db.query("SELECT tagId FROM app_tags ORDER BY tagId ASC").use { cursor ->
            while (cursor.moveToNext()) remaining.add(cursor.getLong(0))
        }
        assertEquals(listOf(1L, 2L), remaining)

        var tagCount = 0
        db.query("SELECT COUNT(*) FROM tags").use { cursor ->
            cursor.moveToFirst()
            tagCount = cursor.getInt(0)
        }
        assertEquals(2, tagCount)

        var appCount = 0
        db.query("SELECT COUNT(*) FROM launcher_apps").use { cursor ->
            cursor.moveToFirst()
            appCount = cursor.getInt(0)
        }
        assertEquals(1, appCount)

        helper.close()
    }
}
