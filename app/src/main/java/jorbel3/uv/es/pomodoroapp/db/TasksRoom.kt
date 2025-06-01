package jorbel3.uv.es.pomodoroapp.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val subject: String,
    val isCompleted: Boolean = false,
    val numberPomodoros: Int,
    val numberCompletedPomodoros: Int = 0
)

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: Task)

    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT DISTINCT subject FROM tasks ORDER BY subject ASC")
    suspend fun getAllSubjects(): List<String>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET numberCompletedPomodoros = :numberCompletedPomodoros WHERE id = :id")
    suspend fun updateNumberCompletedPomodoros(id: Int, numberCompletedPomodoros: Int)

    @Query("SELECT * FROM tasks WHERE subject = :subject")
    suspend fun getTasksBySubject(subject: String): List<Task>

    @Query("SELECT * FROM tasks WHERE numberCompletedPomodoros >= numberPomodoros")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE numberCompletedPomodoros < numberPomodoros")
    fun getActiveTasks(): Flow<List<Task>>
}

@Database(entities = [Task::class], version = 1)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}