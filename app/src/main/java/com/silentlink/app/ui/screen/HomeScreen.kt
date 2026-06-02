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
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.manager.AudioControlManager
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
    val canWriteSettings = remember { AudioControlManager(context).canWriteSettings() }
    var partnerCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 광고 배너
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface), contentAlignment = Alignment.Center) {
            BannerAdView()
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            // ── 헤더 ──────────────────────────────────────────────
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
                        Text("연결됨", fontSize = 13.sp, color = SuccessGreen)
                    }
                } else {
                    Text("연결 없음", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.4f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 내 초대코드 카드 (항상 표시) ──────────────────────
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AccentBlue,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(uiState.myCode)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "복사", tint = colors.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 연결 안된 경우: 상대방 코드 입력 ─────────────────
            if (!uiState.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = colors.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "상대방 초대 코드를 입력하여 연결하세요",
                            fontSize = 14.sp,
                            color = colors.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = partnerCodeInput,
                            onValueChange = {
                                partnerCodeInput = it.uppercase().filter(Char::isLetterOrDigit).take(6)
                            },
                            placeholder = { Text("상대방 6자리 코드", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                letterSpacing = 3.sp,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentBlue,
                                cursorColor = AccentBlue
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (partnerCodeInput.length == 6)
                                    viewModel.connectWithPartnerCode(partnerCodeInput)
                            })
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.connectWithPartnerCode(partnerCodeInput) },
                            enabled = partnerCodeInput.length == 6,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                disabledContainerColor = AccentBlue.copy(alpha = 0.3f)
                            )
                        ) {
                            Text("연결하기", fontWeight = FontWeight.SemiBold)
                        }
                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            } else {
                // ── 내 현재 상태 ───────────────────────────────────
                MyActivityCard(
                    currentActivity = uiState.myActivity,
                    onActivitySelect = { viewModel.setMyActivity(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── 상대방 상태 ────────────────────────────────────
                PartnerStatusCard(
                    isMuted = uiState.partnerStatus.isMuted,
                    volumeLevel = uiState.partnerStatus.volumeLevel,
                    activity = uiState.partnerStatus.activity,
                    onRefresh = { viewModel.refreshPartnerStatus() }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── 무음 제어 ──────────────────────────────────────
                MuteControlCard(
                    volumeLevel = uiState.partnerStatus.volumeLevel,
                    isAccessAllowed = uiState.partnerStatus.isAccessAllowed,
                    partnerActivity = uiState.partnerStatus.activity,
                    canWriteSettings = canWriteSettings,
                    onVolumeSelect = { viewModel.sendVolumeCommand(it) },
                    onGrantPermission = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── 방해금지 요약 ──────────────────────────────────
                DndSummaryCard(isEnabled = uiState.dndConfig.isEnabled)
                Spacer(modifier = Modifier.height(12.dp))

                // ── 접근 허용 토글 ─────────────────────────────────
                AccessToggleCard(
                    isAllowed = uiState.myStatus.isAccessAllowed,
                    onToggle = { viewModel.toggleMyAccess(it) }
                )
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
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

            // 4열 그리드
            activities.chunked(4).forEachIndexed { rowIdx, rowItems ->
                if (rowIdx > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                    activity.label.replace("중", "\n중"),
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 12.sp,
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

// ── 상대방 상태 카드 ──────────────────────────────────────────────

@Composable
private fun PartnerStatusCard(
    isMuted: Boolean,
    volumeLevel: VolumeLevel,
    activity: UserActivity,
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasActivity = activity != UserActivity.NONE
    val statusColor = when (volumeLevel) {
        VolumeLevel.MUTE    -> DangerRed
        VolumeLevel.VIBRATE -> AccentBlue
        VolumeLevel.SOUND   -> SuccessGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasActivity) DangerRed.copy(alpha = 0.08f) else colors.surfaceVariant
        )
    ) {
        Column {
            if (hasActivity) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(activity.emoji, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(activity.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                        Text(activity.description, fontSize = 11.sp, color = DangerRed.copy(alpha = 0.7f))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("상대방 현재 상태", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${volumeLevel.icon} ${volumeLevel.label}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(volumeLevel.icon, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = colors.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── 무음 제어 카드 ────────────────────────────────────────────────

@Composable
private fun MuteControlCard(
    volumeLevel: VolumeLevel,
    isAccessAllowed: Boolean,
    partnerActivity: UserActivity,
    canWriteSettings: Boolean,
    onVolumeSelect: (VolumeLevel) -> Unit,
    onGrantPermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var pendingLevel by remember { mutableStateOf<VolumeLevel?>(null) }

    pendingLevel?.let { level ->
        val levelColor = when (level) {
            VolumeLevel.MUTE    -> DangerRed
            VolumeLevel.VIBRATE -> AccentBlue
            VolumeLevel.SOUND   -> SuccessGreen
        }
        val levelDesc = when (level) {
            VolumeLevel.MUTE    -> "무음"
            VolumeLevel.VIBRATE -> "진동"
            VolumeLevel.SOUND   -> "소리"
        }
        AlertDialog(
            onDismissRequest = { pendingLevel = null },
            title = { Text("${level.icon} $levelDesc 모드로 전환", fontWeight = FontWeight.Bold) },
            text = { Text("상대방 기기를 $levelDesc 상태로 전환할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    onVolumeSelect(level)
                    pendingLevel = null
                }) { Text("전환", color = levelColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLevel = null }) { Text("취소") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            if (!canWriteSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue.copy(alpha = 0.1f))
                        .clickable(onClick = onGrantPermission)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙️", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("시스템 설정 변경 권한 필요", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                        Text("탭하여 허용 → SilentLink 켜기", fontSize = 11.sp, color = AccentBlue.copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("소리 제어", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)

            if (!isAccessAllowed) {
                Text("상대방이 접근을 차단했습니다", fontSize = 12.sp, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
            }

            if (partnerActivity != UserActivity.NONE) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DangerRed.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "상대방이 ${partnerActivity.label}이에요. 소리를 켜도 될까요?",
                        fontSize = 12.sp, color = DangerRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VolumeLevel.entries.forEach { level ->
                    val isSelected = volumeLevel == level
                    val levelColor = when (level) {
                        VolumeLevel.MUTE    -> DangerRed
                        VolumeLevel.VIBRATE -> AccentBlue
                        VolumeLevel.SOUND   -> SuccessGreen
                    }
                    OutlinedButton(
                        onClick = {
                            if (!isAccessAllowed) return@OutlinedButton
                            pendingLevel = level
                        },
                        enabled = isAccessAllowed,
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) levelColor.copy(alpha = 0.12f) else Color.Transparent,
                            contentColor = if (isSelected) levelColor else colors.onSurface.copy(alpha = 0.65f),
                            disabledContentColor = colors.onSurface.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) levelColor else colors.outline.copy(alpha = 0.35f)
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(level.icon, fontSize = 20.sp)
                            Text(level.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
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
                Icons.Default.Schedule,
                contentDescription = null,
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
                    if (isAllowed) "상대방이 내 기기를 제어할 수 있습니다"
                    else "모든 원격 제어가 차단됩니다",
                    fontSize = 12.sp,
                    color = if (isAllowed) AccentBlue.copy(alpha = 0.7f) else DangerRed.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = isAllowed,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = DangerRed.copy(alpha = 0.5f)
                )
            )
        }
    }
}
