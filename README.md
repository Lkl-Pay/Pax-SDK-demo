# LKLPay PAX SDK · Demo App

Este repositorio contiene una aplicación **demo** para probar e integrar el **LKLPay PAX SDK** en dispositivos PAX (por ejemplo, A920).  
El objetivo es mostrar, de forma sencilla, cómo consumir el SDK desde una app Android usando Kotlin y Jetpack Compose.

---

## Características del demo

La app permite probar los principales flujos del SDK:

- `INIT`  
- `DO_SALE` (venta)  
- `DO_CANCEL` (cancelación)  
- `DO_REFUND` (devolución)  
- `PRINT` (impresión simple)

Además, incluye:

- Modo **DEV** (simulado, sin cobros reales).
- Modo **REAL** (usa el flujo completo: sesión, llaves, backend, dispositivo).
- UI simple en Jetpack Compose para:
  - Configurar parámetros (monto, referencia, TxnId original, texto a imprimir).
  - Ejecutar acciones y ver el resultado en pantalla.

---

## Requisitos

- **Android Studio** (Iguana o superior recomendado).
- **Gradle** con soporte para Kotlin DSL.
- **Kotlin** 1.9+.
- Dispositivo PAX compatible (ej. **PAX A920**) para pruebas en modo REAL.
- Acceso al **Maven privado** donde se publica el AAR del SDK.

---

## Configuración del SDK

### 1. Repositorio Maven privado

En tu proyecto (a nivel `settings.gradle` o `build.gradle` raíz), asegúrate de tener configurado el repositorio Maven privado donde vive el AAR del SDK:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://<tu-maven-privado>/repository/maven-releases/")
            credentials {
                username = "TU_USER"
                password = "TU_PASSWORD"
            }
        }
    }
}
````

> Ajusta la URL y credenciales a tu entorno real.

### 2. Dependencia del SDK

En el módulo de la app demo (por defecto `app/build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.lklpay.pax:sdk:<VERSION>")
}
```

Reemplaza `<VERSION>` por la versión actual del SDK (ej. `1.0.0`).

---

## Estructura del proyecto

* `app/src/main/java/com/lklpay/sdkdemo/MainActivity.kt`
  Contiene la pantalla principal del demo y la integración directa con `LklPaySdk`.

* Dependencias principales:

    * `com.lklpay.pax.sdk.LklPaySdk`
    * Jetpack Compose (Material 3)
    * Kotlin Coroutines (para manejo de llamados `suspend` del SDK)

---

## Flujo típico en el demo

1. **Abrir la app demo** en el dispositivo.
2. En la sección **Configuración**:

    * Activar o desactivar el checkbox **Modo DEV**.
    * Ingresar `Master PIN` (ej. `123456`).
    * Definir `Amount (cents)` (ej. `100` para $1.00).
    * Definir `Reference` (opcional).
    * Definir `Original TxnId` para probar `CANCEL` o `REFUND`.
    * Definir el `Print text` para el flujo de impresión.
3. Usar los botones en la sección **Acciones**:

    * `INIT`
    * `DO_SALE`
    * `DO_CANCEL`
    * `DO_REFUND`
    * `PRINT`
4. Ver el resultado en:

    * El card de **"Último resultado"**.
    * El diálogo de resultado que se abre al finalizar cada acción.

---

## Modo DEV vs Modo REAL

### Modo DEV

* No se hacen llamadas reales al SDK del dispositivo ni a la pasarela real.
* Los flujos (`INIT`, `SALE`, `CANCEL`, `REFUND`, `PRINT`) se simulan.
* El SDK genera datos falsos:

    * Serial genérico (ej. `1234567890`).
    * Tokens y apiKey simulados.
    * Transacciones aprobadas con códigos fake.

Ideal para:

* Probar la integración del SDK.
* Validar flujos en UI sin necesidad de conexión a backend o llaves reales.

### Modo REAL

* Ejecuta el flujo real del SDK:

    * Inicialización del `DeviceController`.
    * Envío de comandos (`Z10`, `Z11`, `C14`, `C51`, `C54`, etc.).
    * Sincronización de llaves con el backend.
    * Uso de `SessionManager` y `KeySyncManager`.
    * Llamadas reales a `TransactionsBridge`.

Requiere:

* Dispositivo PAX correctamente provisionado.
* Backend y llaves configuradas.
* Acceso a la red adecuada.

---

## Uso del SDK (ejemplos de código)

A continuación se muestran ejemplos simplificados basados en el código del demo.

### INIT

```kotlin
val cfg = LklPaySdk.InitConfig(
    useDev = dev,               // true = modo DEV, false = REAL
    masterPin = masterPin.trim()
)

val result = LklPaySdk.init(context, cfg)
result.fold(
    onSuccess = { r ->
        // Ejemplo de uso del resultado
        // r.devMode, r.serial, r.apiKey, r.message
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

### SALE

```kotlin
val amountCents = 100L // $1.00

val metadataJson = JSONObject(
    mapOf(
        "source" to "sdk-demo",
        "ts" to System.currentTimeMillis()
    )
).toString()

val cfg = LklPaySdk.SaleConfig(
    amountCents = amountCents,
    reference = "REF-12345",
    metadataJson = metadataJson,
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.sale(context, cfg)
result.fold(
    onSuccess = { txn ->
        // txn.approved, txn.message, txn.txnId, txn.authCode, etc.
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

### CANCEL

```kotlin
val cfg = LklPaySdk.CancelConfig(
    idTransaction = originalTxnId.trim(),
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.cancel(context, cfg)
result.fold(
    onSuccess = { txn ->
        // Resultado de la cancelación
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

### REFUND

```kotlin
val cfg = LklPaySdk.RefundConfig(
    idTransaction = originalTxnId.trim(),
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.refund(context, cfg)
result.fold(
    onSuccess = { txn ->
        // Resultado de la devolución
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

### PRINT

```kotlin
val printResult = LklPaySdk.print(
    context = context,
    text = "Ticket de prueba\nLklPay SDK",
    json = null,
    useDev = dev
)

printResult.fold(
    onSuccess = {
        // PRINT OK
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

---

## Manejo de errores

Todas las funciones `suspend` del SDK devuelven un `Result<T>`:

* En caso de éxito → `onSuccess(result: T)`
* En caso de error → `onFailure(error: Throwable)`

Ejemplo genérico:

```kotlin
val res = LklPaySdk.sale(context, cfg)

res.fold(
    onSuccess = { txn ->
        // Lógica de éxito
    },
    onFailure = { e ->
        // e.message, e::class.simpleName, etc.
    }
)
```

---

## Notas y buenas prácticas

* Se recomienda ejecutar **INIT** al inicio de la sesión o cuando la app se abra por primera vez.
* En producción, usar siempre `useDev = false`.
* Validar siempre `amountCents > 0` antes de llamar a `sale`.
* Guardar y reutilizar `txnId` para permitir operaciones de `CANCEL` y `REFUND`.
* El modo DEV está pensado solo para pruebas, nunca para entornos productivos.

---

## Licencia

Este demo y el SDK asociado son propiedad de **LKLPay**.
Su uso está restringido a integraciones autorizadas y/o entornos de prueba acordados con LKLPay.
