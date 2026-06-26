package com.terminal.rootnode

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.terminal.rootnode.ui.theme.HackerBlue
import com.terminal.rootnode.ui.theme.MatrixGreen
import com.terminal.rootnode.ui.theme.RootNodeTheme
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

    val wallpaperBitmap = remember { mutableStateOf<ImageBitmap?>(null) }

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

        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.peekDrawable() ?: wallpaperManager.drawable
            if (drawable is BitmapDrawable) {
                wallpaperBitmap.value = drawable.bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto-scroll to bottom whenever history changes
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusRequester.requestFocus() })
            }
            .background(Color(0xFF080C08))
    ) {
        // Background Wallpaper
        wallpaperBitmap.value?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080C08).copy(alpha = 0.82f))
        )

        // Scanline overlay (subtle CRT effect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val lineHeight = 4f
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = lineHeight / 2
                        )
                        y += lineHeight
                    }
                }
        )

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
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF001A00),
                                    Color(0xFF001A00).copy(alpha = 0.7f),
                                    Color(0xFF001A00)
                                )
                            )
                        )
                        .border(
                            width = 0.5.dp,
                            color = MatrixGreen.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(0.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: App name with colored dot
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MatrixGreen, RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FenrirCLI",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixGreen,
                                    letterSpacing = 2.sp
                                )
                            )
                        }
                        // Right: Time
                        Text(
                            text = currentTime,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = HackerBlue.copy(alpha = 0.8f),
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
                        // Output history - scrolls from top, auto-scrolls to bottom on new output
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = listState,
                            verticalArrangement = Arrangement.Top
                        ) {
                            items(history) { line ->
                                // Wolf art uses smaller font; normal lines use regular size
                                val isBraille = line.text.any { it.code in 0x2800..0x28FF }
                                Text(
                                    text = line.text,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = if (isBraille) 6.5.sp else 13.sp,
                                        lineHeight = if (isBraille) 7.sp else 18.sp,
                                        letterSpacing = if (isBraille) 0.sp else 0.3.sp,
                                        color = line.color ?: MatrixGreen
                                    ),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    softWrap = false
                                )
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
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF001A00))
                                            .border(
                                                width = 0.5.dp,
                                                color = MatrixGreen.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(4.dp)
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
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MatrixGreen
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ── Input Row ──────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF001200))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MatrixGreen.copy(alpha = glowAlpha * 0.6f),
                                            MatrixGreen.copy(alpha = glowAlpha),
                                            MatrixGreen.copy(alpha = glowAlpha * 0.6f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "❯ ",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MatrixGreen
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
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = MatrixGreen
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
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 13.sp,
                                                            color = MatrixGreen.copy(alpha = 0.3f)
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
                                                            .background(MatrixGreen.copy(alpha = caretAlpha))
                                                    } ?: Modifier
                                                        .size(width = 8.dp, height = 15.dp)
                                                        .background(MatrixGreen.copy(alpha = caretAlpha))
                                                    
                                                    Box(modifier = caretModifier)
                                                    innerTextField()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
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