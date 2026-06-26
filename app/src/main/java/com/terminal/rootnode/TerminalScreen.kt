package com.terminal.rootnode

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.terminal.rootnode.ui.theme.InputBarDark
import com.terminal.rootnode.ui.theme.LocalBlurEnabled
import com.terminal.rootnode.ui.theme.NordFrost1
import com.terminal.rootnode.ui.theme.NordGreen
import com.terminal.rootnode.ui.theme.NordNight0
import com.terminal.rootnode.ui.theme.NordNight3
import com.terminal.rootnode.ui.theme.NordSnow0
import com.terminal.rootnode.ui.theme.NordYellow
import com.terminal.rootnode.ui.theme.RootNodeTheme
import com.terminal.rootnode.ui.theme.TerminalFont
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val history by viewModel.history.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val inlineSuggestion by viewModel.inlineSuggestion.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val cursorRect = textLayoutResult?.let { layoutResult ->
        val cursorIndex = input.selection.start
        if (cursorIndex <= layoutResult.layoutInput.text.length) {
            try {
                layoutResult.getCursorRect(cursorIndex)
            } catch (e: Exception) {
                null
            }
        } else null
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    // Live clock
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(30_000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
        viewModel.initDatabase(context)
        focusRequester.requestFocus()
    }

    // Auto-scroll to bottom whenever history changes — instant to avoid jank during typing
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.size - 1)
        }
    }

    // Caret Animation
    val infiniteTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    // Glow pulse for input border
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val isBlurEnabled = LocalBlurEnabled.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusRequester.requestFocus() })
            }
            .background(if (isBlurEnabled) Color.Transparent else NordNight0)
    ) {

        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NordNight0.copy(alpha = 0.5f))
        )

        // Scanline overlay removed — was causing per-frame redraws on top of blurred wallpaper

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // ── Top Status Bar ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF000000).copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: >_ FenrirCLI
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ">_ ",
                                style = TextStyle(
                                    fontFamily = TerminalFont,
                                    fontSize = 13.sp,
                                    color = NordGreen.copy(alpha = 0.6f),
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "FenrirCLI",
                                style = TextStyle(
                                    fontFamily = TerminalFont,
                                    fontSize = 13.sp,
                                    color = NordGreen,
                                    letterSpacing = 2.sp
                                )
                            )
                        }
                        // Right: Time
                        Text(
                            text = currentTime,
                            style = TextStyle(
                                fontFamily = TerminalFont,
                                fontSize = 12.sp,
                                color = NordFrost1.copy(alpha = 0.85f),
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }

                // ── Terminal Content ───────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusRequester.requestFocus() })
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .then(
                                if (isExpanded) Modifier.widthIn(max = 800.dp)
                                else Modifier.fillMaxWidth()
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Output history
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = listState,
                            verticalArrangement = Arrangement.Top
                        ) {
                            items(history) { line ->
                                if (line.type == "neofetch") {
                                    NeofetchBanner(
                                        bannerText = line.text,
                                        wolfLines = viewModel.wolfLines
                                    )
                                } else {
                                    val isBraille = line.text.any { it.code in 0x2800..0x28FF }
                                    Text(
                                        text = line.text,
                                        style = TextStyle(
                                            fontFamily = TerminalFont,
                                            fontSize = if (isBraille) 6.5.sp else 13.sp,
                                            lineHeight = if (isBraille) 7.sp else 19.sp,
                                            letterSpacing = if (isBraille) 0.sp else 0.2.sp,
                                            color = line.color ?: NordGreen
                                        ),
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // ── Suggestions Row ────────────────────
                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(suggestions) { suggestion ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NordNight3.copy(alpha = 0.7f))
                                            .border(
                                                width = 0.5.dp,
                                                color = NordGreen.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                val parts = input.text.split(" ")
                                                val newInput = if (parts.size > 1) {
                                                    parts.dropLast(1).joinToString(" ") + " " + suggestion
                                                } else {
                                                    suggestion
                                                }
                                                input = TextFieldValue(newInput, selection = TextRange(newInput.length))
                                                viewModel.onInputChange(newInput)
                                                focusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = TextStyle(
                                                fontFamily = TerminalFont,
                                                fontSize = 11.sp,
                                                color = NordGreen
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ── Input Row (Pill) ───────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(InputBarDark.copy(alpha = 0.88f))
                                .border(
                                    width = 0.5.dp,
                                    color = NordNight3.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Prompt prefix
                                Text(
                                    text = "> root@fenrir:~$ ",
                                    style = TextStyle(
                                        fontFamily = TerminalFont,
                                        fontSize = 12.sp,
                                        color = NordGreen.copy(alpha = 0.8f)
                                    )
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = input,
                                        onValueChange = {
                                            input = it
                                            viewModel.onInputChange(it.text)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        textStyle = TextStyle(
                                            fontFamily = TerminalFont,
                                            fontSize = 13.sp,
                                            color = NordGreen
                                        ),
                                        cursorBrush = SolidColor(Color.Transparent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (input.text.isNotBlank()) {
                                                    viewModel.processCommand(context, input.text)
                                                    input = TextFieldValue("")
                                                    coroutineScope.launch {
                                                        if (history.isNotEmpty()) {
                                                            listState.animateScrollToItem(history.size - 1)
                                                        }
                                                    }
                                                }
                                            }
                                        ),
                                        onTextLayout = { textLayoutResult = it },
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (inlineSuggestion.isNotEmpty() && input.text.isNotEmpty()) {
                                                    Text(
                                                        text = input.text + inlineSuggestion,
                                                        style = TextStyle(
                                                            fontFamily = TerminalFont,
                                                            fontSize = 13.sp,
                                                            color = NordGreen.copy(alpha = 0.3f)
                                                        )
                                                    )
                                                }

                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    // Custom block caret drawn behind the text so characters remain readable
                                                    val density = LocalDensity.current
                                                    val caretModifier = cursorRect?.let { rect ->
                                                        val leftDp = with(density) { rect.left.toDp() }
                                                        val topDp = with(density) { rect.top.toDp() }
                                                        val heightDp = with(density) { rect.height.toDp() }
                                                        Modifier
                                                            .offset(x = leftDp, y = topDp)
                                                            .size(width = 8.dp, height = heightDp)
                                                            .background(NordGreen.copy(alpha = caretAlpha))
                                                    } ?: Modifier
                                                        .size(width = 8.dp, height = 15.dp)
                                                        .background(NordGreen.copy(alpha = caretAlpha))

                                                    Box(modifier = caretModifier)
                                                    innerTextField()
                                                }
                                            }
                                        }
                                    )
                                }
                                // ✦ Sparkle button
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✦",
                                    style = TextStyle(
                                        fontFamily = TerminalFont,
                                        fontSize = 18.sp,
                                        color = NordFrost1.copy(alpha = glowAlpha)
                                    ),
                                    modifier = Modifier.clickable {
                                        input = TextFieldValue("")
                                        viewModel.onInputChange("")
                                        focusRequester.requestFocus()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NeofetchBanner(bannerText: String, wolfLines: List<String>) {
    // Parse the encoded info: "NEOFETCH_BANNER|osVersion|apiLevel|model"
    val parts = bannerText.split("|")
    val osVersion = parts.getOrNull(1) ?: "?"
    val apiLevel = parts.getOrNull(2) ?: "?"
    val model = parts.getOrNull(3) ?: "?"

    val infoLines = listOf(
        Triple("OS", "Android $osVersion (API $apiLevel)", NordYellow),
        Triple("DEVICE", model, NordYellow),
        Triple("SHELL", "FenrirCLI v1.0", NordFrost1),
        Triple("KERNEL", "Linux (Android)", NordFrost1),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Wolf art on the left
        Column(modifier = Modifier.weight(0.55f)) {
            wolfLines.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = TerminalFont,
                        fontSize = 6.5.sp,
                        lineHeight = 7.sp,
                        letterSpacing = 0.sp,
                        color = NordFrost1
                    ),
                    softWrap = false
                )
            }
        }

        // Info panel on the right
        Column(
            modifier = Modifier
                .weight(0.45f)
                .padding(top = 24.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "root@fenrir",
                style = TextStyle(
                    fontFamily = TerminalFont,
                    fontSize = 13.sp,
                    color = NordGreen,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = "─".repeat(14),
                style = TextStyle(
                    fontFamily = TerminalFont,
                    fontSize = 11.sp,
                    color = NordNight3
                )
            )
            infoLines.forEach { (key, value, color) ->
                Row {
                    Text(
                        text = "$key: ",
                        style = TextStyle(
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            color = NordFrost1
                        )
                    )
                    Text(
                        text = value,
                        style = TextStyle(
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            color = color
                        ),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,navigation=buttons")

@Composable
fun TerminalScreenPreview() {
    RootNodeTheme(darkTheme = true) {
        TerminalScreen()
    }
}