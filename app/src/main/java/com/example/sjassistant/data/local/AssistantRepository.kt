package com.example.sjassistant.data.local

import com.example.sjassistant.core.security.LocalVaultCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NoteModel(
    val id: Long,
    val title: String,
    val content: String,
    val isEncrypted: Boolean,
    val languageCode: String,
    val isPinned: Boolean,
    val updatedAt: Long
)

class AssistantRepository(private val db: SjDatabase) {
    private val chatDao = db.chatDao()
    private val noteDao = db.noteDao()
    private val todoDao = db.todoDao()
    private val reminderDao = db.reminderDao()
    private val documentDao = db.documentDao()

    val messages: Flow<List<ChatMessageEntity>> = chatDao.observeMessages()

    val notes: Flow<List<NoteModel>> = noteDao.observeNotes().map { list ->
        list.map { entity ->
            val clearText = if (entity.isEncrypted) {
                LocalVaultCrypto.decrypt(entity.contentPayload)
            } else {
                entity.contentPayload
            }
            NoteModel(
                id = entity.id,
                title = entity.title,
                content = clearText,
                isEncrypted = entity.isEncrypted,
                languageCode = entity.languageCode,
                isPinned = entity.isPinned,
                updatedAt = entity.updatedAt
            )
        }
    }

    val todos: Flow<List<TodoEntity>> = todoDao.observeTodos()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeReminders()
    val documents: Flow<List<DocumentEntity>> = documentDao.observeDocuments()

    suspend fun addMessage(message: ChatMessageEntity): Long = chatDao.insertMessage(message)

    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity> =
        chatDao.getRecentMessages(limit).reversed()

    suspend fun clearChat() = chatDao.clearAll()

    suspend fun addNote(
        title: String,
        content: String,
        encrypt: Boolean,
        languageCode: String
    ): Long {
        val payload = if (encrypt) LocalVaultCrypto.encrypt(content) else content
        return noteDao.insertNote(
            NoteEntity(
                title = title,
                contentPayload = payload,
                isEncrypted = encrypt,
                languageCode = languageCode
            )
        )
    }

    suspend fun togglePinNote(note: NoteModel) {
        val payload = if (note.isEncrypted) LocalVaultCrypto.encrypt(note.content) else note.content
        noteDao.updateNote(
            NoteEntity(
                id = note.id,
                title = note.title,
                contentPayload = payload,
                isEncrypted = note.isEncrypted,
                languageCode = note.languageCode,
                isPinned = !note.isPinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteNote(id: Long) = noteDao.deleteNoteById(id)

    suspend fun getAllNotesDecrypted(): List<NoteModel> =
        noteDao.getAllNotesSnapshot().map { entity ->
            NoteModel(
                id = entity.id,
                title = entity.title,
                content = if (entity.isEncrypted) LocalVaultCrypto.decrypt(entity.contentPayload) else entity.contentPayload,
                isEncrypted = entity.isEncrypted,
                languageCode = entity.languageCode,
                isPinned = entity.isPinned,
                updatedAt = entity.updatedAt
            )
        }

    suspend fun addTodo(title: String, priority: Int, languageCode: String): Long =
        todoDao.insertTodo(TodoEntity(title = title, priority = priority, languageCode = languageCode))

    suspend fun toggleTodo(id: Long, completed: Boolean) = todoDao.setCompleted(id, completed)

    suspend fun deleteTodo(id: Long) = todoDao.deleteTodoById(id)

    suspend fun addReminder(title: String, triggerAtMillis: Long, languageCode: String): Long =
        reminderDao.insertReminder(
            ReminderEntity(
                title = title,
                triggerAtMillis = triggerAtMillis,
                languageCode = languageCode
            )
        )

    suspend fun markReminderCompleted(id: Long) =
        reminderDao.updateStatus(id, active = false, completed = true)

    suspend fun deleteReminder(id: Long) = reminderDao.deleteReminderById(id)

    suspend fun indexDocument(
        fileName: String,
        mimeType: String,
        rawText: String
    ): DocumentEntity {
        val chunks = chunkText(rawText, chunkSize = 360, overlap = 60)
        val docId = documentDao.insertDocument(
            DocumentEntity(
                fileName = fileName,
                mimeType = mimeType,
                charCount = rawText.length,
                chunkCount = chunks.size
            )
        )
        val chunkEntities = chunks.mapIndexed { index, text ->
            DocumentChunkEntity(
                documentId = docId,
                documentName = fileName,
                chunkIndex = index,
                content = text
            )
        }
        documentDao.insertChunks(chunkEntities)
        return DocumentEntity(
            id = docId,
            fileName = fileName,
            mimeType = mimeType,
            charCount = rawText.length,
            chunkCount = chunks.size
        )
    }

    suspend fun getAllDocumentChunks(): List<DocumentChunkEntity> =
        documentDao.getAllChunksSnapshot()

    suspend fun deleteDocument(id: Long) = documentDao.deleteDocumentById(id)

    private fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= chunkSize) return listOf(cleaned)
        val result = mutableListOf<String>()
        var start = 0
        while (start < cleaned.length) {
            val end = (start + chunkSize).coerceAtMost(cleaned.length)
            result.add(cleaned.substring(start, end))
            if (end >= cleaned.length) break
            start += (chunkSize - overlap).coerceAtLeast(1)
        }
        return result
    }
}
