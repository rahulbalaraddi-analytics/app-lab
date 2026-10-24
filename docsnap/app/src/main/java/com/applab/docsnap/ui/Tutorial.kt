package com.applab.docsnap.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ the photo tips

enum class Scene { BACKGROUND, LIGHT, ANGLE, CORNERS, FLAT }

data class Tip(val title: String, val good: String, val bad: String, val scene: Scene)

val PHOTO_TIPS = listOf(
    Tip("Dark plain background", "Black cloth or black chart paper", "Wood table, bedsheet with designs", Scene.BACKGROUND),
    Tip("Soft daylight", "Near a window, or 2 lamps from the sides", "Flash, bright spot, your shadow on it", Scene.LIGHT),
    Tip("Phone flat above", "Phone straight over the paper", "Phone tilted at an angle", Scene.ANGLE),
    Tip("All 4 corners inside", "Small gap around the paper", "A corner cut off", Scene.CORNERS),
    Tip("Paper flat, hands away", "Press folds out first", "Fingers holding it, folds showing", Scene.FLAT),
)

private val GoodGreen = Color(0xFF0F6E6E)
private val BadRed = Color(0xFFB42318)
private val PaperWhite = Color(0xFFFAFAF7)
private val TextLine = Color(0xFFC9C5BA)

/** A small drawn picture of a scene, good or bad. Drawn in code so it works offline and on any phone. */
@Composable
fun SceneArt(scene: Scene, good: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        // ground
        when {
            scene == Scene.BACKGROUND && !good -> {
                drawRect(Color(0xFFB98552))
                var x = 0f
                while (x < w) { drawLine(Color(0xFF8E6237), Offset(x, 0f), Offset(x + w * 0.15f, h), 3f); x += w / 9 }
            }
            else -> drawRect(Color(0xFF22262A))
        }
        val pw = w * 0.46f; val ph = h * 0.74f
        val left = (w - pw) / 2; val top = (h - ph) / 2
        when (scene) {
            Scene.ANGLE -> if (good) paper(left, top, pw, ph) else {
                val p = Path().apply {
                    moveTo(left + pw * 0.2f, top); lineTo(left + pw * 0.8f, top)
                    lineTo(left + pw * 1.12f, top + ph); lineTo(left - pw * 0.12f, top + ph); close()
                }
                drawPath(p, PaperWhite)
                for (i in 1..5) {
                    val y = top + ph * i / 7f
                    val inset = pw * 0.2f * (1 - i / 7f)
                    drawLine(TextLine, Offset(left + inset + 8, y), Offset(left + pw - inset - 8, y), 4f)
                }
            }
            Scene.CORNERS -> if (good) paper(left, top, pw, ph) else paper(left + pw * 0.45f, top - ph * 0.18f, pw, ph)
            Scene.LIGHT -> {
                paper(left, top, pw, ph)
                if (!good) {
                    drawCircle(Color.White.copy(alpha = 0.95f), radius = pw * 0.28f, center = Offset(left + pw * 0.62f, top + ph * 0.35f))
                    drawRect(Color.Black.copy(alpha = 0.35f), Offset(left, top + ph * 0.62f), Size(pw * 0.7f, ph * 0.38f))
                }
            }
            Scene.FLAT -> {
                paper(left, top, pw, ph)
                if (!good) {
                    drawLine(Color(0xFF9E998C), Offset(left, top + ph * 0.5f), Offset(left + pw, top + ph * 0.45f), 5f)
                    drawCircle(Color(0xFFD9A47A), radius = pw * 0.13f, center = Offset(left + pw * 0.05f, top + ph * 0.8f))
                    drawRoundRect(Color(0xFFD9A47A), Offset(left - pw * 0.35f, top + ph * 0.74f), Size(pw * 0.4f, pw * 0.2f),
                        androidx.compose.ui.geometry.CornerRadius(20f))
                }
            }
            Scene.BACKGROUND -> paper(left, top, pw, ph)
        }
        // camera frame for the corners scene
        if (scene == Scene.CORNERS) drawRect(Color.White.copy(alpha = 0.6f), Offset(w * 0.08f, h * 0.06f), Size(w * 0.84f, h * 0.88f), style = Stroke(3f))
    }
}

private fun DrawScope.paper(left: Float, top: Float, pw: Float, ph: Float) {
    drawRect(PaperWhite, Offset(left, top), Size(pw, ph))
    for (i in 1..5) {
        val y = top + ph * i / 7f
        drawLine(TextLine, Offset(left + pw * 0.12f, y), Offset(left + pw * (if (i % 2 == 0) 0.7f else 0.88f), y), 4f)
    }
}

@Composable
fun GoodBad(tip: Tip) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tip.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArtWithBadge(tip.scene, true, tip.good, Modifier.weight(1f))
            ArtWithBadge(tip.scene, false, tip.bad, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArtWithBadge(scene: Scene, good: Boolean, caption: String, modifier: Modifier) {
    val color = if (good) GoodGreen else BadRed
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            SceneArt(scene, good, Modifier.fillMaxWidth().height(150.dp).background(Color.Black, RoundedCornerShape(12.dp)))
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (good) Icons.Filled.Check else Icons.Filled.Close, contentDescription = if (good) "Good" else "Bad", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(if (good) "YES" else "NO", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(caption, fontSize = 15.sp, textAlign = TextAlign.Center, color = Palette.Ink)
    }
}

// ------------------------------------------------------------------ first-launch tutorial

private data class Slide(val title: String, val lines: List<String>, val tip: Tip? = null)

private val SLIDES = listOf(
    Slide("Welcome to DocSnap", listOf(
        "We are putting all family papers on the phone, safely.",
        "Only 3 steps for every paper:",
        "1.  Take the photo",
        "2.  Say its name",
        "3.  Next paper",
    )),
) + PHOTO_TIPS.map { Slide(it.title, emptyList(), it) } + listOf(
    Slide("One page or many?", listOf(
        "Single page — for most papers. Photo, name, next. Fast.",
        "Multi-page — for deeds and books. Take all pages, then press Done.",
        "A bad page? Push it UP in the bottom row to delete it.",
    )),
    Slide("Say the name clearly", listOf(
        "Example: \"Survey 123, Bijapur, 7/12 extract, uncle's share\"",
        "Say what it is, which place, which year, whose.",
        "English or Kannada — both are fine.",
        "You cannot skip the name. If the mic fails, type it.",
    )),
    Slide("Nothing gets lost", listOf(
        "Every paper is saved on the phone first.",
        "Long documents are saved every 5 pages.",
        "On Wi-Fi, press Upload all. You will get a message when all are uploaded.",
        "Stuck? Press the ? button on any screen.",
    )),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    Page {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pager.currentPage + 1} / ${SLIDES.size}", color = Palette.Quiet, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Skip", color = Palette.Quiet, fontSize = 16.sp) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                val s = SLIDES[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(12.dp))
                    if (s.tip != null) {
                        Text("PHOTO TIP ${i} OF ${PHOTO_TIPS.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Teal)
                        Spacer(Modifier.height(8.dp))
                        GoodBad(s.tip)
                    } else {
                        Text(s.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                        Spacer(Modifier.height(20.dp))
                        s.lines.forEach {
                            Text(it, fontSize = 20.sp, lineHeight = 28.sp, color = Palette.Ink)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center) {
                repeat(SLIDES.size) { i ->
                    Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 8.dp)
                        .background(if (i == pager.currentPage) Palette.Teal else Palette.Line, CircleShape))
                }
            }
            Box(Modifier.padding(20.dp)) {
                val last = pager.currentPage == SLIDES.lastIndex
                BigButton(if (last) "Start scanning" else "Next") {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ? button + help sheet per screen

enum class HelpTopic { HOME, FOLDER, PAGES, NAME }

@Composable
fun HelpButton(onClick: () -> Unit, tint: Color = Palette.Teal) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = tint, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topic: HelpTopic, onClose: () -> Unit, onTutorial: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Palette.Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            val (title, lines) = when (topic) {
                HelpTopic.HOME -> "How DocSnap works" to listOf(
                    "Tap a folder to scan papers into it.",
                    "Every paper is saved on this phone first.",
                    "On Wi-Fi, press Upload all to send them to the family Drive.",
                    "Problem? Press Report a problem.",
                )
                HelpTopic.FOLDER -> "Choose the type" to listOf(
                    "Single page — most papers. Photo → name → next.",
                    "Multi-page — deeds, books, files with many pages.",
                    "Check the folder name at the top before you start.",
                )
                HelpTopic.PAGES -> "Many pages" to listOf(
                    "Scan more — adds up to 5 pages each time. They are saved right away.",
                    "Bad page? Push it UP in the bottom row. Tap Undo if it was a mistake.",
                    "Red border = low quality. Delete it and scan closer.",
                    "All pages done? Press Done.",
                )
                HelpTopic.NAME -> "Say the name" to listOf(
                    "Say what it is, place, year, whose. Example: \"Sale deed 1998, farm plot, father's name\".",
                    "EN or ಕನ್ನಡ — pick at the top.",
                    "Mic not working? Tap the box and type.",
                    "The name cannot be skipped.",
                )
            }
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            lines.forEach { Text("•  $it", fontSize = 18.sp, lineHeight = 26.sp); Spacer(Modifier.height(8.dp)) }
            if (topic == HelpTopic.FOLDER || topic == HelpTopic.PAGES) {
                Spacer(Modifier.height(12.dp))
                Text("For a good photo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                PHOTO_TIPS.forEach { GoodBad(it); Spacer(Modifier.height(20.dp)) }
            }
            BigButton("Watch the full tutorial", onClick = onTutorial)
            Spacer(Modifier.height(32.dp))
        }
    }
}
