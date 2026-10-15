# MINA Móvil

Cliente multiplataforma del sistema MINA para el seguimiento operativo y financiero de órdenes de trabajo. Comparte interfaz y lógica entre **Android, iOS y escritorio JVM** mediante Kotlin Multiplatform y Compose Multiplatform.

Backend y documentación del sistema: [Hitzui/mina](https://github.com/Hitzui/mina).

## Funcionalidad

- Autenticación con token, perfil, permisos y caja asignada; sesión conservada en SQLite sin guardar la contraseña.
- Inicio con indicadores, alertas y órdenes en seguimiento, con datos de API en Online y de la descarga local en Offline.
- Clientes, empleados y órdenes de trabajo con navegación al detalle y al resumen operativo y financiero.
- Registro y seguimiento de procesos, trabajos de empleados, equipos, materiales y costos según permisos.
- Consulta y gestión de ingresos, acuerdos y liquidaciones según permisos del usuario.
- Equipos con **horas efectivas**, electricidad, depreciación y recursos de almacén. Pantalla de trabajo efectivo, horómetro, entregas, consumos, devoluciones y costo por hora.
- Modos Online/Offline, descarga de datos, fecha de descarga y operaciones pendientes de sincronización.
- Inicialización y migración de SQLite en escritorio, conservando los datos locales entre aperturas.

## Online y Offline

| Operación | Disponibilidad |
|---|---|
| Inicio y consultas de datos descargados | Online / Offline, según permisos y contenido de la descarga |
| Registro de operaciones compatibles, incluidos usos de equipo y horas al asignar | Online / Offline; la cola se valida al sincronizar |
| Entregas, consumos y devoluciones de insumos de equipos | Online |
| Corrección de horas y horómetro de usos existentes | Online |

Los costos calculados Offline son **provisionales**. Al sincronizar, el servidor valida el periodo y calcula con su configuración. La estimación de combustible no se suma al consumo real: el costo definitivo de insumos proviene de los consumos registrados.

La guía funcional del costeo se encuentra en [docs/COSTEO_EQUIPOS.md del backend](https://github.com/Hitzui/mina/blob/main/docs/COSTEO_EQUIPOS.md).

## Tecnologías

- Kotlin Multiplatform y Compose Multiplatform / Material 3.
- Koin para dependencias y ViewModels.
- Ktor para la API versionada de Laravel.
- SQLDelight / SQLite para sesión, descarga local y cola de sincronización.
- Coroutines y StateFlow para operaciones asíncronas y estado.

Las versiones de las herramientas y bibliotecas se definen en `gradle/libs.versions.toml`.

## Requisitos

- JDK compatible con Gradle y Android Gradle Plugin del proyecto (JDK 17 o superior).
- Android SDK con la plataforma configurada en `gradle/libs.versions.toml` (actualmente API 37).
- `ANDROID_HOME` o `local.properties` con `sdk.dir` para compilar Android.
- macOS y Xcode para compilar y probar iOS.

## Configurar la API

La URL base se configura en:

```text
shared/src/commonMain/kotlin/com/jomiro/app/mina_mobil/network/ApiConfig.kt
```

La configuración actual usa un servidor de la red local:

```text
http://192.168.123.33/mina/public/api/v1
```

Ajuste `API_BASE_URL` al servidor que utilizará la instalación. Cuando se configura `http://localhost`, Android lo convierte en `http://10.0.2.2` para el emulador. Para un teléfono físico, utilice una dirección de red accesible desde ese teléfono; para producción, configure la URL HTTPS correspondiente.

El backend debe tener aplicadas las migraciones y las rutas API de la misma versión funcional del cliente. El acceso a cada operación depende de los permisos del usuario autenticado.

## Compilar y ejecutar

En Windows:

```powershell
# APK Android de depuración
.\gradlew.bat :androidApp:assembleDebug

# Aplicación de escritorio
.\gradlew.bat :desktopApp:run

# Pruebas compartidas y de SQLite en escritorio
.\gradlew.bat :shared:jvmTest :desktopApp:test

# Instalador Windows, con las herramientas de empaquetado disponibles
.\gradlew.bat :desktopApp:packageMsi
```

En macOS/Linux sustituya `.\gradlew.bat` por `./gradlew`. Para iOS, abra `iosApp/iosApp.xcodeproj` con Xcode y configure la firma de la aplicación.

El APK de depuración se genera en:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

La distribución Android de producción requiere preparar y firmar una versión release. Publicar el código en Git no actualiza automáticamente las aplicaciones instaladas ni el backend.

## Estructura

```text
shared/src/commonMain/kotlin/com/jomiro/app/mina_mobil/
├── data/                 Repositorios, modelos, sesión y sincronización
├── di/                   Configuración de Koin
├── login/                Autenticación
├── network/              Cliente Ktor y URL de API
└── screen/               Inicio, clientes, empleados y órdenes de trabajo

shared/src/commonMain/sqldelight/  Esquema, consultas y migraciones SQLite
shared/src/jvmTest/                Pruebas compartidas en JVM
androidApp/                       Entrada Android
desktopApp/                       Entrada, base local y empaquetado JVM
iosApp/                           Proyecto iOS para Xcode
```

## Estado de las plataformas

La funcionalidad operativa se implementa en el módulo compartido. Las pruebas JVM verifican lógica compartida y funcionamiento local; la compilación Android verifica el APK. La validación de iOS requiere macOS/Xcode y la de una versión distribuible incluye firma, instalación y conexión con el servidor de destino.
