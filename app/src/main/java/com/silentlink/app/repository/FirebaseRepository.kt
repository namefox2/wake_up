package com.silentlink.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.silentlink.app.model.ConnectionInfo
import com.silentlink.app.model.DeviceStatus
import com.silentlink.app.model.DndSchedule
import com.silentlink.app.model.VolumeLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: throw Exception("인증 실패")
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun registerDevice(uid: String, inviteCode: String) {
        db.getReference("codes/$inviteCode").setValue(uid).await()
        db.getReference("devices/$uid/inviteCode").setValue(inviteCode).await()
        db.getReference("devices/$uid/status").setValue(
            mapOf(
                "isMuted" to false,
                "volumeLevel" to "MEDIUM",
                "isAccessAllowed" to true,
                "isOnline" to true,
                "lastUpdated" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun connectWithCode(myUid: String, partnerCode: String): Boolean {
        val snapshot = db.getReference("codes/$partnerCode").get().await()
        val partnerUid = snapshot.getValue(String::class.java) ?: return false

        db.getReference("devices/$myUid/partnerId").setValue(partnerUid).await()
        db.getReference("devices/$partnerUid/partnerId").setValue(myUid).await()
        db.getReference("devices/$myUid/connectedAt").setValue(System.currentTimeMillis()).await()
        db.getReference("devices/$partnerUid/connectedAt").setValue(System.currentTimeMillis()).await()
        return true
    }

    fun observePartnerStatus(partnerUid: String): Flow<DeviceStatus> = callbackFlow {
        val ref = db.getReference("devices/$partnerUid/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isMuted = snapshot.child("isMuted").getValue(Boolean::class.java) ?: false
                val volumeStr = snapshot.child("volumeLevel").getValue(String::class.java) ?: "MEDIUM"
                val isAccessAllowed = snapshot.child("isAccessAllowed").getValue(Boolean::class.java) ?: true
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L
                trySend(
                    DeviceStatus(
                        isMuted = isMuted,
                        volumeLevel = runCatching { VolumeLevel.valueOf(volumeStr) }.getOrDefault(VolumeLevel.MEDIUM),
                        isAccessAllowed = isAccessAllowed,
                        isOnline = isOnline,
                        lastUpdated = lastUpdated
                    )
                )
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeMyCommands(myUid: String): Flow<Map<String, Any>> = callbackFlow {
        val ref = db.getReference("devices/$myUid/commands")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.value as? Map<String, Any> ?: return
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendCommand(partnerUid: String, command: String, value: Any) {
        db.getReference("devices/$partnerUid/commands/$command").setValue(value).await()
    }

    suspend fun updateMyStatus(myUid: String, status: DeviceStatus) {
        db.getReference("devices/$myUid/status").setValue(
            mapOf(
                "isMuted" to status.isMuted,
                "volumeLevel" to status.volumeLevel.name,
                "isAccessAllowed" to status.isAccessAllowed,
                "isOnline" to status.isOnline,
                "lastUpdated" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun getPartnerUid(myUid: String): String? {
        return db.getReference("devices/$myUid/partnerId").get().await().getValue(String::class.java)
    }

    suspend fun disconnect(myUid: String) {
        val partnerUid = getPartnerUid(myUid)
        db.getReference("devices/$myUid/partnerId").removeValue().await()
        db.getReference("devices/$myUid/connectedAt").removeValue().await()
        partnerUid?.let {
            db.getReference("devices/$it/partnerId").removeValue().await()
        }
    }

    suspend fun updateAccessAllowed(myUid: String, allowed: Boolean) {
        db.getReference("devices/$myUid/status/isAccessAllowed").setValue(allowed).await()
    }
}
