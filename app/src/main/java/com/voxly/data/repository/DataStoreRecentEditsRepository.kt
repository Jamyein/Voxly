package com.voxly.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.RecentEdit
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentEditsDataStore by preferencesDataStore(name = "recent_edits")

@Singleton
class DataStoreRecentEditsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : RecentEditsRepository {

    companion object {
        private val RECENT_EDITS_JSON = stringPreferencesKey("recent_edits_json")
        private const val MAX_ENTRIES = 1000
    }

    override fun getRecentEdits(limit: Int): Flow<List<RecentEdit>> {
        val safeLimit = if (limit <= 0) Int.MAX_VALUE else limit
        return context.recentEditsDataStore.data.map { prefs ->
            decodeRecentEdits(prefs[RECENT_EDITS_JSON]).take(safeLimit)
        }
    }

    override suspend fun addRecentEdit(
        filePath: String,
        originalMetadata: AudioMetadata,
        newMetadata: AudioMetadata
    ) {
        context.recentEditsDataStore.edit { prefs ->
            val current = decodeRecentEdits(prefs[RECENT_EDITS_JSON]).toMutableList()
            val entry = RecentEdit(
                filePath = filePath,
                fileName = filePath.substringAfterLast('/').substringAfterLast('\\'),
                timestamp = System.currentTimeMillis(),
                originalMetadata = originalMetadata.withoutAlbumArt(),
                newMetadata = newMetadata.withoutAlbumArt()
            )
            current.add(0, entry)
            val trimmed = current.take(MAX_ENTRIES)
            prefs[RECENT_EDITS_JSON] = encodeRecentEdits(trimmed)
        }
    }

    override suspend fun clearRecentEdits() {
        context.recentEditsDataStore.edit { prefs ->
            prefs.remove(RECENT_EDITS_JSON)
        }
    }

    private fun decodeRecentEdits(raw: String?): List<RecentEdit> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        RecentEdit(
                            filePath = obj.optString("filePath"),
                            fileName = obj.optString("fileName"),
                            timestamp = obj.optLong("timestamp"),
                            originalMetadata = decodeMetadata(obj.optJSONObject("originalMetadata")),
                            newMetadata = decodeMetadata(obj.optJSONObject("newMetadata"))
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun encodeRecentEdits(edits: List<RecentEdit>): String {
        val arr = JSONArray()
        edits.forEach { edit ->
            val obj = JSONObject().apply {
                put("filePath", edit.filePath)
                put("fileName", edit.fileName)
                put("timestamp", edit.timestamp)
                put("originalMetadata", encodeMetadata(edit.originalMetadata))
                put("newMetadata", encodeMetadata(edit.newMetadata))
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeMetadata(obj: JSONObject?): AudioMetadata {
        if (obj == null) return AudioMetadata()
        val customFieldsObj = obj.optJSONObject("customFields")
        val customFields = buildMap<String, String> {
            if (customFieldsObj != null) {
                val keys = customFieldsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, customFieldsObj.optString(key))
                }
            }
        }
        return AudioMetadata(
            title = obj.optNullableString("title"),
            artist = obj.optNullableString("artist"),
            album = obj.optNullableString("album"),
            albumArtist = obj.optNullableString("albumArtist"),
            year = obj.optNullableString("year"),
            genre = obj.optNullableString("genre"),
            trackNumber = obj.optNullableInt("trackNumber"),
            totalTracks = obj.optNullableInt("totalTracks"),
            discNumber = obj.optNullableInt("discNumber"),
            totalDiscs = obj.optNullableInt("totalDiscs"),
            composer = obj.optNullableString("composer"),
            lyricist = obj.optNullableString("lyricist"),
            conductor = obj.optNullableString("conductor"),
            originalArtist = obj.optNullableString("originalArtist"),
            comment = obj.optNullableString("comment"),
            lyrics = obj.optNullableString("lyrics"),
            albumArt = null,
            customFields = customFields
        )
    }

    private fun encodeMetadata(metadata: AudioMetadata): JSONObject {
        val customFields = JSONObject().apply {
            metadata.customFields.forEach { (key, value) -> put(key, value) }
        }
        return JSONObject().apply {
            putNullable("title", metadata.title)
            putNullable("artist", metadata.artist)
            putNullable("album", metadata.album)
            putNullable("albumArtist", metadata.albumArtist)
            putNullable("year", metadata.year)
            putNullable("genre", metadata.genre)
            putNullable("trackNumber", metadata.trackNumber)
            putNullable("totalTracks", metadata.totalTracks)
            putNullable("discNumber", metadata.discNumber)
            putNullable("totalDiscs", metadata.totalDiscs)
            putNullable("composer", metadata.composer)
            putNullable("lyricist", metadata.lyricist)
            putNullable("conductor", metadata.conductor)
            putNullable("originalArtist", metadata.originalArtist)
            putNullable("comment", metadata.comment)
            putNullable("lyrics", metadata.lyrics)
            put("customFields", customFields)
        }
    }

    private fun AudioMetadata.withoutAlbumArt(): AudioMetadata = copy(albumArt = null)

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
