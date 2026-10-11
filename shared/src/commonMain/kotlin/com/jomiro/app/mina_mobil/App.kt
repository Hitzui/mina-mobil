package com.jomiro.app.mina_mobil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jomiro.app.mina_mobil.login.LoginViewModel
import com.jomiro.app.mina_mobil.screen.clientes.listado.ClientesListadoScreen
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch

private val Ink = Color(0xFF172C3D)
private val Teal = Color(0xFF087E83)
private val Canvas = Color(0xFFF5F7F8)
private val Muted = Color(0xFF71808B)
private val Line = Color(0xFFE1E7E9)

@Composable
fun App() {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Teal, onPrimary = Color.White)) {
        val viewModel: LoginViewModel = koinViewModel()
        val state by viewModel.state.collectAsState()
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            val session = state.session
            if (session != null) {
                SignedInScreen(
                    userId = session.userId,
                    name = session.name,
                    email = session.email,
                    cashRegister = session.cashRegisterName,
                    onLogout = viewModel::logout,
                )
            } else {
                LoginScreen(
                    email = state.email,
                    password = state.password,
                    error = state.error,
                    isLoading = state.isLoading,
                    onEmailChange = viewModel::emailChanged,
                    onPasswordChange = viewModel::passwordChanged,
                    onLogin = viewModel::login,
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    email: String,
    password: String,
    error: String?,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(66.dp))
        BrandMark()
        Spacer(Modifier.height(22.dp))
        Text("MINA", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Spacer(Modifier.height(7.dp))
        Text("GESTIÓN DEL TALLER", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        Spacer(Modifier.height(46.dp))

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Bienvenido", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text("Inicia sesión para continuar", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(29.dp))

            Text("Usuario (correo electrónico)", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("nombre@taller.com", color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = loginFieldColors(),
            )
            Spacer(Modifier.height(19.dp))
            Text("Contraseña", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            var showPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ingresa tu contraseña", color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Ocultar" else "Ver", color = Teal, fontSize = 12.sp)
                    }
                },
                colors = loginFieldColors(),
            )
            Spacer(Modifier.height(16.dp))
            if (error != null) {
                Text(error, color = Color(0xFFB33A3A), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = onLogin,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, disabledContainerColor = Teal.copy(alpha = 0.6f)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Iniciar sesión", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Acceso seguro", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text("•", color = Teal, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text("Tu caja de trabajo se asigna automáticamente", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier.size(74.dp).background(Teal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("M", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
    }
}

private enum class AppSection(val title: String, val icon: String) {
    HOME("Inicio", "⌂"),
    CLIENTS("Clientes", "♙"),
    WORK_ORDERS("Órdenes de trabajo", "▤"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInScreen(userId: Long, name: String, email: String, cashRegister: String, onLogout: () -> Unit) {
    var selectedSection by remember { mutableStateOf(AppSection.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                DrawerHeader(name = name, email = email, cashRegister = cashRegister)
                HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                AppSection.entries.forEach { section ->
                    NavigationDrawerItem(
                        label = { Text(section.title) },
                        selected = selectedSection == section,
                        onClick = {
                            selectedSection = section
                            coroutineScope.launch { drawerState.close() }
                        },
                        icon = { Text(section.icon, color = if (selectedSection == section) Teal else Muted, fontSize = 20.sp) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onLogout()
                    },
                    icon = { Text("↪", color = Muted, fontSize = 20.sp) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Canvas,
            topBar = {
                TopAppBar(
                    title = {
                        Text(selectedSection.title, color = Ink, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Text("☰", color = Ink, fontSize = 23.sp)
                        }
                    },
                    actions = {
                        // Reservado para acciones contextuales que se definirán por módulo.
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
            },
        ) { contentPadding ->
            when (selectedSection) {
                AppSection.HOME -> HomeContent(
                    name = name,
                    email = email,
                    cashRegister = cashRegister,
                    modifier = Modifier.padding(contentPadding),
                )
                AppSection.CLIENTS -> ClientesListadoScreen(
                    userId = userId,
                    modifier = Modifier.padding(contentPadding),
                )
                AppSection.WORK_ORDERS -> ModulePlaceholder(
                    title = "Órdenes de trabajo",
                    description = "Aquí se mostrarán las órdenes de trabajo del taller.",
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun DrawerHeader(name: String, email: String, cashRegister: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Canvas).padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(17.dp))
        Text(name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(email, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(13.dp))
        Text("Caja: $cashRegister", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeContent(name: String, email: String, cashRegister: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
    ) {
        Text("Hola, $name", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(email, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("CAJA ASIGNADA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                Text(cashRegister, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ModulePlaceholder(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text(description, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal,
    unfocusedBorderColor = Line,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = Teal,
)
