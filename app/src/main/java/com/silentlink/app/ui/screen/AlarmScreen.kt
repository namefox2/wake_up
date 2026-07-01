package com.silentlink.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.model.RemoteAlarm
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.SuccessGreen
import java.util.UUID

private val DAY_LABELS_ALARM = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun AlarmScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<RemoteAlarm?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (uiState.isConnected) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AccentBlue,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "알람 추가")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                BannerAdView()
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "알람 설정",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
                Text(
                    "상대방 핸드폰에 알람을 설정합니다",
                    fontSize = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )
            }

            if (!uiState.isConnected) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = colors.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "상대방과 연결 후 사용할 수 있습니다",
                                fontSize = 14.sp,
                                color = colors.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                val selectedPartner = uiState.selectedPartnerForAlarm
                val selectedUid = selectedPartner?.uid ?: ""

                // 기기 선택기 — 항상 표시 (1대면 라벨만, 여럿이면 탭 선택)
                item {
                    if (uiState.partners.size > 1) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            uiState.partners.forEachIndexed { idx, partner ->
                                val label = uiState.deviceNames[partner.uid] ?: "기기 ${idx + 1}"
                                SegmentedButton(
                                    selected = partner.uid == selectedUid,
                                    onClick = { viewModel.selectAlarmPartner(partner.uid) },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = uiState.partners.size)
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    } else {
                        val partnerName = uiState.deviceNames[uiState.partners.first().uid] ?: "연결된 기기"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("적용 기기", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(partnerName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 내가 상대방에게 설정한 알람 (선택된 기기 이름 표시)
                val targetName = uiState.deviceNames[selectedUid] ?: "상대방"
                item { SectionLabel("$targetName 에게 설정한 알람") }

                if (uiState.alarmsForSelectedPartner.isEmpty()) {
                    item { EmptyAlarmHint() }
                } else {
                    items(uiState.alarmsForSelectedPartner, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { viewModel.toggleAlarmFor(selectedUid, alarm.id, !alarm.isEnabled) },
                            onDelete = { viewModel.deleteAlarmFor(selectedUid, alarm.id) },
                            onClick = { editingAlarm = alarm }
                        )
                    }
                }

                // 상대방이 나에게 설정한 알람
                if (uiState.alarmsFromPartners.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionLabel("상대방이 나에게 설정한 알람")
                    }
                    items(uiState.alarmsFromPartners, key = { "p_${it.id}" }) { alarm ->
                        AlarmCard(alarm = alarm, readOnly = true, onToggle = {}, onDelete = {}, onClick = {})
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        }
    }

    if (showAddDialog) {
        AlarmEditDialog(
            alarm = null,
            onDismiss = { showAddDialog = false },
            onSave = { alarm ->
                val uid = uiState.selectedPartnerForAlarm?.uid ?: return@AlarmEditDialog
                viewModel.addAlarmFor(uid, alarm)
                showAddDialog = false
            }
        )
    }

    editingAlarm?.let { alarm ->
        AlarmEditDialog(
            alarm = alarm,
            onDismiss = { editingAlarm = null },
            onSave = { updated ->
                val uid = uiState.selectedPartnerForAlarm?.uid ?: return@AlarmEditDialog
                viewModel.updateAlarmFor(uid, updated)
                editingAlarm = null
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyAlarmHint() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+ 버튼으로 알람을 추가하세요",
            fontSize = 13.sp,
            color = colors.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: RemoteAlarm,
    readOnly: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        onClick = if (!readOnly) onClick else ({})
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (alarm.label.isNotEmpty()) {
                    Text(
                        alarm.label,
                        fontSize = 13.sp,
                        color = colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    alarm.displayTime(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (alarm.isEnabled) colors.onBackground else colors.onSurface.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (alarm.days.isEmpty()) {
                        DayChip("매일", active = alarm.isEnabled)
                    } else {
                        DAY_LABELS_ALARM.forEachIndexed { idx, label ->
                            DayChip(label, active = (idx + 1) in alarm.days && alarm.isEnabled)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!readOnly) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = colors.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Text(
                        "상대방 설정",
                        fontSize = 10.sp,
                        color = SuccessGreen.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(label: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentBlue else colors.outline.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) Color.White else colors.onSurface.copy(alpha = 0.35f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── 알람 추가/수정 다이얼로그 ─────────────────────────────

@Composable
fun AlarmEditDialog(
    alarm: RemoteAlarm?,
    onDismiss: () -> Unit,
    onSave: (RemoteAlarm) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var label by remember { mutableStateOf(alarm?.label ?: "") }
    var isAm by remember { mutableStateOf(alarm == null || alarm.hour < 12) }
    var hour12 by remember {
        mutableIntStateOf(
            alarm?.let {
                when {
                    it.hour == 0 -> 12
                    it.hour > 12 -> it.hour - 12
                    else -> it.hour
                }
            } ?: 7
        )
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var selectedDays by remember { mutableStateOf(alarm?.days ?: setOf(1, 2, 3, 4, 5)) }
    var everyDay by remember { mutableStateOf(alarm?.days?.isEmpty() ?: false) }
    var alarmSound by remember { mutableStateOf(alarm?.alarmSound ?: true) }
    var alarmVibrate by remember { mutableStateOf(alarm?.alarmVibrate ?: true) }

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
                Text(
                    if (alarm == null) "알람 추가" else "알람 수정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 시간 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AM/PM
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AmPmButton("오전", selected = isAm) { isAm = true }
                        Spacer(modifier = Modifier.height(8.dp))
                        AmPmButton("오후", selected = !isAm) { isAm = false }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // 시
                    TimeSpinner(value = hour12, range = 1..12, onValueChange = { hour12 = it })
                    Text(
                        " : ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onBackground
                    )
                    // 분
                    TimeSpinner(value = minute, range = 0..59, step = 1, onValueChange = { minute = it }, formatAs2Digit = true)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 라벨
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("알람 이름 (선택)") },
                    placeholder = { Text("예: 기상, 약 먹기") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue,
                        cursorColor = AccentBlue
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 요일 선택
                Text("반복", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))

                // 프리셋 버튼
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "매일" to emptySet<Int>(),
                        "평일" to setOf(1, 2, 3, 4, 5),
                        "주말" to setOf(6, 7)
                    ).forEach { (preset, days) ->
                        val isSelected = if (preset == "매일") everyDay
                        else !everyDay && selectedDays == days
                        OutlinedButton(
                            onClick = {
                                if (preset == "매일") {
                                    everyDay = true
                                } else {
                                    everyDay = false
                                    selectedDays = days
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (isSelected) AccentBlue else colors.onSurface.copy(alpha = 0.6f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AccentBlue else colors.outline.copy(alpha = 0.4f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(preset, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 개별 요일
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS_ALARM.forEachIndexed { idx, dayLabel ->
                        val dayNum = idx + 1
                        val active = !everyDay && dayNum in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                everyDay = false
                                selectedDays = if (active) selectedDays - dayNum else selectedDays + dayNum
                            },
                            label = { Text(dayLabel, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 소리 / 진동
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant)
                        .clickable { alarmSound = !alarmSound }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("알람 소리", fontSize = 14.sp, color = colors.onBackground, fontWeight = FontWeight.Medium)
                    }
                    Switch(
                        checked = alarmSound,
                        onCheckedChange = { alarmSound = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant)
                        .clickable { alarmVibrate = !alarmVibrate }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📳", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("알람 진동", fontSize = 14.sp, color = colors.onBackground, fontWeight = FontWeight.Medium)
                    }
                    Switch(
                        checked = alarmVibrate,
                        onCheckedChange = { alarmVibrate = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = colors.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val hour24 = when {
                                isAm && hour12 == 12 -> 0
                                !isAm && hour12 != 12 -> hour12 + 12
                                else -> hour12
                            }
                            onSave(
                                RemoteAlarm(
                                    id = alarm?.id ?: UUID.randomUUID().toString(),
                                    label = label,
                                    hour = hour24,
                                    minute = minute,
                                    days = if (everyDay) emptySet() else selectedDays,
                                    isEnabled = alarm?.isEnabled ?: true,
                                    createdAt = alarm?.createdAt ?: System.currentTimeMillis(),
                                    alarmSound = alarmSound,
                                    alarmVibrate = alarmVibrate
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun AmPmButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentBlue else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else colors.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TimeSpinner(
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    formatAs2Digit: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    var isEditing by remember { mutableStateOf(false) }
    // textInput is independent of value — not reset when value changes externally
    var textInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            isEditing = false
            val next = value + step
            onValueChange(if (next > range.last) range.first else next)
        }) {
            Text("▲", fontSize = 14.sp, color = AccentBlue)
        }

        if (isEditing) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = textInput,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(2)
                    textInput = filtered
                    // Apply immediately when the typed value is valid so Save always sees latest value
                    val num = filtered.toIntOrNull()
                    if (num != null && num in range) onValueChange(num)
                },
                textStyle = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentBlue,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { isEditing = false }),
                singleLine = true,
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier
                    .width(72.dp)
                    .focusRequester(focusRequester)
            )
        } else {
            Text(
                text = if (formatAs2Digit) "%02d".format(value) else value.toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = colors.onBackground,
                modifier = Modifier
                    .width(72.dp)
                    .clickable {
                        textInput = ""
                        isEditing = true
                    },
                textAlign = TextAlign.Center
            )
        }

        IconButton(onClick = {
            isEditing = false
            val prev = value - step
            onValueChange(if (prev < range.first) range.last else prev)
        }) {
            Text("▼", fontSize = 14.sp, color = AccentBlue)
        }
    }
}
