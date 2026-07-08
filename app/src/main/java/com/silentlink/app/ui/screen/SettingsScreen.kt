package com.silentlink.app.ui.screen

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.silentlink.app.service.ServiceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.silentlink.app.billing.SUPPORT_TIERS
import com.silentlink.app.manager.AudioControlManager
import com.silentlink.app.model.AppTheme
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.SuccessGreen
import com.silentlink.app.ui.theme.toSilentLinkColors

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val billingManager = viewModel.billingManager
    val clipboard = LocalClipboardManager.current
    val audioControlManager = remember { AudioControlManager(context) }
    val pm = remember { context.getSystemService(PowerManager::class.java) }
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }

    fun checkPerms() = Triple(
        audioControlManager.canWriteSettings(),
        audioControlManager.canSetMute(),
        pm.isIgnoringBatteryOptimizations(context.packageName)
    )
    var perms by remember { mutableStateOf(checkPerms()) }
    val canWriteSettings = perms.first
    val canSetMute = perms.second
    val batteryIgnored = perms.third
    val canExactAlarm = remember(Build.VERSION.SDK_INT) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms = checkPerms()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }



    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showCouponDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "설정",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 연결 정보
        if (uiState.isConnected) {
            SettingSection(title = "연결 정보") {
                SettingItem(
                    icon = Icons.Default.QrCode,
                    title = "내 초대 코드",
                    subtitle = uiState.myCode.chunked(3).joinToString(" - "),
                    iconTint = AccentBlue,
                    onClick = { clipboard.setText(AnnotatedString(uiState.myCode)) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 테마 선택
        SettingSection(title = "앱 테마") {
            SettingItem(
                icon = Icons.Default.Palette,
                title = "테마 선택",
                subtitle = uiState.theme.displayName,
                iconTint = AccentBlue,
                onClick = { showThemeDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 디바이스 슬롯
        SettingSection(title = "디바이스 슬롯") {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("연결 가능 기기", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        Text(
                            "현재 ${uiState.partners.size}대 연결 / 최대 ${uiState.maxDevices}대 허용",
                            fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        "${uiState.partners.size}/${uiState.maxDevices}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (uiState.partners.size >= uiState.maxDevices) DangerRed else AccentBlue
                    )
                }

                uiState.googleEmail?.let { email ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(email, fontSize = 11.sp, color = SuccessGreen)
                    }
                }

                if (uiState.maxDevices < 5) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { context.findActivity()?.let { viewModel.requestSlotPurchase(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("슬롯 추가 (₩1,000)", fontSize = 14.sp)
                    }
                    Text(
                        "슬롯 1개당 기기 1대 추가 (최대 5대)\n구매 시 Google 계정 연동이 필요합니다\n기기 등록 완료 후에는 환불이 어렵습니다",
                        fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 6.dp), lineHeight = 16.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("최대 슬롯에 도달했습니다 (5대)", fontSize = 12.sp, color = SuccessGreen)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showCouponDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("쿠폰 코드 입력", fontSize = 13.sp, color = AccentBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 개발자 후원
        SettingSection(title = "개발자 후원") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "앱 개발을 응원해주세요 ❤️",
                    fontSize = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPORT_TIERS.chunked(2).forEach { rowTiers ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTiers.forEach { tier ->
                                OutlinedButton(
                                    onClick = {
                                        context.findActivity()?.let {
                                            viewModel.billingManager.launchBillingFlow(it, tier.productId)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = tier.displayName, fontSize = 11.sp, color = AccentBlue.copy(alpha = 0.7f))
                                        Text(text = tier.price, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 권한 설정
        SettingSection(title = "권한 설정") {
            SettingItem(
                icon = Icons.Default.VolumeUp,
                title = "시스템 설정 변경 권한",
                subtitle = if (canWriteSettings) "허용됨" else "볼륨 제어에 필요 — 탭하여 허용",
                iconTint = if (canWriteSettings) SuccessGreen else AccentBlue,
                granted = canWriteSettings,
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.NotificationsOff,
                title = "방해금지 접근 허용",
                subtitle = if (canSetMute) "허용됨" else "무음 모드 전환에 필요 — 탭하여 허용",
                iconTint = if (canSetMute) SuccessGreen else DangerRed,
                granted = canSetMute,
                onClick = {
                    context.startActivity(Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                SettingItem(
                    icon = Icons.Default.Alarm,
                    title = "정확한 알람 권한",
                    subtitle = if (canExactAlarm) "허용됨" else "알람이 정시에 울리도록 — 탭하여 허용",
                    iconTint = if (canExactAlarm) SuccessGreen else AccentBlue,
                    granted = canExactAlarm,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.BatteryFull,
                title = "배터리 최적화 제외",
                subtitle = if (batteryIgnored) "제외됨" else "백그라운드에서 꺼지지 않도록 — 탭하여 허용",
                iconTint = if (batteryIgnored) SuccessGreen else AccentBlue,
                granted = batteryIgnored,
                onClick = {
                    if (!batteryIgnored) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 기타
        SettingSection(title = "기타") {
            SettingItem(
                icon = Icons.Default.Article,
                title = "이용약관",
                iconTint = colors.onSurface.copy(alpha = 0.6f),
                onClick = { showPrivacyDialog = true }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.PrivacyTip,
                title = "개인정보처리방침",
                iconTint = colors.onSurface.copy(alpha = 0.6f),
                onClick = { showPrivacyDialog = true }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.Info,
                title = "앱 버전",
                subtitle = "1.0.0",
                iconTint = colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (uiState.isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingSection(title = "연결") {
                SettingItem(
                    icon = Icons.Default.LinkOff,
                    title = "모두 연결 해제",
                    subtitle = "연결된 기기 ${uiState.partners.size}대 모두 해제",
                    iconTint = DangerRed,
                    titleColor = DangerRed,
                    onClick = { showDisconnectDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 디버그 로그 (임시)
        SettingSection(title = "디버그") {
            SettingItem(
                icon = Icons.Default.BugReport,
                title = "서비스 로그 보기",
                subtitle = "서비스 시작/종료/Firebase 오류 기록",
                iconTint = colors.onSurface.copy(alpha = 0.5f),
                onClick = { showDebugLog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "이용 시 주의사항",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "상대방의 상황(회의, 수업, 운전 등)을 충분히 고려하여 신중하게 사용하세요.",
                    "잘못된 사용으로 인해 발생한 피해에 대해 개발자는 어떠한 법적 책임도 지지 않습니다."
                ).forEach { notice ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.4f))
                        Text(
                            notice,
                            fontSize = 11.sp,
                            color = colors.onSurface.copy(alpha = 0.5f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("연결 해제") },
            text = { Text("상대방과의 연결을 해제하시겠습니까?\n연결 해제 후에는 상호 제어가 불가능합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnectAll()
                        showDisconnectDialog = false
                    }
                ) { Text("해제", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = colors.surfaceVariant
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = uiState.theme,
            onSelect = {
                viewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showPrivacyDialog) {
        PrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }

    if (showCouponDialog) {
        CouponDialog(
            resultMessage = uiState.couponMessage,
            isSuccess = uiState.couponSuccess,
            onRedeem = { code -> viewModel.redeemCoupon(code) },
            onDismiss = {
                viewModel.clearCouponMessage()
                showCouponDialog = false
            }
        )
    }

    if (showDebugLog) {
        DebugLogDialog(context = context, onDismiss = { showDebugLog = false })
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = AccentBlue,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    granted: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = titleColor, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = if (granted == true) SuccessGreen else colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (granted == true) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(18.dp)
            )
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "테마 선택",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                AppTheme.entries.forEach { theme ->
                    val themeColors = theme.toSilentLinkColors()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(theme) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                            listOf(themeColors.background, themeColors.accent, themeColors.surface).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = theme.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal,
                            color = if (theme == currentTheme) themeColors.accent else colors.onSurface
                        )
                        if (theme == currentTheme) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouponDialog(
    resultMessage: String?,
    isSuccess: Boolean,
    onRedeem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var code by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("쿠폰 코드 입력", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("쿠폰 코드") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedBorderColor = AccentBlue,
                        cursorColor = AccentBlue
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) onRedeem(code.trim()) })
                )
                if (resultMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = resultMessage,
                        fontSize = 13.sp,
                        color = if (isSuccess) SuccessGreen else DangerRed
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (code.isNotBlank()) onRedeem(code.trim()) },
                        enabled = code.isNotBlank() && !isSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("적용") }
                }
            }
        }
    }
}

@Composable
private fun DebugLogDialog(context: Context, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var logText by remember { mutableStateOf("로그 불러오는 중...") }

    LaunchedEffect(Unit) {
        logText = withContext(Dispatchers.IO) { ServiceLogger.readLogs(context) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                Text("서비스 로그", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "최근 400줄 · 오래된 순→최신 순",
                    fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(10.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = logText,
                        fontSize = 10.sp,
                        color = colors.onSurface.copy(alpha = 0.85f),
                        lineHeight = 15.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { ServiceLogger.clear(context) }
                                logText = "(로그 지움)"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) { Text("지우기", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(logText)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) { Text("복사", fontSize = 13.sp) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("닫기", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("이용약관 및 개인정보처리방침", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = """
【이용약관】

제1조 (목적)
본 약관은 SilentLink 앱 서비스 이용에 관한 조건과 절차를 규정합니다.

제2조 (서비스 이용 조건)
• 상대방의 명시적 동의 없이 기능이 작동하지 않습니다.
• 동의 없이 타인의 기기에 설치하는 행위는 금지됩니다.
• 스토킹·감시 목적의 사용은 정보통신망법에 의해 처벌받을 수 있습니다.

제3조 (면책 조항)
앱 기능 악용으로 발생한 모든 민·형사상 책임은 사용자 본인에게 있으며, 개발자는 법적 책임을 지지 않습니다.

【개인정보처리방침】

수집 항목: Firebase 익명 인증 UID, 기기 상태 정보
수집 목적: 기기 간 실시간 상태 동기화
보유 기간: 연결 해제 시 즉시 삭제
제3자 제공: 없음
                    """.trimIndent(),
                    fontSize = 13.sp,
                    color = colors.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("확인")
                }
            }
        }
    }
}
