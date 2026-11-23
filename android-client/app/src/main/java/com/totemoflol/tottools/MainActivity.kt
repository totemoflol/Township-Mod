package com.totemoflol.tottools

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF101713)
private val Panel = Color(0xFF1A251F)
private val PanelAlt = Color(0xFF223129)
private val Mint = Color(0xFF8CE5B4)
private val Gold = Color(0xFFFFD27A)
private val Muted = Color(0xFFA8B8AE)

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
    var status by remember { mutableStateOf("Choose a Township save export to get started.") }
    var busy by remember { mutableStateOf(false) }
    var showXml by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Reading and verifying save..."
                report = null
                showXml = false
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Android could not open this file.")
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Township save"
                        TownshipDecoder.decode(bytes, name)
                    }
                    report = decoded
                    status = "Save decoded and XML validated."
                } catch (e: Exception) {
                    status = e.message ?: "Could not decode this file."
                } finally {
                    busy = false
                }
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri: Uri? ->
        val current = report
        if (uri != null && current != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(current.decodedXml) }
                            ?: throw IllegalStateException("Could not create the output file.")
                    }
                    status = "Decoded XML exported successfully."
                } catch (e: Exception) {
                    status = e.message ?: "Export failed."
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF06351C),
            secondary = Gold, background = Ink, surface = Panel,
            surfaceVariant = PanelAlt, onBackground = Color(0xFFEAF2EC),
            onSurface = Color(0xFFEAF2EC), onSurfaceVariant = Muted
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Mint) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                Text("ToT", color = Color(0xFF07351D), fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("ToT Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("TOWNSHIP SAVE TOOLKIT", style = MaterialTheme.typography.labelSmall, color = Mint, letterSpacing = 1.6.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = PanelAlt) {
                            Text("v1.0.0", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Muted, fontSize = 11.sp)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        border = BorderStroke(1.dp, Color(0xFF304238)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("LOCAL SAVE INSPECTOR", color = Gold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                            Text("Your save.\\nDecoded on-device.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, lineHeight = 35.sp)
                            Text(
                                "Import mGameInfo or LocalInfo exports. ToT Tools verifies the 0x79 decode, handles the LZ4 block when present, and checks the resulting XML.",
                                color = Muted, style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { importPicker.launch(arrayOf("*/*")) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(19.dp), color = Color(0xFF06351C), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Processing...")
                                } else {
                                    Text("Import Township save", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text("ZIP exports and extracted XML files â€¢ Read-only", color = Muted, fontSize = 11.sp)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelAlt),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = if (report != null) Color(0xFF214A35) else Color(0xFF394333)) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Text(if (report != null) "âœ“" else "i", color = if (report != null) Mint else Gold, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (report != null) "Ready" else if (busy) "Working" else "Status", fontWeight = FontWeight.SemiBold)
                                Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    report?.let { data ->
                        SectionHeading("DECODE RESULTS", "Validated output")
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(data.sourceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (data.sourceEntry != null) Text("ZIP entry: " + data.sourceEntry, color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Metric("XML root", data.rootName, Modifier.weight(1f))
                                    Metric("Top-level nodes", data.directChildren.toString(), Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Metric("Decoded size", formatBytes(data.decodedXml.size), Modifier.weight(1f))
                                    Metric("LZ4", if (data.usedLz4) "Yes" else "No", Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Metric("0x79 verified", if (data.roundTripVerified) "PASS" else "N/A", Modifier.weight(1f))
                                    Metric("Padding bytes", data.ignoredPaddingBytes.toString(), Modifier.weight(1f))
                                }
                                HorizontalDivider(color = Color(0xFF34443A))
                                Text("Verification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                data.notes.forEach { note ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        Text("â€¢", color = Mint)
                                        Text(note, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { exportPicker.launch(data.sourceName.substringBeforeLast('.').ifBlank { "Township" } + ".decoded.xml") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Export decoded XML") }
                                TextButton(onClick = { showXml = !showXml }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (showXml) "Hide XML preview" else "Preview decoded XML")
                                }
                                if (showXml) {
                                    Surface(color = Color(0xFF0B100D), shape = RoundedCornerShape(12.dp)) {
                                        Text(
                                            data.decodedXml.toString(Charsets.UTF_8).take(9000),
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            color = Color(0xFFC1D8C8), fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace, lineHeight = 14.sp
                                        )
                                    }
                                    if (data.decodedXml.size > 9000) Text("Preview limited to first 9,000 characters.", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    SectionHeading("TOOLS", "Included in this build")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("0x79 Decoder", "MurmurHash2 + differential XOR", Modifier.weight(1f))
                        FeatureTile("LZ4 Handler", "Raw block decompression", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureTile("XML Validator", "Root and node inspection", Modifier.weight(1f))
                        FeatureTile("Save Export", "Write decoded XML copy", Modifier.weight(1f))
                    }
                    Text(
                        "ToT Tools does not modify your original save or connect to Township servers. Keep a backup of every export.",
                        Modifier.padding(vertical = 8.dp),
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Mint, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = PanelAlt, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            Text(value, color = Color(0xFFEAF2EC), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun FeatureTile(title: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF304238))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

private fun formatBytes(value: Int): String = String.format(Locale.US, "%,d B", value)

