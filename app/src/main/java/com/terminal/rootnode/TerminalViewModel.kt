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
import androidx.compose.ui.graphics.Color
import com.terminal.rootnode.ui.theme.HackerAmber
import com.terminal.rootnode.ui.theme.HackerBlue
import com.terminal.rootnode.ui.theme.HackerPurple
import com.terminal.rootnode.ui.theme.HackerRed
import com.terminal.rootnode.ui.theme.MatrixGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TerminalLine(
    val text: String,
    val color: Color? = null
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

    init {
        // Startup sequence is now handled in initDatabase or a dedicated startup method
    }

    private fun showBanner() {
        // Read all 33 lines of the wolf from wolf.txt to show the complete art
        val wolfLines = listOf(
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
        wolfLines.forEach { addToHistory(it, HackerBlue) }
        addToHistory("RootNode Terminal v1.0.0", MatrixGreen)
        addToHistory("OS    : Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", HackerAmber)
        addToHistory("MODEL : ${android.os.Build.MODEL}", HackerAmber)
    }

    fun startup(context: Context) {
        if (_history.value.isEmpty()) {
            showBanner()
            viewModelScope.launch {
                val battery = getBatteryLevel(context)
                val storage = getStorageInfo()
                addToHistory("BATT  : $battery%", if (battery < 20) HackerRed else HackerAmber)
                addToHistory("DISK  : $storage", HackerAmber)
                addToHistory("------------------------------------------", MatrixGreen)
                addToHistory("Type 'help' for available commands.", HackerPurple)
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

        val appSuggestions = installedApps
            .filter { it.label.lowercase().startsWith(lastWord.lowercase()) }
            .map { it.label }
        
        val aliasSuggestions = aliases.keys
            .filter { it.lowercase().startsWith(lastWord.lowercase()) }

        val allSuggestions = (aliasSuggestions + appSuggestions)
        _suggestions.value = allSuggestions.take(10)

        // Set inline suggestion if we have a match
        val bestMatch = allSuggestions.firstOrNull { it.lowercase().startsWith(lastWord.lowercase()) }
        if (bestMatch != null && bestMatch.length > lastWord.length) {
            _inlineSuggestion.value = bestMatch.substring(lastWord.length)
        } else {
            _inlineSuggestion.value = ""
        }
    }

    fun processCommand(context: Context, command: String) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return

        addToHistory("root@node:~$ $trimmedCommand", MatrixGreen)

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
                    addToHistory("Usage: open <appname>", HackerRed)
                }
            }
            else -> {
                // Try to launch directly by name if it's not a multi-word command we don't know
                if (finalParts.size == 1) {
                    if (!launchApp(context, actualCommand)) {
                        addToHistory("Command not found: $mainCommand", HackerRed)
                    }
                } else {
                    addToHistory("Command not found: $mainCommand", HackerRed)
                }
            }
        }
        _suggestions.value = emptyList()
        _inlineSuggestion.value = ""
    }

    private fun showHelp() {
        addToHistory("Available commands:", HackerBlue)
        addToHistory("  open <appname>  - Launch an application", HackerAmber)
        addToHistory("  help            - Show this help message", HackerAmber)
        addToHistory("  ls              - List all installed apps", HackerAmber)
        addToHistory("  sysinfo         - Show system information", HackerAmber)
        addToHistory("  alias <n>=<c>   - Create an alias (e.g. alias g=Gmail)", HackerAmber)
        addToHistory("  unalias <name>  - Remove an alias", HackerAmber)
        addToHistory("  clear           - Clear terminal history", HackerAmber)
        addToHistory("  <appname>       - Launch an application directly", HackerAmber)
    }

    private fun listApps() {
        addToHistory("Installed Applications:", HackerBlue)
        installedApps.forEach { addToHistory("  ${it.label}", HackerAmber) }
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
                    addToHistory("Alias created: $name -> $command", HackerPurple)
                }
            } else {
                addToHistory("Invalid alias format. Usage: alias <name>=<command>", HackerRed)
            }
        } else if (content.isEmpty()) {
            addToHistory("Current Aliases:", HackerBlue)
            if (aliases.isEmpty()) addToHistory("  None", HackerRed)
            aliases.forEach { (name, cmd) -> addToHistory("  $name -> $cmd", HackerAmber) }
        } else {
            addToHistory("Invalid alias format. Usage: alias <name>=<command>", HackerRed)
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

            addToHistory("┌──────────────────────────────────────────┐", HackerBlue)
            addToHistory("│             SYSTEM STATUS                │", HackerBlue)
            addToHistory("├──────────────────────────────────────────┤", HackerBlue)
            addToHistory("│ BATTERY: $batteryStatus% " + getProgressBar(batteryStatus), if (batteryStatus < 20) HackerRed else HackerAmber)
            addToHistory("│ STORAGE: $storageInfo", HackerAmber)
            addToHistory("│ NETWORK: $networkInfo", HackerPurple)
            addToHistory("│ KERNEL : Android ${android.os.Build.VERSION.RELEASE}", HackerPurple)
            addToHistory("│ DEVICE : ${android.os.Build.MODEL}", HackerPurple)
            addToHistory("└──────────────────────────────────────────┘", HackerBlue)
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
                addToHistory("Launching ${app.label}...", HackerPurple)
                true
            } else {
                addToHistory("Error: Could not find launch intent for ${app.label}", HackerRed)
                false
            }
        } else {
            false
        }
    }

    private fun addToHistory(line: String, color: Color? = null) {
        _history.value = _history.value + TerminalLine(line, color)
    }
}