package com.totemoflol.tottools

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF07110B)
private val Panel = Color(0xFF101D14)
private val PanelAlt = Color(0xFF17271C)
private val Mint = Color(0xFF7DF0A8)
private val Green = Color(0xFF2EC66D)
private val Gold = Color(0xFFEACB77)
private val Muted = Color(0xFFA6B8AA)
private val Line = Color(0xFF294233)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ToTToolsApp() }
    }
}

@Composable
private fun ToTToolsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logoTransition = rememberInfiniteTransition(label = "brand-pulse")
    val logoScale by logoTransition.animateFloat(0.985f, 1.025f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "logo-scale")
    var report by remember { mutableStateOf<DecodeReport?>(null) }
    var status by remember { mutableStateOf("Import a Township save to begin.") }
    var busy by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Currency") }
    var search by remember { mutableStateOf("") }
    var editedValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    var updateStatus by remember { mutableStateOf("Checking for updates…") }
    var latestReleaseUrl by remember { mutableStateOf("https://github.com/totemoflol/Township-Mod/releases/latest") }

    val items = remember(report) {
        report?.let { TownshipSaveEditor.findItems(it.decodedXml) } ?: emptyList()
    }
    val categories = remember(items) { items.map { it.category }.distinct() }
    val changedItems = items.filter { item ->
        editedValues[item.key]?.let { it != item.value } == true
    }
    val validChanges = changedItems.all { item ->
        editedValues[item.key]?.toLongOrNull()?.let { it in 0L..2_000_000_000L } == true
    }
    val visibleItems = items.filter { item ->
        item.category == selectedCategory &&
            (search.isBlank() || item.label.contains(search, ignoreCase = true))
    }


    LaunchedEffect(Unit) {
        updateStatus = withContext(Dispatchers.IO) {
            try {
                val connection = (URL("https://api.github.com/repos/totemoflol/Township-Mod/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                try {
                    val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.optString("tag_name", "")
                    latestReleaseUrl = json.optString("html_url", latestReleaseUrl)
                    val latest = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(tag)?.value
                    val current = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0")?.value
                    if (latest == null || current == null) "Update status unavailable"
                    else if (latest == current) "You're up to date · $current"
                    else "Update available · $latest (installed $current)"
                } finally { connection.disconnect() }
            } catch (_: Exception) { "Couldn't check for updates" }
        }
    }

    LaunchedEffect(report) {
        editedValues = emptyMap()
        search = ""
        selectedCategory = categories.firstOrNull() ?: "Currency"
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Opening save..."
                report = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Android could not open this file.")
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Township save"
                        val decoded = TownshipDecoder.decode(bytes, name)
                        val fieldCount = TownshipSaveEditor.findItems(decoded.decodedXml).size
                        decoded to fieldCount
                    }
                    report = result.first
                    status = if (result.second > 0) {
                        "Save loaded. " + result.second + " supported values are ready to edit."
                    } else {
                        "Save loaded, but no supported item values were found in this file."
                    }
                } catch (e: Exception) {
                    status = e.message ?: "Could not open this save."
                } finally {
                    busy = false
                }
            }
        }
    }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val payload = pendingSave
        pendingSave = null
        if (uri != null && payload != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                            ?: throw IllegalStateException("Could not create the output file.")
                    }
                    status = "Edited save copy exported. Your original file was not changed."
                } catch (e: Exception) {
                    status = e.message ?: "Export failed."
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Color(0xFF062311),
            secondary = Gold,
            background = Ink,
            surface = Panel,
            surfaceVariant = PanelAlt,
            onBackground = Color(0xFFE8F2EA),
            onSurface = Color(0xFFE8F2EA),
            onSurfaceVariant = Muted
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Box(Modifier.fillMaxSize()) {
                AmbientGreenBackground(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 17.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = PanelAlt,
                            border = BorderStroke(1.dp, Green.copy(alpha = 0.7f))
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.mipmap.tot_tools_icon),
                                contentDescription = "ToT Tools emblem",
                                modifier = Modifier.size(58.dp).clip(CircleShape).graphicsLayer { scaleX = logoScale; scaleY = logoScale }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("ToT Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            Text("TOWNSHIP TOOLKIT", style = MaterialTheme.typography.labelSmall, color = Mint, letterSpacing = 2.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = PanelAlt) {
                            Text("BETA 0.3.0", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        border = BorderStroke(1.dp, Line),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text("SAVE WORKSPACE", color = Gold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                            Text(
                                "Make your town\nyour own.",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 36.sp
                            )
                            Text(
                                "Import a save, browse item categories, change supported values and export a separate edited copy.",
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { importPicker.launch(arrayOf("*/*")) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(19.dp), color = Color(0xFF062311), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Opening save...")
                                } else {
                                    Text("Import Township save", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        status = "Searching Township app data…"
                                        try {
                                            val found = withContext(Dispatchers.IO) { RootSaveFinder.find() }
                                            val decoded = withContext(Dispatchers.IO) { TownshipDecoder.decode(found.second, found.first) }
                                            report = decoded
                                            status = "Detected and imported " + found.first.substringAfterLast('/') + "."
                                        } catch (e: Exception) {
                                            status = e.message ?: "Automatic detection failed. Use Import Township save."
                                        } finally { busy = false }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(15.dp)
                            ) { Text("Auto-detect save on device", fontWeight = FontWeight.Bold) }
                            Text("Auto-detect requires root access. Otherwise import an exported save above.", color = Muted, fontSize = 11.sp)
                        }
                    }


                    Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("APP UPDATE", color = Gold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                                Text(updateStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestReleaseUrl))) }) { Text("View") }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = PanelAlt), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (report != null) Color(0xFF1E4B30) else Color(0xFF394333)
                            ) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (report != null) "✓" else "i",
                                        color = if (report != null) Mint else Gold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (report != null) "Save status" else if (busy) "Working" else "Ready", fontWeight = FontWeight.SemiBold)
                                Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    report?.let { data ->
                        SectionHeading("ITEM EDITOR", items.size.toString() + " supported values")
                        if (items.isEmpty()) {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("No editable items found", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Import the main Township save export to access the available item categories.",
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                border = BorderStroke(1.dp, Line),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Town values", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text(categories.size.toString() + " categories · " + changedItems.size + " changed", color = Muted, fontSize = 11.sp)
                                        }
                                        TextButton(
                                            onClick = { editedValues = emptyMap() },
                                            enabled = changedItems.isNotEmpty()
                                        ) { Text("Reset") }
                                    }

                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        categories.forEach { category ->
                                            FilterChip(
                                                selected = selectedCategory == category,
                                                onClick = { selectedCategory = category },
                                                label = { Text(category) },
                                                shape = RoundedCornerShape(50)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = search,
                                        onValueChange = { search = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("Search this category") },
                                        shape = RoundedCornerShape(13.dp)
                                    )

                                    if (visibleItems.isEmpty()) {
                                        Text("No matching items in this category.", color = Muted, fontSize = 12.sp)
                                    } else {
                                        visibleItems.forEach { item ->
                                            ItemEditorRow(
                                                item = item,
                                                value = editedValues[item.key] ?: item.value,
                                                onValueChange = { input ->
                                                    editedValues = editedValues + (item.key to input.filter { it.isDigit() }.take(10))
                                                }
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Line)
                                    if (data.encodedData == null) {
                                        Text(
                                            "To export a ready-to-use save package, import the original encoded save file rather than an already-decoded XML copy.",
                                            color = Gold,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val edits = changedItems.associate { item ->
                                                    item.key to (editedValues[item.key] ?: item.value)
                                                }
                                                val editedXml = TownshipSaveEditor.applyEdits(data.decodedXml, edits)
                                                val packageBytes = TownshipDecoder.encodeEditedSave(data, editedXml)
                                                pendingSave = packageBytes
                                                savePicker.launch("ToT-Tools-edited-save.zip")
                                                status = "Choose a destination for your edited copy."
                                            } catch (e: Exception) {
                                                status = e.message ?: "Could not prepare the edited save."
                                            }
                                        },
                                        enabled = data.encodedData != null && changedItems.isNotEmpty() && validChanges,
                                        modifier = Modifier.fillMaxWidth().height(51.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Export edited save copy", fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "The app writes a new file. It never overwrites the save you imported.",
                                        color = Muted,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    SectionHeading("FEATURES", "")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("EDITOR", Modifier.weight(1f))
                        FeatureTile("EXPORT", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("SEARCH", Modifier.weight(1f))
                        FeatureTile("LOCAL", Modifier.weight(1f))
                    }
                    Text(
                        "Keep an untouched backup before using an edited file. Game compatibility and acceptance are not guaranteed.",
                        Modifier.padding(vertical = 8.dp),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ItemEditorRow(item: EditableItem, value: String, onValueChange: (String) -> Unit) {
    Surface(
        color = PanelAlt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(item.category, color = Muted, fontSize = 10.sp)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(126.dp),
                singleLine = true,
                label = { Text("Amount", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(11.dp)
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Mint, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun FeatureTile(title: String, modifier: Modifier = Modifier) {
    Surface(modifier.animateContentSize(), color = Panel.copy(alpha = 0.92f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 17.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = Mint)
        }
    }
}

@Composable
private fun AmbientGreenBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient-glow")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "glow-drift")
    val pulse by transition.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "glow-pulse")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Ink)
        val first = Offset(w * (0.08f + 0.10f * drift), h * 0.12f)
        drawCircle(Brush.radialGradient(listOf(Green.copy(alpha = 0.18f * pulse), Color.Transparent), first, w * 0.78f), w * 0.78f, first)
        val second = Offset(w * 0.95f, h * (0.60f - 0.08f * drift))
        drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = 0.09f * pulse), Color.Transparent), second, w * 0.62f), w * 0.62f, second)
        val ring = Offset(w * 0.88f, h * 0.26f)
        val radius = w * (0.24f + 0.012f * drift)
        drawCircle(Mint.copy(alpha = 0.055f * pulse), radius, ring, style = Stroke(width = 1.2.dp.toPx()))
        drawCircle(Green.copy(alpha = 0.045f), radius * 1.28f, ring, style = Stroke(width = 1.dp.toPx()))
        for (i in 0 until 22) {
            val x = ((i * 73) % 997) / 997f * w
            val y = ((i * 137) % 991) / 991f * h
            val phase = (drift + i * 0.071f) % 1f
            val alpha = (0.12f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(phase * 6.283f))) * pulse
            drawCircle(Mint.copy(alpha = alpha), (1.1f + (i % 3) * 0.55f).dp.toPx(), Offset(x, y))
        }
    }
}
