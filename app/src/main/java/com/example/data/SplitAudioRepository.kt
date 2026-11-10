package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SplitAudioRepository(
    private val context: Context,
    private val dao: SplitAudioDao
) {
    val allTracksFlow: Flow<List<SavedTrackEntity>> = dao.observeAllTracks()
    val allBookmarksFlow: Flow<List<TrackBookmarkEntity>> = dao.observeAllBookmarks()

    private val supportedExtensions = setOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus")

    suspend fun scanDeviceLocalAudio(): Int = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<SavedTrackEntity>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000")
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: ""
                    val mimeType = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    val isSupportedMime = mimeType.startsWith("audio/") || ext in supportedExtensions
                    if (!isSupportedMime) continue

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val rawTitle = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.').ifBlank { "ملف صوتي #$id" }
                    val rawArtist = cursor.getString(artistCol)?.takeIf {
                        it.isNotBlank() && it != "<unknown>"
                    } ?: cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "تخزين الهاتف (vivo Y05)"
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)

                    discovered.add(
                        SavedTrackEntity(
                            uriString = contentUri.toString(),
                            title = rawTitle,
                            artistOrFolder = rawArtist,
                            mimeType = mimeType,
                            durationMs = durationMs,
                            playlistName = "ملفات الجهاز"
                        )
                    )
                }
            }
        }

        if (discovered.isNotEmpty()) {
            dao.insertTracksIgnoreExisting(discovered)
        }
        discovered.size
    }

    suspend fun importPickedUris(uris: List<Uri>, playlistName: String = "ملفات مختارة"): List<SavedTrackEntity> =
        withContext(Dispatchers.IO) {
            val imported = mutableListOf<SavedTrackEntity>()
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val entity = buildTrackEntityFromUri(uri, playlistName)
                if (entity != null) {
                    val existing = dao.getTrackByUri(entity.uriString)
                    val merged = if (existing != null) {
                        entity.copy(
                            lastPositionMs = existing.lastPositionMs,
                            isFavorite = existing.isFavorite,
                            lastPlayedTimestamp = existing.lastPlayedTimestamp
                        )
                    } else {
                        entity
                    }
                    dao.upsertTrack(merged)
                    imported.add(merged)
                }
            }
            imported
        }

    suspend fun importFolderTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val discovered = mutableListOf<SavedTrackEntity>()
        val folderLabel = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "مجلد صوتي" }

        runCatching {
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol) ?: continue
                    val displayName = cursor.getString(nameCol) ?: ""
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    if (mimeType.startsWith("audio/") || ext in supportedExtensions) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val durationMs = extractDurationMs(fileUri)
                        discovered.add(
                            SavedTrackEntity(
                                uriString = fileUri.toString(),
                                title = displayName.substringBeforeLast('.').ifBlank { displayName },
                                artistOrFolder = "مجلد: $folderLabel",
                                mimeType = mimeType.ifBlank { "audio/mpeg" },
                                durationMs = durationMs,
                                playlistName = folderLabel
                            )
                        )
                    }
                }
            }
        }

        if (discovered.isNotEmpty()) {
            dao.insertTracksIgnoreExisting(discovered)
        }
        discovered.size
    }

    suspend fun ensureBuiltInDemoPodcastTracks(): List<SavedTrackEntity> = withContext(Dispatchers.IO) {
        val demoDir = File(context.filesDir, "demo_podcasts").apply { mkdirs() }
        val demoSpecs = listOf(
            Triple(
                "vivo_y05_bluetooth_test_1.wav",
                "حلقة تجريبية ١: اختبار عزل صوت البلوتوث (A2DP)",
                "بودكاست هندسة الصوت • مسار البلوتوث المعزول"
            ),
            Triple(
                "vivo_y05_bluetooth_test_2.wav",
                "حلقة تجريبية ٢: الاستماع المتزامن مع يوتيوب بدون انقطاع",
                "اختبار OriginOS 6 • تجاوز تركيز الصوت AudioFocus"
            )
        )

        val entities = mutableListOf<SavedTrackEntity>()
        demoSpecs.forEachIndexed { index, (fileName, title, subtitle) ->
            val file = File(demoDir, fileName)
            if (!file.exists() || file.length() < 1024L) {
                generateWarmAmbientWavFile(
                    targetFile = file,
                    durationSeconds = 36,
                    baseFreqHz = if (index == 0) 220.0 else 261.63
                )
            }
            val uriString = Uri.fromFile(file).toString()
            val entity = SavedTrackEntity(
                uriString = uriString,
                title = title,
                artistOrFolder = subtitle,
                mimeType = "audio/wav",
                durationMs = 36_000L,
                playlistName = "حلقات اختبار البلوتوث"
            )
            entities.add(entity)
        }
        dao.insertTracksIgnoreExisting(entities)
        entities
    }

    suspend fun saveProgress(uriString: String, positionMs: Long, durationMs: Long) {
        dao.updatePlaybackProgress(uriString, positionMs, durationMs)
    }

    suspend fun toggleFavorite(uriString: String, isFavorite: Boolean) {
        dao.updateFavorite(uriString, isFavorite)
    }

    suspend fun assignToPlaylist(uriString: String, playlistName: String) {
        dao.updateTrackPlaylist(uriString, playlistName)
    }

    suspend fun removeTrack(uriString: String) {
        dao.deleteTrack(uriString)
    }

    suspend fun addBookmark(trackUriString: String, trackTitle: String, positionMs: Long, label: String) {
        dao.insertBookmark(
            TrackBookmarkEntity(
                trackUriString = trackUriString,
                trackTitle = trackTitle,
                positionMs = positionMs,
                label = label
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        dao.deleteBookmark(bookmarkId)
    }

    private fun buildTrackEntityFromUri(uri: Uri, playlistName: String): SavedTrackEntity? {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف صوتي"
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
        }
        val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
        val durationMs = extractDurationMs(uri)
        val cleanTitle = displayName.substringBeforeLast('.').ifBlank { displayName }

        return SavedTrackEntity(
            uriString = uri.toString(),
            title = cleanTitle,
            artistOrFolder = playlistName,
            mimeType = mimeType,
            durationMs = durationMs,
            playlistName = playlistName
        )
    }

    private fun extractDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun generateWarmAmbientWavFile(
        targetFile: File,
        durationSeconds: Int,
        baseFreqHz: Double
    ) {
        val sampleRate = 16000
        val numSamples = sampleRate * durationSeconds
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val dataSize = numSamples * numChannels * bitsPerSample / 8

        FileOutputStream(targetFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((numChannels * bitsPerSample / 8).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chunkSamples = 4096
            val buffer = ByteBuffer.allocate(chunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            var sampleIndex = 0
            while (sampleIndex < numSamples) {
                buffer.clear()
                val count = minOf(chunkSamples, numSamples - sampleIndex)
                for (i in 0 until count) {
                    val t = (sampleIndex + i).toDouble() / sampleRate
                    // Gentle rhythmic chord pulse so the user clearly hears the Bluetooth audio stream
                    val envelope = 0.45 + 0.35 * sin(2.0 * PI * 0.5 * t)
                    val fundamental = sin(2.0 * PI * baseFreqHz * t)
                    val fifth = 0.5 * sin(2.0 * PI * (baseFreqHz * 1.498) * t)
                    val octave = 0.25 * sin(2.0 * PI * (baseFreqHz * 2.0) * t)
                    val sampleValue = ((fundamental + fifth + octave) / 1.75 * envelope * 12000).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    buffer.putShort(sampleValue)
                }
                fos.write(buffer.array(), 0, count * 2)
                sampleIndex += count
            }
        }
    }
}
