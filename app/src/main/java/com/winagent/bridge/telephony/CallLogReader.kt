package com.winagent.bridge.telephony

import android.content.Context
import android.provider.CallLog

data class MissedCall(
    val id: Long,
    val number: String?,
    val cachedName: String?,
    val dateMs: Long,
    val durationSec: Long,
    val isNew: Boolean
)

object CallLogReader {
    /**
     * Reads up to [limit] missed calls.
     * If possible, filters to "new" missed calls (NEW=1).
     */
    fun queryMissedCalls(context: Context, limit: Int = 20, onlyNew: Boolean = true): List<MissedCall> {
        if (limit <= 0) return emptyList()
        val cr = context.contentResolver

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.NEW
        )

        // TYPE = MISSED
        val selection = buildString {
            append(CallLog.Calls.TYPE)
            append(" = ?")
            if (onlyNew) {
                append(" AND ")
                append(CallLog.Calls.NEW)
                append(" = 1")
            }
        }

        val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())

        // IMPORTANT: Do NOT use "LIMIT" in sortOrder. Some OEM providers crash.
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        val out = ArrayList<MissedCall>(minOf(limit, 32))

        val cursor = try {
            cr.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder)
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }

        cursor?.use { c ->
            val idxId = c.getColumnIndex(CallLog.Calls._ID)
            val idxNum = c.getColumnIndex(CallLog.Calls.NUMBER)
            val idxName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val idxDate = c.getColumnIndex(CallLog.Calls.DATE)
            val idxDur = c.getColumnIndex(CallLog.Calls.DURATION)
            val idxNew = c.getColumnIndex(CallLog.Calls.NEW)

            while (c.moveToNext() && out.size < limit) {
                val id = if (idxId >= 0) c.getLong(idxId) else 0L
                val number = if (idxNum >= 0) c.getString(idxNum) else null
                val name = if (idxName >= 0) c.getString(idxName) else null
                val date = if (idxDate >= 0) c.getLong(idxDate) else 0L
                val dur = if (idxDur >= 0) c.getLong(idxDur) else 0L
                val isNewVal = if (idxNew >= 0) c.getInt(idxNew) == 1 else true
                out += MissedCall(id, number, name, date, dur, isNewVal)
            }
        }

        return out
    }
}
