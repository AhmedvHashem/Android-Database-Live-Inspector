package dev.ahmedvhashem.databaseliveinspector.sample.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.ahmedvhashem.databaseliveinspector.sample.R
import dev.ahmedvhashem.databaseliveinspector.sample.SampleApp
import dev.ahmedvhashem.databaseliveinspector.sample.data.Author
import dev.ahmedvhashem.databaseliveinspector.sample.data.Book
import dev.ahmedvhashem.databaseliveinspector.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val db get() = (application as SampleApp).database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSeed.setOnClickListener {
            runOp(getString(R.string.status_seeding)) {
                seedLibrary()
                getString(R.string.status_seeded)
            }
        }

        binding.btnListBooks.setOnClickListener {
            runOp(getString(R.string.status_querying)) {
                val books = db.bookDao().getAll()
                resources.getQuantityString(R.plurals.status_books_found, books.size, books.size)
            }
        }

        binding.btnBooksWithAuthors.setOnClickListener {
            runOp(getString(R.string.status_querying)) {
                val rows = db.bookDao().getBooksWithAuthors()
                resources.getQuantityString(R.plurals.status_rows_found, rows.size, rows.size)
            }
        }

        binding.btnByAuthor.setOnClickListener {
            runOp(getString(R.string.status_querying)) {
                // Query books by García Márquez (id = 2 after seeding)
                val books = db.bookDao().getByAuthorId(authorId = 2)
                resources.getQuantityString(R.plurals.status_books_found, books.size, books.size)
            }
        }

        binding.btnDeleteBooks.setOnClickListener {
            runOp(getString(R.string.status_deleting)) {
                db.bookDao().deleteAll()
                getString(R.string.status_books_deleted)
            }
        }

        binding.btnReseed.setOnClickListener {
            runOp(getString(R.string.status_seeding)) {
                db.bookDao().deleteAll()
                db.authorDao().deleteAll()
                seedLibrary()
                getString(R.string.status_seeded)
            }
        }
    }

    private fun runOp(statusWhile: String, block: suspend () -> String) {
        lifecycleScope.launch {
            binding.tvStatus.text = statusWhile
            val result = withContext(Dispatchers.IO) { block() }
            binding.tvStatus.text = result
        }
    }

    private fun seedLibrary() {
        db.authorDao().insertAll(
            listOf(
                Author(1, "George Orwell", "United Kingdom"),
                Author(2, "Gabriel García Márquez", "Colombia"),
                Author(3, "Fyodor Dostoevsky", "Russia"),
                Author(4, "Jane Austen", "United Kingdom"),
                Author(5, "Leo Tolstoy", "Russia"),
            )
        )
        db.bookDao().insertAll(
            listOf(
                Book(1, "1984", 1949, 1),
                Book(2, "Animal Farm", 1945, 1),
                Book(3, "One Hundred Years of Solitude", 1967, 2),
                Book(4, "Love in the Time of Cholera", 1985, 2),
                Book(5, "Crime and Punishment", 1866, 3),
                Book(6, "The Idiot", 1869, 3),
                Book(7, "Pride and Prejudice", 1813, 4),
                Book(8, "Sense and Sensibility", 1811, 4),
                Book(9, "War and Peace", 1869, 5),
                Book(10, "Anna Karenina", 1878, 5),
            )
        )
    }
}
