package com.qiju.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qiju.data.HomeViewModel

/** 设备 Tab：按房间分组的设备列表 */
@Composable
fun DevicesScreen(vm: HomeViewModel) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        }
        vm.plan.rooms.forEach { room ->
            val devices = vm.plan.devices.filter { it.roomId == room.id }
            if (devices.isEmpty()) return@forEach
            item {
                Text(
                    "${room.name} · ${devices.size} 个设备",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            devices.chunked(2).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { d -> DeviceCard(vm, d, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
