package com.chenxiaocai.todobar.inbox

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class InboxItem(val id: String, val text: String, val createdAt: Long, val deliveredAt: Long?)

class InboxDatabase(context: Context) : SQLiteOpenHelper(context, "inbox.db", null, 1) {
    private val state = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = state

    init { reload() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE inbox (id TEXT PRIMARY KEY, text TEXT NOT NULL, created_at INTEGER NOT NULL, delivered_at INTEGER)")
        db.execSQL("CREATE INDEX inbox_pending ON inbox(delivered_at, created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun add(text: String): InboxItem {
        val value = text.trim()
        require(value.isNotEmpty() && value.toByteArray().size <= 16_384)
        val item = InboxItem(UUID.randomUUID().toString(), value, System.currentTimeMillis(), null)
        writableDatabase.insertOrThrow("inbox", null, ContentValues().apply {
            put("id", item.id); put("text", item.text); put("created_at", item.createdAt)
        })
        Log.i("ToDoBarInbox", "Inbox item added id=${item.id} textByteCount=${value.toByteArray().size} createdAt=${item.createdAt}")
        reload(); return item
    }

    @Synchronized fun pending(): List<InboxItem> = query("delivered_at IS NULL")
    @Synchronized fun deletePending(id: String) {
        writableDatabase.delete("inbox", "id=? AND delivered_at IS NULL", arrayOf(id))
        Log.i("ToDoBarInbox", "Pending item delete id=$id"); reload()
    }
    @Synchronized fun acknowledge(ids: List<String>) {
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            ids.forEach { writableDatabase.update("inbox", ContentValues().apply { put("delivered_at", now) }, "id=? AND delivered_at IS NULL", arrayOf(it)) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        Log.i("ToDoBarInbox", "Items acknowledged ids=$ids deliveredAt=$now"); reload()
    }
    @Synchronized fun clearDelivered() {
        val count = writableDatabase.delete("inbox", "delivered_at IS NOT NULL", null)
        Log.i("ToDoBarInbox", "Delivered history cleared count=$count"); reload()
    }
    @Synchronized private fun reload() { state.value = query(null) }
    private fun query(where: String?): List<InboxItem> {
        val result = mutableListOf<InboxItem>()
        readableDatabase.query("inbox", arrayOf("id", "text", "created_at", "delivered_at"), where, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) result += InboxItem(cursor.getString(0), cursor.getString(1), cursor.getLong(2), if (cursor.isNull(3)) null else cursor.getLong(3))
        }
        return result
    }
}
