package com.silentlink.app.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.manager.DndManager
import com.silentlink.app.model.AppTheme
import com.silentlink.app.model.DeviceStatus
import com.silentlink.app.model.DndConfig
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.model.UserActivity
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.repository.FirebaseRepository
import com.silentlink.app.service.SilentLinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "silentlink_prefs")

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository()
    private val audioManager = AudioControlManager(application)
    private val dndManager = DndManager(application)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(SilentLinkUiState())
    val uiState: StateFlow<SilentLinkUiState> = _uiState

    private val _isOnboarded = MutableStateFlow(false)
    val isOnboarded: StateFlow<Boolean> = _isOnboarded

    companion object {
        val KEY_ONBOARDED   = booleanPreferencesKey("onboarded")
        val KEY_MY_UID      = stringPreferencesKey("my_uid")
        val KEY_MY_CODE     = stringPreferencesKey("my_code")
        val KEY_PARTNER_UID = stringPreferencesKey("partner_uid")
        val KEY_THEME       = stringPreferencesKey("theme")
        val KEY_DND_CONFIG  = stringPreferencesKey("dnd_config")
        val KEY_MY_ACTIVITY = stringPreferencesKey("my_activity")
    }

    init {
        viewModelScope.launch { loadPersistedState() }
    }

    private suspend fun loadPersistedState() {
        val context = getApplication<Application>()
        val prefs = context.dataStore.data.first()
        val onboarded = prefs[KEY_ONBOARDED] ?: false
        _isOnboarded.value = onboarded

        if (onboarded) {
            val myUid      = prefs[KEY_MY_UID] ?: return
            val myCode     = prefs[KEY_MY_CODE] ?: return
            val partnerUid = prefs[KEY_PARTNER_UID]
            val theme      = runCatching { AppTheme.valueOf(prefs[KEY_THEME] ?: "") }.getOrDefault(AppTheme.DARK)
            val dndConfig  = runCatching { gson.fromJson(prefs[KEY_DND_CONFIG], DndConfig::class.java) }.getOrDefault(DndConfig())
            val myActivity = runCatching { UserActivity.valueOf(prefs[KEY_MY_ACTIVITY] ?: "") }.getOrDefault(UserActivity.NONE)

            _uiState.value = _uiState.value.copy(
                myCode     = myCode,
                myUid      = myUid,
                partnerUid = partnerUid ?: "",
                isConnected = partnerUid != null,
                theme      = theme,
                dndConfig  = dndConfig ?: DndConfig(),
                myStatus   = DeviceStatus(isMuted = audioManager.isMuted(), isOnline = true),
                myActivity = myActivity
            )

            // 앱 재실행 시에도 서비스가 확실히 구동되도록
            SilentLinkService.start(context)
            listenToMyAlarms(myUid)
            if (partnerUid != null) listenToPartnerStatus(partnerUid)
        }
    }

    fun onboard(termsAccepted: Boolean) {
        if (!termsAccepted) return
        viewModelScope.launch {
            try {
                val uid  = repository.signInAnonymously()
                val code = repository.generateInviteCode()
                repository.registerDevice(uid, code)
                val context = getApplication<Application>()
                context.dataStore.edit { prefs ->
                    prefs[KEY_ONBOARDED] = true
                    prefs[KEY_MY_UID]    = uid
                    prefs[KEY_MY_CODE]   = code
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
            _uiState.value = _uiState.value.copy(errorMessage = null)
            try {
                val success = repository.connectWithCode(myUid, partnerCode)
                if (success) {
                    val partnerUid = repository.getPartnerUid(myUid) ?: return@launch
                    getApplication<Application>().dataStore.edit { prefs ->
                        prefs[KEY_PARTNER_UID] = partnerUid
                    }
                    _uiState.value = _uiState.value.copy(
                        partnerUid  = partnerUid,
                        isConnected = true,
                        errorMessage = null
                    )
                    listenToPartnerStatus(partnerUid)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "코드를 찾을 수 없습니다")
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
        viewModelScope.launch {
            repository.observeAlarms(partnerUid).collect { alarms ->
                _uiState.value = _uiState.value.copy(alarmsForPartner = alarms)
            }
        }
    }

    private fun listenToMyAlarms(myUid: String) {
        viewModelScope.launch {
            repository.observeAlarms(myUid).collect { alarms ->
                _uiState.value = _uiState.value.copy(alarmsFromPartner = alarms)
            }
        }
    }

    fun refreshPartnerStatus() {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            val status = repository.readPartnerStatus(partnerUid) ?: return@launch
            _uiState.value = _uiState.value.copy(partnerStatus = status)
        }
    }

    fun sendMuteCommand(muted: Boolean) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            if (!_uiState.value.partnerStatus.isAccessAllowed) return@launch
            if (dndManager.isInDndTime(_uiState.value.dndConfig)) return@launch
            repository.sendCommand(partnerUid, "setMute", muted)
        }
    }

    fun sendVolumeCommand(level: VolumeLevel) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            if (!_uiState.value.partnerStatus.isAccessAllowed) return@launch
            if (dndManager.isInDndTime(_uiState.value.dndConfig)) return@launch
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

    fun updateDndConfig(config: DndConfig) {
        _uiState.value = _uiState.value.copy(dndConfig = config)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_DND_CONFIG] = gson.toJson(config)
            }
        }
    }

    // ── 알람 ────────────────────────────────────────────────────

    fun addAlarmForPartner(alarm: RemoteAlarm) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            try {
                repository.addAlarm(partnerUid, alarm)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 저장 실패: ${e.message}")
            }
        }
    }

    fun updateAlarmForPartner(alarm: RemoteAlarm) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            try {
                repository.updateAlarm(partnerUid, alarm)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 수정 실패: ${e.message}")
            }
        }
    }

    fun deleteAlarmForPartner(alarmId: String) {
        viewModelScope.launch {
            val partnerUid = _uiState.value.partnerUid.ifEmpty { return@launch }
            try {
                repository.deleteAlarm(partnerUid, alarmId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 삭제 실패: ${e.message}")
            }
        }
    }

    fun toggleAlarmForPartner(alarmId: String, enabled: Boolean) {
        val alarm = _uiState.value.alarmsForPartner.find { it.id == alarmId } ?: return
        updateAlarmForPartner(alarm.copy(isEnabled = enabled))
    }

    fun setMyActivity(activity: UserActivity) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            repository.updateMyActivity(myUid, activity)
            _uiState.value = _uiState.value.copy(myActivity = activity)
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_MY_ACTIVITY] = activity.name
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
            getApplication<Application>().dataStore.edit { prefs ->
                prefs.remove(KEY_PARTNER_UID)
            }
            _uiState.value = _uiState.value.copy(
                partnerUid   = "",
                isConnected  = false,
                partnerStatus = DeviceStatus()
            )
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
    val dndConfig: DndConfig = DndConfig(),
    val alarmsForPartner: List<RemoteAlarm> = emptyList(),
    val alarmsFromPartner: List<RemoteAlarm> = emptyList(),
    val theme: AppTheme = AppTheme.DARK,
    val errorMessage: String? = null
)
