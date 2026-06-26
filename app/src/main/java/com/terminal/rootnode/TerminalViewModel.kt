package com.terminal.rootnode

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
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

    private var installedApps = listOf<AppInfo>()
    private var db: AliasDatabase? = null
    private var aliases = mutableMapOf<String, String>()
    private var suggestionJob: Job? = null

    init {
        // Startup sequence is now handled in initDatabase or a dedicated startup method
    }

    private fun showBanner(osVersion: String, apiLevel: Int, model: String) {
        // Encode OS info in the text so the UI can display it alongside the wolf art
        addToHistory("NEOFETCH_BANNER|$osVersion|$apiLevel|$model", NordFrost1, type = "neofetch")
    }

    fun startup(context: Context) {
        if (_history.value.isEmpty()) {
            val osVersion = android.os.Build.VERSION.RELEASE
            val apiLevel = android.os.Build.VERSION.SDK_INT
            val model = android.os.Build.MODEL
            showBanner(osVersion, apiLevel, model)
            viewModelScope.launch {
                val battery = getBatteryLevel(context)
                val storage = getStorageInfo()
                addToHistory("BATT  : $battery%", if (battery < 20) NordRed else NordYellow)
                addToHistory("DISK  : $storage", NordYellow)
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
            startup(context)
        }
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

        addToHistory("root@fenrir:~$ $trimmedCommand", NordGreen)

        val parts = trimmedCommand.split(" ")
        val mainCommandOrAlias = parts[0].lowercase()

        // Check for alias
        val actualCommand = aliases[mainCommandOrAlias] ?: trimmedCommand
        val finalParts = actualCommand.split(" ")
        val mainCommand = finalParts[0].lowercase()

        when (mainCommand) {
            "help" -> showHelp()
            "ls" -> listApps()
            "sysinfo" -> showSysInfo(context)
            "clear" -> clearHistory(context)
            "alias" -> handleAliasCommand(trimmedCommand)
            "open" -> {
                if (finalParts.size > 1) {
                    val appName = finalParts.drop(1).joinToString(" ")
                    launchApp(context, appName)
                } else {
                    addToHistory("Usage: open <appname>", NordRed)
                }
            }
            else -> {
                // Try to launch directly by name if it's not a multi-word command we don't know
                if (finalParts.size == 1) {
                    if (!launchApp(context, actualCommand)) {
                        addToHistory("Command not found: $mainCommand", NordRed)
                    }
                } else {
                    addToHistory("Command not found: $mainCommand", NordRed)
                }
            }
        }
        _suggestions.value = emptyList()
        _inlineSuggestion.value = ""
    }

    private fun showHelp() {
        addToHistory("Available commands:", NordFrost1)
        addToHistory("  open <appname>  - Launch an application", NordYellow)
        addToHistory("  help            - Show this help message", NordYellow)
        addToHistory("  ls              - List all installed apps", NordYellow)
        addToHistory("  sysinfo         - Show system information", NordYellow)
        addToHistory("  alias <n>=<c>   - Create an alias (e.g. alias g=Gmail)", NordYellow)
        addToHistory("  unalias <name>  - Remove an alias", NordYellow)
        addToHistory("  clear           - Clear terminal history", NordYellow)
        addToHistory("  <appname>       - Launch an application directly", NordYellow)
    }

    private fun listApps() {
        addToHistory("Installed Applications:", NordFrost1)
        installedApps.forEach { addToHistory("  ${it.label}", NordSnow0) }
    }

    private fun clearHistory(context: Context) {
        _history.value = emptyList()
        startup(context)
    }

    private fun handleAliasCommand(input: String) {
        // Expected format: alias name=command
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

    private fun showSysInfo(context: Context) {
        viewModelScope.launch {
            val batteryStatus = withContext(Dispatchers.IO) {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, ifilter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = level * 100 / scale.toFloat()
                batteryPct.toInt()
            }

            val storageInfo = withContext(Dispatchers.IO) {
                val path = Environment.getDataDirectory()
                val stat = StatFs(path.path)
                val blockSize = stat.blockSizeLong
                val availableBlocks = stat.availableBlocksLong
                val totalBlocks = stat.blockCountLong
                val available = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
                val total = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
                "$available GB / $total GB free"
            }

            val networkInfo = withContext(Dispatchers.IO) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Connected (Wi-Fi)"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Connected (Cellular)"
                    else -> "Disconnected"
                }
            }

            addToHistory("┌──────────────────────────────────────────┐", NordFrost1)
            addToHistory("│             SYSTEM STATUS                │", NordFrost1)
            addToHistory("├──────────────────────────────────────────┤", NordFrost1)
            addToHistory("│ BATTERY: $batteryStatus% " + getProgressBar(batteryStatus), if (batteryStatus < 20) NordRed else NordYellow)
            addToHistory("│ STORAGE: $storageInfo", NordYellow)
            addToHistory("│ NETWORK: $networkInfo", NordPurple)
            addToHistory("│ KERNEL : Android ${android.os.Build.VERSION.RELEASE}", NordPurple)
            addToHistory("│ DEVICE : ${android.os.Build.MODEL}", NordPurple)
            addToHistory("└──────────────────────────────────────────┘", NordFrost1)
        }
    }

    private fun getProgressBar(percent: Int): String {
        val filledChars = percent / 10
        val emptyChars = 10 - filledChars
        return "[" + "=".repeat(filledChars) + ">" + " ".repeat(emptyChars.coerceAtLeast(0)) + "]"
    }

    private fun launchApp(context: Context, appName: String): Boolean {
        val app = installedApps.find { it.label.equals(appName, ignoreCase = true) }
            ?: installedApps.find { it.label.lowercase().contains(appName.lowercase()) }
        
        return if (app != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
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