package com.totemoflol.tottools

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var report by remember { mutableStateOf<DecodeReport?>(null) }
    var status by remember { mutableStateOf("Import a Township save to begin.") }
    var busy by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Currency") }
    var search by remember { mutableStateOf("") }
    var editedValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }

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
                                modifier = Modifier.size(58.dp).clip(CircleShape)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("ToT Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            Text("TOWNSHIP TOOLKIT", style = MaterialTheme.typography.labelSmall, color = Mint, letterSpacing = 2.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = PanelAlt) {
                            Text("v1.1.0", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Muted, fontSize = 11.sp)
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
                            Text("Choose an exported ZIP or an extracted save file.", color = Muted, fontSize = 11.sp)
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

                    SectionHeading("TOOLS", "Built into this version")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("Category editor", "Change supported values by group", Modifier.weight(1f))
                        FeatureTile("Save export", "Create a separate edited copy", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("Quick search", "Find a value in the current group", Modifier.weight(1f))
                        FeatureTile("Local processing", "Your save stays on your device", Modifier.weight(1f))
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

@Composable
private fun ItemEditorRow(item: EditableItem, value: String, onValueChange: (String) -> Unit) {
    Surface(
        color = PanelAlt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
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
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun FeatureTile(title: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
