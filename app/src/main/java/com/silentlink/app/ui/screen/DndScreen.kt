package com.silentlink.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.silentlink.app.model.DndSchedule
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed

private val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun DndScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<DndSchedule?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "추가")
            }
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "방해금지 스케줄",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                text = "설정된 시간에는 상대방 제어가 차단됩니다",
                fontSize = 13.sp,
                color = colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            if (uiState.dndSchedules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "스케줄이 없습니다",
                            fontSize = 16.sp,
                            color = colors.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "+ 버튼으로 새 스케줄을 추가하세요",
                            fontSize = 13.sp,
                            color = colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.dndSchedules, key = { it.id }) { schedule ->
                        DndScheduleCard(
                            schedule = schedule,
                            onToggle = {
                                viewModel.updateDndSchedule(schedule.copy(isEnabled = !schedule.isEnabled))
                            },
                            onDelete = { viewModel.deleteDndSchedule(schedule.id) },
                            onClick = { editingSchedule = schedule }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        DndEditDialog(
            schedule = null,
            onDismiss = { showAddDialog = false },
            onSave = { schedule ->
                viewModel.addDndSchedule(schedule)
                showAddDialog = false
            }
        )
    }

    editingSchedule?.let { schedule ->
        DndEditDialog(
            schedule = schedule,
            onDismiss = { editingSchedule = null },
            onSave = { updated ->
                viewModel.updateDndSchedule(updated)
                editingSchedule = null
            }
        )
    }
}

@Composable
private fun DndScheduleCard(
    schedule: DndSchedule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.name.ifEmpty { "스케줄" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%02d:%02d ~ %02d:%02d".format(
                        schedule.startHour, schedule.startMinute,
                        schedule.endHour, schedule.endMinute
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (schedule.isEnabled) DangerRed else colors.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val dayNum = index + 1
                        val active = dayNum in schedule.days
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (active && schedule.isEnabled) DangerRed else colors.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = if (active && schedule.isEnabled) Color.White
                                else colors.onSurface.copy(alpha = 0.4f),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DangerRed
                    )
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = colors.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DndEditDialog(
    schedule: DndSchedule?,
    onDismiss: () -> Unit,
    onSave: (DndSchedule) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(schedule?.name ?: "") }
    var startHour by remember { mutableIntStateOf(schedule?.startHour ?: 22) }
    var startMinute by remember { mutableIntStateOf(schedule?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(schedule?.endHour ?: 7) }
    var endMinute by remember { mutableIntStateOf(schedule?.endMinute ?: 0) }
    var selectedDays by remember { mutableStateOf(schedule?.days ?: setOf(1, 2, 3, 4, 5)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (schedule == null) "새 스케줄 추가" else "스케줄 수정",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("시작 시간", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                TimePickerRow(hour = startHour, minute = startMinute, onHourChange = { startHour = it }, onMinuteChange = { startMinute = it })

                Spacer(modifier = Modifier.height(12.dp))

                Text("종료 시간", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                TimePickerRow(hour = endHour, minute = endMinute, onHourChange = { endHour = it }, onMinuteChange = { endMinute = it })

                Spacer(modifier = Modifier.height(16.dp))

                Text("반복 요일", fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { index, label ->
                        val dayNum = index + 1
                        val active = dayNum in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedDays = if (active) selectedDays - dayNum else selectedDays + dayNum
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DangerRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = colors.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                DndSchedule(
                                    id = schedule?.id ?: "",
                                    name = name,
                                    startHour = startHour,
                                    startMinute = startMinute,
                                    endHour = endHour,
                                    endMinute = endMinute,
                                    days = selectedDays,
                                    isEnabled = schedule?.isEnabled ?: true
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
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        NumberPicker(
            value = hour,
            range = 0..23,
            onValueChange = onHourChange,
            label = "시"
        )
        Text(
            text = " : ",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = colors.onBackground
        )
        NumberPicker(
            value = minute,
            range = 0..59,
            step = 5,
            onValueChange = onMinuteChange,
            label = "분"
        )
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    label: String
) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                val next = value + step
                if (next <= range.last) onValueChange(next) else onValueChange(range.first)
            }
        ) {
            Text("▲", fontSize = 14.sp, color = AccentBlue)
        }
        Text(
            text = "%02d".format(value),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = colors.onBackground
        )
        IconButton(
            onClick = {
                val prev = value - step
                if (prev >= range.first) onValueChange(prev) else onValueChange(range.last)
            }
        ) {
            Text("▼", fontSize = 14.sp, color = AccentBlue)
        }
        Text(text = label, fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.5f))
    }
}
