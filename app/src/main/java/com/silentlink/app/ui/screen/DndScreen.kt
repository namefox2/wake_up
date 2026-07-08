package com.silentlink.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.model.DndConfig
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed

private val DAY_LABELS_DND = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun DndScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val config = uiState.dndConfig

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 광고 배너
        Box(modifier = Modifier.fillMaxWidth().background(colors.surface), contentAlignment = Alignment.Center) {
            BannerAdView(adConsentAccepted = uiState.adConsentAccepted)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {

            Text("방해금지", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text(
                "활성화된 시간에는 상대방이 강제로 소리를 켤 수 없습니다",
                fontSize = 13.sp,
                color = colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // ── 메인 토글 카드 ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (config.isEnabled) DangerRed.copy(alpha = 0.1f) else colors.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    // 토글 행
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "방해금지 활성화",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (config.isEnabled) DangerRed else colors.onBackground
                            )
                            Text(
                                if (config.isEnabled) "강제 무음 해제 차단 중" else "비활성 상태",
                                fontSize = 12.sp,
                                color = if (config.isEnabled) DangerRed.copy(alpha = 0.7f) else colors.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = config.isEnabled,
                            onCheckedChange = { viewModel.updateDndConfig(config.copy(isEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DangerRed
                            )
                        )
                    }

                    // 시간/요일 설정 (토글 on 일 때만 표시)
                    AnimatedVisibility(
                        visible = config.isEnabled,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = DangerRed.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(20.dp))

                            // 시작/종료 시간
                            Text("시간 설정", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.55f), modifier = Modifier.padding(bottom = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TimeButton(
                                    label = "시작",
                                    hour = config.startHour,
                                    minute = config.startMinute,
                                    onClick = { showStartPicker = true },
                                    modifier = Modifier.weight(1f)
                                )
                                TimeButton(
                                    label = "종료",
                                    hour = config.endHour,
                                    minute = config.endMinute,
                                    onClick = { showEndPicker = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 요일 선택
                            Text("반복 요일", fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.55f), modifier = Modifier.padding(bottom = 10.dp))

                            // 프리셋 버튼
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("매일" to setOf(1,2,3,4,5,6,7), "평일" to setOf(1,2,3,4,5), "주말" to setOf(6,7)).forEach { (label, days) ->
                                    val selected = config.days == days
                                    OutlinedButton(
                                        onClick = { viewModel.updateDndConfig(config.copy(days = days)) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) DangerRed.copy(alpha = 0.12f) else Color.Transparent,
                                            contentColor = if (selected) DangerRed else colors.onSurface.copy(alpha = 0.6f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, if (selected) DangerRed else colors.outline.copy(alpha = 0.4f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 개별 요일 칩
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DAY_LABELS_DND.forEachIndexed { idx, label ->
                                    val dayNum = idx + 1
                                    val active = dayNum in config.days
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            val newDays = if (active) config.days - dayNum else config.days + dayNum
                                            viewModel.updateDndConfig(config.copy(days = newDays))
                                        },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DangerRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DangerRed.copy(alpha = 0.08f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⛔", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "설정된 시간에는 상대방이 소리를 켤 수 없습니다",
                                    fontSize = 12.sp,
                                    color = DangerRed.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 시작 시간 다이얼로그
    if (showStartPicker) {
        DndTimePickerDialog(
            title = "시작 시간",
            hour = config.startHour,
            minute = config.startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { h, m ->
                viewModel.updateDndConfig(config.copy(startHour = h, startMinute = m))
                showStartPicker = false
            }
        )
    }

    // 종료 시간 다이얼로그
    if (showEndPicker) {
        DndTimePickerDialog(
            title = "종료 시간",
            hour = config.endHour,
            minute = config.endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { h, m ->
                viewModel.updateDndConfig(config.copy(endHour = h, endMinute = m))
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun TimeButton(label: String, hour: Int, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val amPm = if (hour < 12) "오전" else "오후"
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(6.dp))
            Text(amPm, fontSize = 12.sp, color = DangerRed.copy(alpha = 0.75f))
            Text(
                "%d:%02d".format(h12, minute),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = DangerRed
            )
        }
    }
}

@Composable
private fun DndTimePickerDialog(
    title: String,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var isAm   by remember { mutableStateOf(hour < 12) }
    var hour12 by remember { mutableIntStateOf(when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }) }
    var min    by remember { mutableIntStateOf(minute) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AmPmBtn("오전", selected = isAm)  { isAm = true }
                        Spacer(modifier = Modifier.height(8.dp))
                        AmPmBtn("오후", selected = !isAm) { isAm = false }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    DndTimeSpinner(value = hour12, range = 1..12, onValueChange = { hour12 = it })
                    Text(" : ", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = colors.onBackground)
                    DndTimeSpinner(value = min, range = 0..59, step = 5, onValueChange = { min = it }, padded = true)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소", color = colors.onSurface.copy(alpha = 0.5f)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val h24 = when { isAm && hour12 == 12 -> 0; !isAm && hour12 != 12 -> hour12 + 12; else -> hour12 }
                            onConfirm(h24, min)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("확인") }
                }
            }
        }
    }
}

@Composable
private fun AmPmBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = androidx.compose.ui.Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DangerRed else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else colors.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun DndTimeSpinner(value: Int, range: IntRange, step: Int = 1, onValueChange: (Int) -> Unit, padded: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { val n = value + step; onValueChange(if (n > range.last) range.first else n) }) {
            Text("▲", fontSize = 14.sp, color = DangerRed)
        }
        Text(
            if (padded) "%02d".format(value) else value.toString(),
            fontSize = 36.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = colors.onBackground
        )
        IconButton(onClick = { val p = value - step; onValueChange(if (p < range.first) range.last else p) }) {
            Text("▼", fontSize = 14.sp, color = DangerRed)
        }
    }
}
