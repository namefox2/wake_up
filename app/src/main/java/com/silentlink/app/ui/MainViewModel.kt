package com.silentlink.app.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.manager.DndManager
import com.silentlink.app.model.AppTheme
import com.silentlink.app.model.DeviceStatus
import com.silentlink.app.model.DndSchedule
import com.silentlink.app.model.UserActivity
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.repository.FirebaseRepository
import com.silentlink.app.service.SilentLinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "silentlink_prefs")

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository()
    private val audioManager = AudioControlManager(application)
    private val dndManager = DndManager(application)

    private val _uiState = MutableStateFlow(SilentLinkUiState())
    val uiState: StateFlow<SilentLinkUiState> = _uiState

    private val _isOnboarded = MutableStateFlow(false)
    val isOnboarded: StateFlow<Boolean> = _isOnboarded

    companion object {
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_MY_UID = stringPreferencesKey("my_uid")
        val KEY_MY_CODE = stringPreferencesKey("my_code")
        val KEY_PARTNER_UID = stringPreferencesKey("partner_uid")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DND_SCHEDULES = stringPreferencesKey("dnd_schedules")
    }

    init {
        viewModelScope.launch {
            loadPersistedState()
        }
    }

    private suspend fun loadPersistedState() {
        val context = getApplication<Application>()
        val prefs = context.dataStore.data.first()
        val onboarded = prefs[KEY_ONBOARDED] ?: false
        _isOnboarded.value = onboarded

        if (onboarded) {
            val myUid = prefs[KEY_MY_UID] ?: return
            val myCode = prefs[KEY_MY_CODE] ?: return
            val partnerUid = prefs[KEY_PARTNER_UID]
            val themeStr = prefs[KEY_THEME] ?: AppTheme.DARK.name
            val theme = runCatching { AppTheme.valueOf(themeStr) }.getOrDefault(AppTheme.DARK)

            _uiState.value = _uiState.value.copy(
                myCode = myCode,
                myUid = myUid,
                partnerUid = partnerUid ?: "",
                isConnected = partnerUid != null,
                theme = theme,
                myStatus = DeviceStatus(
                    isMuted = audioManager.isMuted(),
                    volumeLevel = VolumeLevel.MEDIUM,
                    isOnline = true
                )
            )

            if (partnerUid != null) {
                listenToPartnerStatus(partnerUid)
            }
        }
    }

    fun generateMyCode(): String {
        val code = repository.generateInviteCode()
        _uiState.value = _uiState.value.copy(myCode = code)
        return code
    }

    fun onboard(termsAccepted: Boolean) {
        if (!termsAccepted) return
        viewModelScope.launch {
            try {
                val uid = repository.signInAnonymously()
                val code = repository.generateInviteCode()
                repository.registerDevice(uid, code)

                val context = getApplication<Application>()
                context.dataStore.edit { prefs ->
                    prefs[KEY_ONBOARDED] = true
                    prefs[KEY_MY_UID] = uid
                    prefs[KEY_MY_CODE] = code
                }

                _uiState.value = _uiState.value.copy(myUid = uid, myCode = code)
                _isOnboarded.value = true
                SilentLinkService.start(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "초기화 실패: ${e.message}")
            }
        }
    }

    fun connectWithPartnerCode(partnerCode: String) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            try {
                val success = repository.connectWithCode(myUid, partnerCode)
                if (success) {
                    val partnerUid = repository.getPartnerUid(myUid) ?: return@launch
                    val context = getApplication<Application>()
                    context.dataStore.edit { prefs ->
                        prefs[KEY_PARTNER_UID] = partnerUid
                    }
                    _uiState.value = _uiState.value.copy(
                        partnerUid = partnerUid,
                        isConnected = true,
                        errorMessage = null
                    )
                    listenToPartnerStatus(partnerUid)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "연결 코드를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "연결 실패: ${e.message}")
            }
        }
    }

    private fun listenToPartnerStatus(partnerUid: String) {
        viewModelScope.launch {
            repository.observePartnerStatus(partnerUid).collect { status ->
                _uiState.value = _uiState.value.copy(partnerStatus = status)
            }
        }
    }

    fun sendMuteCommand(muted: Boolean) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            if (!_uiState.value.partnerStatus.isAccessAllowed) return@launch
            if (dndManager.isInDndSchedule(_uiState.value.dndSchedules)) return@launch
            repository.sendCommand(partnerUid, "setMute", muted)
        }
    }

    fun sendVolumeCommand(level: VolumeLevel) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            if (!_uiState.value.partnerStatus.isAccessAllowed) return@launch
            if (dndManager.isInDndSchedule(_uiState.value.dndSchedules)) return@launch
            repository.sendCommand(partnerUid, "setVolume", level.name)
        }
    }

    fun toggleMyAccess(allowed: Boolean) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            repository.updateAccessAllowed(myUid, allowed)
            _uiState.value = _uiState.value.copy(
                myStatus = _uiState.value.myStatus.copy(isAccessAllowed = allowed)
            )
        }
    }

    fun addDndSchedule(schedule: DndSchedule) {
        val newSchedules = _uiState.value.dndSchedules + schedule.copy(id = UUID.randomUUID().toString())
        _uiState.value = _uiState.value.copy(dndSchedules = newSchedules)
        persistDndSchedules(newSchedules)
    }

    fun updateDndSchedule(schedule: DndSchedule) {
        val newSchedules = _uiState.value.dndSchedules.map {
            if (it.id == schedule.id) schedule else it
        }
        _uiState.value = _uiState.value.copy(dndSchedules = newSchedules)
        persistDndSchedules(newSchedules)
    }

    fun deleteDndSchedule(scheduleId: String) {
        val newSchedules = _uiState.value.dndSchedules.filter { it.id != scheduleId }
        _uiState.value = _uiState.value.copy(dndSchedules = newSchedules)
        persistDndSchedules(newSchedules)
    }

    private fun persistDndSchedules(schedules: List<DndSchedule>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val json = com.google.gson.Gson().toJson(schedules)
            context.dataStore.edit { prefs ->
                prefs[KEY_DND_SCHEDULES] = json
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_THEME] = theme.name
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            repository.disconnect(myUid)
            val context = getApplication<Application>()
            context.dataStore.edit { prefs ->
                prefs.remove(KEY_PARTNER_UID)
            }
            _uiState.value = _uiState.value.copy(
                partnerUid = "",
                isConnected = false,
                partnerStatus = DeviceStatus()
            )
        }
    }

    fun setMyActivity(activity: UserActivity) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            repository.updateMyActivity(myUid, activity)
            _uiState.value = _uiState.value.copy(myActivity = activity)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class SilentLinkUiState(
    val myUid: String = "",
    val myCode: String = "",
    val partnerUid: String = "",
    val isConnected: Boolean = false,
    val myStatus: DeviceStatus = DeviceStatus(),
    val partnerStatus: DeviceStatus = DeviceStatus(),
    val myActivity: UserActivity = UserActivity.NONE,
    val dndSchedules: List<DndSchedule> = emptyList(),
    val theme: AppTheme = AppTheme.DARK,
    val errorMessage: String? = null
)
