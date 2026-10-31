package com.cottonchou.qiju.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cottonchou.qiju.data.HomeViewModel
import com.cottonchou.qiju.ui.editor.EditorScreen

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QijuTheme {
                App(vm)
            }
        }
    }
}

private sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Devices : Screen("devices")
    data object Calendar : Screen("calendar")
    data object Profile : Screen("profile")
    data object Editor : Screen("editor")
}

@Composable
fun App(vm: HomeViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showEditor by remember { mutableStateOf(false) }

    if (showEditor) {
        EditorScreen(vm = vm, onBack = { showEditor = false })
        return
    }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = { BottomBar(screen) { screen = it } }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(padding)
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Devices -> DevicesScreen(vm)
                Screen.Calendar -> CalendarScreen(vm)
                Screen.Profile -> ProfileScreen(vm, onOpenEditor = { showEditor = true })
                else -> {}
            }
        }
    }
    DeviceDetailSheet(vm)
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Home, Icons.Rounded.Home, "全屋"),
        Triple(Screen.Devices, Icons.Rounded.GridView, "设备"),
        Triple(Screen.Calendar, Icons.Rounded.CalendarMonth, "日历"),
        Triple(Screen.Profile, Icons.Rounded.Person, "我的")
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(DarkCard)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { (s, icon, label) ->
                val active = s == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (active) Accent else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(s) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        icon, contentDescription = label,
                        tint = if (active) Color.White else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
