package com.silentlink.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.silentlink.app.manager.DndManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.ui.util.findActivity
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.model.PartnerState
import com.silentlink.app.model.RestoreDuration
import com.silentlink.app.model.UserActivity
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.SuccessGreen

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val audioManager = remember { AudioControlManager(context) }
    val dndManager = remember { DndManager(context) }
    var canWriteSettings by remember { mutableStateOf(audioManager.canWriteSettings()) }
    var canSetMute by remember { mutableStateOf(audioManager.canSetMute()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canWriteSettings = audioManager.canWriteSettings()
                canSetMute = audioManager.canSetMute()
                viewModel.refreshPartnerStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSlotLoginDialog by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.showPermissionGuide.collect {
            canWriteSettings = audioManager.canWriteSettings()
            canSetMute = audioManager.canSetMute()
            if (!canWriteSettings || !canSetMute) showPermDialog = true
        }
    }
    // 두 권한이 모두 허용되면 팝업 자동 닫기
    LaunchedEffect(canWriteSettings, canSetMute) {
        if (canWriteSettings && canSetMute) showPermDialog = false
    }

    if (showSlotLoginDialog) {
        AlertDialog(
            onDismissRequest = { showSlotLoginDialog = false },
            title = { Text("Google 로그인 필요") },
            text = { Text("결제를 위해 Google 로그인이 필요합니다.\n로그인하시겠습니까?", fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showSlotLoginDialog = false
                    context.findActivity()?.let { viewModel.requestSlotPurchase(it) }
                }) { Text("로그인", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showSlotLoginDialog = false }) { Text("취소") }
            }
        )
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("권한 설정 필요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("상대방이 내 기기를 제어할 수 있도록\n아래 권한을 허용해 주세요.", fontSize = 14.sp)
                    if (!canWriteSettings) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, AccentBlue)
                        ) { Text("시스템 설정 변경 허용", color = AccentBlue) }
                    }
                    if (!canSetMute) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, AccentBlue)
                        ) { Text("방해금지 접근 허용 (무음 설정)", color = AccentBlue) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPermDialog = false }) { Text("나중에") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface), contentAlignment = Alignment.Center) {
            BannerAdView(adConsentAccepted = uiState.adConsentAccepted)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("깨워줘", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                if (uiState.isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${uiState.partners.size}대 연결됨", fontSize = 13.sp, color = SuccessGreen)
                    }
                } else {
                    Text("연결 없음", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.4f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 내 초대코드
            if (uiState.myCode.isNotEmpty()) {
                var showRefreshConfirm by remember { mutableStateOf(false) }

                if (showRefreshConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRefreshConfirm = false },
                        title = { Text("코드 새로고침") },
                        text = { Text("기존 코드가 즉시 무효화됩니다.\n이미 연결된 파트너는 영향받지 않습니다.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showRefreshConfirm = false
                                viewModel.refreshMyCode()
                            }) { Text("새로고침") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRefreshConfirm = false }) { Text("취소") }
                        }
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("내 초대 코드", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
                            Spacer(modifier = Modifier.height(4.dp))
                            if (uiState.isRefreshingCode) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                InviteCodeDisplay(code = uiState.myCode)
                            }
                        }
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.myCode)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "복사", tint = colors.onSurface.copy(alpha = 0.5f))
                            }
                            IconButton(
                                onClick = { showRefreshConfirm = true },
                                enabled = !uiState.isRefreshingCode
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "코드 새로고침", tint = colors.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 탭
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentBlue,
                divider = { HorizontalDivider(color = colors.outline.copy(alpha = 0.2f)) }
            ) {
                listOf("내 상태", "상대방").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = AccentBlue,
                        unselectedContentColor = colors.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    MyActivityCard(currentActivity = uiState.myActivity, onActivitySelect = { viewModel.setMyActivity(it) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DndSummaryCard(
                        isEnabled = uiState.dndConfig.isEnabled,
                        isCurrentlyInDndTime = dndManager.isInDndTime(uiState.dndConfig)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AccessToggleCard(isAllowed = uiState.myStatus.isAccessAllowed, onToggle = { viewModel.toggleMyAccess(it) })
                }
                else -> {
                    PartnerListTab(
                        partners = uiState.partners,
                        controllers = uiState.controllers,
                        maxDevices = uiState.maxDevices,
                        canWriteSettings = canWriteSettings,
                        canSetMute = canSetMute,
                        volumeRestoreInfo = uiState.volumeRestoreInfo,
                        volumeRestorePartnerUid = uiState.volumeRestorePartnerUid,
                        deviceNames = uiState.deviceNames,
                        restoreDuration = uiState.restoreDuration,
                        onRestoreDurationChange = { viewModel.setRestoreDuration(it) },
                        onVolumeSelect = { uid, level -> viewModel.sendVolumeCommandTo(uid, level) },
                        onDisconnect = { uid -> viewModel.disconnectFromPartner(uid) },
                        onRemoveController = { uid -> viewModel.removeController(uid) },
                        onSetDeviceName = { uid, name -> viewModel.setDeviceName(uid, name) },
                        onConnect = { code -> viewModel.connectWithPartnerCode(code) },
                        onAddSlot = {
                            if (uiState.googleEmail == null) {
                                showSlotLoginDialog = true
                            } else {
                                context.findActivity()?.let { viewModel.requestSlotPurchase(it) }
                            }
                        },
                        onGrantPermission = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        },
                        onGrantDndPermission = {
                            context.startActivity(Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))
                        },
                        errorMessage = uiState.errorMessage,
                        onClearError = { viewModel.clearError() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ── 상대방 탭 ────────────────────────────────────────────────────

@Composable
private fun PartnerListTab(
    partners: List<PartnerState>,
    controllers: List<com.silentlink.app.model.ControllerState>,
    maxDevices: Int,
    canWriteSettings: Boolean,
    canSetMute: Boolean,
    volumeRestoreInfo: String?,
    volumeRestorePartnerUid: String?,
    deviceNames: Map<String, String>,
    restoreDuration: RestoreDuration,
    onRestoreDurationChange: (RestoreDuration) -> Unit,
    onVolumeSelect: (String, VolumeLevel) -> Unit,
    onDisconnect: (String) -> Unit,
    onRemoveController: (String) -> Unit,
    onSetDeviceName: (String, String) -> Unit,
    onConnect: (String) -> Unit,
    onAddSlot: () -> Unit,
    onGrantPermission: () -> Unit,
    onGrantDndPermission: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showConnectInput by remember { mutableStateOf(false) }
    var partnerCodeInput by remember { mutableStateOf("") }
    val dndManager = remember { DndManager(LocalContext.current) }

    val canAddMore = partners.size < maxDevices

    // 복원 시간 선택
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("복원", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.5f))
        RestoreDuration.entries.forEach { option ->
            val selected = option == restoreDuration
            OutlinedButton(
                onClick = { onRestoreDurationChange(option) },
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) AccentBlue.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (selected) AccentBlue else colors.onSurface.copy(alpha = 0.55f)
                ),
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) AccentBlue else colors.outline.copy(alpha = 0.3f)
                )
            ) {
                Text(option.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 연결 추가 버튼
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "연결된 기기 (${partners.size}/$maxDevices)",
            fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.6f)
        )
        TextButton(
            onClick = {
                if (canAddMore) showConnectInput = !showConnectInput else onAddSlot()
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (canAddMore) AccentBlue else DangerRed
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (canAddMore) "연결 추가" else "슬롯 부족", fontSize = 13.sp)
        }
    }

    // 코드 입력 카드
    if (showConnectInput) {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = partnerCodeInput,
                    onValueChange = { partnerCodeInput = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    placeholder = { Text("상대방 6자리 코드", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                        letterSpacing = 3.sp, textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, cursorColor = AccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (partnerCodeInput.length == 6) {
                            onConnect(partnerCodeInput)
                            partnerCodeInput = ""
                            showConnectInput = false
                        }
                    })
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showConnectInput = false; partnerCodeInput = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text("취소") }
                    Button(
                        onClick = {
                            if (partnerCodeInput.length == 6) {
                                onConnect(partnerCodeInput)
                                partnerCodeInput = ""
                                showConnectInput = false
                            }
                        },
                        enabled = partnerCodeInput.length == 6,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("연결") }
                }
            }
        }
    }

    errorMessage?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (partners.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LinkOff, contentDescription = null,
                    tint = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("연결된 기기가 없습니다", fontSize = 14.sp, color = colors.onSurface.copy(alpha = 0.5f))
                Text("위의 '연결 추가' 버튼을 눌러 연결하세요", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.35f))
            }
        }
    } else {
        partners.forEachIndexed { index, partner ->
            key(partner.uid) {
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                val isPartnerInDnd = remember(partner.dndConfig) {
                    partner.dndConfig?.let { dndManager.isInDndTime(it) } == true
                }
                PartnerCard(
                    partner = partner,
                    name = deviceNames[partner.uid],
                    canWriteSettings = canWriteSettings,
                    canSetMute = canSetMute,
                    restoreInfo = if (partner.uid == volumeRestorePartnerUid) volumeRestoreInfo else null,
                    restoreDurationLabel = restoreDuration.label,
                    isPartnerInDnd = isPartnerInDnd,
                    onVolumeSelect = { level -> onVolumeSelect(partner.uid, level) },
                    onDisconnect = { onDisconnect(partner.uid) },
                    onRename = { name -> onSetDeviceName(partner.uid, name) },
                    onGrantPermission = onGrantPermission,
                    onGrantDndPermission = onGrantDndPermission
                )
            }
        }
    }

    // 나를 등록한 기기 섹션
    if (controllers.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "나를 제어하는 기기",
            fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        controllers.forEach { controller ->
            key(controller.uid) {
            var showBlockDialog by remember { mutableStateOf(false) }
            var showRenameDialog by remember { mutableStateOf(false) }
            var renameInput by remember { mutableStateOf(deviceNames[controller.uid] ?: "") }
            val displayName = deviceNames[controller.uid] ?: "코드 ${controller.inviteCode}"

            if (showBlockDialog) {
                AlertDialog(
                    onDismissRequest = { showBlockDialog = false },
                    title = { Text("제어 차단") },
                    text = { Text("$displayName 기기가 더 이상 내 기기를 제어할 수 없게 됩니다.") },
                    confirmButton = {
                        TextButton(onClick = { onRemoveController(controller.uid); showBlockDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                        ) { Text("차단") }
                    },
                    dismissButton = { TextButton(onClick = { showBlockDialog = false }) { Text("취소") } }
                )
            }
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("기기 이름 설정") },
                    text = {
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it.take(20) },
                            placeholder = { Text("예: 남자친구, 엄마") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = AccentBlue,
                                cursorColor = AccentBlue
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onSetDeviceName(controller.uid, renameInput)
                            showRenameDialog = false
                        }) { Text("저장") }
                    },
                    dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("취소") } }
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit, contentDescription = "이름 변경",
                                tint = colors.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(14.dp).clickable {
                                    renameInput = deviceNames[controller.uid] ?: ""
                                    showRenameDialog = true
                                }
                            )
                        }
                        Text("이 기기가 나를 제어할 수 있습니다", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.5f))
                    }
                    TextButton(
                        onClick = { showBlockDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) { Text("차단", fontSize = 13.sp) }
                }
            }
            } // key(controller.uid)
        }
    }
}

// ── 파트너 카드 (상태 + 제어) ────────────────────────────────────

@Composable
private fun PartnerCard(
    partner: PartnerState,
    name: String?,
    canWriteSettings: Boolean,
    canSetMute: Boolean,
    restoreInfo: String?,
    restoreDurationLabel: String,
    isPartnerInDnd: Boolean = false,
    onVolumeSelect: (VolumeLevel) -> Unit,
    onDisconnect: () -> Unit,
    onRename: (String) -> Unit,
    onGrantPermission: () -> Unit,
    onGrantDndPermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var pendingLevel by remember { mutableStateOf<VolumeLevel?>(null) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(name ?: "") }
    val status = partner.status
    val hasActivity = status.activity != UserActivity.NONE
    val displayName = name ?: "연결된 기기"

    val statusColor = when (status.volumeLevel) {
        VolumeLevel.MUTE    -> DangerRed
        VolumeLevel.VIBRATE -> AccentBlue
        VolumeLevel.SOUND   -> SuccessGreen
    }

    // 이름 변경 다이얼로그
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("기기 이름 설정") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it.take(20) },
                    placeholder = { Text("예: 남자친구, 엄마") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = AccentBlue,
                        cursorColor = AccentBlue
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(renameInput); showRenameDialog = false }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("취소") } }
        )
    }

    // 전환 확인 다이얼로그
    pendingLevel?.let { level ->
        val levelColor = when (level) {
            VolumeLevel.MUTE -> DangerRed; VolumeLevel.VIBRATE -> AccentBlue; VolumeLevel.SOUND -> SuccessGreen
        }
        val levelDesc = when (level) {
            VolumeLevel.MUTE -> "무음"; VolumeLevel.VIBRATE -> "진동"; VolumeLevel.SOUND -> "소리"
        }
        AlertDialog(
            onDismissRequest = { pendingLevel = null },
            title = { Text("${level.icon} $levelDesc 모드로 전환", fontWeight = FontWeight.Bold) },
            text = {
                val restoreNote = if (restoreDurationLabel == "무제한") "자동 복원 없음"
                                  else "$restoreDurationLabel 후 원래 상태로 자동 복원됩니다"
                Text("$displayName 기기를 $levelDesc 상태로 전환할까요?\n\n$restoreNote")
            },
            confirmButton = {
                TextButton(onClick = { onVolumeSelect(level); pendingLevel = null }) {
                    Text("전환", color = levelColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingLevel = null }) { Text("취소") } }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("연결 해제") },
            text = { Text("$displayName 와의 연결을 해제할까요?") },
            confirmButton = {
                TextButton(onClick = { onDisconnect(); showDisconnectDialog = false }) {
                    Text("해제", color = DangerRed)
                }
            },
            dismissButton = { TextButton(onClick = { showDisconnectDialog = false }) { Text("취소") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasActivity) DangerRed.copy(alpha = 0.08f) else colors.surfaceVariant
        )
    ) {
        Column {
            // 활동 배너
            if (hasActivity) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status.activity.emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(status.activity.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDisconnectDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.LinkOff, contentDescription = "연결 해제", tint = DangerRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 헤더: 이름 + 현재 상태 + 연결 해제
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit, contentDescription = "이름 변경",
                            tint = colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(13.dp).clickable {
                                renameInput = name ?: ""
                                showRenameDialog = true
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${status.volumeLevel.icon} ${status.volumeLevel.label}",
                            fontSize = 12.sp, color = statusColor
                        )
                        if (isPartnerInDnd) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("🌙 방해금지", fontSize = 9.sp, color = AccentBlue)
                            }
                        }
                    }
                }
                if (!hasActivity) {
                    IconButton(onClick = { showDisconnectDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.LinkOff, contentDescription = "연결 해제", tint = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 볼륨 제어 버튼 (항상 상단에)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!status.isAccessAllowed) {
                    Text("상대방이 접근을 차단했습니다", fontSize = 11.sp, color = DangerRed, modifier = Modifier.padding(bottom = 6.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeLevel.entries.forEach { level ->
                        val isSelected = status.volumeLevel == level
                        val levelColor = when (level) {
                            VolumeLevel.MUTE -> DangerRed; VolumeLevel.VIBRATE -> AccentBlue; VolumeLevel.SOUND -> SuccessGreen
                        }
                        OutlinedButton(
                            onClick = { if (status.isAccessAllowed) pendingLevel = level },
                            enabled = status.isAccessAllowed,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) levelColor.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (isSelected) levelColor else colors.onSurface.copy(alpha = 0.65f),
                                disabledContentColor = colors.onSurface.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) levelColor else colors.outline.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(level.icon, fontSize = 18.sp)
                                Text(level.label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                // 복원 예정 배너
                restoreInfo?.let { info ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(info, fontSize = 11.sp, color = AccentBlue)
                    }
                }

                // 권한 경고 (볼륨 버튼 아래)
                if (!canWriteSettings || !canSetMute) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!canWriteSettings) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(AccentBlue.copy(alpha = 0.1f)).clickable(onClick = onGrantPermission)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚙️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("시스템 설정 변경 권한 필요", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                Text("탭하여 허용", fontSize = 10.sp, color = AccentBlue.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (!canSetMute) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(DangerRed.copy(alpha = 0.08f)).clickable(onClick = onGrantDndPermission)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔕", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("무음 모드 권한 필요", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DangerRed)
                                Text("탭하여 방해금지 접근 허용", fontSize = 10.sp, color = DangerRed.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DangerRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── 내 현재 상태 카드 ──────────────────────────────────────────────

@Composable
private fun MyActivityCard(currentActivity: UserActivity, onActivitySelect: (UserActivity) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val activities = UserActivity.entries.filter { it != UserActivity.NONE }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("내 현재 상태", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
                    Text(
                        text = if (currentActivity == UserActivity.NONE) "상태 없음"
                               else "${currentActivity.emoji} ${currentActivity.label}",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (currentActivity == UserActivity.NONE) colors.onSurface.copy(alpha = 0.35f) else DangerRed,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (currentActivity != UserActivity.NONE) {
                    TextButton(
                        onClick = { onActivitySelect(UserActivity.NONE) },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface.copy(alpha = 0.45f))
                    ) { Text("해제", fontSize = 12.sp) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            activities.chunked(4).forEachIndexed { rowIdx, rowItems ->
                if (rowIdx > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { activity ->
                        val isSelected = currentActivity == activity
                        OutlinedButton(
                            onClick = { onActivitySelect(if (isSelected) UserActivity.NONE else activity) },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) DangerRed.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (isSelected) DangerRed else colors.onSurface.copy(alpha = 0.65f)
                            ),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) DangerRed else colors.outline.copy(alpha = 0.35f)
                            ),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(activity.emoji, fontSize = 20.sp)
                                Text(
                                    activity.label.replace("중", "\n중"), fontSize = 9.sp,
                                    textAlign = TextAlign.Center, lineHeight = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ── 방해금지 요약 카드 ────────────────────────────────────────────

@Composable
private fun DndSummaryCard(isEnabled: Boolean, isCurrentlyInDndTime: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyInDndTime) DangerRed.copy(alpha = 0.08f) else colors.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule, contentDescription = null,
                tint = if (isEnabled) DangerRed else colors.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("방해금지 스케줄", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(
                    when {
                        isCurrentlyInDndTime -> "🚫 지금은 방해금지 시간입니다"
                        isEnabled -> "활성 중 – 강제 무음 해제 차단됨"
                        else -> "비활성"
                    },
                    fontSize = 12.sp,
                    color = if (isEnabled) DangerRed else colors.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

// ── 접근 허용 토글 카드 ───────────────────────────────────────────

@Composable
private fun AccessToggleCard(isAllowed: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllowed) AccentBlue.copy(alpha = 0.1f) else DangerRed.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "내 기기 접근 허용",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isAllowed) AccentBlue else DangerRed
                )
                Text(
                    if (isAllowed) "상대방이 내 기기를 제어할 수 있습니다" else "모든 원격 제어가 차단됩니다",
                    fontSize = 12.sp,
                    color = if (isAllowed) AccentBlue.copy(alpha = 0.7f) else DangerRed.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = isAllowed, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = DangerRed.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun InviteCodeDisplay(code: String) {
    val chars = code.uppercase().padEnd(6).take(6)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chars.forEachIndexed { index, ch ->
            if (index == 3) {
                Text(
                    text = "-",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 30.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBlue.copy(alpha = 0.12f))
            ) {
                Text(
                    text = ch.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentBlue,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
