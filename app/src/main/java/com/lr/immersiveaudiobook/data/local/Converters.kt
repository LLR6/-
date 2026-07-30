package com.lr.immersiveaudiobook.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun importStateToString(value: ImportState): String = value.name

    @TypeConverter
    fun stringToImportState(value: String): ImportState =
        runCatching { ImportState.valueOf(value) }.getOrDefault(ImportState.ERROR)
}
