# MINA Móvil

Aplicación multiplataforma para el sistema del taller MINA, desarrollada con
Kotlin Multiplatform y Compose Multiplatform. Comparte interfaz y lógica entre
Android, iOS y escritorio (JVM).

## Tecnologías

- **Compose Multiplatform / Material 3** para la interfaz.
- **Koin** para inyección de dependencias y creación de ViewModels.
- **Ktor** para consumir la API Laravel versionada.
- **SQLDelight / SQLite** para conservar localmente la sesión autenticada.
- **Kotlin Coroutines y StateFlow** para estado y operaciones asíncronas.

## Funcionalidad disponible

- Inicio de sesión con correo y contraseña.
- Conservación del token, perfil, permisos y caja asignada en SQLite. La
  contraseña no se guarda.
- Cierre de sesión local y revocación del token en el servidor.
- Menú lateral hamburguesa con Inicio, Clientes y Órdenes de trabajo.
- Listado paginado de clientes, con teléfono, dirección y estado.
- Las respuestas y operaciones de clientes requieren el permiso
  `clientes.view` del usuario autenticado.

## Estructura principal

```text
shared/src/commonMain/kotlin/com/jomiro/app/mina_mobil/
├── data/                         # Repositorios y modelos de datos
├── di/                           # Módulos de Koin
├── login/                        # ViewModel de autenticación
├── network/                      # Ktor y configuración del API
└── screen/
    └── clientes/listado/         # Pantalla y ViewModel del listado

shared/src/commonMain/sqldelight/  # Esquema y consultas SQLite
androidApp/                        # Entrada y configuración Android
desktopApp/                        # Entrada de escritorio JVM
iosApp/                            # Proyecto de entrada iOS para Xcode
```

## Configurar la API

La URL base se configura en un solo lugar:

```text
shared/src/commonMain/kotlin/com/jomiro/app/mina_mobil/network/ApiConfig.kt
```

El valor local actual apunta a:

```text
http://localhost/mina/public/api/v1
```

En Android Emulator, la aplicación convierte automáticamente `localhost` en
`10.0.2.2` para acceder al servidor del equipo anfitrión. Escritorio e iOS
Simulator usan `localhost`. Para usar un teléfono físico, configura en
`API_BASE_URL` la dirección LAN del equipo servidor. Para producción, cambia
ese valor por la URL HTTPS del servidor.

El backend debe tener disponibles las rutas `/api/v1/auth/login` y
`/api/v1/clients`; para cargar clientes el usuario necesita `clientes.view`.
El contrato OpenAPI está en `docs/api-openapi-v1.yaml` del proyecto backend.

## Compilar y ejecutar

En Windows usa `gradlew.bat`; en macOS/Linux usa `./gradlew`.

```powershell
# APK Android de depuración
.\gradlew.bat :androidApp:assembleDebug

# Aplicación de escritorio
.\gradlew.bat :desktopApp:run

# Pruebas compartidas JVM
.\gradlew.bat :shared:jvmTest
```

El APK se genera en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Para iOS, abre `iosApp` con Xcode en macOS.

## Diagnóstico de API

Si falla la carga de clientes, la aplicación imprime una línea con el prefijo
`[MINA][Clientes]` en la consola de escritorio o en Logcat. Incluye URL, estado
HTTP, Content-Type y estructura de la respuesta, sin registrar los datos
personales de los clientes.
