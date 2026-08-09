package dev.ahmedvhashem.databaseliveinspector.sample.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuthorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(authors: List<Author>)

    @Query("SELECT * FROM Author ORDER BY name")
    fun getAll(): List<Author>

    @Query("DELETE FROM Author")
    fun deleteAll()
}
