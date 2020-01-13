package de.ueen.roomassethelper

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.IOException

class RoomAssetHelper {

    //Mainly based on the great work of MikeT https://stackoverflow.com/a/59637092/3123142 Thanks a million!

    companion object {
        private val TAG = RoomAssetHelper::class.java.simpleName

        @JvmStatic
        fun <T : RoomDatabase> databaseBuilder(
            context: Context,
            klass: Class<T>,
            name: String,
            databasePath: String = "",
            version: Int,
            preserve: Array<TablePreserve> = emptyArray())
                : RoomDatabase.Builder<T> {

            if (getDBVersion(context, name) < version) copyFromAssets(context, name, databasePath, version, preserve)

            val builder = Room.databaseBuilder(context, klass, name)

            for (i in 2..version) {
                builder.addMigrations(object : Migration(i-1,i) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        Log.w(TAG, "instantiated")
                    }
                })
            }
            return builder
        }

        private fun doesDatabaseExist(context: Context, databaseName: String): Boolean {
            if (File(context.getDatabasePath(databaseName).path).exists()) return true
            if (!File(context.getDatabasePath(databaseName).path).parentFile.exists()) {
                File(context.getDatabasePath(databaseName).path).parentFile.mkdirs()
            }
            return false
        }

        private fun copyFromAssets(context: Context, databaseName: String, databasePath: String, version: Int, preserve: Array<TablePreserve>) {
            val dbExists = doesDatabaseExist(context, databaseName)

            //First Copy
            if (!dbExists) {
                copyAssetFile(context, databaseName, databasePath)
                setDBVersion(context, databaseName, version)
                return
            }
            //Subsequent Copies

            val originalDBPath = File(context.getDatabasePath(databaseName).path)
            // Open and close the original DB so as to checkpoint the WAL file
            val originalDB =
                SQLiteDatabase.openDatabase(originalDBPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            originalDB.close()

            //1. Rename original database
            val preservedDBName = "preserved_$databaseName"
            val preservedDBPath = File(originalDBPath.parentFile.path + File.separator + preservedDBName)
            File(context.getDatabasePath(databaseName).path)
                .renameTo(preservedDBPath)

            //2. Copy the replacement database from the assets folder
            copyAssetFile(context,databaseName, databasePath)

            //3. Open the newly copied database
            val copiedDB =
                SQLiteDatabase.openDatabase(originalDBPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            val preservedDB =
                SQLiteDatabase.openDatabase(preservedDBPath.path, null, SQLiteDatabase.OPEN_READONLY)

            //4. Apply preserved data to the newly copied data
            copiedDB.beginTransaction()
            for (tp in preserve) {
                preserveTableColumns(
                    preservedDB,
                    copiedDB,
                    tp.table,
                    tp.preserveColumns,
                    tp.macthByColumns,
                    true
                )
            }
            copiedDB.version = version
            copiedDB.setTransactionSuccessful()
            copiedDB.endTransaction()
            //5. Cleanup
            copiedDB.close()
            preservedDB.close()
            preservedDBPath.delete()
        }

        private fun copyAssetFile(context: Context, databaseName: String, databasePath: String) {
            try {
                context.assets.open(databasePath+databaseName).use { stream ->
                    File(context.getDatabasePath(databaseName).path).outputStream().use {
                        stream.copyTo(it)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("Unable to copy from assets")
            }

        }

        private fun getDBVersion(context: Context, databaseName: String): Int {
            if (!doesDatabaseExist(context, databaseName)) {
                return 0
            }
            val db = SQLiteDatabase.openDatabase(
                context.getDatabasePath(databaseName).path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val rv = db.version
            db.close()
            return rv
        }

        private fun setDBVersion(context: Context, databaseName: String, version: Int) {
            val db = SQLiteDatabase.openDatabase(
                context.getDatabasePath(databaseName).path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            db.version = version
            db.close()
        }

        private fun preserveTableColumns(
            originalDatabase: SQLiteDatabase,
            newDatabase: SQLiteDatabase,
            tableName: String,
            columnsToPreserve: Array<String>,
            whereClauseColumns: Array<String>,
            failWithException: Boolean
        ): Boolean {

            var sb = StringBuilder()
            var csr = originalDatabase.query(
                "sqlite_master",
                arrayOf("name"),
                "name=? AND type=?",
                arrayOf(tableName, "table"),
                null,
                null,
                null
            )
            if (!csr.moveToFirst()) {
                sb.append("\n\tTable ").append(tableName).append(" not found in database ")
                    .append(originalDatabase.path)
            }
            csr = newDatabase.query(
                "sqlite_master",
                arrayOf("name"),
                "name=? AND type=?",
                arrayOf(tableName, "table"),
                null,
                null,
                null
            )
            if (!csr.moveToFirst()) {
                sb.append("\n\tTable ").append(tableName).append(" not found in database ")
                    .append(originalDatabase.path)
            }
            if (sb.isNotEmpty()) {
                if (failWithException) {
                    throw RuntimeException("Both databases are required to have a table named $tableName$sb")
                }
                return false
            }

            sb = StringBuilder()
            for (c in whereClauseColumns) {
                sb.append(c).append("=? ")
            }
            val whereargs = arrayOfNulls<String>(whereClauseColumns.size)
            val columnsToExtract = whereClauseColumns + columnsToPreserve
            csr = originalDatabase.query(
                tableName,
                columnsToExtract,
                null,
                null,
                null,
                null,
                null
            )
            val cv = ContentValues()
            while (csr.moveToNext()) {
                cv.clear()
                for (pc in columnsToPreserve) {
                    when (csr.getType(csr.getColumnIndex(pc))) {
                        Cursor.FIELD_TYPE_INTEGER -> cv.put(pc, csr.getLong(csr.getColumnIndex(pc)))
                        Cursor.FIELD_TYPE_STRING -> cv.put(pc, csr.getString(csr.getColumnIndex(pc)))
                        Cursor.FIELD_TYPE_FLOAT -> cv.put(pc, csr.getDouble(csr.getColumnIndex(pc)))
                        Cursor.FIELD_TYPE_BLOB -> cv.put(pc, csr.getBlob(csr.getColumnIndex(pc)))
                    }
                }
                val waix = 0
                for (wa in whereClauseColumns) {
                    whereargs[waix] = csr.getString(csr.getColumnIndex(wa))
                }
                newDatabase.update(tableName, cv, sb.toString(), whereargs)
            }
            csr.close()
            return true
        }
    }



}

class TablePreserve(
    val table: String,
    val preserveColumns: Array<String>,
    val matchByColumns: Array<String>
)
