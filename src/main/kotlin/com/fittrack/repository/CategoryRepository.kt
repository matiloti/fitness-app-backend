package com.fittrack.repository

import com.fittrack.model.Category
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class CategoryRepository(private val jdbcTemplate: JdbcTemplate) {

    private val categoryRowMapper = RowMapper { rs: ResultSet, _: Int ->
        Category(
            id = rs.getInt("id"),
            name = rs.getString("name"),
            icon = rs.getString("icon"),
            isSystem = rs.getBoolean("is_system"),
            createdAt = rs.getTimestamp("created_at")?.toInstant()
        )
    }

    fun findById(id: Int): Category? {
        val sql = "SELECT * FROM categories WHERE id = ?"
        return jdbcTemplate.query(sql, categoryRowMapper, id).firstOrNull()
    }

    fun findAll(): List<Category> {
        val sql = "SELECT * FROM categories ORDER BY id"
        return jdbcTemplate.query(sql, categoryRowMapper)
    }

    fun existsById(id: Int): Boolean {
        val sql = "SELECT COUNT(*) FROM categories WHERE id = ?"
        return (jdbcTemplate.queryForObject(sql, Int::class.java, id) ?: 0) > 0
    }
}
