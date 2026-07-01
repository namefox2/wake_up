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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.model.PartnerState
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

    var showPermDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.showPermissionGuide.collect {
            canWriteSettings = audioManager.canWriteSettings()
            canSetMute = audioManager.canSetMute()
            if (!canWriteSettings || !canSetMute) showPermDialog = true
        }
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("볼륨 제어 권한 필요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("상대방 기기를 제어하려면 아래 권한이 필요합니다.", fontSize = 14.sp)
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
            confirmButton = { TextButton(onClick = { showPermDialog = false }) { Text("확인") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface), contentAlignment = Alignment.Center) {
            BannerAdView()
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SilentLink", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
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
                            Text(
                                text = uiState.myCode.chunked(3).joinToString(" - "),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = AccentBlue, letterSpacing = 1.sp
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.myCode)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "복사", tint = colors.onSurface.copy(alpha = 0.5f))
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
                    DndSummaryCard(isEnabled = uiState.dndConfig.isEnabled)
                    Spacer(modifier = Modifier.height(12.dp))
                    AccessToggleCard(isAllowed = uiState.myStatus.isAccessAllowed, onToggle = { viewModel.toggleMyAccess(it) })
                }
                else -> {
                    PartnerListTab(
                        partners = uiState.partners,
                        maxDevices = uiState.maxDevices,
                        canWriteSettings = canWriteSettings,
                        canSetMute = canSetMute,
                        volumeRestoreInfo = uiState.volumeRestoreInfo,
                        onVolumeSelect = { uid, level -> viewModel.sendVolumeCommandTo(uid, level) },
                        onDisconnect = { uid -> viewModel.disconnectFromPartner(uid) },
                        onConnect = { code -> viewModel.connectWithPartnerCode(code) },
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
    maxDevices: Int,
    canWriteSettings: Boolean,
    canSetMute: Boolean,
    volumeRestoreInfo: String?,
    onVolumeSelect: (String, VolumeLevel) -> Unit,
    onDisconnect: (String) -> Unit,
    onConnect: (String) -> Unit,
    onGrantPermission: () -> Unit,
    onGrantDndPermission: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showConnectInput by remember { mutableStateOf(false) }
    var partnerCodeInput by remember { mutableStateOf("") }

    val canAddMore = partners.size < maxDevices

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
                if (canAddMore) showConnectInput = !showConnectInput
            },
            enabled = canAddMore,
            colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)
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
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            PartnerCard(
                partner = partner,
                canWriteSettings = canWriteSettings,
                canSetMute = canSetMute,
                restoreInfo = if (index == 0) volumeRestoreInfo else null,
                onVolumeSelect = { level -> onVolumeSelect(partner.uid, level) },
                onDisconnect = { onDisconnect(partner.uid) },
                onGrantPermission = onGrantPermission,
                onGrantDndPermission = onGrantDndPermission
            )
        }
    }
}

// ── 파트너 카드 (상태 + 제어) ────────────────────────────────────

@Composable
private fun PartnerCard(
    partner: PartnerState,
    canWriteSettings: Boolean,
    canSetMute: Boolean,
    restoreInfo: String?,
    onVolumeSelect: (VolumeLevel) -> Unit,
    onDisconnect: () -> Unit,
    onGrantPermission: () -> Unit,
    onGrantDndPermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var pendingLevel by remember { mutableStateOf<VolumeLevel?>(null) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val status = partner.status
    val hasActivity = status.activity != UserActivity.NONE

    val statusColor = when (status.volumeLevel) {
        VolumeLevel.MUTE    -> DangerRed
        VolumeLevel.VIBRATE -> AccentBlue
        VolumeLevel.SOUND   -> SuccessGreen
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
            text = { Text("상대방 기기를 $levelDesc 상태로 전환할까요?\n\n10분 후 원래 상태로 자동 복원됩니다") },
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
            text = { Text("이 기기와의 연결을 해제할까요?") },
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

            // 상태 행
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("현재 상태", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${status.volumeLevel.icon} ${status.volumeLevel.label}",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = statusColor
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text(status.volumeLevel.icon, fontSize = 20.sp) }
                    if (!hasActivity) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showDisconnectDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.LinkOff, contentDescription = "연결 해제", tint = colors.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))

            // 권한 경고
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                            Text("탭하여 허용 → SilentLink 켜기", fontSize = 10.sp, color = AccentBlue.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!status.isAccessAllowed) {
                    Text("상대방이 접근을 차단했습니다", fontSize = 11.sp, color = DangerRed, modifier = Modifier.padding(bottom = 6.dp))
                }

                // 볼륨 버튼
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
                    Spacer(modifier = Modifier.height(8.dp))
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
private fun DndSummaryCard(isEnabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
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
                    if (isEnabled) "활성 중 – 강제 무음 해제 차단됨" else "비활성",
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
