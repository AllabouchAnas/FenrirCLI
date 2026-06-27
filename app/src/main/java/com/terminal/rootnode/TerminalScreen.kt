package com.terminal.rootnode

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import android.widget.Toast
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.InterceptPlatformTextInput
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
import com.terminal.rootnode.ui.theme.NordPurple
import com.terminal.rootnode.ui.theme.NordSnow0
import com.terminal.rootnode.ui.theme.NordYellow
import com.terminal.rootnode.ui.theme.RootNodeTheme
import com.terminal.rootnode.ui.theme.TerminalFont
import com.terminal.rootnode.ui.theme.TerminalNormalTextStyle
import com.terminal.rootnode.ui.theme.TerminalBrailleTextStyle
import com.terminal.rootnode.ui.theme.TerminalInfoTextStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val history by viewModel.history.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val inlineSuggestion by viewModel.inlineSuggestion.collectAsState()
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()

    val terminalNormalStyle = remember(terminalFontSize) {
        TerminalNormalTextStyle.copy(
            fontSize = terminalFontSize.sp,
            lineHeight = (terminalFontSize * 1.43f).sp
        )
    }
    val terminalInfoStyle = remember(terminalFontSize) {
        TerminalInfoTextStyle.copy(
            fontSize = (terminalFontSize - 2).coerceAtLeast(10).sp,
            lineHeight = ((terminalFontSize - 2) * 1.43f).sp
        )
    }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showCustomKeyboard by remember { mutableStateOf(false) }
    val softKeyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

    // Startup permission launcher
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Startup permissions response
        }
    )

    // On-demand permission launcher
    val onDemandPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.onPermissionGranted(context)
            } else {
                viewModel.onPermissionDenied()
            }
        }
    )

    // Awake Screen lock management via DisposableEffect
    val awakeLocked by viewModel.awakeLocked.collectAsState()
    DisposableEffect(awakeLocked) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            if (awakeLocked) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            // Cleanup on dispose
        }
    }

    // React to permission request trigger from ViewModel
    val permissionToRequest by viewModel.permissionRequestTrigger.collectAsState()
    LaunchedEffect(permissionToRequest) {
        val perm = permissionToRequest
        if (perm != null) {
            onDemandPermissionLauncher.launch(perm)
            viewModel.clearPermissionRequestTrigger()
        }
    }

    // Startup permission trigger
    LaunchedEffect(Unit) {
        val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        
        val ungranted = permissionsToRequest.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (ungranted.isNotEmpty()) {
            startupPermissionLauncher.launch(ungranted)
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    input = TextFieldValue(spokenText, selection = TextRange(spokenText.length))
                    viewModel.onInputChange(spokenText)
                }
            }
        }
    )

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

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                viewModel.loadApps(ctx)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
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
                detectTapGestures(onTap = {
                    focusRequester.requestFocus()
                    showCustomKeyboard = true
                    softKeyboardController?.hide()
                })
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
                        // Output history — tapping dismisses the keyboard
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        showCustomKeyboard = false
                                        focusRequester.requestFocus()
                                    })
                                },
                            state = listState,
                            verticalArrangement = Arrangement.Top
                        ) {
                            items(history) { line ->
                                when (line.type) {
                                    "neofetch" -> {
                                        NeofetchBanner(
                                            bannerText = line.text,
                                            wolfLines = viewModel.trimmedWolfLines
                                        )
                                    }
                                    "sysinfo" -> {
                                        SysInfoPanel()
                                    }
                                    else -> {
                                        val isBraille = line.text.any { it.code in 0x2800..0x28FF }
                                        Text(
                                            text = line.text,
                                            style = if (isBraille) {
                                                TerminalBrailleTextStyle.copy(color = line.color ?: NordGreen)
                                            } else {
                                                terminalNormalStyle.copy(color = line.color ?: NordGreen)
                                            },
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            softWrap = false
                                        )
                                    }
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
                                            style = terminalInfoStyle.copy(
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
                                    style = terminalNormalStyle.copy(
                                        fontSize = (terminalFontSize - 2).coerceAtLeast(10).sp,
                                        color = NordGreen.copy(alpha = 0.8f)
                                    )
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    // InterceptPlatformTextInput severs the IME connection
                                    // at the source — the system keyboard never opens
                                    InterceptPlatformTextInput(
                                        interceptor = { _, _ -> kotlinx.coroutines.awaitCancellation() }
                                    ) {
                                        BasicTextField(
                                            value = input,
                                            onValueChange = {
                                                input = it
                                                viewModel.onInputChange(it.text)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester)
                                                .onFocusChanged { state ->
                                                    if (state.isFocused) {
                                                        showCustomKeyboard = true
                                                    }
                                                }
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            awaitFirstDown(requireUnconsumed = false)
                                                            showCustomKeyboard = true
                                                        }
                                                    }
                                                },
                                            textStyle = terminalNormalStyle.copy(
                                                color = NordGreen
                                            ),
                                            cursorBrush = SolidColor(Color.Transparent),
                                            keyboardOptions = KeyboardOptions(
                                                imeAction = ImeAction.Done
                                            ),
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
                                                            style = terminalNormalStyle.copy(
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
                                    } // end InterceptPlatformTextInput
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
                                         try {
                                             val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                 putExtra(
                                                     RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                 )
                                                 putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                                 putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...")
                                             }
                                             speechRecognizerLauncher.launch(intent)
                                         } catch (e: Exception) {
                                             Toast.makeText(
                                                 context,
                                                 "Speech recognition not supported on this device",
                                                 Toast.LENGTH_SHORT
                                             ).show()
                                         }
                                     }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // ── FenrirKeyboard (in-app custom keyboard) ────────
                AnimatedVisibility(
                    visible = showCustomKeyboard,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    FenrirKeyboard(
                        value = input,
                        onValueChange = { newValue ->
                            input = newValue
                            viewModel.onInputChange(newValue.text)
                        },
                        onEnter = {
                            if (input.text.isNotBlank()) {
                                viewModel.processCommand(context, input.text)
                                input = TextFieldValue("")
                                coroutineScope.launch {
                                    if (history.isNotEmpty()) {
                                        listState.animateScrollToItem(history.size - 1)
                                    }
                                }
                            }
                        },
                        onHistoryUp   = { viewModel.historyUp() },
                        onHistoryDown = { viewModel.historyDown() }
                    )
                }
            }
        }
    }
}

@Composable
fun NeofetchBanner(
    bannerText: String,
    wolfLines: List<String>,
    viewModel: TerminalViewModel = viewModel()
) {
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val terminalNormalStyle = remember(terminalFontSize) {
        TerminalNormalTextStyle.copy(
            fontSize = terminalFontSize.sp,
            lineHeight = (terminalFontSize * 1.43f).sp
        )
    }
    val terminalInfoStyle = remember(terminalFontSize) {
        TerminalInfoTextStyle.copy(
            fontSize = (terminalFontSize - 2).coerceAtLeast(10).sp,
            lineHeight = ((terminalFontSize - 2) * 1.43f).sp
        )
    }

    val parts = bannerText.split("|")
    val osVersion = parts.getOrNull(1) ?: "?"
    val apiLevel = parts.getOrNull(2) ?: "?"
    val model = parts.getOrNull(3) ?: "?"

    val context = LocalContext.current
    var liveUptime by remember { mutableStateOf("") }
    var liveRam by remember { mutableStateOf("") }
    var liveStorage by remember { mutableStateOf("") }
    var liveBattery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            // Uptime
            val uptimeMs = android.os.SystemClock.elapsedRealtime()
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs / (1000 * 60)) % 60
            liveUptime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

            // RAM
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            liveRam = if (activityManager != null) {
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
                val availRam = memoryInfo.availMem / (1024 * 1024 * 1024.0)
                val usedRam = totalRam - availRam
                val percent = (usedRam / totalRam * 100).toInt()
                "%.1fG / %.1fG ($percent%%)".format(usedRam, totalRam)
            } else "Unknown"

            // Storage
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong
            val totalStorage = (totalBlocks * blockSize) / (1024 * 1024 * 1024.0)
            val availStorage = (availableBlocks * blockSize) / (1024 * 1024 * 1024.0)
            val usedStorage = totalStorage - availStorage
            val storagePercent = (usedStorage / totalStorage * 100).toInt()
            liveStorage = "%.1fG / %.1fG ($storagePercent%%)".format(usedStorage, totalStorage)

            // Battery
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            liveBattery = "$batteryLevel%${if (isCharging) " (charging)" else " (discharging)"}"

            kotlinx.coroutines.delay(2000)
        }
    }

    val infoLines = listOf(
        Triple("OS", "Android $osVersion (API $apiLevel)", NordYellow),
        Triple("DEVICE", model, NordYellow),
        Triple("SHELL", "FenrirCLI v1.0", NordFrost1),
        Triple("UPTIME", liveUptime, NordFrost1),
        Triple("RAM", liveRam, NordYellow),
        Triple("STORAGE", liveStorage, NordYellow),
        Triple("BATTERY", liveBattery, NordPurple),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Wolf art on the left
        Column(modifier = Modifier.weight(0.5f)) {
            wolfLines.forEach { line ->
                Text(
                    text = line,
                    style = TerminalBrailleTextStyle.copy(
                        color = NordFrost1
                    ),
                    softWrap = false
                )
            }
        }

        // Info panel on the right
        Column(
            modifier = Modifier
                .weight(0.5f)
                .padding(top = 8.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "root@fenrir",
                style = terminalNormalStyle.copy(
                    color = NordGreen,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = "─".repeat(14),
                style = terminalInfoStyle.copy(
                    color = NordNight3
                )
            )
            infoLines.forEach { (key, value, color) ->
                Row {
                    Text(
                        text = "$key: ",
                        style = terminalInfoStyle.copy(
                            color = NordFrost1
                        )
                    )
                    Text(
                        text = value,
                        style = terminalInfoStyle.copy(
                            color = color
                        ),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Composable
fun SysInfoPanel(
    viewModel: TerminalViewModel = viewModel()
) {
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val terminalNormalStyle = remember(terminalFontSize) {
        TerminalNormalTextStyle.copy(
            fontSize = terminalFontSize.sp,
            lineHeight = (terminalFontSize * 1.43f).sp
        )
    }

    val context = LocalContext.current
    var liveUptime by remember { mutableStateOf("") }
    var liveRam by remember { mutableStateOf("") }
    var liveStorage by remember { mutableStateOf("") }
    var liveBattery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            // Uptime
            val uptimeMs = android.os.SystemClock.elapsedRealtime()
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs / (1000 * 60)) % 60
            liveUptime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

            // RAM
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            liveRam = if (activityManager != null) {
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
                val availRam = memoryInfo.availMem / (1024 * 1024 * 1024.0)
                val usedRam = totalRam - availRam
                val percent = (usedRam / totalRam * 100).toInt()
                "%.1fG / %.1fG ($percent%%)".format(usedRam, totalRam)
            } else "Unknown"

            // Storage
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong
            val totalStorage = (totalBlocks * blockSize) / (1024 * 1024 * 1024.0)
            val availStorage = (availableBlocks * blockSize) / (1024 * 1024 * 1024.0)
            val usedStorage = totalStorage - availStorage
            val storagePercent = (usedStorage / totalStorage * 100).toInt()
            liveStorage = "%.1fG / %.1fG ($storagePercent%%)".format(usedStorage, totalStorage)

            // Battery
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            liveBattery = "$batteryLevel%${if (isCharging) " (charging)" else " (discharging)"}"

            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text("┌──────────────────────────────────────────┐", style = terminalNormalStyle.copy(color = NordFrost1))
        Text("│             SYSTEM STATUS                │", style = terminalNormalStyle.copy(color = NordFrost1))
        Text("├──────────────────────────────────────────┤", style = terminalNormalStyle.copy(color = NordFrost1))
        Text("│ OS VERSION : Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ HARDWARE   : ${android.os.Build.MODEL}", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ SHELL BUILD: FenrirCLI v1.0.0", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ UPTIME     : $liveUptime", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ RAM RATIO  : $liveRam", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ STORAGE    : $liveStorage", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("│ BATTERY    : $liveBattery", style = terminalNormalStyle.copy(color = NordSnow0))
        Text("└──────────────────────────────────────────┘", style = terminalNormalStyle.copy(color = NordFrost1))
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,navigation=buttons")

@Composable
fun TerminalScreenPreview() {
    RootNodeTheme(darkTheme = true) {
        TerminalScreen()
    }
}