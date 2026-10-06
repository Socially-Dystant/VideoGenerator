package com.example.videogenerator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videogenerator.R

/** Colours sampled from the artwork, so Home looks the same in light and dark mode. */
val HomeParchment = Color(0xFFEEECDD)
private val HomeInk = Color(0xFF26231D)
private val HomeInkSoft = Color(0xFF6B6558)

/** Start page: the skull artwork and the three things you can make. */
@Composable
fun HomeScreen(
    onCreateImage: () -> Unit,
    onCreateVideo: () -> Unit,
    onCreateCharacter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(HomeParchment),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Image(
                    painterResource(R.drawable.home_art),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Fade the artwork's bottom edge into the page.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(0.75f to Color.Transparent, 1f to HomeParchment)),
                )
            }
            Text(
                "What do you want to create?",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                color = HomeInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Wan 3.0 videos · Wan 2.7 images · consistent characters",
                fontSize = 13.sp,
                color = HomeInkSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HomeChoice(Icons.Default.Star, "Create an image", "Prompt, references or characters", onCreateImage)
                HomeChoice(Icons.Default.PlayArrow, "Create a video", "Scenes, shots and a start frame", onCreateVideo)
                HomeChoice(Icons.Default.Face, "Create a character", "Lock a look and outfit for reuse", onCreateCharacter)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HomeChoice(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, HomeInk),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = HomeParchment.copy(alpha = 0.92f), contentColor = HomeInk),
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, fontSize = 12.sp, color = HomeInkSoft, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
