package com.parsfilo.contentapp.feature.messages.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.parsfilo.contentapp.core.common.network.AppDispatchers
import com.parsfilo.contentapp.core.common.network.Dispatcher
import com.parsfilo.contentapp.core.model.Message
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface MessageRepository {
    fun getMessages(): Flow<List<Message>>

    suspend fun sendMessage(subject: String, message: String, category: String)
}

class FirestoreMessageRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) : MessageRepository {

    override fun getMessages(): Flow<List<Message>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("user_messages")
            .whereEqualTo("userId", user.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }.flowOn(ioDispatcher)

    override suspend fun sendMessage(subject: String, message: String, category: String) {
        withContext(ioDispatcher) {
            val user = auth.currentUser ?: throw IllegalStateException("User not logged in")

            val newMessage = Message(
                userId = user.uid,
                userEmail = user.email ?: "",
                userName = user.displayName ?: "",
                subject = subject,
                message = message,
                category = category
            )

            firestore.collection("user_messages").add(newMessage).await()
        }
    }
}
