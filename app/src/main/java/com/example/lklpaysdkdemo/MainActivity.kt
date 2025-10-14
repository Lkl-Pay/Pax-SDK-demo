package com.example.lklpaysdkdemo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis
import com.lklpay.sdk.LklPaySdk

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LklPayDemo"
    }

    // Mejor como propiedad de la Activity, para poder loguear su creación
    private lateinit var sdk: LklPaySdk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate() - Iniciando MainActivity en hilo=${Thread.currentThread().name}")

        try {
            sdk = LklPaySdk.create(applicationContext)
            Log.i(TAG, "SDK creado correctamente: ${sdk::class.java.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Error creando SDK", t)
            Toast.makeText(this, "Error creando SDK: ${t.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            DemoScreen()
        }
    }

    @Composable
    private fun DemoScreen() {
        MaterialTheme {
            val scope = rememberCoroutineScope()

            // Capturamos excepciones de coroutines y las logueamos
            val handler = remember {
                CoroutineExceptionHandler { _, e ->
                    Log.e(TAG, "Excepción no atrapada en coroutine", e)
                }
            }

            var masterPin by remember { mutableStateOf("1234") }
            var amount by remember { mutableStateOf("500") } // minor units
            var orderId by remember { mutableStateOf("12345") }
            var txnId by remember { mutableStateOf("74555465286493439313306") }
            var output by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var showDialog by remember { mutableStateOf(false) }

            fun show(result: String) {
                output = result
                showDialog = true
            }

            // Helper para loguear entrada/salida, duración y excepciones
            suspend fun <T> runLogged(
                opName: String,
                block: suspend () -> T
            ): T {
                Log.d(TAG, ">>>>>>>> $opName: INICIO " +
                        "(hilo=${Thread.currentThread().name}) " +
                        "inputs={ masterPin=$masterPin, amount=$amount, orderId=$orderId, txnId=$txnId }")
                var result: T? = null
                val elapsed = measureTimeMillis {
                    result = block()
                }
                Log.d(TAG, "<<<<<<<< $opName: FIN (${elapsed}ms) result=$result")
                return result as T
            }

            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LklPay SDK Demo", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = masterPin,
                        onValueChange = { masterPin = it },
                        label = { Text("Master PIN (demo)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (minor units)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = orderId,
                        onValueChange = { orderId = it },
                        label = { Text("orderId (metadata)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = txnId,
                        onValueChange = { txnId = it },
                        label = { Text("txnId (cancel/refund)") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(enabled = !loading, onClick = {
                            scope.launch(handler) {
                                loading = true
                                try {
                                    val json = runLogged("Init") {
                                        // Validaciones rápidas
                                        if (!::sdk.isInitialized) {
                                            error("SDK no inicializado")
                                        }
                                        sdk.init(masterPin).also {
                                            Log.v(TAG, "Init -> JSON:\n$it")
                                        }
                                    }
                                    show(json)
                                } catch (t: Throwable) {
                                    val err = """{"op":"init","error":"${t.message}"}"""
                                    Log.e(TAG, "Init falló: $err\n${t.stackTraceToString()}")
                                    show(err)
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text("Init") }

                        Button(enabled = !loading, onClick = {
                            scope.launch(handler) {
                                loading = true
                                try {
                                    val json = runLogged("DoSale") {
                                        if (!::sdk.isInitialized) {
                                            error("SDK no inicializado")
                                        }
                                        val meta = mapOf("orderId" to orderId)
                                        Log.d(TAG, "DoSale: amount=$amount meta=$meta")
                                        sdk.doSale(
                                            amountMinorUnits = amount,
                                            metadata = meta
                                        ).also {
                                            Log.v(TAG, "DoSale -> JSON:\n$it")
                                        }
                                    }
                                    show(json)
                                } catch (t: Throwable) {
                                    val err = """{"op":"doSale","error":"${t.message}"}"""
                                    Log.e(TAG, "DoSale falló: $err\n${t.stackTraceToString()}")
                                    show(err)
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text("DoSale") }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(enabled = !loading, onClick = {
                            scope.launch(handler) {
                                loading = true
                                try {
                                    val json = runLogged("Cancel") {
                                        if (!::sdk.isInitialized) {
                                            error("SDK no inicializado")
                                        }
                                        Log.d(TAG, "Cancel: txnId=$txnId")
                                        sdk.cancel(txnId).also {
                                            Log.v(TAG, "Cancel -> JSON:\n$it")
                                        }
                                    }
                                    show(json)
                                } catch (t: Throwable) {
                                    val err = """{"op":"cancel","error":"${t.message}"}"""
                                    Log.e(TAG, "Cancel falló: $err\n${t.stackTraceToString()}")
                                    show(err)
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text("Cancel") }

                        Button(enabled = !loading, onClick = {
                            scope.launch(handler) {
                                loading = true
                                try {
                                    val json = runLogged("Refund") {
                                        if (!::sdk.isInitialized) {
                                            error("SDK no inicializado")
                                        }
                                        Log.d(TAG, "Refund: txnId=$txnId")
                                        sdk.refund(txnId).also {
                                            Log.v(TAG, "Refund -> JSON:\n$it")
                                        }
                                    }
                                    show(json)
                                } catch (t: Throwable) {
                                    val err = """{"op":"refund","error":"${t.message}"}"""
                                    Log.e(TAG, "Refund falló: $err\n${t.stackTraceToString()}")
                                    show(err)
                                } finally {
                                    loading = false
                                }
                            }
                        }) { Text("Refund") }
                    }

                    if (loading) {
                        Spacer(Modifier.height(20.dp))
                        CircularProgressIndicator()
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("Output (JSON):", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (output.isBlank()) "(sin resultados aún)" else output,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Resultado") },
                        text = { Text(output) },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) { Text("OK") }
                        },
                        properties = DialogProperties(dismissOnClickOutside = true)
                    )
                }
            }
        }
    }
}
