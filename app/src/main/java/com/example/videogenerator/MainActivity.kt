package com.example.videogenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videogenerator.ui.CharactersScreen
import com.example.videogenerator.ui.CreateScreen
import com.example.videogenerator.ui.GeneratorViewModel
import com.example.videogenerator.ui.HistoryScreen
import com.example.videogenerator.ui.HomeParchment
import com.example.videogenerator.ui.HomeScreen
import com.example.videogenerator.ui.ImageScreen
import com.example.videogenerator.ui.ProcessingBar
import com.example.videogenerator.ui.InstructionsScreen
import com.example.videogenerator.ui.SettingsScreen
import com.example.videogenerator.ui.theme.VideoGeneratorTheme
import kotlinx.coroutines.launch

private enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    IMAGE("Create image", Icons.Default.Star),
    VIDEO("Create video", Icons.Default.PlayArrow),
    CHARACTERS("Characters", Icons.Default.Face),
    HISTORY("History", Icons.Default.DateRange),
    INSTRUCTIONS("Instructions", Icons.AutoMirrored.Filled.List),
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
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val onHome = screen == Screen.HOME
                val jobs by vm.jobs.collectAsState()

                // A submitted video or image sends you Home; the bar below tracks it.
                LaunchedEffect(Unit) { vm.submitted.collect { screen = Screen.HOME } }

                // Back returns to Home before leaving the app.
                BackHandler(enabled = drawer.isOpen || !onHome) {
                    if (drawer.isOpen) scope.launch { drawer.close() } else screen = Screen.HOME
                }

                ModalNavigationDrawer(
                    drawerState = drawer,
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Logo() }
                            Screen.entries.forEach { item ->
                                NavigationDrawerItem(
                                    label = { Text(item.label) },
                                    icon = { Icon(item.icon, null) },
                                    selected = screen == item,
                                    onClick = {
                                        screen = item
                                        scope.launch { drawer.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = if (onHome) HomeParchment else MaterialTheme.colorScheme.background,
                        bottomBar = {
                            if (screen != Screen.HISTORY) ProcessingBar(jobs) { screen = Screen.HISTORY }
                        },
                        topBar = {
                            TopAppBar(
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") }
                                },
                                title = { if (!onHome) Text(screen.label) },
                                colors = if (onHome) {
                                    TopAppBarDefaults.topAppBarColors(
                                        containerColor = HomeParchment,
                                        navigationIconContentColor = Color(0xFF26231D),
                                    )
                                } else TopAppBarDefaults.topAppBarColors(),
                            )
                        },
                    ) { padding ->
                        val modifier = Modifier.padding(padding).imePadding()
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onCreateImage = { screen = Screen.IMAGE },
                                onCreateVideo = { screen = Screen.VIDEO },
                                onCreateCharacter = { screen = Screen.CHARACTERS },
                                modifier = modifier,
                            )
                            Screen.IMAGE -> ImageScreen(vm, modifier)
                            Screen.VIDEO -> CreateScreen(vm, modifier)
                            Screen.CHARACTERS -> CharactersScreen(vm, modifier)
                            Screen.HISTORY -> HistoryScreen(vm, modifier, onOpenCreate = { screen = Screen.VIDEO })
                            Screen.INSTRUCTIONS -> InstructionsScreen(vm, modifier)
                            Screen.SETTINGS -> SettingsScreen(vm, modifier)
                        }
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
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
