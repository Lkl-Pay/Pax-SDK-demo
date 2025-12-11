package com.lklpay.sdkdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.lklpay.pax.sdk.LklPaySdk


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SdkDemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SdkDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estado de configuración
    var dev by remember { mutableStateOf(false) }
    var masterPin by remember { mutableStateOf("123456") }
    var amount by remember { mutableStateOf("1") }
    var reference by remember { mutableStateOf("REF-${System.currentTimeMillis()}") }
    var originalTxnId by remember { mutableStateOf("TXN-123456") }
    var printText by remember { mutableStateOf("Ticket de prueba\nLklPay SDK") }

    // Estado de UI
    var output by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    @Suppress("UNUSED_CHANGED_VALUE")
    var showDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "LKLPay PAX SDK Demo",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "SDK Tester",
                            style = MaterialTheme.typography.bodySmall
                        )
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
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.Start
            ) {

                // ---------------------------
                // Card de configuración
                // ---------------------------
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Configuración",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = dev,
                                onCheckedChange = { dev = it }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Modo DEV (simulado, sin cobro real)")
                        }

                        OutlinedTextField(
                            value = masterPin,
                            onValueChange = { masterPin = it },
                            label = { Text("Master PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Amount (cents)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                            OutlinedTextField(
                                value = reference,
                                onValueChange = { reference = it },
                                label = { Text("Reference") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = originalTxnId,
                            onValueChange = { originalTxnId = it },
                            label = { Text("Original TxnId (cancel/refund)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = printText,
                            onValueChange = { printText = it },
                            label = { Text("Print text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------------------------
                // Card de acciones
                // ---------------------------
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Acciones",
                            style = MaterialTheme.typography.titleMedium
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // INIT
                            ActionButton(
                                label = "INIT",
                                enabled = !isProcessing,
                                icon = Icons.Default.Refresh
                            ) {
                                scope.launch {
                                    isProcessing = true
                                    output = ""
                                    showDialog = false

                                    val cfg = LklPaySdk.InitConfig(
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.init(context, cfg)
                                    output = res.fold(
                                        onSuccess = { r ->
                                            buildString {
                                                appendLine("INIT OK")
                                                appendLine("devMode: ${r.devMode}")
                                                appendLine("serial: ${r.serial}")
                                                appendLine("apiKey: ${r.apiKey}")
                                                appendLine("message: ${r.message}")
                                            }
                                        },
                                        onFailure = { e ->
                                            "INIT ERROR: ${e.message ?: "desconocido"}"
                                        }
                                    )

                                    isProcessing = false
                                    showDialog = true
                                }
                            }

                            // SALE
                            ActionButton(
                                label = "DO_SALE",
                                enabled = !isProcessing,
                                icon = Icons.Default.ShoppingCart
                            ) {
                                scope.launch {
                                    val amt = amount.toLongOrNull() ?: 0L
                                    if (amt <= 0) {
                                        output = "Monto inválido"
                                        showDialog = true
                                        return@launch
                                    }

                                    isProcessing = true
                                    output = ""
                                    showDialog = false

                                    val metaJson = JSONObject(
                                        mapOf(
                                            "source" to "sdk-demo",
                                            "ts" to System.currentTimeMillis()
                                        )
                                    ).toString()

                                    val cfg = LklPaySdk.SaleConfig(
                                        amountCents = amt,
                                        reference = reference.trim().ifBlank { null },
                                        metadataJson = metaJson,
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.sale(context, cfg)
                                    output = res.fold(
                                        onSuccess = { txn ->
                                            buildTxnResultText("SALE", txn)
                                        },
                                        onFailure = { e ->
                                            "SALE ERROR: ${e.message ?: "desconocido"}"
                                        }
                                    )

                                    isProcessing = false
                                    showDialog = true
                                }
                            }

                            // CANCEL
                            ActionButton(
                                label = "DO_CANCEL",
                                enabled = !isProcessing,
                                icon = Icons.Default.Refresh
                            ) {
                                scope.launch {
                                    if (originalTxnId.isBlank()) {
                                        output = "DO_CANCEL: falta Original TxnId"
                                        showDialog = true
                                        return@launch
                                    }

                                    isProcessing = true
                                    output = ""
                                    showDialog = false

                                    val cfg = LklPaySdk.CancelConfig(
                                        idTransaction = originalTxnId.trim(),
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.cancel(context, cfg)
                                    output = res.fold(
                                        onSuccess = { txn ->
                                            buildTxnResultText("CANCEL", txn)
                                        },
                                        onFailure = { e ->
                                            "CANCEL ERROR: ${e.message ?: "desconocido"}"
                                        }
                                    )

                                    isProcessing = false
                                    showDialog = true
                                }
                            }

                            // REFUND
                            ActionButton(
                                label = "DO_REFUND",
                                enabled = !isProcessing,
                                icon = Icons.Default.Refresh
                            ) {
                                scope.launch {
                                    if (originalTxnId.isBlank()) {
                                        output = "DO_REFUND: falta Original TxnId"
                                        showDialog = true
                                        return@launch
                                    }

                                    isProcessing = true
                                    output = ""
                                    showDialog = false

                                    val cfg = LklPaySdk.RefundConfig(
                                        idTransaction = originalTxnId.trim(),
                                        useDev = dev,
                                        masterPin = masterPin.trim()
                                    )

                                    val res = LklPaySdk.refund(context, cfg)
                                    output = res.fold(
                                        onSuccess = { txn ->
                                            buildTxnResultText("REFUND", txn)
                                        },
                                        onFailure = { e ->
                                            "REFUND ERROR: ${e.message ?: "desconocido"}"
                                        }
                                    )

                                    isProcessing = false
                                    showDialog = true
                                }
                            }

                            // PRINT
                            ActionButton(
                                label = "PRINT",
                                enabled = !isProcessing,
                                icon = Icons.AutoMirrored.Filled.List
                            ) {
                                scope.launch {
                                    isProcessing = true
                                    output = ""
                                    showDialog = false

                                    val res = LklPaySdk.print(
                                        context = context,
                                        text = printText,
                                        json = null,
                                        useDev = dev
                                    )

                                    output = res.fold(
                                        onSuccess = { "PRINT OK" },
                                        onFailure = { e ->
                                            "PRINT ERROR: ${e.message ?: "desconocido"}"
                                        }
                                    )

                                    isProcessing = false
                                    showDialog = true
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------------------------
                // Resultado
                // ---------------------------
                Text(
                    "Último resultado",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = output.ifBlank { "(sin resultados aún)" },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }


            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Resultado") },
                    text = { Text(output.ifBlank { "(sin resultados aún)" }) },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("OK")
                        }
                    },
                    properties = DialogProperties(dismissOnClickOutside = true)
                )
            }

            if (isProcessing) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Procesando en el SDK") },
                    text = {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("No cierres esta pantalla…")
                        }
                    },
                    properties = DialogProperties(dismissOnClickOutside = false)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .defaultMinSize(minWidth = 140.dp, minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
        if (!r.errorCode.isNullOrBlank()) {
            appendLine("errorCode: ${r.errorCode}")
        }
    }
}
