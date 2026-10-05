package com.example.videogenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.videogenerator.ui.CreateScreen
import com.example.videogenerator.ui.GeneratorViewModel
import com.example.videogenerator.ui.HistoryScreen
import com.example.videogenerator.ui.InstructionsScreen
import com.example.videogenerator.ui.SettingsScreen
import com.example.videogenerator.ui.theme.VideoGeneratorTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    CREATE("Create", Icons.Default.Create),
    INSTRUCTIONS("Instructions", Icons.AutoMirrored.Filled.List),
    HISTORY("History", Icons.Default.PlayArrow),
    SETTINGS("Settings", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: GeneratorViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoGeneratorTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.CREATE) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text(if (tab == Tab.CREATE) "Video Generator" else tab.label) }) },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach {
                                NavigationBarItem(
                                    selected = tab == it,
                                    onClick = { tab = it },
                                    icon = { Icon(it.icon, null) },
                                    label = { Text(it.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding).imePadding()
                    when (tab) {
                        Tab.CREATE -> CreateScreen(vm, modifier)
                        Tab.INSTRUCTIONS -> InstructionsScreen(vm, modifier)
                        Tab.HISTORY -> HistoryScreen(vm, modifier)
                        Tab.SETTINGS -> SettingsScreen(vm, modifier)
                    }
                }
            }
        }
    }
}
