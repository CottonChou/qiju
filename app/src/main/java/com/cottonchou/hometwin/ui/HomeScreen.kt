package com.cottonchou.hometwin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cottonchou.hometwin.data.Device
import com.cottonchou.hometwin.data.DeviceState
import com.cottonchou.hometwin.data.HomeViewModel

/** 全屋 Tab：天气头部 + 3D 户型 + 场景 + 房间筛选 + 设备卡片 */
@Composable
fun HomeScreen(vm: HomeViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Header(vm)
        Spacer(Modifier.height(10.dp))
        Home3DView(vm, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        SceneRow(vm)
        Spacer(Modifier.height(14.dp))
        RoomChips(vm)
        Spacer(Modifier.height(10.dp))
        DeviceGrid(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(vm: HomeViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (vm.targetDay) "☀️" else "🌙", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("晴 4.9°C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(2.dp))
            Text("次卧 26.6°C · 47.2%", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        CircleButton(Icons.Rounded.Home, "重置视角") { ExternalControls.resetView() }
        Spacer(Modifier.width(10.dp))
        CircleButton(
            if (vm.targetDay) Icons.Rounded.Bedtime else Icons.Rounded.WbSunny,
            "昼夜切换"
        ) { vm.toggleDayNight() }
        Spacer(Modifier.width(10.dp))
        CircleButton(Icons.Rounded.Layers, "墙体高度") { ExternalControls.toggleWalls() }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(DarkCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SceneRow(vm: HomeViewModel) {
    val icons = mapOf("home" to Icons.Rounded.Home, "logout" to Icons.Rounded.Logout, "bedtime" to Icons.Rounded.Bedtime)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        vm.plan.scenes.forEach { scene ->
            val applied = vm.lastSceneId == scene.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (applied) DarkCardHi else DarkCard)
                    .clickable { vm.applyScene(scene) }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(
                    icons[scene.icon] ?: Icons.Rounded.Home,
                    contentDescription = scene.name,
                    tint = if (applied) Warm else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(scene.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (applied) "已应用" else "待配置",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (applied) Accent else TextDim
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomChips(vm: HomeViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip("全屋", vm.selectedRoomId == null) { vm.selectedRoomId = null }
        vm.plan.rooms.forEach { r ->
            Chip(r.name, vm.selectedRoomId == r.id) { vm.selectedRoomId = r.id }
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Accent else DarkCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (active) Color.White else TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceGrid(vm: HomeViewModel) {
    val devices = vm.plan.devices.filter { vm.selectedRoomId == null || it.roomId == vm.selectedRoomId }
    if (devices.isEmpty()) {
        Text("该房间暂无设备", color = TextDim, modifier = Modifier.padding(vertical = 20.dp))
        return
    }
    val rows = devices.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    DeviceCard(vm, d, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DeviceCard(vm: HomeViewModel, d: Device, modifier: Modifier = Modifier) {
    val st = vm.state(d.id)
    val active = st.on && !st.unavailable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF3A3220) else DarkCard)
            .clickable { vm.selectedDeviceId = d.id }
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) Color(0xFF4A4028) else DarkCardHi),
            contentAlignment = Alignment.Center
        ) {
            DeviceIcon(d.type, active)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(d.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                statusText(d, st),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    st.unavailable -> TextDim
                    active -> Warm
                    else -> TextSecondary
                },
                maxLines = 1
            )
        }
    }
}

@Composable
fun DeviceIcon(type: String, active: Boolean) {
    val tint = if (active) Warm else TextSecondary
    val icon = when (type) {
        "light" -> Icons.Rounded.Lightbulb
        "ac" -> Icons.Rounded.AcUnit
        "sensor" -> Icons.Rounded.Sensors
        "camera" -> Icons.Rounded.Videocam
        else -> Icons.Rounded.Lightbulb
    }
    Icon(icon, contentDescription = type, tint = tint, modifier = Modifier.size(20.dp))
}

fun statusText(d: Device, st: DeviceState): String = when {
    st.unavailable -> "设备不可用"
    d.type == "sensor" && st.value.isNotBlank() -> st.value
    !st.on -> "已关闭"
    d.type == "light" -> "已开启 · ${st.brightness}%"
    d.type == "ac" -> "已开启 · ${st.temperature.toInt()}°C"
    else -> "已开启"
}
