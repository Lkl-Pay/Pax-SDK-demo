package com.lklpay.sdkdemo

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lklpay.bridge.utils.PreferenceManager
import com.lklpay.pax.sdk.LklPaySdk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SdkDemoScreen() } }
    }
}

/* ---------------------------------------------------------------------------------------------
 *  SDK Status (UI state)
 * --------------------------------------------------------------------------------------------- */

private data class SdkStatus(
    val mode: String,               // DEV / REAL
    val ready: Boolean,
    val lastInitAt: Long? = null,
    val serial: String? = null,
    val apiKey: String? = null,
    val lastAction: String? = null,
    val lastActionAt: Long? = null,
    val lastActionOk: Boolean? = null,
    val lastActionMessage: String? = null
)

private fun nowMs() = System.currentTimeMillis()

private fun fmtTs(ms: Long?): String {
    if (ms == null) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(ms)
}

private fun maskApiKey(apiKey: String?): String {
    if (apiKey.isNullOrBlank()) return "—"
    val tail = apiKey.takeLast(4)
    return apiKey.take(8) + "••••••••" + tail
}

/**
 * Limpieza TOTAL como el test de INIT:
 * Evita 401 por token/apiKey viejos y deja la TPV lista para un INIT REAL limpio.
 */
private fun resetSdkCreds(prefs: PreferenceManager) {
    prefs.setApiKey("")
    prefs.setTokenLKL("")
    prefs.setLklTokenExpiration(0L)
    prefs.setEwToken("")
    prefs.setEsToken("")
    prefs.setSerialNumber("")
    prefs.setPIN("")
    prefs.setTokenDate("")
}

private fun loadStatusFromPrefs(
    prefs: PreferenceManager,
    dev: Boolean,
    lastInitAt: Long? = null,
    lastAction: String? = null,
    lastActionAt: Long? = null,
    lastActionOk: Boolean? = null,
    lastActionMessage: String? = null
): SdkStatus {
    val serial = prefs.getSerialNumber()?.takeIf { it.isNotBlank() }
    val apiKey = prefs.getApiKey()?.takeIf { it.isNotBlank() }

    // Ready “real”: al menos serial + apiKey (ajústalo si tu criterio es otro)
    val ready = !serial.isNullOrBlank() && !apiKey.isNullOrBlank()

    return SdkStatus(
        mode = if (dev) "DEV" else "REAL",
        ready = ready,
        lastInitAt = lastInitAt,
        serial = serial,
        apiKey = apiKey,
        lastAction = lastAction,
        lastActionAt = lastActionAt,
        lastActionOk = lastActionOk,
        lastActionMessage = lastActionMessage
    )
}

/* ---------------------------------------------------------------------------------------------
 *  UI
 * --------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SdkDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val prefs = remember { PreferenceManager(context) }

    // Global config
    var dev by remember { mutableStateOf(false) }
    var masterPin by remember { mutableStateOf("123456") }

    // Sale
    var amountCents by remember { mutableStateOf("100") }
    var reference by remember { mutableStateOf("REF-${System.currentTimeMillis()}") }
    var metadataJson by remember {
        mutableStateOf(
            JSONObject(mapOf("source" to "sdk-demo", "ts" to System.currentTimeMillis())).toString()
        )
    }

    // Cancel / Refund
    var originalTxnId by remember { mutableStateOf("TXN-123456") }

    // Print text/json
    var printText by remember { mutableStateOf("Ticket de prueba\nLKLPay SDK\n\nGracias 🙂") }
    var printJson by remember {
        mutableStateOf(
            JSONObject(
                mapOf(
                    "title" to "Voucher LKLPay",
                    "amountCents" to 100,
                    "note" to "Impresión por JSON",
                    "ts" to System.currentTimeMillis()
                )
            ).toString()
        )
    }

    // Print image
    var imageFileName by remember { mutableStateOf("demo_img.png") }
    var imageBase64 by remember { mutableStateOf(samplePngBase64Tiny()) }
    var imageAlign by remember { mutableStateOf(LklPaySdk.ImageAlign.CENTER) }
    var p53Await by remember { mutableStateOf(true) }

    // Output / UX
    var output by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    // Processing overlay (con mensaje)
    var isProcessing by remember { mutableStateOf(false) }
    var processingLabel by remember { mutableStateOf("Procesando…") }

    // SDK Status
    var status by remember {
        mutableStateOf(loadStatusFromPrefs(prefs, dev = dev))
    }

    // Refresca status al cambiar dev
    LaunchedEffect(dev) {
        status = loadStatusFromPrefs(
            prefs = prefs,
            dev = dev,
            lastInitAt = status.lastInitAt,
            lastAction = status.lastAction,
            lastActionAt = status.lastActionAt,
            lastActionOk = status.lastActionOk,
            lastActionMessage = status.lastActionMessage
        )
    }

    // Refresca panel al entrar (por si ya había INIT previo)
    LaunchedEffect(Unit) {
        status = loadStatusFromPrefs(prefs, dev = dev)
    }

    val requestWritePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LKLPay SDK Demo", style = MaterialTheme.typography.titleLarge)
                        Text("PAX | Métodos por sección + status", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // -------------------- SDK Status Panel --------------------
                SdkStatusPanel(
                    status = status,
                    onReload = {
                        status = loadStatusFromPrefs(
                            prefs = prefs,
                            dev = dev,
                            lastInitAt = status.lastInitAt,
                            lastAction = status.lastAction,
                            lastActionAt = status.lastActionAt,
                            lastActionOk = status.lastActionOk,
                            lastActionMessage = status.lastActionMessage
                        )
                        output = "Status recargado desde prefs."
                        showDialog = true
                    },
                    onReset = {
                        resetSdkCreds(prefs)
                        status = loadStatusFromPrefs(prefs, dev = dev)
                        output = "Credenciales reseteadas (prefs limpiadas como el test de INIT)."
                        showDialog = true
                    }
                )

                // -------------------- Global Config --------------------
                SectionCard(
                    title = "Configuración global",
                    subtitle = "Afecta a todos los métodos del SDK."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo DEV (simulación)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "En DEV no se hacen llamadas al SDK vendor ni backend.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = dev, onCheckedChange = { dev = it })
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = masterPin,
                        onValueChange = { masterPin = it },
                        label = { Text("Master PIN") },
                        supportingText = { Text("Se usa para INIT/SESSION en modo REAL.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // -------------------- INIT --------------------
                SectionCard(
                    title = "INIT",
                    subtitle = "Inicializa la TPV. En REAL: obtiene serial, sesión y llaves. Aquí hacemos reset antes para evitar 401."
                ) {
                    PrimaryActionButton(
                        label = "Ejecutar INIT (con reset)",
                        enabled = !isProcessing,
                        icon = Icons.Default.Refresh
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { on -> isProcessing = on },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "INIT",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Inicializando (INIT)…",
                                timeoutMs = 180_000L
                            ) {
                                if (!dev) {
                                    resetSdkCreds(prefs) // 🔥 clave: evita 401 por tokens/apiKey viejos
                                }

                                val res = LklPaySdk.init(
                                    context = context,
                                    config = LklPaySdk.InitConfig(
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )
                                )

                                res.fold(
                                    onSuccess = { r ->
                                        // Después de INIT, recarga desde prefs reales
                                        val refreshed = loadStatusFromPrefs(
                                            prefs = prefs,
                                            dev = dev,
                                            lastInitAt = nowMs(),
                                            lastAction = "INIT",
                                            lastActionAt = nowMs(),
                                            lastActionOk = true,
                                            lastActionMessage = r.message
                                        )
                                        status = refreshed

                                        buildString {
                                            appendLine("INIT OK")
                                            appendLine("devMode: ${r.devMode}")
                                            appendLine("serial: ${r.serial}")
                                            appendLine("apiKey: ${r.apiKey ?: "—"}")
                                            appendLine("message: ${r.message}")
                                        }
                                    },
                                    onFailure = { e ->
                                        "INIT ERROR: ${e.message ?: "desconocido"}"
                                    }
                                )
                            }
                        }
                    }
                }

                // -------------------- SALE --------------------
                SectionCard(
                    title = "SALE",
                    subtitle = "Ejecuta una venta. Recomendado: correr INIT primero en REAL."
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = amountCents,
                            onValueChange = { amountCents = it.filter(Char::isDigit) },
                            label = { Text("Amount (cents)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            label = { Text("Reference") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = metadataJson,
                        onValueChange = { metadataJson = it },
                        label = { Text("Metadata JSON (opcional)") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    PrimaryActionButton(
                        label = "Ejecutar SALE",
                        enabled = !isProcessing,
                        icon = Icons.Default.ShoppingCart
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { isProcessing = it },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "SALE",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Procesando SALE…"
                            ) {
                                val amt = amountCents.toLongOrNull() ?: 0L
                                require(amt > 0) { "Monto inválido" }

                                val cfg = LklPaySdk.SaleConfig(
                                    amountCents = amt,
                                    reference = reference.trim().ifBlank { null },
                                    metadataJson = metadataJson.trim().ifBlank { null },
                                    useDev = dev,
                                    masterPin = masterPin.trim()
                                )

                                val res = LklPaySdk.sale(context, cfg)
                                res.fold(
                                    onSuccess = { txn -> buildTxnResultText("SALE", txn) },
                                    onFailure = { e -> "SALE ERROR: ${e.message ?: "desconocido"}" }
                                )
                            }
                        }
                    }
                }

                // -------------------- CANCEL / REFUND --------------------
                SectionCard(
                    title = "CANCEL / REFUND",
                    subtitle = "Operaciones sobre una transacción existente (txnId)."
                ) {
                    OutlinedTextField(
                        value = originalTxnId,
                        onValueChange = { originalTxnId = it },
                        label = { Text("Original TxnId") },
                        supportingText = { Text("Usa el txnId retornado por SALE.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryActionButton(
                            label = "Ejecutar CANCEL",
                            enabled = !isProcessing,
                            icon = Icons.Default.Refresh
                        ) {
                            scope.launch {
                                safeAction(
                                    setProcessing = { isProcessing = it },
                                    setProcessingLabel = { processingLabel = it },
                                    setOutput = { output = it },
                                    setDialog = { showDialog = it },
                                    onStatus = { ok, msg ->
                                        status = status.copy(
                                            lastAction = "CANCEL",
                                            lastActionAt = nowMs(),
                                            lastActionOk = ok,
                                            lastActionMessage = msg
                                        )
                                    },
                                    processingMsg = "Procesando CANCEL…"
                                ) {
                                    val tx = originalTxnId.trim()
                                    require(tx.isNotBlank()) { "TxnId requerido" }

                                    val cfg = LklPaySdk.CancelConfig(
                                        idTransaction = tx,
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.cancel(context, cfg)
                                    res.fold(
                                        onSuccess = { txn -> buildTxnResultText("CANCEL", txn) },
                                        onFailure = { e -> "CANCEL ERROR: ${e.message ?: "desconocido"}" }
                                    )
                                }
                            }
                        }

                        PrimaryActionButton(
                            label = "Ejecutar REFUND",
                            enabled = !isProcessing,
                            icon = Icons.Default.Refresh
                        ) {
                            scope.launch {
                                safeAction(
                                    setProcessing = { isProcessing = it },
                                    setProcessingLabel = { processingLabel = it },
                                    setOutput = { output = it },
                                    setDialog = { showDialog = it },
                                    onStatus = { ok, msg ->
                                        status = status.copy(
                                            lastAction = "REFUND",
                                            lastActionAt = nowMs(),
                                            lastActionOk = ok,
                                            lastActionMessage = msg
                                        )
                                    },
                                    processingMsg = "Procesando REFUND…"
                                ) {
                                    val tx = originalTxnId.trim()
                                    require(tx.isNotBlank()) { "TxnId requerido" }

                                    val cfg = LklPaySdk.RefundConfig(
                                        idTransaction = tx,
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.refund(context, cfg)
                                    res.fold(
                                        onSuccess = { txn -> buildTxnResultText("REFUND", txn) },
                                        onFailure = { e -> "REFUND ERROR: ${e.message ?: "desconocido"}" }
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------- PRINT (P52) --------------------
                SectionCard(
                    title = "PRINT_TEXT (P52)",
                    subtitle = "Imprime texto plano."
                ) {
                    OutlinedTextField(
                        value = printText,
                        onValueChange = { printText = it },
                        label = { Text("Texto a imprimir") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    PrimaryActionButton(
                        label = "Imprimir TEXTO (P52)",
                        enabled = !isProcessing,
                        icon = Icons.Default.Print
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { isProcessing = it },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "PRINT_TEXT",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Imprimiendo texto…"
                            ) {
                                val res = LklPaySdk.print(
                                    context = context,
                                    text = printText,
                                    json = null,
                                    useDev = dev
                                )
                                res.fold(
                                    onSuccess = { "PRINT_TEXT OK" },
                                    onFailure = { e -> "PRINT_TEXT ERROR: ${e.message ?: "desconocido"}" }
                                )
                            }
                        }
                    }
                }

                SectionCard(
                    title = "PRINT_JSON (P52)",
                    subtitle = "Imprime usando JSON."
                ) {
                    OutlinedTextField(
                        value = printJson,
                        onValueChange = { printJson = it },
                        label = { Text("JSON a imprimir") },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    PrimaryActionButton(
                        label = "Imprimir JSON (P52)",
                        enabled = !isProcessing,
                        icon = Icons.Default.Print
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { isProcessing = it },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "PRINT_JSON",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Imprimiendo JSON…"
                            ) {
                                val res = LklPaySdk.print(
                                    context = context,
                                    text = null,
                                    json = printJson,
                                    useDev = dev
                                )
                                res.fold(
                                    onSuccess = { "PRINT_JSON OK" },
                                    onFailure = { e -> "PRINT_JSON ERROR: ${e.message ?: "desconocido"}" }
                                )
                            }
                        }
                    }
                }

                // -------------------- PRINT IMAGE (P53) --------------------
                SectionCard(
                    title = "PRINT_IMAGE (P53)",
                    subtitle = "Convierte Base64 a archivo y manda P53."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = imageFileName,
                            onValueChange = { imageFileName = it },
                            label = { Text("FileName") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = p53Await, onCheckedChange = { p53Await = it })
                                Spacer(Modifier.width(8.dp))
                                Text("await", fontWeight = FontWeight.SemiBold)
                            }
                            Text("Recomendado: true", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(onClick = { imageAlign = LklPaySdk.ImageAlign.LEFT }, label = { Text("LEFT") })
                        AssistChip(onClick = { imageAlign = LklPaySdk.ImageAlign.CENTER }, label = { Text("CENTER") })
                        AssistChip(onClick = { imageAlign = LklPaySdk.ImageAlign.RIGHT }, label = { Text("RIGHT") })
                        Spacer(Modifier.weight(1f))
                        Text("Align: ${imageAlign.name}", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = imageBase64,
                        onValueChange = { imageBase64 = it },
                        label = { Text("Base64 (png/jpg). Puede incluir data:image/...") },
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    PrimaryActionButton(
                        label = "Imprimir imagen Base64 (P53)",
                        enabled = !isProcessing,
                        icon = Icons.Default.Image
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { isProcessing = it },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "PRINT_IMAGE_B64",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Imprimiendo imagen…"
                            ) {
                                if (!dev) requestWritePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

                                val cfg = LklPaySdk.PrintImageConfig(
                                    imageBase64 = imageBase64.trim(),
                                    fileName = imageFileName.trim().ifBlank { null },
                                    alignment = imageAlign,
                                    useDev = dev,
                                    await = p53Await
                                )

                                val res = LklPaySdk.printImage(context, cfg)
                                res.fold(
                                    onSuccess = { r ->
                                        buildString {
                                            appendLine("PRINT_IMAGE OK: ${r.ok}")
                                            appendLine("message: ${r.message}")
                                            appendLine("path: ${r.path}")
                                        }
                                    },
                                    onFailure = { e -> "PRINT_IMAGE ERROR: ${e.message ?: "desconocido"}" }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    HorizontalDivider()

                    Text("Atajo útil: imprimir QR", fontWeight = FontWeight.SemiBold)
                    Text("Genera un QR con https://paymentnexus.com.mx y lo imprime.", style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(10.dp))

                    PrimaryActionButton(
                        label = "Imprimir QR (paymentnexus.com.mx)",
                        enabled = !isProcessing,
                        icon = Icons.Default.Image
                    ) {
                        scope.launch {
                            safeAction(
                                setProcessing = { isProcessing = it },
                                setProcessingLabel = { processingLabel = it },
                                setOutput = { output = it },
                                setDialog = { showDialog = it },
                                onStatus = { ok, msg ->
                                    status = status.copy(
                                        lastAction = "PRINT_QR",
                                        lastActionAt = nowMs(),
                                        lastActionOk = ok,
                                        lastActionMessage = msg
                                    )
                                },
                                processingMsg = "Imprimiendo QR…"
                            ) {
                                if (!dev) requestWritePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

                                val qrB64 = qrBase64Png("https://paymentnexus.com.mx", 512)

                                val cfg = LklPaySdk.PrintImageConfig(
                                    imageBase64 = qrB64,
                                    fileName = "qr_paymentnexus_${System.currentTimeMillis()}.png",
                                    alignment = LklPaySdk.ImageAlign.CENTER,
                                    useDev = dev,
                                    await = p53Await
                                )

                                val res = LklPaySdk.printImage(context, cfg)
                                res.fold(
                                    onSuccess = { r ->
                                        buildString {
                                            appendLine("PRINT_QR OK: ${r.ok}")
                                            appendLine("message: ${r.message}")
                                            appendLine("path: ${r.path}")
                                        }
                                    },
                                    onFailure = { e -> "PRINT_QR ERROR: ${e.message ?: "desconocido"}" }
                                )
                            }
                        }
                    }
                }

                // -------------------- Último resultado --------------------
                Text("Último resultado", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Text(
                        text = output.ifBlank { "(sin resultados aún)" },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Resultado
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Resultado") },
                    text = { Text(output.ifBlank { "(sin resultados aún)" }) },
                    confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK") } },
                    properties = DialogProperties(dismissOnClickOutside = true)
                )
            }

            // Procesando (overlay con texto)
            if (isProcessing) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Procesando") },
                    text = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(processingLabel)
                            Spacer(Modifier.height(6.dp))
                            Text("No cierres esta pantalla…", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    properties = DialogProperties(dismissOnClickOutside = false)
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 *  UI Helpers
 * --------------------------------------------------------------------------------------------- */

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.defaultMinSize(minWidth = 220.dp, minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SdkStatusPanel(
    status: SdkStatus,
    onReload: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    val pillColor = if (status.ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = pillColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (status.ready) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = pillColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (status.ready) "SDK READY" else "SDK NOT READY",
                            fontWeight = FontWeight.SemiBold,
                            color = pillColor
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "MODE: ${status.mode}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.weight(1f))

                if (onReload != null) {
                    IconButton(onClick = onReload) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                }
            }

            HorizontalDivider()

            Text("SDK Status", style = MaterialTheme.typography.titleMedium)

            InfoRow("Last INIT", fmtTs(status.lastInitAt))
            InfoRow("Serial", status.serial ?: "—")
            InfoRow("ApiKey", maskApiKey(status.apiKey))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ✅ Quitados botones de copy
                if (onReset != null) {
                    AssistChip(
                        onClick = onReset,
                        label = { Text("Reset creds") }
                    )
                }
            }

            HorizontalDivider()

            Text("Última operación", fontWeight = FontWeight.SemiBold)
            InfoRow("Action", status.lastAction ?: "—")
            InfoRow("When", fmtTs(status.lastActionAt))
            InfoRow("OK", status.lastActionOk?.toString() ?: "—")
            InfoRow("Message", status.lastActionMessage ?: "—")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/* ---------------------------------------------------------------------------------------------
 *  Safe action runner (con overlay y mensaje)
 * --------------------------------------------------------------------------------------------- */

private suspend fun safeAction(
    setProcessing: (Boolean) -> Unit,
    setProcessingLabel: (String) -> Unit,
    setOutput: (String) -> Unit,
    setDialog: (Boolean) -> Unit,
    onStatus: (ok: Boolean, msg: String) -> Unit,
    processingMsg: String,
    timeoutMs: Long = 120_000L,
    block: suspend () -> String
) {
    setProcessingLabel(processingMsg)
    setProcessing(true)
    setOutput("")
    setDialog(false)

    try {
        val txt = withTimeout(timeoutMs) { block() }
        onStatus(true, "OK")
        setOutput(txt)
        setDialog(true)
    } catch (t: TimeoutCancellationException) {
        onStatus(false, "timeout")
        setOutput("ERROR: Timeout (${timeoutMs / 1000}s). Posible bloqueo en SDK vendor (Z10/Z11/C14).")
        setDialog(true)
    } catch (t: Throwable) {
        val msg = t.message ?: "desconocido"
        onStatus(false, msg)
        setOutput("ERROR: $msg")
        setDialog(true)
    } finally {
        setProcessing(false)
    }
}

/* ---------------------------------------------------------------------------------------------
 *  Result helpers
 * --------------------------------------------------------------------------------------------- */

private fun buildTxnResultText(prefix: String, r: LklPaySdk.TxnResult): String {
    val status = if (r.approved) "APPROVED" else "DECLINED"
    return buildString {
        appendLine("$prefix $status")
        appendLine("message: ${r.message}")
        appendLine("txnId: ${r.txnId}")
        appendLine("authCode: ${r.authCode}")
        appendLine("rrn: ${r.rrn}")
        appendLine("stan: ${r.stan}")
        appendLine("last4: ${r.last4}")
        appendLine("brand: ${r.brand}")
        appendLine("amountCents: ${r.amountCents}")
        if (!r.errorCode.isNullOrBlank()) appendLine("errorCode: ${r.errorCode}")
    }
}

/* ---------------------------------------------------------------------------------------------
 *  QR / Base64 helpers
 * --------------------------------------------------------------------------------------------- */

private fun samplePngBase64Tiny(): String {
    return "data:image/png;base64," +
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8" +
            "/w8AAn8B9p1Z0QAAAABJRU5ErkJggg=="
}

private fun qrBase64Png(url: String, size: Int = 512): String {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return "data:image/png;base64,$b64"
}
