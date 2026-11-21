package app.cobre.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Copper = Color(0xFFC9773F)
private val Copper2 = Color(0xFFE6A06A)
private val Bg = Color(0xFF121110)
private val Panel = Color(0xFF201D1A)
private val Muted = Color(0xFFAAA199)

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
fun CobreTvApp() {
    val context = LocalContext.current
    val idRepository = remember { TvIdRepository(context) }
    val desktopRepository = remember { DesktopLibraryRepository() }
    val scope = rememberCoroutineScope()
    val httpFactory = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var serverAddress by remember { mutableStateOf(idRepository.serverUrl) }
    var desktopAddress by remember { mutableStateOf(idRepository.desktopOrigin) }
    var accountEmail by remember { mutableStateOf(idRepository.savedEmail) }
    var desktopHosts by remember { mutableStateOf(emptyList<TvDesktopHost>()) }
    var albums by remember { mutableStateOf(emptyList<DesktopAlbum>()) }
    var activeGrantToken by remember { mutableStateOf(idRepository.desktopGrantToken.orEmpty()) }
    var connectionMessage by remember { mutableStateOf("Conecte o Cobre ID e escolha um computador.") }
    var connectionError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showDesktopDialog by remember { mutableStateOf(false) }
    var pairingChallenge by remember { mutableStateOf<TvPairingChallenge?>(null) }
    var pairingJob by remember { mutableStateOf<Job?>(null) }
    var selectedAlbum by remember { mutableStateOf<DesktopAlbum?>(null) }
    var nowPlaying by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nowPlaying = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun playAlbum(album: DesktopAlbum) {
        val token = activeGrantToken
        if (token.isBlank()) {
            connectionMessage = "Conecte e autorize o computador antes de reproduzir."
            connectionError = true
            return
        }
        runCatching {
            httpFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token", "Cache-Control" to "no-store"))
            val items = album.tracks.map { track ->
                MediaItem.Builder()
                    .setUri(desktopRepository.streamUri(desktopAddress, track.id))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build())
                    .build()
            }
            player.setMediaItems(items, 0, 0L)
            player.prepare()
            player.play()
            selectedAlbum = album
            playing = true
            connectionMessage = "Reproduzindo direto do Cobre Desktop pela rede local."
            connectionError = false
        }.onFailure { error ->
            connectionMessage = error.message ?: "Não foi possível iniciar a reprodução."
            connectionError = true
        }
    }

    LaunchedEffect(Unit) {
        if (idRepository.isConnected && serverAddress.isNotBlank()) {
            busy = true
            runCatching { idRepository.restoreSession() }
                .onSuccess { session ->
                    accountEmail = session.email
                    connectionMessage = "Cobre ID conectado · ${session.email}"
                    connectionError = false
                    if (idRepository.desktopGrantId != null && desktopAddress.isNotBlank()) {
                        runCatching {
                            val grant = idRepository.refreshDesktopGrant()
                            activeGrantToken = grant.token
                            val catalog = desktopRepository.load(desktopAddress, grant.token)
                            albums = catalog
                            connectionMessage = "Computador conectado · ${catalog.sumOf { it.tracks.size }} faixas em ${catalog.size} álbuns."
                        }.onFailure { error ->
                            activeGrantToken = ""
                            connectionMessage = error.message ?: "Não foi possível restaurar o acesso ao computador."
                            connectionError = true
                        }
                    }
                }
                .onFailure { error -> connectionMessage = error.message ?: "Sessão do Cobre ID expirada."; connectionError = true }
            busy = false
        }
    }

    LaunchedEffect(idRepository.desktopGrantId, accountEmail) {
        if (idRepository.desktopGrantId != null && accountEmail.isNotBlank()) {
            while (isActive) {
                delay(6 * 60 * 1000L)
                val refreshed = runCatching {
                    idRepository.restoreSession()
                    idRepository.refreshDesktopGrant()
                }
                if (refreshed.isSuccess) {
                    val grant = refreshed.getOrThrow()
                    activeGrantToken = grant.token
                    httpFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${grant.token}", "Cache-Control" to "no-store"))
                } else {
                    val error = refreshed.exceptionOrNull()
                    activeGrantToken = ""
                    connectionMessage = error?.message ?: "A autorização expirou. Conecte o computador novamente."
                    connectionError = true
                    break
                }
            }
        }
    }

    LaunchedEffect(showDesktopDialog, accountEmail) {
        if (showDesktopDialog && accountEmail.isNotBlank()) {
            runCatching { idRepository.desktops() }
                .onSuccess { desktopHosts = it }
                .onFailure { error -> connectionMessage = error.message ?: "Não foi possível localizar seus computadores."; connectionError = true }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 52.dp, vertical = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Cobre", color = Copper2, style = MaterialTheme.typography.headlineLarge)
                Text(if (accountEmail.isNotBlank()) "Cobre ID · $accountEmail" else "Música da sua biblioteca, na TV", color = Muted)
            }
            Button(onClick = { showServerDialog = true }) { Text("Cobre Server") }
            Button(onClick = {
                if (accountEmail.isBlank()) {
                    connectionMessage = "Configure o Cobre Server e conecte o Cobre ID primeiro."
                    connectionError = true
                } else showDesktopDialog = true
            }, enabled = !busy && accountEmail.isNotBlank()) { Text("Computador") }
            Button(onClick = {
                if (serverAddress.isBlank()) {
                    showServerDialog = true
                } else {
                    pairingJob?.cancel()
                    pairingJob = scope.launch {
                        busy = true
                        connectionMessage = "Solicitando pareamento…"
                        try {
                            val challenge = idRepository.requestPairing()
                            pairingChallenge = challenge
                            connectionMessage = "Aprove o código no Cobre Mobile ou Desktop."
                            val session = idRepository.awaitApproval(challenge)
                            accountEmail = session.email
                            pairingChallenge = null
                            connectionMessage = "Cobre ID conectado · ${session.email}"
                            connectionError = false
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) {
                            pairingChallenge = null
                            connectionMessage = error.message ?: "Não foi possível conectar ao Cobre ID."
                            connectionError = true
                        } finally { busy = false }
                    }
                }
            }, enabled = !busy) { Text(if (busy) "Aguardando…" else if (accountEmail.isBlank()) "Conectar Cobre ID" else "Vincular TV") }
            if (accountEmail.isNotBlank()) Button(onClick = {
                scope.launch {
                    busy = true
                    runCatching { idRepository.logout() }
                    albums = emptyList(); activeGrantToken = ""; accountEmail = ""
                    connectionMessage = "Sessão encerrada nesta TV."
                    connectionError = false; busy = false
                }
            }, enabled = !busy) { Text("Sair") }
        }
        Text(connectionMessage, color = if (connectionError) Color(0xFFE58B78) else Muted, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))

        if (albums.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Panel).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Sua biblioteca aparece aqui", color = Color(0xFFECE6DF), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text("Conecte sua conta, escolha um Desktop online e aprove o acesso nele. O áudio segue direto do computador para esta TV.", color = Muted, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { showDesktopDialog = true }, enabled = accountEmail.isNotBlank() && !busy) { Text("Conectar computador") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Biblioteca", color = Color(0xFFECE6DF), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${albums.size} álbuns · ${albums.sumOf { it.tracks.size }} faixas", color = Muted)
                Spacer(Modifier.width(18.dp))
                Button(onClick = {
                    scope.launch {
                        busy = true
                        runCatching { idRepository.refreshDesktopGrant() }.onSuccess { grant ->
                            activeGrantToken = grant.token
                            runCatching { desktopRepository.load(desktopAddress, grant.token) }
                                .onSuccess { albums = it; connectionMessage = "Biblioteca atualizada · ${it.size} álbuns."; connectionError = false }
                                .onFailure { error -> connectionMessage = error.message ?: "Falha ao carregar a biblioteca."; connectionError = true }
                        }.onFailure { error -> connectionMessage = error.message ?: "Autorização do computador expirada."; connectionError = true }
                        busy = false
                    }
                }, enabled = !busy && activeGrantToken.isNotBlank()) { Text("Atualizar biblioteca") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumTile(album, desktopAddress, activeGrantToken, desktopRepository) { playAlbum(album) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nowPlaying.ifBlank { if (selectedAlbum == null) "Nada tocando" else selectedAlbum?.tracks?.firstOrNull()?.title.orEmpty() }, color = Color(0xFFECE6DF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: selectedAlbum?.artist ?: "Escolha um álbum", color = Muted, maxLines = 1)
            }
            Button(onClick = { player.seekToPreviousMediaItem() }, enabled = player.mediaItemCount > 0) { Text("Anterior") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (player.isPlaying) player.pause() else player.play(); playing = player.isPlaying }) { Text(if (playing) "Pausar" else "Tocar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { player.seekToNextMediaItem() }, enabled = player.mediaItemCount > 0) { Text("Próxima") }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false }, title = { Text("Conectar ao Cobre Server") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Informe o endereço do Cobre Server. No computador, TV e Server precisam estar na mesma rede.", color = Muted)
                OutlinedTextField(value = serverAddress, onValueChange = { serverAddress = it }, label = { Text("Endereço do Server") }, placeholder = { Text("http://192.168.1.20:8080") }, singleLine = true)
            } },
            confirmButton = { Button(onClick = {
                try {
                    idRepository.saveServerUrl(serverAddress); serverAddress = idRepository.serverUrl; showServerDialog = false
                    scope.launch { runCatching { idRepository.checkServer() }.onSuccess { ok -> connectionMessage = if (ok) "Cobre Server encontrado. Conecte o Cobre ID." else "O endereço não respondeu como Cobre Server."; connectionError = !ok }.onFailure { e -> connectionMessage = e.message ?: "Não foi possível alcançar o Server."; connectionError = true } }
                } catch (error: Exception) { connectionMessage = error.message ?: "Endereço inválido."; connectionError = true }
            }) { Text("Salvar e testar") } },
            dismissButton = { Button(onClick = { showServerDialog = false }) { Text("Cancelar") } },
        )
    }

    if (showDesktopDialog) {
        AlertDialog(
            onDismissRequest = { showDesktopDialog = false }, title = { Text("Conectar computador") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Informe o endereço local mostrado em Acesso remoto no Desktop. A chave não deve ser incluída no endereço.", color = Muted)
                OutlinedTextField(value = desktopAddress, onValueChange = { desktopAddress = it }, label = { Text("Endereço local do Desktop") }, placeholder = { Text("http://192.168.1.20:8787") }, singleLine = true)
                Text("Escolha seu Desktop. Ele precisa estar online e aprovar esta TV.", color = Muted)
                if (desktopHosts.isEmpty()) Text("Nenhum Desktop registrado nesta conta.", color = Muted)
                desktopHosts.forEach { host ->
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            try {
                                idRepository.saveDesktopOrigin(desktopAddress)
                                desktopAddress = idRepository.desktopOrigin
                                val (requestId, expiresIn) = idRepository.requestDesktopAccess(host.id)
                                showDesktopDialog = false
                                connectionMessage = "Pedido enviado a ${host.name}. Aprove no Cobre Desktop."
                                connectionError = false
                                val deadline = System.currentTimeMillis() + expiresIn * 1000L
                                var grant: TvDesktopGrant? = null
                                while (System.currentTimeMillis() < deadline && grant == null) {
                                    delay(2500L)
                                    grant = idRepository.pollDesktopAccess(host.id, requestId)
                                }
                                val approved = grant ?: throw IllegalStateException("O pedido expirou. Tente conectar novamente.")
                                activeGrantToken = approved.token
                                httpFactory.setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${approved.token}", "Cache-Control" to "no-store"))
                                connectionMessage = "Acesso aprovado. Carregando biblioteca do computador…"
                                val catalog = desktopRepository.load(desktopAddress, approved.token)
                                albums = catalog
                                connectionMessage = "Computador conectado · ${catalog.sumOf { it.tracks.size }} faixas em ${catalog.size} álbuns."
                                connectionError = false
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { connectionMessage = error.message ?: "Não foi possível conectar ao computador."; connectionError = true }
                            finally { busy = false }
                        }
                    }, enabled = host.online && !busy && desktopAddress.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("${host.name}${if (host.online) " · Online" else " · Offline"}")
                    }
                }
            } },
            confirmButton = { Button(onClick = { showDesktopDialog = false }) { Text("Fechar") } },
        )
    }

    pairingChallenge?.let { challenge ->
        AlertDialog(
            onDismissRequest = { pairingJob?.cancel(); pairingJob = null; pairingChallenge = null; connectionMessage = "Pareamento cancelado." }, title = { Text("Vincular Cobre ID") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("No Cobre Mobile ou Desktop, abra Cobre ID e informe este código:", color = Muted)
                Text(challenge.userCode, color = Copper2, style = MaterialTheme.typography.displayMedium)
                Text("O código expira em ${challenge.expiresInSeconds / 60} min.", color = Muted)
            } },
            confirmButton = { Button(onClick = { pairingJob?.cancel(); pairingJob = null; pairingChallenge = null; connectionMessage = "Pareamento cancelado." }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumTile(album: DesktopAlbum, origin: String, grantToken: String, repository: DesktopLibraryRepository, onClick: () -> Unit) {
    val cover by produceState<android.graphics.Bitmap?>(null, album.id, origin, grantToken) {
        value = repository.loadCover(origin, album.coverTrackId, grantToken)
    }
    Column {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Color(0xFF332820)),
            modifier = Modifier.fillMaxWidth().height(148.dp),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF251C16), Color(0xFF60402B), Color(0xFF29221D)))).padding(18.dp), contentAlignment = Alignment.BottomStart) {
                if (cover != null) Image(bitmap = cover!!.asImageBitmap(), contentDescription = "Capa de ${album.title}", modifier = Modifier.fillMaxSize())
                Text(album.title.take(1).uppercase(), color = Copper2.copy(alpha = 0.8f), style = MaterialTheme.typography.displayLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(album.title, color = Color(0xFFECE6DF), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.artist} · ${album.tracks.size} faixas", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
