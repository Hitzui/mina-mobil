package com.jomiro.app.mina_mobil.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.data.ConnectionMode

private val SettingsTeal = Color(0xFF087E83)
private val SettingsInk = Color(0xFF172C3D)
private val SettingsMuted = Color(0xFF71808B)

@Composable
fun ConnectionSettingsScreen(
    mode: ConnectionMode,
    hasOfflineSnapshot: Boolean,
    pendingOperations: Long,
    onSelectMode: (ConnectionMode) -> String?,
    onOpenDownload: () -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Configuración", color = SettingsInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Elige cómo quieres trabajar en este dispositivo.", color = SettingsMuted, fontSize = 14.sp)

        ModeCard(
            title = "Online",
            description = "Consulta y guarda directamente en el servidor. Necesita conexión para cada operación; al activarlo se descarta la copia local para evitar datos desactualizados.",
            selected = mode == ConnectionMode.ONLINE,
            enabled = mode == ConnectionMode.ONLINE || pendingOperations == 0L,
            onClick = {
                error = onSelectMode(ConnectionMode.ONLINE)
            },
        )
        ModeCard(
            title = "Offline",
            description = "Trabaja con datos descargados. Los cambios quedan guardados en este dispositivo hasta que los subas.",
            selected = mode == ConnectionMode.OFFLINE,
            enabled = mode != ConnectionMode.OFFLINE,
            onClick = {
                error = onSelectMode(ConnectionMode.OFFLINE)
            },
        )

        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado de trabajo", color = SettingsInk, fontWeight = FontWeight.SemiBold)
                Text("Modo actual: ${if (mode == ConnectionMode.ONLINE) "Online" else "Offline"}", color = SettingsMuted)
                Text("Datos locales: ${if (hasOfflineSnapshot) "descargados" else "no descargados"}", color = SettingsMuted)
                Text("Cambios offline pendientes: $pendingOperations", color = SettingsMuted)
                if (mode == ConnectionMode.OFFLINE && !hasOfflineSnapshot) {
                    Spacer(Modifier.height(4.dp))
                    Text("Para trabajar Offline, primero descarga los datos necesarios.", color = SettingsMuted, fontSize = 13.sp)
                    Button(onClick = onOpenDownload, colors = ButtonDefaults.buttonColors(containerColor = SettingsTeal)) {
                        Text("Ir a descargar datos")
                    }
                }
                if (pendingOperations > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Sube los cambios pendientes antes de cambiar a Online.", color = SettingsMuted, fontSize = 13.sp)
                    Button(onClick = onOpenUpload, colors = ButtonDefaults.buttonColors(containerColor = SettingsTeal)) {
                        Text("Ir a subir cambios")
                    }
                }
            }
        }
        error?.let {
            Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(12.dp)) {
                Text(it, modifier = Modifier.padding(14.dp), color = Color(0xFFB33A3A), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFEAF5F5) else Color.White,
            disabledContainerColor = Color(0xFFF4F5F6),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold)
                Text(description, color = SettingsMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
