package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_tracks")
data class SavedTrackEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val artistOrFolder: String,
    val mimeType: String,
    val durationMs: Long,
    val lastPositionMs: Long = 0L,
    val playlistName: String = "القائمة الرئيسية",
    val isFavorite: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastPlayedTimestamp: Long = 0L
)

@Entity(tableName = "track_bookmarks")
data class TrackBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackUriString: String,
    val trackTitle: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SplitAudioDao {
    @Query("SELECT * FROM saved_tracks ORDER BY lastPlayedTimestamp DESC, addedTimestamp DESC")
    fun observeAllTracks(): Flow<List<SavedTrackEntity>>

    @Query("SELECT * FROM saved_tracks WHERE uriString = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): SavedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: SavedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIgnoreExisting(tracks: List<SavedTrackEntity>)

    @Query("UPDATE saved_tracks SET lastPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastPlayedTimestamp = :timestamp WHERE uriString = :uri")
    suspend fun updatePlaybackProgress(uri: String, positionMs: Long, durationMs: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_tracks SET playlistName = :playlistName WHERE uriString = :uri")
    suspend fun updateTrackPlaylist(uri: String, playlistName: String)

    @Query("UPDATE saved_tracks SET isFavorite = :isFavorite WHERE uriString = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("DELETE FROM saved_tracks WHERE uriString = :uri")
    suspend fun deleteTrack(uri: String)

    @Query("SELECT * FROM track_bookmarks ORDER BY createdAt DESC")
    fun observeAllBookmarks(): Flow<List<TrackBookmarkEntity>>

    @Query("SELECT * FROM track_bookmarks WHERE trackUriString = :trackUri ORDER BY positionMs ASC")
    fun observeBookmarksForTrack(trackUri: String): Flow<List<TrackBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: TrackBookmarkEntity)

    @Query("DELETE FROM track_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)
}

@Database(
    entities = [SavedTrackEntity::class, TrackBookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SplitAudioDatabase : RoomDatabase() {
    abstract fun splitAudioDao(): SplitAudioDao

    companion object {
        @Volatile
        private var INSTANCE: SplitAudioDatabase? = null

        fun getInstance(context: Context): SplitAudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SplitAudioDatabase::class.java,
                    "vivo_y05_split_audio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
