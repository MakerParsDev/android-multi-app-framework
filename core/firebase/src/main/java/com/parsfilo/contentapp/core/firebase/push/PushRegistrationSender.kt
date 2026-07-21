package com.parsfilo.contentapp.core.firebase.push

interface PushRegistrationSender {
    suspend fun send(payload: PushRegistrationPayload): Boolean
}
