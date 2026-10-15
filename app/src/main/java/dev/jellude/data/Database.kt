package dev.jellude.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val disc: Int = 0,
    val number: Int = 0,
    val durationMs: Long = 0,
    val imageTag: String = "",
    val sourceTag: String = "",
    val albumArtistIds: String = "",
    @ColumnInfo(defaultValue = "''") val codec: String = "",
    @ColumnInfo(defaultValue = "0") val bitrate: Int = 0,
    @ColumnInfo(defaultValue = "0") val sampleRate: Int = 0
)

@Entity(tableName = "selections")
data class Selection(@PrimaryKey val id: String, val title: String, val kind: String,
    val keepSynced: Boolean, val refreshedAt: Long = 0, val error: String? = null,
    @ColumnInfo(defaultValue = "1") val retained: Boolean = true)

@Entity(tableName = "members", primaryKeys = ["selectionId", "trackId"],
    indices = [Index("trackId")])
data class Member(val selectionId: String, val trackId: String)

@Entity(tableName = "assets")
data class Asset(@PrimaryKey val trackId: String, val state: String = "Queued",
    val bytes: Long = 0, val hash: String = "", val error: String? = null,
    val verifiedAt: Long = 0, val sourceTag: String = "", val transferState: String = "")

@Entity(tableName = "lyrics")
data class StoredLyrics(@PrimaryKey val trackId: String, val json: String, val fetchedAt: Long)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "entryId"], indices = [Index("trackId")])
data class PlaylistEntry(val playlistId: String, val entryId: String, val trackId: String, val position: Int)

@Entity(tableName = "edits")
data class PlaylistEdit(@PrimaryKey val id: String, val playlistId: String, val operation: String,
    val value: String, val baseJson: String, val status: String = "Pending", val error: String? = null,
    val createdAt: Long = System.currentTimeMillis())

@Dao
interface LibraryDao {
    @Query("SELECT * FROM items ORDER BY title COLLATE NOCASE") fun observeItems(): Flow<List<Item>>
    @Query("SELECT * FROM items ORDER BY title COLLATE NOCASE") suspend fun items(): List<Item>
    @Query("SELECT * FROM items WHERE id=:id") suspend fun item(id: String): Item?
    @Upsert suspend fun putItems(items: List<Item>)
    @Query("DELETE FROM items WHERE id=:id") suspend fun deleteItem(id: String)
    @Query("SELECT * FROM selections ORDER BY title") fun observeSelections(): Flow<List<Selection>>
    @Query("SELECT * FROM selections") suspend fun selections(): List<Selection>
    @Query("SELECT * FROM selections WHERE id=:id") suspend fun selection(id: String): Selection?
    @Upsert suspend fun putSelection(selection: Selection)
    @Query("DELETE FROM selections WHERE id=:id") suspend fun deleteSelection(id: String)
    @Query("SELECT * FROM members") fun observeMembers(): Flow<List<Member>>
    @Query("SELECT * FROM members") suspend fun members(): List<Member>
    @Query("DELETE FROM members WHERE selectionId=:id") suspend fun clearMembers(id: String)
    @Upsert suspend fun putMembers(members: List<Member>)
    @Transaction suspend fun replaceMembers(selection: Selection, items: List<Item>) {
        putItems(items); clearMembers(selection.id)
        putMembers(items.map { Member(selection.id, it.id) }.distinct())
        putSelection(selection.copy(refreshedAt = System.currentTimeMillis(), error = null))
    }
    @Query("SELECT * FROM assets") fun observeAssets(): Flow<List<Asset>>
    @Query("SELECT * FROM assets") suspend fun assets(): List<Asset>
    @Query("SELECT * FROM assets WHERE trackId=:id") suspend fun asset(id: String): Asset?
    @Upsert suspend fun putAsset(asset: Asset)
    @Query("SELECT * FROM lyrics WHERE trackId=:id") suspend fun lyrics(id: String): StoredLyrics?
    @Upsert suspend fun putLyrics(lyrics: StoredLyrics)
    @Query("SELECT * FROM playlist_entries WHERE playlistId=:id ORDER BY position") suspend fun entries(id: String): List<PlaylistEntry>
    @Query("SELECT * FROM playlist_entries ORDER BY playlistId, position") fun observeEntries(): Flow<List<PlaylistEntry>>
    @Query("DELETE FROM playlist_entries WHERE playlistId=:id") suspend fun clearEntries(id: String)
    @Upsert suspend fun putEntries(entries: List<PlaylistEntry>)
    @Transaction suspend fun replaceEntries(id: String, entries: List<PlaylistEntry>, items: List<Item>) {
        putItems(items); clearEntries(id); putEntries(entries)
    }
    @Query("SELECT * FROM edits ORDER BY createdAt") fun observeEdits(): Flow<List<PlaylistEdit>>
    @Query("SELECT * FROM edits WHERE status='Pending' ORDER BY createdAt") suspend fun pendingEdits(): List<PlaylistEdit>
    @Query("SELECT COUNT(*) FROM edits WHERE playlistId=:id AND status!='Done'") suspend fun outstanding(id: String): Int
    @Upsert suspend fun putEdit(edit: PlaylistEdit)
    @Query("DELETE FROM edits WHERE id=:id") suspend fun discardEdit(id: String)
}

@Database(entities = [Item::class, Selection::class, Member::class, Asset::class,
    StoredLyrics::class, PlaylistEntry::class, PlaylistEdit::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN codec TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN bitrate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN sampleRate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE selections ADD COLUMN retained INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
