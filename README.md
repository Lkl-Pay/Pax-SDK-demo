
# LKLPay PAX SDK · Demo App

Este repositorio contiene una aplicación **demo** para probar e integrar el **LKLPay PAX SDK** en dispositivos **PAX** (por ejemplo, **A920**).  
El objetivo es mostrar, de forma sencilla y directa, cómo consumir el SDK desde una app Android usando **Kotlin** y **Jetpack Compose**.

---

## Objetivo del demo

- Mostrar cómo consumir el SDK desde Android sin lógica adicional.
- Permitir pruebas rápidas de los flujos principales.
- Facilitar debug y validación en modo **DEV** y **REAL**.
- Servir como base de referencia para integraciones reales.

---

## Características del demo

La app permite probar los principales flujos del SDK:

- `INIT`
- `SALE` (venta)
- `CANCEL` (cancelación)
- `REFUND` (devolución)
- `PRINT_TEXT` (P52 – texto / JSON)
- `PRINT_IMAGE` (P53 – imagen Base64 / QR)

Además, incluye:

- Modo **DEV** (simulado, sin cobros reales).
- Modo **REAL** (flujo completo: sesión, llaves, backend y dispositivo).
- Panel de **SDK Status**:
  - Modo actual (DEV / REAL)
  - Estado `READY / NOT READY`
  - Serial
  - ApiKey (enmascarada)
  - Última acción ejecutada
- Botón **Reset creds**:
  - Limpia completamente `SharedPreferences`
  - Equivalente al reset usado en el test de `INIT`
  - Evita errores comunes como `401` por tokens viejos
- UI en **Jetpack Compose** con feedback visual:
  - Loader bloqueante durante operaciones
  - Timeout controlado
  - Resultado detallado por acción

---

## Requisitos

- **Android Studio** (Iguana o superior recomendado).
- **Gradle** con soporte para Kotlin DSL.
- **Kotlin** 1.9+.
- Dispositivo **PAX compatible** (ej. **PAX A920**) para pruebas en modo REAL.
- Acceso al **Maven privado** donde se publica el AAR del SDK.

---

## Configuración del SDK

### 1. Repositorio Maven privado

En tu proyecto (a nivel `settings.gradle` o `build.gradle` raíz), configura el repositorio Maven privado donde se publica el SDK:

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

> Ajusta la URL y credenciales según tu entorno.

---

### 2. Dependencia del SDK

En el módulo de la app demo (`app/build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.lklpay.pax:sdk:<VERSION>")
}
```

Ejemplo:

```kotlin
implementation("com.lklpay.pax:sdk:1.0.8")
```

o versión de desarrollo:

```kotlin
implementation("com.lklpay.pax:sdk:1.0.8-dev")
```

---

## Estructura del proyecto

```
app/
 └─ src/main/java/com/lklpay/sdkdemo/
    └─ MainActivity.kt
```

**MainActivity.kt** contiene:

* La UI del demo (Jetpack Compose)
* Llamadas directas a `LklPaySdk`
* Manejo de estado, loading y resultados
* Reset de credenciales
* Ejemplos reales de uso del SDK

Dependencias principales:

* `com.lklpay.pax.sdk.LklPaySdk`
* Jetpack Compose (Material 3)
* Kotlin Coroutines (manejo de funciones `suspend`)

---

## Flujo típico en el demo

1. Abrir la app demo en el dispositivo.
2. En **Configuración global**:

    * Activar o desactivar **Modo DEV**
    * Ingresar **Master PIN** (ej. `123456`)
3. Ejecutar **INIT** (recomendado siempre al inicio).
4. Probar:

    * `SALE` ingresando monto y referencia
    * `CANCEL` o `REFUND` usando un `txnId` previo
    * `PRINT_TEXT` o `PRINT_IMAGE`
5. Revisar el resultado en:

    * Panel **Último resultado**
    * Diálogo de resultado
    * Panel **SDK Status**

---

## Modo DEV vs Modo REAL

### Modo DEV

* No se hacen llamadas reales al SDK del dispositivo.
* No hay llamadas reales al backend.
* El SDK **simula**:

    * Serial genérico (ej. `1234567890`)
    * Tokens y ApiKey falsos
    * Transacciones aprobadas con datos simulados

Ideal para:

* QA sin hardware.
* Desarrollo de UI.
* Validar flujos sin llaves ni backend.

---

### Modo REAL

* Ejecuta el flujo completo del SDK:

    * Inicialización del dispositivo
    * Envío de comandos internos PAX (ej. `Z10`, `Z11`, `C14`, `C51`, `C54`)
    * Sincronización de llaves con el backend
    * Manejo de sesión
    * Transacciones reales

Requiere:

* Dispositivo PAX correctamente provisionado.
* Backend y llaves configuradas.
* Acceso a red adecuado.

---

## Uso del SDK (ejemplos)

### INIT

```kotlin
val cfg = LklPaySdk.InitConfig(
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.init(context, cfg)

result.fold(
    onSuccess = { r ->
        // r.devMode, r.serial, r.apiKey, r.message
    },
    onFailure = { e ->
        // Manejo de error
    }
)
```

---

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
```

---

### CANCEL

```kotlin
val cfg = LklPaySdk.CancelConfig(
    idTransaction = originalTxnId.trim(),
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.cancel(context, cfg)
```

---

### REFUND

```kotlin
val cfg = LklPaySdk.RefundConfig(
    idTransaction = originalTxnId.trim(),
    useDev = dev,
    masterPin = masterPin.trim()
)

val result = LklPaySdk.refund(context, cfg)
```

---

### PRINT (P52)

```kotlin
val printResult = LklPaySdk.print(
    context = context,
    text = "Ticket de prueba\nLKLPay SDK",
    json = null,
    useDev = dev
)
```

---

### PRINT IMAGE / QR (P53)

```kotlin
val cfg = LklPaySdk.PrintImageConfig(
    imageBase64 = base64Png,
    fileName = "qr.png",
    alignment = LklPaySdk.ImageAlign.CENTER,
    useDev = dev,
    await = true
)

LklPaySdk.printImage(context, cfg)
```

---

## Manejo de errores

Todas las funciones del SDK retornan `Result<T>`:

```kotlin
val res = LklPaySdk.sale(context, cfg)

res.fold(
    onSuccess = { txn ->
        // Éxito
    },
    onFailure = { e ->
        // e.message, e::class.simpleName
    }
)
```

La demo además maneja:

* Timeout explícito
* Bloqueo de UI durante operaciones
* Reset de estado con **Reset creds**

---

## Notas y buenas prácticas

* Ejecutar **INIT** al iniciar la app o sesión.
* Usar **Reset creds** si hay errores de autenticación.
* En producción, usar siempre `useDev = false`.
* Validar siempre `amountCents > 0` antes de `SALE`.
* Guardar el `txnId` para `CANCEL` y `REFUND`.
* El modo DEV es solo para pruebas, nunca para producción.

---

## Licencia

Este demo y el **LKLPay PAX SDK** son propiedad de **LKLPay**.
Su uso está restringido a integraciones autorizadas y/o entornos de prueba acordados con LKLPay.

