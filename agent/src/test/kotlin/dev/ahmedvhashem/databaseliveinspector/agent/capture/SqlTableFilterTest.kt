package dev.ahmedvhashem.databaseliveinspector.agent.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlTableFilterTest {

    @Test
    fun `excludes room internal table references`() {
        val sqlStatements = listOf(
            "SELECT * FROM room_table_modification_log",
            "INSERT INTO room_table_x (id) VALUES (?)",
            "UPDATE room_table_x SET invalidated = 1",
            "DELETE FROM room_table_x WHERE id = ?",
            "SELECT * FROM users JOIN room_table_x ON users.id = room_table_x.user_id",
            "CREATE TABLE room_table_x (id INTEGER PRIMARY KEY)",
            "DROP TABLE IF EXISTS room_table_x",
            "DROP TRIGGER IF EXISTS `room_table_modification_trigger_order_items_INSERT`",
            "CREATE TRIGGER IF NOT EXISTS `room_table_modification_trigger_order_items_INSERT` AFTER INSERT ON order_items BEGIN SELECT 1; END",
        )

        sqlStatements.forEach { sql ->
            assertTrue(sql, SqlTableFilter.referencesExcludedTable(sql))
        }
    }

    @Test
    fun `excludes quoted and schema-qualified room internal tables`() {
        val sqlStatements = listOf(
            """SELECT * FROM "room_table_modification_log"""",
            "SELECT * FROM `room_table_modification_log`",
            "SELECT * FROM [room_table_modification_log]",
            "SELECT * FROM main.room_table_modification_log",
            """INSERT INTO main."room_table_x" (id) VALUES (1)""",
        )

        sqlStatements.forEach { sql ->
            assertTrue(sql, SqlTableFilter.referencesExcludedTable(sql))
        }
    }

    @Test
    fun `does not exclude regular app table references`() {
        val sqlStatements = listOf(
            "SELECT * FROM users",
            "INSERT INTO orders (id) VALUES (?)",
            "UPDATE room_table SET value = ?",
            "DELETE FROM room_table WHERE id = ?",
            "SELECT * FROM rooms WHERE name LIKE 'room_table_%'",
            "SELECT 'room_table_modification_log' FROM users",
            "SELECT * FROM users -- JOIN room_table_modification_log",
            "SELECT * FROM users /* JOIN room_table_modification_log */",
        )

        sqlStatements.forEach { sql ->
            assertFalse(sql, SqlTableFilter.referencesExcludedTable(sql))
        }
    }
}
