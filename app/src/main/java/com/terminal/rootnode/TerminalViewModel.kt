package com.terminal.rootnode

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.media.AudioManager
import android.os.PowerManager
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.terminal.rootnode.data.Alias
import com.terminal.rootnode.data.AliasDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import com.terminal.rootnode.ui.theme.NordFrost1
import com.terminal.rootnode.ui.theme.NordGreen
import com.terminal.rootnode.ui.theme.NordNight3
import com.terminal.rootnode.ui.theme.NordPurple
import com.terminal.rootnode.ui.theme.NordRed
import com.terminal.rootnode.ui.theme.NordSnow0
import com.terminal.rootnode.ui.theme.NordYellow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TerminalLine(
    val text: String,
    val color: Color? = null,
    val type: String = "text" // "text" | "neofetch"
)

data class AppInfo(
    val label: String,
    val packageName: String
)

class TerminalViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<TerminalLine>>(emptyList())
    val history: StateFlow<List<TerminalLine>> = _history.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _inlineSuggestion = MutableStateFlow("")
    val inlineSuggestion: StateFlow<String> = _inlineSuggestion.asStateFlow()

    private val _awakeLocked = MutableStateFlow(false)
    val awakeLocked: StateFlow<Boolean> = _awakeLocked.asStateFlow()

    private val _terminalFontSize = MutableStateFlow(14)
    val terminalFontSize: StateFlow<Int> = _terminalFontSize.asStateFlow()

    private val _permissionRequestTrigger = MutableStateFlow<String?>(null)
    val permissionRequestTrigger: StateFlow<String?> = _permissionRequestTrigger.asStateFlow()

    private var pendingCallNumber: String? = null

    private var installedApps = listOf<AppInfo>()
    private var db: AliasDatabase? = null
    private var aliases = mutableMapOf<String, String>()
    private var suggestionJob: Job? = null
    private var pendingAction: (() -> Unit)? = null
    val pendingConfirmationPrompt = MutableStateFlow<String?>(null)
    private var lastSavedVolume: Int = -1
    private var isTorchOn: Boolean = false
    private val hiddenPackages = mutableSetOf<String>()
    private var confirmationTimeoutJob: Job? = null

    // ── Command History (arrow-up / arrow-down navigation) ─────────────────
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1   // -1 = not navigating; 0 = oldest visible

    /** Move to older command. Returns the command string, or null if already at oldest. */
    fun historyUp(): String? {
        if (commandHistory.isEmpty()) return null
        if (historyIndex == -1) historyIndex = commandHistory.size
        if (historyIndex > 0) historyIndex--
        return commandHistory.getOrNull(historyIndex)
    }

    /** Move to newer command. Returns the command string, or empty string when past the newest. */
    fun historyDown(): String {
        if (historyIndex == -1) return ""
        historyIndex++
        return if (historyIndex >= commandHistory.size) {
            historyIndex = -1
            ""
        } else {
            commandHistory[historyIndex]
        }
    }

    init {
        // Startup sequence is now handled in initDatabase or a dedicated startup method
    }

    private fun showBanner(context: Context) {
        val osVersion = android.os.Build.VERSION.RELEASE
        val apiLevel = android.os.Build.VERSION.SDK_INT
        val model = android.os.Build.MODEL

        // Real Uptime
        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        val hours = uptimeMs / (1000 * 60 * 60)
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val uptimeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        // Real RAM Info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val ramStr = if (activityManager != null) {
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
            val availRam = memoryInfo.availMem / (1024 * 1024 * 1024.0)
            val usedRam = totalRam - availRam
            val percent = (usedRam / totalRam * 100).toInt()
            "%.1fG / %.1fG ($percent%%)".format(usedRam, totalRam)
        } else {
            "Unknown"
        }

        // Real Storage Info
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong
        val totalStorage = (totalBlocks * blockSize) / (1024 * 1024 * 1024.0)
        val availStorage = (availableBlocks * blockSize) / (1024 * 1024 * 1024.0)
        val usedStorage = totalStorage - availStorage
        val storagePercent = (usedStorage / totalStorage * 100).toInt()
        val storageStr = "%.1fG / %.1fG ($storagePercent%%)".format(usedStorage, totalStorage)

        // Real Battery Info
        val batteryLevel = getBatteryLevel(context)
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val batteryStr = "$batteryLevel%${if (isCharging) " (charging)" else " (discharging)"}"

        // Encode everything in the pipeline string
        addToHistory("NEOFETCH_BANNER|$osVersion|$apiLevel|$model|$uptimeStr|$ramStr|$storageStr|$batteryStr", NordFrost1, type = "neofetch")
    }

    fun startup(context: Context) {
        if (_history.value.isEmpty()) {
            showBanner(context)
            viewModelScope.launch {
                addToHistory("------------------------------------------", NordNight3)
                addToHistory("Type 'help' for available commands.", NordPurple)
            }
        }
    }

    fun initDatabase(context: Context) {
        if (db == null) {
            db = Room.databaseBuilder(
                context.applicationContext,
                AliasDatabase::class.java, "alias-db"
            ).build()
            loadAliases()
            
            // Load hidden apps from SharedPreferences
            val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)
            val hidden = sharedPrefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()
            hiddenPackages.clear()
            hiddenPackages.addAll(hidden)
            
            _awakeLocked.value = sharedPrefs.getBoolean("awake_locked", false)
            _terminalFontSize.value = sharedPrefs.getInt("terminal_font_size", 14)
            
            startup(context)
        }
    }

    fun clearPermissionRequestTrigger() {
        _permissionRequestTrigger.value = null
    }

    fun onPermissionGranted(context: Context) {
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) {
            callNumber(context, number)
        }
    }

    fun onPermissionDenied() {
        pendingCallNumber = null
        addToHistory("Permission denied. Cannot place call directly.", NordRed)
    }

    private fun getBatteryLevel(context: Context): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return (level * 100 / scale.toFloat()).toInt()
    }

    private fun getStorageInfo(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong
        val available = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        val total = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
        return "$available GB / $total GB free"
    }

    private fun loadAliases() {
        viewModelScope.launch {
            val list = db?.aliasDao()?.getAll() ?: emptyList()
            aliases = list.associate { it.name to it.command }.toMutableMap()
        }
    }

    fun loadApps(context: Context) {
        viewModelScope.launch {
            installedApps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, 0).map { resolveInfo ->
                    AppInfo(
                        label = resolveInfo.loadLabel(pm).toString(),
                        packageName = resolveInfo.activityInfo.packageName
                    )
                }.sortedBy { it.label.lowercase() }
            }
        }
    }

    private val helpMap = mapOf(
        "help" to "Syntax: help [<command>]\nDescription: Displays general help or details for a specific command.\nExample: help alias",
        "exit" to "Syntax: exit\nDescription: Exits FenrirCLI to the original phone launcher activity.",
        "default" to "Syntax: default\nDescription: Opens system settings to configure the default home/launcher application.",
        "awake" to "Syntax: awake [lock | unlock | on | off]\nDescription: Sets the screen awake mode. When locked or on, the screen will never go to sleep while inside FenrirCLI.",
        "font" to "Syntax: font [size (10-30) | reset]\nDescription: Sets the terminal console text size in sp units, or resets it to the default (14sp).\nExample: font 16",
        "fontsize" to "Syntax: fontsize [size (10-30) | reset]\nDescription: Sets the terminal console text size in sp units, or resets it to the default (14sp).\nExample: fontsize 16",
        "version" to "Syntax: version\nDescription: Displays the current version, open source license flag, and the official GitHub repository link.",
        "ls" to "Syntax: ls\nDescription: Loops through the device's PackageManager to return a clean, alphabetized list of launchable user-installed apps. Hidden apps are skipped.",
        "sysinfo" to "Syntax: sysinfo\nDescription: Queries and shows hardware specs (OS version, device model, build version, uptime, live RAM ratio, storage allocations, and battery status).",
        "neofetch" to "Syntax: neofetch\nDescription: Renders the wolf ASCII logo side-by-side with system stats in neofetch format.",
        "clear" to "Syntax: clear\nDescription: Clears out all terminal display history buffer lines.",
        "alias" to "Syntax: alias <name>=<command>\nDescription: Saves a macro shortcut string locally.\nExample: alias ig=open Instagram",
        "unalias" to "Syntax: unalias <name>\nDescription: Deletes a saved shortcut macro key from local storage.\nExample: unalias ig",
        "echo" to "Syntax: echo <text>\nDescription: Displays the input text back on the screen.\nExample: echo Hello Fenrir",
        "open" to "Syntax: open <appname>\nDescription: Fuzzy matches the app label or package and boots it into the foreground.\nExample: open YouTube",
        "find" to "Syntax: find <query>  or  search <query>\nDescription: Filters and lists all installed apps matching the query string.\nExample: find g",
        "search" to "Syntax: search <query>  or  find <query>\nDescription: Filters and lists all installed apps matching the query string.\nExample: search tik",
        "info" to "Syntax: info <appname>\nDescription: Deep-links straight to the system application management settings for that app.\nExample: info Gmail",
        "hide" to "Syntax: hide <appname>\nDescription: Registers target app package into hidden blocklist so it's skipped from listings and suggestions.\nExample: hide TikTok",
        "unhide" to "Syntax: unhide <appname>\nDescription: Purges app package from blocklist to restore normal visibility.\nExample: unhide TikTok",
        "call" to "Syntax: call <number>\nDescription: Initiates a dialer call intent to the specified phone number.\nExample: call 12345678",
        "mute" to "Syntax: mute\nDescription: Mutes system media volume stream and sets ringer to vibrate.",
        "unmute" to "Syntax: unmute\nDescription: Restores media volume stream to previously saved level.",
        "volume" to "Syntax: volume <0-100>\nDescription: Sets master media stream volume percentage level.\nExample: volume 50",
        "bright" to "Syntax: bright <0-100> | bright auto\nDescription: Sets manual screen brightness percentage level or enables automatic mode.\nExample: bright 80",
        "torch" to "Syntax: torch  or  flash\nDescription: Toggles the physical camera LED flashlight state.",
        "flash" to "Syntax: flash  or  torch\nDescription: Toggles the physical camera LED flashlight state.",
        "uninstall" to "Syntax: uninstall <appname>\nDescription: Invokes uninstallation confirmation prompt, then launches package deletion intent.\nExample: uninstall Instagram"
    )

    fun onInputChange(input: String) {
        suggestionJob?.cancel()
        if (input.isBlank()) {
            _suggestions.value = emptyList()
            _inlineSuggestion.value = ""
            return
        }

        val lastWord = input.split(" ").last()
        if (lastWord.isBlank()) {
            _suggestions.value = emptyList()
            _inlineSuggestion.value = ""
            return
        }

        suggestionJob = viewModelScope.launch(Dispatchers.Default) {
            // Small debounce so rapid typing doesn't thrash
            delay(80)
            val appSuggestions = installedApps
                .filter { it.packageName !in hiddenPackages }
                .filter { it.label.lowercase().startsWith(lastWord.lowercase()) }
                .map { it.label }

            val aliasSuggestions = aliases.keys
                .filter { it.lowercase().startsWith(lastWord.lowercase()) }

            val allSuggestions = (aliasSuggestions + appSuggestions)
            _suggestions.value = allSuggestions.take(10)

            val bestMatch = allSuggestions.firstOrNull { it.lowercase().startsWith(lastWord.lowercase()) }
            _inlineSuggestion.value = if (bestMatch != null && bestMatch.length > lastWord.length) {
                bestMatch.substring(lastWord.length)
            } else ""
        }
    }

    fun processCommand(context: Context, command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return

        // Push to command history (skip duplicates of the immediate last entry)
        if (commandHistory.isEmpty() || commandHistory.last() != trimmedCommand) {
            commandHistory.add(trimmedCommand)
            if (commandHistory.size > 100) commandHistory.removeAt(0)
        }
        historyIndex = -1  // reset navigation pointer after each submission

        val activePrompt = pendingConfirmationPrompt.value
        if (activePrompt != null) {
            // Intercept response
            addToHistory("> $trimmedCommand", NordGreen)
            confirmationTimeoutJob?.cancel()
            pendingConfirmationPrompt.value = null
            val action = pendingAction
            pendingAction = null

            val response = trimmedCommand.lowercase()
            if (response == "y" || response == "yes") {
                action?.invoke()
            } else {
                addToHistory("Operation aborted.", NordRed)
            }
            _suggestions.value = emptyList()
            _inlineSuggestion.value = ""
            return
        }

        addToHistory("root@fenrir:~$ $trimmedCommand", NordGreen)

        // Split by logical '&&' for sequential execution
        val commands = trimmedCommand.split("&&").map { it.trim() }.filter { it.isNotEmpty() }
        executeCommandChain(context, commands)

        _suggestions.value = emptyList()
        _inlineSuggestion.value = ""
    }

    private fun executeCommandChain(context: Context, commands: List<String>) {
        if (commands.isEmpty()) return

        val currentCmd = commands.first()
        val remainingCmds = commands.drop(1)

        executeSingleCommand(context, currentCmd) { success ->
            if (success) {
                if (remainingCmds.isNotEmpty()) {
                    executeCommandChain(context, remainingCmds)
                }
            } else {
                addToHistory("Execution aborted due to command failure.", NordRed)
            }
        }
    }

    private fun executeSingleCommand(context: Context, commandStr: String, onComplete: (Boolean) -> Unit) {
        val trimmed = commandStr.trim()
        if (trimmed.isEmpty()) {
            onComplete(true)
            return
        }

        val parts = trimmed.split(" ").filter { it.isNotEmpty() }
        val mainCommandOrAlias = parts[0].lowercase()

        // Resolve alias, preserving arguments
        val actualCommand = if (aliases.containsKey(mainCommandOrAlias)) {
            val macro = aliases[mainCommandOrAlias]!!
            val extraArgs = parts.drop(1).joinToString(" ")
            if (extraArgs.isNotEmpty()) "$macro $extraArgs" else macro
        } else {
            trimmed
        }

        val finalParts = actualCommand.split(" ").filter { it.isNotEmpty() }
        if (finalParts.isEmpty()) {
            onComplete(true)
            return
        }
        val mainCommand = finalParts[0].lowercase()
        val args = finalParts.drop(1).joinToString(" ")

        when (mainCommand) {
            "exit" -> {
                exitLauncher(context)
                onComplete(true)
            }
            "default" -> {
                showDefaultLauncherSettings(context)
                onComplete(true)
            }
            "awake" -> {
                setAwake(context, args)
                onComplete(true)
            }
            "font", "fontsize" -> {
                setFontSize(context, args)
                onComplete(true)
            }
            "help" -> {
                showHelp(args.ifEmpty { null })
                onComplete(true)
            }
            "version" -> {
                showVersion()
                onComplete(true)
            }
            "ls" -> {
                listApps()
                onComplete(true)
            }
            "sysinfo" -> {
                showSysInfo(context)
                onComplete(true)
            }
            "neofetch" -> {
                showBanner(context)
                onComplete(true)
            }
            "clear" -> {
                clearHistory(context)
                onComplete(true)
            }
            "alias" -> {
                handleAliasCommand(actualCommand)
                onComplete(true)
            }
            "unalias" -> {
                handleUnaliasCommand(actualCommand)
                onComplete(true)
            }
            "echo" -> {
                echoCommand(args)
                onComplete(true)
            }
            "open" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: open <appname>", NordRed)
                    onComplete(false)
                } else {
                    val success = launchAppByName(context, args)
                    onComplete(success)
                }
            }
            "find", "search" -> {
                findApps(args)
                onComplete(true)
            }
            "info" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: info <appname>", NordRed)
                    onComplete(false)
                } else {
                    showAppInfo(context, args)
                    onComplete(true)
                }
            }
            "hide" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: hide <appname>", NordRed)
                    onComplete(false)
                } else {
                    hideApp(context, args)
                    onComplete(true)
                }
            }
            "unhide" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: unhide <appname>", NordRed)
                    onComplete(false)
                } else {
                    unhideApp(context, args)
                    onComplete(true)
                }
            }
            "call" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: call <number>", NordRed)
                    onComplete(false)
                } else {
                    callNumber(context, args)
                    onComplete(true)
                }
            }
            "mute" -> {
                muteAudio(context)
                onComplete(true)
            }
            "unmute" -> {
                unmuteAudio(context)
                onComplete(true)
            }
            "volume" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: volume <0-100>", NordRed)
                    onComplete(false)
                } else {
                    setVolume(context, args)
                    onComplete(true)
                }
            }
            "bright" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: bright <0-100>  or  bright auto", NordRed)
                    onComplete(false)
                } else {
                    setBrightness(context, args)
                    onComplete(true)
                }
            }
            "torch", "flash" -> {
                toggleTorch(context)
                onComplete(true)
            }
            "uninstall" -> {
                if (args.isEmpty()) {
                    addToHistory("Usage: uninstall <appname>", NordRed)
                    onComplete(false)
                } else {
                    uninstallApp(context, args, onComplete)
                }
            }
            else -> {
                val success = launchAppByName(context, actualCommand)
                if (!success) {
                    addToHistory("Command not found: $mainCommand", NordRed)
                }
                onComplete(success)
            }
        }
    }

    private fun showHelp(commandArg: String?) {
        if (!commandArg.isNullOrBlank()) {
            val cleanCmd = commandArg.trim().lowercase()
            val manual = helpMap[cleanCmd]
            if (manual != null) {
                addToHistory("Manual for '$cleanCmd':", NordFrost1)
                manual.split("\n").forEach { addToHistory("  $it", NordSnow0) }
            } else {
                addToHistory("No manual entry for: $cleanCmd", NordRed)
            }
            return
        }

        addToHistory("=== FenrirCLI v1.0.0 Help Manual ===", NordFrost1)
        
        addToHistory("[System Info & Navigation]", NordPurple)
        addToHistory("  help [<cmd>]  - Show this manual or command syntax", NordSnow0)
        addToHistory("  version       - Show version, license, and GitHub repo", NordSnow0)
        addToHistory("  exit          - Exit to original phone launcher", NordSnow0)
        addToHistory("  default       - Select default launcher settings", NordSnow0)
        addToHistory("  awake [<val>] - Keep screen awake (lock/unlock/on/off)", NordSnow0)
        addToHistory("  fontsize [s]  - Change console text font size (or 'font')", NordSnow0)
        addToHistory("  ls            - List user launchable installed apps", NordSnow0)
        addToHistory("  sysinfo       - Query hardware, storage, uptime, battery", NordSnow0)
        addToHistory("  neofetch      - Render banner art and hardware specs", NordSnow0)
        addToHistory("  clear         - Clear the terminal history buffer", NordSnow0)

        addToHistory("[Application Shorthands & Scripting]", NordPurple)
        addToHistory("  alias <n>=<c> - Save shortcut macro link in local storage", NordSnow0)
        addToHistory("  unalias <n>   - Delete shortcut macro key from memory", NordSnow0)
        addToHistory("  echo <text>   - Echo literal text for scripting checks", NordSnow0)

        addToHistory("[Application Control]", NordPurple)
        addToHistory("  open <app>    - Launch package matching query foreground", NordSnow0)
        addToHistory("  find <q>      - Pattern match installed apps (or 'search')", NordSnow0)
        addToHistory("  info <app>    - Open App Settings details pane deep-link", NordSnow0)
        addToHistory("  hide <app>    - Skip app from standard directory listings", NordSnow0)
        addToHistory("  unhide <app>  - Restore visibility of target app in lists", NordSnow0)

        addToHistory("[Communication]", NordPurple)
        addToHistory("  call <num>    - Access device dialer to place outgoing call", NordSnow0)

        addToHistory("[Hardware & Sound Control]", NordPurple)
        addToHistory("  mute          - Force music volume to zero (vibrate)", NordSnow0)
        addToHistory("  unmute        - Restore stream to last-saved volume level", NordSnow0)
        addToHistory("  volume <val>  - Set system music volume percentage scale", NordSnow0)
        addToHistory("  bright <val>  - Set screen brightness percent or 'auto'", NordSnow0)
        addToHistory("  torch         - Toggle physical LED flashlight (or 'flash')", NordSnow0)

        addToHistory("[Protected Commands]", NordPurple)
        addToHistory("  uninstall <app> - Request device app deletion with y/n confirmation", NordSnow0)
    }

    private fun showVersion() {
        addToHistory("FenrirCLI v1.0.0", NordFrost1)
        addToHistory("License: MIT", NordYellow)
        addToHistory("GitHub: https://github.com/AllabouchAnas/FenrirCLI", NordPurple)
    }

    private fun listApps() {
        val visibleApps = installedApps.filter { it.packageName !in hiddenPackages }
        if (visibleApps.isEmpty()) {
            addToHistory("No launchable applications found.", NordRed)
        } else {
            addToHistory("Installed Applications (${visibleApps.size}):", NordFrost1)
            visibleApps.forEach { addToHistory("  ${it.label}", NordSnow0) }
        }
    }

    private fun clearHistory(context: Context) {
        val current = _history.value
        if (current.isNotEmpty() && current[0].type == "neofetch") {
            _history.value = current.take(3)
        } else {
            _history.value = emptyList()
            showBanner(context)
            viewModelScope.launch {
                addToHistory("------------------------------------------", NordNight3)
                addToHistory("Type 'help' for available commands.", NordPurple)
            }
        }
    }

    private fun handleAliasCommand(input: String) {
        val content = input.removePrefix("alias").trim()
        if (content.contains("=")) {
            val parts = content.split("=", limit = 2)
            val name = parts[0].trim()
            val command = parts[1].trim()
            if (name.isNotEmpty() && command.isNotEmpty()) {
                viewModelScope.launch {
                    db?.aliasDao()?.insert(Alias(name, command))
                    aliases[name] = command
                    addToHistory("Alias created: $name -> $command", NordPurple)
                }
            } else {
                addToHistory("Invalid alias format. Usage: alias <name>=<command>", NordRed)
            }
        } else if (content.isEmpty()) {
            addToHistory("Current Aliases:", NordFrost1)
            if (aliases.isEmpty()) addToHistory("  None", NordRed)
            aliases.forEach { (name, cmd) -> addToHistory("  $name -> $cmd", NordYellow) }
        } else {
            addToHistory("Invalid alias format. Usage: alias <name>=<command>", NordRed)
        }
    }

    private fun handleUnaliasCommand(input: String) {
        val name = input.removePrefix("unalias").trim()
        if (name.isEmpty()) {
            addToHistory("Usage: unalias <name>", NordRed)
            return
        }
        viewModelScope.launch {
            val alias = db?.aliasDao()?.getByName(name)
            if (alias != null) {
                db?.aliasDao()?.delete(alias)
                aliases.remove(name)
                addToHistory("Alias removed: $name", NordYellow)
            } else {
                addToHistory("Alias not found: $name", NordRed)
            }
        }
    }

    private fun echoCommand(args: String) {
        addToHistory(args, NordSnow0)
    }

    private fun findApp(query: String): AppInfo? {
        val cleanQuery = query.trim().lowercase()
        return installedApps.firstOrNull { it.label.lowercase() == cleanQuery }
            ?: installedApps.firstOrNull { it.packageName.lowercase() == cleanQuery }
            ?: installedApps.firstOrNull { it.label.lowercase().contains(cleanQuery) }
            ?: installedApps.firstOrNull { it.packageName.lowercase().contains(cleanQuery) }
    }

    private fun launchAppByName(context: Context, appName: String): Boolean {
        val app = findApp(appName)
        return if (app != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                addToHistory("Launching ${app.label}...", NordPurple)
                true
            } else {
                addToHistory("Error: Could not find launch intent for ${app.label}", NordRed)
                false
            }
        } else {
            false
        }
    }

    private fun findApps(query: String) {
        if (query.isEmpty()) {
            addToHistory("Usage: find <query>  or  search <query>", NordRed)
            return
        }
        val matches = installedApps
            .filter { it.packageName !in hiddenPackages }
            .filter {
                it.label.lowercase().contains(query.lowercase()) ||
                it.packageName.lowercase().contains(query.lowercase())
            }
        if (matches.isEmpty()) {
            addToHistory("No matches found for: $query", NordRed)
        } else {
            addToHistory("Matches for \"$query\":", NordFrost1)
            matches.forEach { addToHistory("  ${it.label} (${it.packageName})", NordSnow0) }
        }
    }

    private fun showAppInfo(context: Context, appName: String) {
        val app = findApp(appName)
        if (app != null) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                addToHistory("Opening details for ${app.label}...", NordPurple)
            } catch (e: Exception) {
                addToHistory("Error launching details settings: ${e.localizedMessage}", NordRed)
            }
        } else {
            addToHistory("App not found: $appName", NordRed)
        }
    }

    private fun hideApp(context: Context, appName: String) {
        val app = findApp(appName)
        if (app != null) {
            hiddenPackages.add(app.packageName)
            val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putStringSet("hidden_packages", hiddenPackages).apply()
            addToHistory("Application ${app.label} is now hidden.", NordYellow)
        } else {
            addToHistory("App not found: $appName", NordRed)
        }
    }

    private fun unhideApp(context: Context, appName: String) {
        val app = findApp(appName)
        if (app != null) {
            if (hiddenPackages.remove(app.packageName)) {
                val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putStringSet("hidden_packages", hiddenPackages).apply()
                addToHistory("Application ${app.label} is now visible.", NordGreen)
            } else {
                addToHistory("Application ${app.label} was not hidden.", NordRed)
            }
        } else {
            val matchingPackage = hiddenPackages.firstOrNull { it.lowercase().contains(appName.lowercase()) }
            if (matchingPackage != null) {
                hiddenPackages.remove(matchingPackage)
                val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putStringSet("hidden_packages", hiddenPackages).apply()
                addToHistory("Application $matchingPackage is now visible.", NordGreen)
            } else {
                addToHistory("App not found in hidden blocklist: $appName", NordRed)
            }
        }
    }

    private fun callNumber(context: Context, number: String) {
        val hasCallPermission = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasCallPermission) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                addToHistory("Placing call to $number...", NordPurple)
            } catch (e: Exception) {
                addToHistory("Error executing call: ${e.localizedMessage}", NordRed)
            }
        } else {
            addToHistory("Permission CALL_PHONE is required. Launching system request...", NordYellow)
            pendingCallNumber = number
            _permissionRequestTrigger.value = android.Manifest.permission.CALL_PHONE
        }
    }

    private fun muteAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lastSavedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            addToHistory("System audio muted (vibrate mode).", NordYellow)
        } catch (e: SecurityException) {
            addToHistory("System audio muted.", NordYellow)
        }
    }

    private fun unmuteAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (lastSavedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, lastSavedVolume, AudioManager.FLAG_SHOW_UI)
            addToHistory("Audio volume restored to $lastSavedVolume.", NordGreen)
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 2, AudioManager.FLAG_SHOW_UI)
            addToHistory("Audio volume restored to default (50%).", NordGreen)
        }
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (e: SecurityException) {}
    }

    private fun setVolume(context: Context, levelStr: String) {
        val percent = levelStr.toIntOrNull()
        if (percent == null || percent !in 0..100) {
            addToHistory("Usage: volume <0-100>", NordRed)
            return
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (percent / 100f * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        addToHistory("Volume set to $percent% ($targetVolume/$maxVolume).", NordGreen)
    }

    private fun setBrightness(context: Context, arg: String) {
        val cleanArg = arg.trim().lowercase()
        if (!Settings.System.canWrite(context)) {
            addToHistory("Permission required: Modify system settings.", NordRed)
            addToHistory("Opening permission settings dialog...", NordYellow)
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        try {
            if (cleanArg == "auto") {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                addToHistory("Auto-brightness enabled.", NordGreen)
            } else {
                val percent = cleanArg.toIntOrNull()
                if (percent == null || percent !in 0..100) {
                    addToHistory("Usage: bright <0-100>  or  bright auto", NordRed)
                    return
                }
                val val255 = (percent / 100f * 255).toInt()
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    val255
                )
                addToHistory("Brightness set to $percent% ($val255/255).", NordGreen)
            }
        } catch (e: Exception) {
            addToHistory("Failed to write brightness settings: ${e.localizedMessage}", NordRed)
        }
    }

    private fun toggleTorch(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                addToHistory("Torch turned ${if (isTorchOn) "ON" else "OFF"}.", NordPurple)
            } else {
                addToHistory("Error: No flash hardware detected on this device.", NordRed)
            }
        } catch (e: Exception) {
            addToHistory("Failed to toggle torch: ${e.localizedMessage}", NordRed)
        }
    }

    private fun uninstallApp(context: Context, appName: String, onComplete: (Boolean) -> Unit) {
        val app = findApp(appName)
        if (app != null) {
            val prompt = "Are you sure you want to completely uninstall ${app.label}? (y/n): "
            pendingConfirmationPrompt.value = prompt
            addToHistory(prompt, NordYellow)
            pendingAction = {
                try {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    addToHistory("Initiating uninstallation for ${app.label}...", NordPurple)
                    onComplete(true)
                } catch (e: Exception) {
                    addToHistory("Failed to uninstall: ${e.localizedMessage}", NordRed)
                    onComplete(false)
                }
            }
            startConfirmationTimeout()
        } else {
            addToHistory("App not found: $appName", NordRed)
            onComplete(false)
        }
    }



    private fun startConfirmationTimeout() {
        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = viewModelScope.launch {
            delay(30_000)
            if (pendingConfirmationPrompt.value != null) {
                addToHistory("Confirmation timed out. Operation aborted.", NordRed)
                pendingConfirmationPrompt.value = null
                pendingAction = null
            }
        }
    }

    private fun showSysInfo(context: Context) {
        addToHistory("SYSINFO_PANEL", type = "sysinfo")
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun exitLauncher(context: Context) {
        if (isDefaultLauncher(context)) {
            addToHistory("FenrirCLI is set as the default launcher. Opening Default Home settings to change it...", NordYellow)
            showDefaultLauncherSettings(context)
        } else {
            addToHistory("Exiting FenrirCLI...", NordPurple)
            (context as? android.app.Activity)?.finish()
        }
    }

    private fun showDefaultLauncherSettings(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            addToHistory("Opening Default Home App settings...", NordPurple)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Select Home App").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            addToHistory("Opening Home chooser...", NordPurple)
        }
    }

    private fun setAwake(context: Context, arg: String) {
        val cleanArg = arg.trim().lowercase()
        val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)
        
        if (cleanArg.isEmpty()) {
            val status = if (_awakeLocked.value) "locked (always awake)" else "unlocked (normal sleep)"
            addToHistory("Awakeness: $status", NordFrost1)
            return
        }
        
        val newLocked = when (cleanArg) {
            "lock", "on", "true" -> true
            "unlock", "off", "false" -> false
            else -> {
                addToHistory("Usage: awake [lock | unlock | on | off]", NordRed)
                return
            }
        }
        
        _awakeLocked.value = newLocked
        sharedPrefs.edit().putBoolean("awake_locked", newLocked).apply()
        
        val msg = if (newLocked) {
            "Awakeness set to locked. Screen will not go to sleep when on launcher."
        } else {
            "Awakeness set to unlocked. Screen will go to sleep normally."
        }
        addToHistory(msg, NordGreen)
    }

    private fun setFontSize(context: Context, arg: String) {
        val cleanArg = arg.trim().lowercase()
        val sharedPrefs = context.getSharedPreferences("fenrir_prefs", Context.MODE_PRIVATE)

        if (cleanArg.isEmpty()) {
            addToHistory("Current terminal font size: ${_terminalFontSize.value}sp", NordFrost1)
            addToHistory("Usage: fontsize [size (10-30) | reset]", NordPurple)
            return
        }

        if (cleanArg == "reset") {
            _terminalFontSize.value = 14
            sharedPrefs.edit().putInt("terminal_font_size", 14).apply()
            addToHistory("Font size reset to default (14sp).", NordGreen)
            return
        }

        val size = cleanArg.toIntOrNull()
        if (size == null || size < 10 || size > 30) {
            addToHistory("Invalid size. Please specify an integer between 10 and 30.", NordRed)
            return
        }

        _terminalFontSize.value = size
        sharedPrefs.edit().putInt("terminal_font_size", size).apply()
        addToHistory("Font size set to ${size}sp.", NordGreen)
    }

    private fun getProgressBar(percent: Int): String {
        val filledChars = percent / 10
        val emptyChars = 10 - filledChars
        return "[" + "=".repeat(filledChars) + ">" + " ".repeat(emptyChars.coerceAtLeast(0)) + "]"
    }

    private fun addToHistory(line: String, color: Color? = null, type: String = "text") {
        _history.value = _history.value + TerminalLine(line, color, type)
    }

    // Expose wolf lines for UI rendering
    val wolfLines: List<String> = listOf(
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣾⡿⣇⣠⣶⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣾⣿⡿⠁⣿⣿⡏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣿⣿⠏⠃⠀⣿⣿⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⣤⣴⣶⣶⣶⣤⣴⣿⣿⡏⢀⣀⣀⣿⣿⣇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠔⠛⠛⢛⣛⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣠⣴⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡿⢿⣿⣿⣷⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣆⡈⠻⣿⣷⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⡿⠛⢉⣾⣿⣿⣿⣿⣿⣿⣿⣿⡿⠿⠯⠭⠉⠛⠻⠿⣿⣿⠿⣶⣿⣿⣷⣦⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠟⠀⠀⣼⣿⣿⣿⣿⣿⣿⣿⡿⢋⣠⣤⠀⠀⠀⠀⠀⠀⠀⠉⠑⠂⠉⠉⠛⠛⠛⠿⢶⣤⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⡿⣿⣿⣿⣿⣿⣇⣿⣿⡿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⡿⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⠁⢿⣿⣿⣿⣿⣿⣿⣿⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢿⡏⠀⠸⣿⣿⣿⣿⣿⣿⣿⣇⢠⠀⠀⠀⠀⠀⢿⡿⠿⠿⠟⠫⠿⠛⠋⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠸⡇⠀⠀⢹⣿⣿⣿⣿⣿⣿⣿⣮⣧⠀⠀⠀⠀⠈⠳⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠃⠀⠀⠀⢻⣿⣿⣿⣿⢿⣿⣿⣿⣷⣤⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠹⣿⣿⣿⠸⣿⣿⣿⣿⣿⣿⣷⣦⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠿⣿⠀⠙⣿⣿⣏⠙⠛⢿⣿⣿⣧⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠃⠀⠈⠻⣿⡄⠀⠀⠈⠻⣿⣧⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠙⠄⠀⠀⠀⠹⣿⡆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀",
        "⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈"
    )

    val trimmedWolfLines: List<String> by lazy {
        val lines = wolfLines
        fun isBlankChar(c: Char) = c.isWhitespace() || c == '\u2800'
        
        // Find the minimum number of leading blanks in non-empty lines
        val minBlanks = lines
            .filter { line -> line.any { !isBlankChar(it) } }
            .map { line -> line.takeWhile { isBlankChar(it) }.length }
            .minOrNull() ?: 0

        val trimmedLines = lines.map { line ->
            if (line.length >= minBlanks) {
                line.substring(minBlanks).trimEnd { isBlankChar(it) }
            } else {
                ""
            }
        }

        // Trim empty lines from top and bottom
        val firstContentIndex = trimmedLines.indexOfFirst { line -> line.any { !isBlankChar(it) } }
        val lastContentIndex = trimmedLines.indexOfLast { line -> line.any { !isBlankChar(it) } }

        if (firstContentIndex in 0..lastContentIndex) {
            trimmedLines.subList(firstContentIndex, lastContentIndex + 1)
        } else {
            emptyList()
        }
    }
}