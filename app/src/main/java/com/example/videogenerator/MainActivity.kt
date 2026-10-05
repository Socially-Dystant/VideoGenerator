package com.example.videogenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
                    topBar = { TopAppBar(title = { if (tab == Tab.CREATE) Logo() else Text(tab.label) }) },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach {
                                NavigationBarItem(
                                    selected = tab == it,
                                    onClick = { tab = it },
                                    icon = { Icon(it.icon, null) },
                                    // One line at a smaller size so "Instructions" fits on narrow phones.
                                    label = { Text(it.label, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding).imePadding()
                    when (tab) {
                        Tab.CREATE -> CreateScreen(vm, modifier)
                        Tab.INSTRUCTIONS -> InstructionsScreen(vm, modifier)
                        Tab.HISTORY -> HistoryScreen(vm, modifier, onOpenCreate = { tab = Tab.CREATE })
                        Tab.SETTINGS -> SettingsScreen(vm, modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_vg_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Video Generator",
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
