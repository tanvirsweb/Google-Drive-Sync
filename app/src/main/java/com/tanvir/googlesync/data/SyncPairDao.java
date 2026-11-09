package com.tanvir.googlesync.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SyncPairDao {
    @Query("SELECT * FROM sync_pairs ORDER BY createdAt ASC")
    LiveData<List<SyncPair>> observeAll();

    @Query("SELECT * FROM sync_pairs ORDER BY createdAt ASC")
    List<SyncPair> getAll();

    @Query("SELECT * FROM sync_pairs WHERE id = :id")
    SyncPair get(long id);

    @Insert
    long insert(SyncPair pair);

    @Update
    void update(SyncPair pair);

    @Delete
    void delete(SyncPair pair);

    @Query("UPDATE sync_pairs SET status = :status, statusMessage = :message WHERE id = :id")
    void setStatus(long id, SyncStatus status, String message);

    @Query("UPDATE sync_pairs SET statusMessage = :message WHERE id = :id")
    void setMessage(long id, String message);

    @Query("UPDATE sync_pairs SET pendingLocalChanges = :pending WHERE id = :id")
    void setPendingLocalChanges(long id, boolean pending);

    @Query("UPDATE sync_pairs SET bisyncInitialized = :initialized WHERE id = :id")
    void setBisyncInitialized(long id, boolean initialized);
}
