package com.silentlink.app.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.silentlink.app.DndPrefs
import com.silentlink.app.appDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.silentlink.app.billing.BillingManager
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.manager.DndManager
import com.silentlink.app.manager.GoogleSignInManager
import com.silentlink.app.model.AppTheme
import com.silentlink.app.model.ControllerState
import com.silentlink.app.model.DeviceStatus
import com.silentlink.app.model.DndConfig
import com.silentlink.app.model.PartnerState
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.model.UserActivity
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.repository.ConnectResult
import com.silentlink.app.repository.CouponResult
import com.silentlink.app.repository.FirebaseRepository
import com.silentlink.app.repository.LinkResult
import com.silentlink.app.service.SilentLinkService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository()
    private val audioManager = AudioControlManager(application)
    private val dndManager = DndManager(application)
    private val googleSignInManager = GoogleSignInManager(application)
    private val gson = Gson()
    val billingManager = BillingManager(application)

    // SharedPreferences에서 동기적으로 읽어 DataStore 비동기 로드 전 첫 프레임 플래시 방지
    private val quickPrefs = application.getSharedPreferences("silentlink_quick", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(SilentLinkUiState(
        theme = runCatching { AppTheme.valueOf(quickPrefs.getString("theme", "") ?: "") }
            .getOrElse { AppTheme.DARK }
    ))
    val uiState: StateFlow<SilentLinkUiState> = _uiState

    private val _isOnboarded = MutableStateFlow(quickPrefs.getBoolean("onboarded", false))
    val isOnboarded: StateFlow<Boolean> = _isOnboarded

    // ViewModel 생존 기간 동안 권한 안내 탐색을 한 번만 허용 (Activity 재생성 플래시 방지)
    private var permissionPromptShown = false
    fun consumePermissionPrompt(): Boolean {
        if (permissionPromptShown) return false
        permissionPromptShown = true
        return true
    }

    // Google Sign-In 요청 이벤트 (Activity에서 launcher로 처리)
    private val _googleSignInRequest = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val googleSignInRequest: SharedFlow<Intent> = _googleSignInRequest

    // 새 컨트롤러 등록 시 권한 안내 이벤트
    private val _showPermissionGuide = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showPermissionGuide: SharedFlow<Unit> = _showPermissionGuide

    // 알림 탭으로 인한 탭 이동 요청
    private val _pendingNavRoute = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingNavRoute: SharedFlow<String> = _pendingNavRoute

    fun handleNotificationRoute(route: String) {
        _pendingNavRoute.tryEmit(route)
    }

    private var restoreInfoJob: Job? = null
    private var pendingSlotPurchaseActivity: Activity? = null
    private val partnerListenerJobs = mutableMapOf<String, List<Job>>()

    companion object {
        val KEY_ONBOARDED    = booleanPreferencesKey("onboarded")
        val KEY_MY_UID       = stringPreferencesKey("my_uid")
        val KEY_MY_CODE      = stringPreferencesKey("my_code")
        val KEY_PARTNER_UIDS = stringPreferencesKey("partner_uids")   // 쉼표 구분
        val KEY_PARTNER_UID  = stringPreferencesKey("partner_uid")    // 구버전 마이그레이션용
        val KEY_THEME        = stringPreferencesKey("theme")
        val KEY_DND_CONFIG   = stringPreferencesKey("dnd_config")
        val KEY_MY_ACTIVITY  = stringPreferencesKey("my_activity")
        val KEY_DEVICE_NAMES = stringPreferencesKey("device_names")
        val KEY_AD_CONSENT   = booleanPreferencesKey("ad_consent")
    }

    init {
        viewModelScope.launch { loadPersistedState() }
        billingManager.connect(
            onSlotPurchased = { newSlots ->
                viewModelScope.launch {
                    val myUid = _uiState.value.myUid.ifEmpty { return@launch }
                    repository.setPurchasedSlots(myUid, newSlots)
                    _uiState.value = _uiState.value.copy(purchasedSlots = newSlots)
                }
            },
            onBillingReady = { playSlots ->
                // Play Store가 준비되면 Firebase와 슬롯 수를 맞춤 (계정 삭제 후 복원 대응)
                viewModelScope.launch {
                    val myUid = _uiState.value.myUid.ifEmpty { return@launch }
                    val firebaseSlots = _uiState.value.purchasedSlots
                    if (playSlots > firebaseSlots) {
                        repository.setPurchasedSlots(myUid, playSlots)
                        _uiState.value = _uiState.value.copy(purchasedSlots = playSlots)
                    }
                }
            }
        )
        viewModelScope.launch {
            billingManager.billingState.collect { state ->
                if (state is BillingManager.BillingState.Error) {
                    _uiState.value = _uiState.value.copy(errorMessage = state.message)
                }
            }
        }
    }

    private suspend fun loadPersistedState() {
        val context = getApplication<Application>()
        val prefs = context.appDataStore.data.first()
        val onboarded = prefs[KEY_ONBOARDED] ?: false
        quickPrefs.edit().putBoolean("onboarded", onboarded).apply()
        _isOnboarded.value = onboarded
        if (!onboarded) return

        val myUid  = prefs[KEY_MY_UID]  ?: return
        val myCode = prefs[KEY_MY_CODE] ?: return
        val theme  = runCatching { AppTheme.valueOf(prefs[KEY_THEME] ?: "") }.getOrDefault(AppTheme.DARK)
        quickPrefs.edit().putString("theme", theme.name).apply()
        val dndConfig  = runCatching { gson.fromJson(prefs[KEY_DND_CONFIG], DndConfig::class.java) }.getOrDefault(DndConfig())
        val myActivity = runCatching { UserActivity.valueOf(prefs[KEY_MY_ACTIVITY] ?: "") }.getOrDefault(UserActivity.NONE)

        // 파트너 UID 로드 (신규 포맷 → 구버전 폴백)
        val partnerUids = (prefs[KEY_PARTNER_UIDS]?.split(",")?.filter { it.isNotEmpty() }
            ?: prefs[KEY_PARTNER_UID]?.let { listOf(it) }
            ?: emptyList())

        val isAdmin = runCatching { repository.isAdmin(myUid) }.getOrDefault(false)
        val purchasedSlots = if (isAdmin) 4 else runCatching { repository.getPurchasedSlots(myUid) }.getOrDefault(0)
        val deviceNames = runCatching {
            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(prefs[KEY_DEVICE_NAMES] ?: "{}", mapType) ?: emptyMap()
        }.getOrDefault(emptyMap())

        val resolvedDnd = dndConfig ?: DndConfig()
        val adConsent = prefs[KEY_AD_CONSENT] ?: false
        _uiState.value = _uiState.value.copy(
            myCode     = myCode,
            myUid      = myUid,
            partners   = partnerUids.map { PartnerState(uid = it) },
            theme      = theme,
            dndConfig  = resolvedDnd,
            myStatus   = DeviceStatus(isMuted = audioManager.isMuted(), isOnline = true),
            myActivity = myActivity,
            purchasedSlots = purchasedSlots,
            googleEmail = repository.getGoogleEmail(),
            deviceNames = deviceNames,
            adConsentAccepted = adConsent
        )
        DndPrefs.save(context, resolvedDnd)

        // 온보딩된 기기는 파트너 유무와 관계없이 항상 서비스 실행
        // (원격 명령 수신, 볼륨 상태 동기화, 알람 스케줄링 필요)
        SilentLinkService.start(context)
        partnerUids.forEach { listenToPartner(it) }
        listenToMyAlarms(myUid)
        listenToControllers(myUid)
        listenToMyPartnerIds(myUid)

        // 구버전 DataStore 마이그레이션
        if (prefs[KEY_PARTNER_UID] != null && prefs[KEY_PARTNER_UIDS] == null) {
            context.appDataStore.edit { p ->
                p[KEY_PARTNER_UIDS] = partnerUids.joinToString(",")
                p.remove(KEY_PARTNER_UID)
            }
        }
    }

    fun onboard(adConsentAccepted: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOnboarding = true, errorMessage = null)
            try {
                val uid  = repository.signInAnonymously()
                val code = repository.generateInviteCode()
                repository.registerDevice(uid, code)
                getApplication<Application>().appDataStore.edit { prefs ->
                    prefs[KEY_ONBOARDED]  = true
                    prefs[KEY_MY_UID]     = uid
                    prefs[KEY_MY_CODE]    = code
                    prefs[KEY_AD_CONSENT] = adConsentAccepted
                }
                _uiState.value = _uiState.value.copy(myUid = uid, myCode = code, adConsentAccepted = adConsentAccepted)
                _isOnboarded.value = true
                quickPrefs.edit().putBoolean("onboarded", true).apply()
                // 동의 기록은 화면 전환 후 백그라운드에서 처리 (네트워크 지연이 UX에 영향 없도록)
                viewModelScope.launch {
                    runCatching {
                        val ctx = getApplication<Application>()
                        val pm = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                        val versionName = pm.versionName ?: "unknown"
                        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                            pm.longVersionCode else pm.versionCode.toLong()
                        repository.recordConsent(
                            uid = uid,
                            appVersion = versionName,
                            appVersionCode = versionCode,
                            androidSdkInt = android.os.Build.VERSION.SDK_INT,
                            deviceManufacturer = android.os.Build.MANUFACTURER,
                            deviceModel = android.os.Build.MODEL,
                            adConsentAccepted = adConsentAccepted
                        )
                    }
                }
                SilentLinkService.start(getApplication())
                listenToMyAlarms(uid)
                listenToControllers(uid)
                listenToMyPartnerIds(uid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isOnboarding = false, errorMessage = "초기화 실패: ${e.message}")
            }
        }
    }

    fun connectWithPartnerCode(partnerCode: String) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            _uiState.value = _uiState.value.copy(errorMessage = null)

            val maxDevices = 1 + _uiState.value.purchasedSlots
            if (_uiState.value.partners.size >= maxDevices) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "슬롯이 부족합니다. 설정에서 추가 슬롯을 구매하세요."
                )
                return@launch
            }

            try {
                when (repository.connectWithCode(myUid, partnerCode)) {
                    ConnectResult.SUCCESS -> {
                        val partnerUids = repository.getPartnerUids(myUid)
                        savePartnerUids(partnerUids)
                        val newPartners = partnerUids.map { uid ->
                            _uiState.value.partners.find { it.uid == uid } ?: PartnerState(uid = uid)
                        }
                        _uiState.value = _uiState.value.copy(partners = newPartners, errorMessage = null)
                        partnerUids.lastOrNull()?.let { listenToPartner(it) }
                    }
                    ConnectResult.NOT_FOUND ->
                        _uiState.value = _uiState.value.copy(errorMessage = "코드를 찾을 수 없습니다")
                    ConnectResult.ALREADY_CONNECTED ->
                        _uiState.value = _uiState.value.copy(errorMessage = "이미 연결된 코드입니다")
                    ConnectResult.SLOT_LIMIT_REACHED ->
                        _uiState.value = _uiState.value.copy(errorMessage = "슬롯이 부족합니다")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "연결 실패: ${e.message}")
            }
        }
    }

    private fun listenToPartner(partnerUid: String) {
        partnerListenerJobs[partnerUid]?.forEach { it.cancel() }
        val statusJob = viewModelScope.launch {
            repository.observePartnerStatus(partnerUid).collect { status ->
                _uiState.value = _uiState.value.copy(
                    partners = _uiState.value.partners.map { p ->
                        if (p.uid == partnerUid) p.copy(status = status) else p
                    }
                )
            }
        }
        val alarmsJob = viewModelScope.launch {
            repository.observeAlarms(partnerUid).collect { alarms ->
                _uiState.value = _uiState.value.copy(
                    partners = _uiState.value.partners.map { p ->
                        if (p.uid == partnerUid) p.copy(alarmsForThem = alarms) else p
                    }
                )
            }
        }
        partnerListenerJobs[partnerUid] = listOf(statusJob, alarmsJob)
    }

    private fun listenToMyAlarms(myUid: String) {
        viewModelScope.launch {
            repository.observeAlarms(myUid).collect { alarms ->
                _uiState.value = _uiState.value.copy(alarmsFromPartners = alarms)
            }
        }
    }

    private fun listenToControllers(myUid: String) {
        var initialized = false
        var prevUids = emptySet<String>()
        val codeCache = mutableMapOf<String, String>()
        viewModelScope.launch {
            repository.observeControllers(myUid).collect { uids ->
                val current = uids.toSet()
                val newOnes = current - prevUids
                // Fetch invite codes only for newly seen UIDs, reuse cache for the rest
                newOnes.forEach { uid ->
                    codeCache[uid] = runCatching { repository.getInviteCode(uid) }
                        .getOrElse { uid.takeLast(6).uppercase() }
                }
                val controllers = uids.map { uid ->
                    ControllerState(uid = uid, inviteCode = codeCache[uid] ?: uid.takeLast(6).uppercase())
                }
                _uiState.value = _uiState.value.copy(controllers = controllers)
                if (initialized && newOnes.isNotEmpty()) {
                    _showPermissionGuide.emit(Unit)
                }
                initialized = true
                prevUids = current
            }
        }
    }

    private fun listenToMyPartnerIds(myUid: String) {
        var initialized = false
        viewModelScope.launch {
            repository.observePartnerIds(myUid).collect { firebaseUids ->
                val currentUids = _uiState.value.partners.map { it.uid }.toSet()
                val incoming = firebaseUids.toSet()
                val removed = currentUids - incoming
                // 첫 emit: 추가는 loadPersistedState에서 이미 처리 — 제거만 확인
                val added = if (initialized) incoming - currentUids else emptySet()
                initialized = true
                if (removed.isEmpty() && added.isEmpty()) return@collect

                removed.forEach { uid ->
                    partnerListenerJobs[uid]?.forEach { it.cancel() }
                    partnerListenerJobs.remove(uid)
                }
                added.forEach { uid -> listenToPartner(uid) }

                val newPartners = firebaseUids.map { uid ->
                    _uiState.value.partners.find { it.uid == uid } ?: PartnerState(uid = uid)
                }
                _uiState.value = _uiState.value.copy(partners = newPartners)
                savePartnerUids(firebaseUids)
            }
        }
    }

    fun removeController(controllerUid: String) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            runCatching { repository.removeController(myUid, controllerUid) }
        }
    }

    fun refreshMyCode() {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            val oldCode = _uiState.value.myCode.ifEmpty { return@launch }
            _uiState.value = _uiState.value.copy(isRefreshingCode = true)
            runCatching {
                val newCode = repository.refreshInviteCode(myUid, oldCode)
                getApplication<Application>().appDataStore.edit { prefs ->
                    prefs[KEY_MY_CODE] = newCode
                }
                _uiState.value = _uiState.value.copy(myCode = newCode, isRefreshingCode = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isRefreshingCode = false)
            }
        }
    }

    fun setDeviceName(uid: String, name: String) {
        viewModelScope.launch {
            val current = _uiState.value.deviceNames.toMutableMap()
            if (name.isBlank()) current.remove(uid) else current[uid] = name.trim()
            _uiState.value = _uiState.value.copy(deviceNames = current)
            getApplication<Application>().appDataStore.edit { prefs ->
                prefs[KEY_DEVICE_NAMES] = gson.toJson(current)
            }
        }
    }

    fun refreshPartnerStatus() {
        viewModelScope.launch {
            _uiState.value.partners.forEach { partner ->
                val status = repository.readPartnerStatus(partner.uid) ?: return@forEach
                _uiState.value = _uiState.value.copy(
                    partners = _uiState.value.partners.map { p ->
                        if (p.uid == partner.uid) p.copy(status = status) else p
                    }
                )
            }
        }
    }

    fun sendVolumeCommandTo(partnerUid: String, level: VolumeLevel) {
        viewModelScope.launch {
            val partner = _uiState.value.partners.find { it.uid == partnerUid } ?: return@launch
            if (!partner.status.isAccessAllowed) return@launch
            if (dndManager.isInDndTime(_uiState.value.dndConfig)) return@launch
            repository.sendCommand(partnerUid, "setVolume", level.name)
            restoreInfoJob?.cancel()
            _uiState.value = _uiState.value.copy(volumeRestoreInfo = "10분 후 원래 상태로 돌아갑니다")
            restoreInfoJob = viewModelScope.launch {
                delay(10 * 60 * 1000L)
                _uiState.value = _uiState.value.copy(volumeRestoreInfo = null)
            }
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
        DndPrefs.save(getApplication(), config)
        viewModelScope.launch {
            getApplication<Application>().appDataStore.edit { prefs ->
                prefs[KEY_DND_CONFIG] = gson.toJson(config)
            }
        }
    }

    // 알람 (파트너별)

    fun addAlarmFor(partnerUid: String, alarm: RemoteAlarm) {
        viewModelScope.launch {
            runCatching { repository.addAlarm(partnerUid, alarm) }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 저장 실패: ${it.message}")
            }
        }
    }

    fun updateAlarmFor(partnerUid: String, alarm: RemoteAlarm) {
        viewModelScope.launch {
            runCatching { repository.updateAlarm(partnerUid, alarm) }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 수정 실패: ${it.message}")
            }
        }
    }

    fun deleteAlarmFor(partnerUid: String, alarmId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteAlarm(partnerUid, alarmId) }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 삭제 실패: ${it.message}")
            }
        }
    }

    fun deleteMyAlarm(alarmId: String) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            runCatching { repository.deleteAlarm(myUid, alarmId) }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "알람 삭제 실패: ${it.message}")
            }
        }
    }

    fun toggleAlarmFor(partnerUid: String, alarmId: String, enabled: Boolean) {
        val partner = _uiState.value.partners.find { it.uid == partnerUid } ?: return
        val alarm = partner.alarmsForThem.find { it.id == alarmId } ?: return
        updateAlarmFor(partnerUid, alarm.copy(isEnabled = enabled))
    }

    fun selectAlarmPartner(uid: String) {
        _uiState.value = _uiState.value.copy(selectedAlarmPartnerUid = uid)
    }

    // 파트너 연결 해제

    fun disconnectFromPartner(partnerUid: String) {
        partnerListenerJobs[partnerUid]?.forEach { it.cancel() }
        partnerListenerJobs.remove(partnerUid)
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            runCatching { repository.disconnectFromPartner(myUid, partnerUid) }
            val newPartners = _uiState.value.partners.filter { it.uid != partnerUid }
            savePartnerUids(newPartners.map { it.uid })
            _uiState.value = _uiState.value.copy(partners = newPartners)
        }
    }

    fun disconnectAll() {
        partnerListenerJobs.values.forEach { jobs -> jobs.forEach { it.cancel() } }
        partnerListenerJobs.clear()
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            runCatching { repository.disconnectAll(myUid) }
            savePartnerUids(emptyList())
            _uiState.value = _uiState.value.copy(
                partners = emptyList(),
                volumeRestoreInfo = null
            )
        }
    }

    // Google Sign-In + 슬롯 구매

    fun requestSlotPurchase(activity: Activity) {
        pendingSlotPurchaseActivity = activity
        if (repository.isGoogleLinked()) {
            launchSlotBilling(activity)
        } else {
            viewModelScope.launch {
                try {
                    _googleSignInRequest.emit(googleSignInManager.getSignInIntent())
                } catch (e: IllegalStateException) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Google 로그인 설정이 완료되지 않았습니다.")
                }
            }
        }
    }

    fun onGoogleSignInResult(data: Intent?) {
        val idToken = googleSignInManager.extractIdToken(data) ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Google 로그인 실패")
            return
        }
        viewModelScope.launch {
            val result = repository.linkGoogleAccount(idToken)
            when (result) {
                LinkResult.LINKED, LinkResult.RESTORED -> {
                    val myUid = repository.getCurrentUserId() ?: return@launch
                    val slots = runCatching { repository.getPurchasedSlots(myUid) }.getOrDefault(0)
                    _uiState.value = _uiState.value.copy(
                        myUid = myUid,
                        purchasedSlots = slots,
                        googleEmail = repository.getGoogleEmail()
                    )
                    // RESTORED: 구매 이력이 Firebase에 있으면 자동 복원됨
                    pendingSlotPurchaseActivity?.let { launchSlotBilling(it) }
                }
                LinkResult.FAILED ->
                    _uiState.value = _uiState.value.copy(errorMessage = "Google 계정 연결 실패")
            }
            pendingSlotPurchaseActivity = null
        }
    }

    private fun launchSlotBilling(activity: Activity) {
        val currentSlots = _uiState.value.purchasedSlots
        if (currentSlots >= 4) {
            _uiState.value = _uiState.value.copy(errorMessage = "이미 최대 슬롯(5대)에 도달했습니다")
            return
        }
        billingManager.launchSlotPurchase(activity, currentSlots)
    }

    // 테마

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
        quickPrefs.edit().putString("theme", theme.name).apply()
        viewModelScope.launch {
            getApplication<Application>().appDataStore.edit { prefs ->
                prefs[KEY_THEME] = theme.name
            }
        }
    }

    fun setMyActivity(activity: UserActivity) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            repository.updateMyActivity(myUid, activity)
            _uiState.value = _uiState.value.copy(myActivity = activity)
            getApplication<Application>().appDataStore.edit { prefs ->
                prefs[KEY_MY_ACTIVITY] = activity.name
            }
        }
    }

    fun redeemCoupon(code: String) {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            val result = runCatching { repository.redeemCoupon(myUid, code) }.getOrElse {
                _uiState.value = _uiState.value.copy(couponMessage = "오류: ${it.message}")
                return@launch
            }
            when (result) {
                CouponResult.SUCCESS -> _uiState.value = _uiState.value.copy(
                    purchasedSlots = _uiState.value.purchasedSlots + 1,
                    couponMessage = "쿠폰이 적용되었습니다! 슬롯 1개가 추가되었습니다.",
                    couponSuccess = true
                )
                CouponResult.INVALID -> _uiState.value = _uiState.value.copy(couponMessage = "유효하지 않은 쿠폰 코드입니다", couponSuccess = false)
                CouponResult.ALREADY_USED -> _uiState.value = _uiState.value.copy(couponMessage = "이미 사용한 쿠폰입니다", couponSuccess = false)
                CouponResult.MAX_REACHED -> _uiState.value = _uiState.value.copy(couponMessage = "이미 최대 슬롯에 도달했습니다", couponSuccess = false)
            }
        }
    }

    fun clearCouponMessage() {
        _uiState.value = _uiState.value.copy(couponMessage = null, couponSuccess = false)
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val myUid = _uiState.value.myUid.ifEmpty { return@launch }
            val myCode = _uiState.value.myCode
            _uiState.value = _uiState.value.copy(isDeletingAccount = true)
            try {
                partnerListenerJobs.values.forEach { jobs -> jobs.forEach { it.cancel() } }
                partnerListenerJobs.clear()
                repository.deleteAccount(myUid, myCode)
                val context = getApplication<Application>()
                context.appDataStore.edit { it.clear() }
                quickPrefs.edit().clear().apply()
                SilentLinkService.stop(context)
                _uiState.value = SilentLinkUiState()
                _isOnboarded.value = false
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeletingAccount = false, errorMessage = "계정 삭제 실패: ${e.message}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun savePartnerUids(uids: List<String>) {
        getApplication<Application>().appDataStore.edit { prefs ->
            prefs[KEY_PARTNER_UIDS] = uids.joinToString(",")
        }
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.disconnect()
    }
}

data class SilentLinkUiState(
    val myUid: String = "",
    val myCode: String = "",
    val partners: List<PartnerState> = emptyList(),
    val controllers: List<ControllerState> = emptyList(),
    val myStatus: DeviceStatus = DeviceStatus(),
    val myActivity: UserActivity = UserActivity.NONE,
    val dndConfig: DndConfig = DndConfig(),
    val alarmsFromPartners: List<RemoteAlarm> = emptyList(),
    val theme: AppTheme = AppTheme.DARK,
    val errorMessage: String? = null,
    val volumeRestoreInfo: String? = null,
    val purchasedSlots: Int = 0,
    val googleEmail: String? = null,
    val selectedAlarmPartnerUid: String = "",
    val isRefreshingCode: Boolean = false,
    val deviceNames: Map<String, String> = emptyMap(),
    val couponMessage: String? = null,
    val couponSuccess: Boolean = false,
    val adConsentAccepted: Boolean = false,
    val isOnboarding: Boolean = false,
    val isDeletingAccount: Boolean = false
) {
    val isConnected: Boolean get() = partners.isNotEmpty()
    val maxDevices: Int get() = 1 + purchasedSlots

    val selectedPartnerForAlarm: PartnerState?
        get() = partners.find { it.uid == selectedAlarmPartnerUid } ?: partners.firstOrNull()

    val alarmsForSelectedPartner: List<RemoteAlarm>
        get() = selectedPartnerForAlarm?.alarmsForThem ?: emptyList()
}
