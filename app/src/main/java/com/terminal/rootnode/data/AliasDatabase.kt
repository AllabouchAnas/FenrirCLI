package com.terminal.rootnode.data

import androidx.room.*

@Entity(tableName = "aliases")
data class Alias(
    @PrimaryKey val name: String,
    val command: String
)

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases")
    suspend fun getAll(): List<Alias>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: Alias)

    @Delete
    suspend fun delete(alias: Alias)

    @Query("SELECT * FROM aliases WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Alias?
}

@Database(entities = [Alias::class], version = 1)
abstract class AliasDatabase : RoomDatabase() {
    abstract fun aliasDao(): AliasDao
}