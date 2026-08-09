package dev.ahmedvhashem.databaseliveinspector.sample.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(books: List<Book>)

    @Query("SELECT * FROM Book ORDER BY year")
    fun getAll(): List<Book>

    @Query("SELECT * FROM Book WHERE authorId = :authorId ORDER BY year")
    fun getByAuthorId(authorId: Int): List<Book>

    @Query("""
        SELECT Book.title, Book.year, Author.name AS authorName, Author.country
        FROM Book
        INNER JOIN Author ON Book.authorId = Author.id
        ORDER BY Book.year
    """)
    fun getBooksWithAuthors(): List<BookWithAuthor>

    @Query("DELETE FROM Book")
    fun deleteAll()
}
