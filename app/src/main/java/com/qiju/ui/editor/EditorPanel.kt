package com.qiju.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qiju.data.Device
import com.qiju.data.Opening
import com.qiju.data.Plan
import com.qiju.data.Room
import com.qiju.ui.Accent
import com.qiju.ui.DarkCard
import com.qiju.ui.TextPrimary
import com.qiju.ui.TextSecondary
import com.qiju.ui.Warm

private val FLOOR_COLORS = listOf(
    "#8A7B66", "#7E7466", "#6F7B80", "#5F6B72", "#6E6A5E", "#7C8378", "#96856B", "#6B6B7A"
)
private val DEVICE_TYPES = listOf("light", "ac", "curtain", "sensor", "camera", "switch", "lock")
private val EDGE_NAMES = listOf("北", "东", "南", "西")

/** 选中对象的属性编辑面板 */
@Composable
fun SelectionPanel(
    plan: Plan,
    selRoomId: String?,
    selDeviceId: String?,
    selOpeningId: String?,
    onRoom: (Room) -> Unit,
    onDevice: (Device) -> Unit,
    onOpening: (Opening) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onDeleteOpening: (String) -> Unit,
    onAddDevice: (String) -> Unit,
    onAddOpening: (String) -> Unit
) {
    val room = plan.rooms.find { it.id == selRoomId }
    val device = plan.devices.find { it.id == selDeviceId }
    val opening = plan.openings.find { it.id == selOpeningId }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(DarkCard)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when {
                room != null -> RoomPanel(room, onRoom, onDeleteRoom, onAddDevice, onAddOpening)
                device != null -> DevicePanel(device, plan, onDevice, onDeleteDevice)
                opening != null -> OpeningPanel(opening, onOpening, onDeleteOpening)
                else -> Text(
                    "点选房间 / 设备 / 门窗进行编辑；拖动可移动，选中房间后拖四角改尺寸",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun RoomPanel(
    room: Room,
    onRoom: (Room) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onAddDevice: (String) -> Unit,
    onAddOpening: (String) -> Unit
) {
    var typeMenu by remember { mutableStateOf(false) }
    var deviceMenu by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = room.name,
                onValueChange = { onRoom(room.copy(name = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("房间名称") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF2A3558),
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.width(10.dp))
            Box {
                TextButton(onClick = { typeMenu = true }) {
                    Text(Room.TYPE_NAMES[room.type] ?: room.type, color = Accent)
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    Room.ROOM_TYPES.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(Room.TYPE_NAMES[t] ?: t, color = TextPrimary) },
                            onClick = { onRoom(room.copy(type = t)); typeMenu = false }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 地板颜色
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("地板", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            FLOOR_COLORS.forEach { c ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(c)))
                        .clickable { onRoom(room.copy(floorColor = c)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                TextButton(onClick = { deviceMenu = true }) { Text("＋ 设备", color = TextPrimary) }
                DropdownMenu(expanded = deviceMenu, onDismissRequest = { deviceMenu = false }) {
                    DEVICE_TYPES.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(Device.TYPE_NAMES[t] ?: t, color = TextPrimary) },
                            onClick = { onAddDevice(t); deviceMenu = false }
                        )
                    }
                }
            }
            TextButton(onClick = { onAddOpening("door") }) { Text("＋ 门", color = TextPrimary) }
            TextButton(onClick = { onAddOpening("window") }) { Text("＋ 窗", color = TextPrimary) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onDeleteRoom(room.id) }) { Text("删除房间", color = Color(0xFFE5697A)) }
        }
    }
}

@Composable
private fun DevicePanel(
    device: Device,
    plan: Plan,
    onDevice: (Device) -> Unit,
    onDeleteDevice: (String) -> Unit
) {
    var roomMenu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = device.name,
                onValueChange = { onDevice(device.copy(name = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("设备名称") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF2A3558),
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.width(10.dp))
            Box {
                TextButton(onClick = { roomMenu = true }) {
                    Text(
                        plan.rooms.find { it.id == device.roomId }?.name ?: "未分配",
                        color = Accent
                    )
                }
                DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                    plan.rooms.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r.name, color = TextPrimary) },
                            onClick = { onDevice(device.copy(roomId = r.id)); roomMenu = false }
                        )
                    }
                }
            }
            TextButton(onClick = { onDeleteDevice(device.id) }) { Text("删除", color = Color(0xFFE5697A)) }
        }
    }
}

@Composable
private fun OpeningPanel(
    opening: Opening,
    onOpening: (Opening) -> Unit,
    onDeleteOpening: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (opening.type == "door") "门" else "窗", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            listOf("door" to "门", "window" to "窗").forEach { (t, label) ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (opening.type == t) Accent else DarkCard)
                        .clickable { onOpening(opening.copy(type = t)) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.width(12.dp))
            Text("所在边", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            EDGE_NAMES.forEachIndexed { i, name ->
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (opening.edge == i) Accent else DarkCard)
                        .clickable { onOpening(opening.copy(edge = i)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) { Text(name, color = TextPrimary, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("宽度 ${opening.width}m", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = opening.width,
                onValueChange = { onOpening(opening.copy(width = (it * 10).toInt() / 10f)) },
                valueRange = 0.6f..3f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Warm, activeTrackColor = Warm)
            )
            TextButton(onClick = { onDeleteOpening(opening.id) }) { Text("删除", color = Color(0xFFE5697A)) }
        }
    }
}
