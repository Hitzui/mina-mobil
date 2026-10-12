package com.jomiro.app.mina_mobil.screen.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

private val SyncTeal = Color(0xFF087E83)
private val SyncInk = Color(0xFF172C3D)
private val SyncMuted = Color(0xFF71808B)

@Composable
fun OfflineSyncScreen(
    userId: Long,
    upload: Boolean,
    onDownloadedChanged: (Boolean) -> Unit,
    onRunningChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OfflineSyncViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(userId, state.downloaded, state.message, state.error) { onDownloadedChanged(state.downloaded) }
    LaunchedEffect(state.running) { onRunningChanged(state.running) }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(if (upload) "Confirmar Upload" else "Confirmar descarga") },
            text = {
                Text(
                    if (upload) "Se enviarán los cambios pendientes. Al finalizar se borrará la copia local y deberá descargar los datos nuevamente. ¿Desea continuar?"
                    else "La descarga reemplazará todos los datos locales y eliminará los cambios pendientes que no se hayan subido. ¿Desea continuar?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    if (upload) viewModel.upload(userId) else viewModel.download(userId)
                }) { Text(if (upload) "Subir" else "Descargar", color = SyncTeal) }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Cancelar") } },
        )
    }

    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (upload) "Subir cambios" else "Descargar datos", color = SyncInk, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Text(
                if (upload) "Envía al sistema web las operaciones guardadas en este dispositivo. Al terminar, la copia local se limpia y deberá descargar los datos nuevamente."
                else "La descarga reemplazará los datos guardados en este dispositivo. Asegúrese de subir los cambios pendientes antes de continuar.",
                color = SyncMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Estado: ${if (state.downloaded) "datos descargados" else "sin descarga local"} · Cambios pendientes: ${state.pending}", color = SyncMuted, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showConfirmation = true },
                enabled = !state.running && (!upload || state.downloaded),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SyncTeal),
            ) {
                if (state.running) CircularProgressIndicator(modifier = Modifier.height(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (upload) "Upload" else "Descargar datos", fontWeight = FontWeight.SemiBold)
            }
            state.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = SyncTeal, fontSize = 14.sp)
            }
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(12.dp)) {
                    Text(it, modifier = Modifier.padding(14.dp), color = Color(0xFFB33A3A), fontSize = 13.sp)
                }
            }
        }
    }
}
