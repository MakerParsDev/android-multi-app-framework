package com.parsfilo.contentapp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val helpers = mutableListOf<SupportSQLiteOpenHelper>()

    @After
    fun tearDown() {
        helpers.forEach { it.close() }
    }

    @Test
    fun `migration 3 to 4 does not create unexpected quran_bookmarks savedAt index`() {
        val db = createDatabase()

        AppDatabase.MIGRATION_3_4.migrate(db)

        val bookmarkIndexes = queryIndexNames(db, "quran_bookmarks")

        assertThat(bookmarkIndexes).doesNotContain("index_quran_bookmarks_savedAt")
        // Sanity check: migration still creates the table.
        assertThat(queryTableExists(db, "quran_bookmarks")).isTrue()
    }

    @Test
    fun `migration 4 to 5 repairs legacy quran_bookmarks schema and removes savedAt index`() {
        val db = createDatabase()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quran_bookmarks` (" +
                "`suraNumber` INTEGER NOT NULL, " +
                "`ayahNumber` INTEGER NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "PRIMARY KEY(`suraNumber`, `ayahNumber`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quran_bookmarks_savedAt` " +
                "ON `quran_bookmarks` (`savedAt`)"
        )
        db.execSQL(
            "INSERT INTO `quran_bookmarks` (`suraNumber`, `ayahNumber`, `savedAt`, `note`) " +
                "VALUES (1, 1, 1000, NULL)"
        )

        AppDatabase.MIGRATION_4_5.migrate(db)

        assertThat(queryIndexNames(db, "quran_bookmarks")).doesNotContain("index_quran_bookmarks_savedAt")
        assertThat(queryColumnNotNull(db, "quran_bookmarks", "note")).isTrue()
        assertThat(queryBookmarkNote(db, 1, 1)).isEqualTo("")
    }

    private fun createDatabase(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                }
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helpers += helper
        return helper.writableDatabase
    }

    private fun queryIndexNames(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): List<String> {
        val cursor = db.query("PRAGMA index_list(`$tableName`)")
        cursor.use {
            val names = mutableListOf<String>()
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (nameIndex >= 0) {
                    names += it.getString(nameIndex)
                }
            }
            return names
        }
    }

    private fun queryTableExists(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): Boolean {
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName),
        )
        cursor.use { return it.moveToFirst() }
    }

    private fun queryColumnNotNull(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        val cursor = db.query("PRAGMA table_info(`$tableName`)")
        cursor.use {
            val nameIndex = it.getColumnIndex("name")
            val notNullIndex = it.getColumnIndex("notnull")
            while (it.moveToNext()) {
                if (nameIndex >= 0 && it.getString(nameIndex) == columnName) {
                    return notNullIndex >= 0 && it.getInt(notNullIndex) == 1
                }
            }
        }
        return false
    }

    private fun queryBookmarkNote(
        db: SupportSQLiteDatabase,
        sura: Int,
        ayah: Int,
    ): String? {
        val cursor = db.query(
            "SELECT note FROM quran_bookmarks WHERE suraNumber=? AND ayahNumber=?",
            arrayOf(sura, ayah),
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(0)
        }
    }
}
