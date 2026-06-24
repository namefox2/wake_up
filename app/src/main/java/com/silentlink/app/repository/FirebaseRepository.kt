package com.silentlink.app.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.silentlink.app.model.DeviceStatus
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.model.UserActivity
import com.silentlink.app.model.VolumeLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: throw Exception("인증 실패")
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isGoogleLinked(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true

    fun getGoogleEmail(): String? =
        auth.currentUser?.providerData?.firstOrNull { it.providerId == "google.com" }?.email

    suspend fun linkGoogleAccount(idToken: String): LinkResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            auth.currentUser?.linkWithCredential(credential)?.await()
            LinkResult.LINKED
        } catch (_: FirebaseAuthUserCollisionException) {
            auth.signInWithCredential(credential).await()
            LinkResult.RESTORED
        } catch (_: Exception) {
            LinkResult.FAILED
        }
    }

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun registerDevice(uid: String, inviteCode: String) {
        db.getReference("codes/$inviteCode").setValue(uid).await()
        db.getReference("devices/$uid/inviteCode").setValue(inviteCode).await()
        db.getReference("devices/$uid/status").setValue(
            mapOf(
                "isMuted" to false, "volumeLevel" to "SOUND",
                "isAccessAllowed" to true, "isOnline" to true,
                "lastUpdated" to System.currentTimeMillis(), "activity" to "NONE"
            )
        ).await()
    }

    // 파트너 연결 (멀티 지원)

    suspend fun getPartnerUids(myUid: String): List<String> {
        val snap = db.getReference("devices/$myUid/partnerIds").get().await()
        if (snap.exists()) return snap.children.mapNotNull { it.key }.filter { it.isNotEmpty() }
        val old = db.getReference("devices/$myUid/partnerId").get().await().getValue(String::class.java)
        if (!old.isNullOrEmpty()) {
            db.getReference("devices/$myUid/partnerIds/$old").setValue(true).await()
            db.getReference("devices/$myUid/partnerId").removeValue().await()
            return listOf(old)
        }
        return emptyList()
    }

    suspend fun connectWithCode(myUid: String, partnerCode: String): ConnectResult {
        val partnerUid = db.getReference("codes/$partnerCode").get().await()
            .getValue(String::class.java) ?: return ConnectResult.NOT_FOUND
        if (partnerUid == myUid) return ConnectResult.NOT_FOUND
        if (db.getReference("devices/$myUid/partnerIds/$partnerUid").get().await().exists())
            return ConnectResult.ALREADY_CONNECTED
        db.getReference("devices/$myUid/partnerIds/$partnerUid").setValue(true).await()
        db.getReference("devices/$partnerUid/partnerIds/$myUid").setValue(true).await()
        return ConnectResult.SUCCESS
    }

    suspend fun disconnectFromPartner(myUid: String, partnerUid: String) {
        db.getReference("devices/$myUid/partnerIds/$partnerUid").removeValue().await()
        db.getReference("devices/$partnerUid/partnerIds/$myUid").removeValue().await()
    }

    suspend fun disconnectAll(myUid: String) {
        getPartnerUids(myUid).forEach { partnerUid ->
            runCatching { db.getReference("devices/$partnerUid/partnerIds/$myUid").removeValue().await() }
        }
        db.getReference("devices/$myUid/partnerIds").removeValue().await()
    }

    // 슬롯 구매 기록

    suspend fun getPurchasedSlots(uid: String): Int =
        db.getReference("users/$uid/purchasedSlots").get().await()
            .getValue(Int::class.java) ?: 0

    suspend fun setPurchasedSlots(uid: String, slots: Int) {
        db.getReference("users/$uid/purchasedSlots").setValue(slots).await()
    }

    suspend fun isAdmin(uid: String): Boolean =
        runCatching {
            db.getReference("users/$uid/isAdmin").get().await()
                .getValue(Boolean::class.java) ?: false
        }.getOrDefault(false)

    // 상태 관찰

    fun observePartnerStatus(partnerUid: String): Flow<DeviceStatus> = callbackFlow {
        val ref = db.getReference("devices/$partnerUid/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.toDeviceStatus()) }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun readPartnerStatus(partnerUid: String): DeviceStatus? =
        runCatching { db.getReference("devices/$partnerUid/status").get().await().toDeviceStatus() }.getOrNull()

    private fun DataSnapshot.toDeviceStatus(): DeviceStatus {
        val volumeStr = child("volumeLevel").getValue(String::class.java) ?: "SOUND"
        val isMuted = child("isMuted").getValue(Boolean::class.java) ?: false
        val level = runCatching { VolumeLevel.valueOf(volumeStr) }.getOrElse {
            if (isMuted) VolumeLevel.MUTE else VolumeLevel.SOUND
        }
        return DeviceStatus(
            isMuted = level == VolumeLevel.MUTE || level == VolumeLevel.VIBRATE,
            volumeLevel = level,
            isAccessAllowed = child("isAccessAllowed").getValue(Boolean::class.java) ?: true,
            isOnline = child("isOnline").getValue(Boolean::class.java) ?: false,
            lastUpdated = child("lastUpdated").getValue(Long::class.java) ?: 0L,
            activity = runCatching {
                UserActivity.valueOf(child("activity").getValue(String::class.java) ?: "NONE")
            }.getOrDefault(UserActivity.NONE)
        )
    }

    // 명령

    fun observeMyCommands(myUid: String): Flow<Map<String, Any>> = callbackFlow {
        val ref = db.getReference("devices/$myUid/commands")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                (snapshot.value as? Map<String, Any>)?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendCommand(partnerUid: String, command: String, value: Any) {
        db.getReference("devices/$partnerUid/commands/$command").setValue(value).await()
    }

    suspend fun deleteCommand(uid: String, command: String) {
        db.getReference("devices/$uid/commands/$command").removeValue().await()
    }

    // 상태 업데이트

    suspend fun updateVolumeStatus(uid: String, level: VolumeLevel) {
        val muted = level == VolumeLevel.MUTE || level == VolumeLevel.VIBRATE
        db.getReference("devices/$uid/status").updateChildren(
            mapOf("isMuted" to muted, "volumeLevel" to level.name, "lastUpdated" to System.currentTimeMillis())
        ).await()
    }

    suspend fun updateAccessAllowed(myUid: String, allowed: Boolean) {
        db.getReference("devices/$myUid/status/isAccessAllowed").setValue(allowed).await()
    }

    suspend fun updateMyActivity(myUid: String, activity: UserActivity) {
        db.getReference("devices/$myUid/status/activity").setValue(activity.name).await()
    }

    // 알람 CRUD

    suspend fun addAlarm(targetUid: String, alarm: RemoteAlarm) {
        db.getReference("devices/$targetUid/alarms/${alarm.id}").setValue(
            mapOf(
                "id" to alarm.id, "label" to alarm.label,
                "hour" to alarm.hour, "minute" to alarm.minute,
                "days" to alarm.days.sorted().joinToString(","),
                "isEnabled" to alarm.isEnabled, "createdAt" to alarm.createdAt,
                "alarmSound" to alarm.alarmSound, "alarmVibrate" to alarm.alarmVibrate
            )
        ).await()
    }

    suspend fun updateAlarm(targetUid: String, alarm: RemoteAlarm) = addAlarm(targetUid, alarm)

    suspend fun deleteAlarm(targetUid: String, alarmId: String) {
        db.getReference("devices/$targetUid/alarms/$alarmId").removeValue().await()
    }

    fun observeAlarms(uid: String): Flow<List<RemoteAlarm>> = callbackFlow {
        val ref = db.getReference("devices/$uid/alarms")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarms = snapshot.children.mapNotNull { child ->
                    runCatching {
                        val id = child.child("id").getValue(String::class.java) ?: return@runCatching null
                        val daysNode = child.child("days")
                        val days: Set<Int> = when {
                            daysNode.getValue(String::class.java) != null ->
                                daysNode.getValue(String::class.java)!!
                                    .split(",").filter { it.isNotBlank() }
                                    .mapNotNull { it.trim().toIntOrNull() }.toSet()
                            else -> {
                                @Suppress("UNCHECKED_CAST")
                                (daysNode.getValue(List::class.java) as? List<*> ?: emptyList<Any>())
                                    .mapNotNull { (it as? Long)?.toInt() ?: (it as? Int) }.toSet()
                            }
                        }
                        RemoteAlarm(
                            id = id,
                            label = child.child("label").getValue(String::class.java) ?: "",
                            hour = (child.child("hour").getValue(Long::class.java) ?: 7L).toInt(),
                            minute = (child.child("minute").getValue(Long::class.java) ?: 0L).toInt(),
                            days = days,
                            isEnabled = child.child("isEnabled").getValue(Boolean::class.java) ?: true,
                            createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L,
                            alarmSound = child.child("alarmSound").getValue(Boolean::class.java) ?: true,
                            alarmVibrate = child.child("alarmVibrate").getValue(Boolean::class.java) ?: true
                        )
                    }.getOrNull()
                }
                trySend(alarms)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

enum class ConnectResult { SUCCESS, NOT_FOUND, ALREADY_CONNECTED, SLOT_LIMIT_REACHED }
enum class LinkResult { LINKED, RESTORED, FAILED }
