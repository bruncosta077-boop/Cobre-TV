package app.cobre.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Copper = Color(0xFFC9773F)
private val Copper2 = Color(0xFFE6A06A)
private val Bg = Color(0xFF121110)
private val Panel = Color(0xFF181614)
private val Muted = Color(0xFF8D847B)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Copper, background = Bg, surface = Panel)) {
                CobreTvApp()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CobreTvApp(source: MusicSource = remember { FakeMusicSource() }) {
    val ctx = LocalContext.current
    val idRepository = remember { TvIdRepository(ctx) }
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var albums by remember { mutableStateOf(emptyList<Album>()) }
    LaunchedEffect(source) { albums = source.albums() }
    var serverAddress by remember { mutableStateOf(idRepository.serverUrl) }
    var accountEmail by remember { mutableStateOf(idRepository.savedEmail) }
    var connectionMessage by remember { mutableStateOf("Cobre ID desconectado") }
    var connectionError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var pairingChallenge by remember { mutableStateOf<TvPairingChallenge?>(null) }
    var pairingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        if (idRepository.isConnected && serverAddress.isNotBlank()) {
            busy = true
            runCatching { idRepository.restoreSession() }
                .onSuccess { session -> accountEmail = session.email; connectionMessage = "TV conectada · ${session.email}"; connectionError = false }
                .onFailure { error -> connectionMessage = error.message ?: "Sessão do Cobre ID expirou."; connectionError = true }
            busy = false
        }
    }
    var selected by remember { mutableStateOf<Album?>(null) }
    var playing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Cobre", color = Copper2, style = MaterialTheme.typography.headlineLarge)
                Text(if (accountEmail.isNotBlank()) "TV vinculada a $accountEmail" else "Cobre ID · ${if (serverAddress.isBlank()) "Configure o servidor" else "Não conectado"}", color = Muted)
            }
            Button(onClick = { showServerDialog = true }) { Text("Servidor") }
            Button(onClick = {
                pairingJob?.cancel()
                pairingJob = scope.launch {
                    busy = true
                    connectionMessage = "Solicitando código…"
                    connectionError = false
                    try {
                        val challenge = idRepository.requestPairing()
                        pairingChallenge = challenge
                        connectionMessage = "Aprove o código pelo Cobre Mobile ou Desktop."
                        val session = idRepository.awaitApproval(challenge)
                        pairingChallenge = null
                        accountEmail = session.email
                        connectionMessage = "TV vinculada · ${session.email}"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        connectionMessage = error.message ?: "Não foi possível conectar ao Cobre ID."
                        connectionError = true
                        pairingChallenge = null
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy && serverAddress.isNotBlank()) { Text(if (busy) "Aguardando…" else if (accountEmail.isBlank()) "Conectar TV" else "Vincular outra conta") }
            if (accountEmail.isNotBlank()) Button(onClick = {
                scope.launch {
                    busy = true
                    runCatching { idRepository.logout() }
                    accountEmail = ""
                    connectionMessage = "Sessão encerrada nesta TV."
                    connectionError = false
                    busy = false
                }
            }, enabled = !busy) { Text("Sair") }
        }
        Text(connectionMessage, color = if (connectionError) Color(0xFFE58B78) else Muted, modifier = Modifier.padding(bottom = 18.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(albums, key = { it.id }) { a ->
                // TODO: player.setMediaItem(...); player.prepare(); player.play()
                AlbumTile(a) { selected = a; playing = true }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(Panel).padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(selected?.title ?: "Nada tocando", color = Color(0xFFECE6DF))
                Text(selected?.artist ?: "Escolha um álbum", color = Muted)
            }
            Button(onClick = { playing = !playing; player.playWhenReady = playing }) {
                Text(if (playing) "Pausar" else "Tocar")
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Cobre Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Digite o endereço local do computador que executa o Cobre Server. TV e computador precisam estar na mesma rede Wi-Fi.", color = Muted)
                    OutlinedTextField(value = serverAddress, onValueChange = { serverAddress = it }, label = { Text("Endereço do servidor") }, placeholder = { Text("http://192.168.1.20:8080") }, singleLine = true)
                }
            },
            confirmButton = { Button(onClick = {
                try {
                    idRepository.saveServerUrl(serverAddress)
                    serverAddress = idRepository.serverUrl
                    showServerDialog = false
                    connectionMessage = "Endereço salvo. Testando Cobre Server…"
                    scope.launch {
                        runCatching { idRepository.checkServer() }
                            .onSuccess { ok -> connectionMessage = if (ok) "Cobre Server encontrado. Você já pode vincular esta TV." else "O endereço não respondeu como Cobre Server."; connectionError = !ok }
                            .onFailure { error -> connectionMessage = error.message ?: "Não foi possível alcançar o Cobre Server."; connectionError = true }
                    }
                } catch (error: Exception) {
                    connectionMessage = error.message ?: "Endereço inválido."
                    connectionError = true
                }
            }) { Text("Salvar e testar") } },
            dismissButton = { Button(onClick = { showServerDialog = false }) { Text("Cancelar") } },
        )
    }

    pairingChallenge?.let { challenge ->
        AlertDialog(
            onDismissRequest = { pairingJob?.cancel(); pairingChallenge = null; busy = false },
            title = { Text("Autorizar esta TV") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("No Cobre Mobile, abra Cobre ID e digite este código para autorizar o aparelho:", color = Muted)
                    Text(challenge.userCode, color = Copper2, style = MaterialTheme.typography.displayMedium)
                    Text("O código expira em ${challenge.expiresInSeconds / 60} min. A TV aguardará a confirmação.", color = Muted)
                }
            },
            confirmButton = { Button(onClick = { pairingJob?.cancel(); pairingChallenge = null; busy = false; connectionMessage = "Pareamento cancelado." }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Copper),
            modifier = Modifier.size(150.dp),
        ) {
            // Troque pela capa real (ex.: Coil) quando houver fonte de dados.
            Box(Modifier.fillMaxSize().background(Brush.sweepGradient(listOf(Color(0xFF6B2D12), Copper, Copper2, Color(0xFF6B2D12)))))
        }
        Spacer(Modifier.height(8.dp))
        Text(album.title, color = Color(0xFFECE6DF), maxLines = 1)
        Text(album.artist, color = Muted, maxLines = 1)
    }
}
