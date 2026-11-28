package app.cobre.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.media3.common.util.UnstableApi
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

@UnstableApi
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
@UnstableApi
@Composable
fun CobreTvApp() {
    val context = LocalContext.current
    val idRepository = remember { TvIdRepository(context) }
    val desktopRepository = remember { DesktopLibraryRepository() }
    val desktopDiscovery = remember(context) { DesktopDiscovery(context) }
    val streamMetrics = remember { TvStreamMetrics() }
    val scope = rememberCoroutineScope()
    val httpFactory = remember(streamMetrics) {
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false).setTransferListener(streamMetrics)
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var serverAddress by remember { mutableStateOf(idRepository.serverUrl) }
    var desktopAddress by remember { mutableStateOf(idRepository.desktopOrigin) }
    var accountEmail by remember { mutableStateOf(idRepository.savedEmail) }
    var sessionReady by remember { mutableStateOf(false) }
    var desktopHosts by remember { mutableStateOf(emptyList<TvDesktopHost>()) }
    var nearbyDesktops by remember { mutableStateOf(emptyList<NearbyDesktop>()) }
    var albums by remember { mutableStateOf(emptyList<DesktopAlbum>()) }
    var activeGrantToken by remember { mutableStateOf(idRepository.desktopGrantToken.orEmpty()) }
    var connectionMessage by remember { mutableStateOf("Conecte o Cobre ID e escolha um computador.") }
    var connectionError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showConnectionsDialog by remember { mutableStateOf(false) }
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
            try {
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
            } finally {
                busy = false
                sessionReady = true
            }
        } else {
            sessionReady = true
        }
    }

    LaunchedEffect(accountEmail, sessionReady) {
        if (accountEmail.isBlank() || !sessionReady) return@LaunchedEffect
        while (isActive) {
            runCatching { idRepository.sendPresence(streamMetrics.recent()) }
            delay(20_000L)
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

    LaunchedEffect(showConnectionsDialog, accountEmail) {
        if (showConnectionsDialog && accountEmail.isNotBlank()) {
            runCatching { idRepository.desktops() }
                .onSuccess { desktopHosts = it }
                .onFailure { error -> connectionMessage = error.message ?: "Não foi possível localizar seus computadores."; connectionError = true }
        }
    }

    DisposableEffect(showConnectionsDialog, accountEmail, desktopDiscovery) {
        if (showConnectionsDialog && accountEmail.isNotBlank()) {
            desktopDiscovery.start(
                onChanged = { nearbyDesktops = it },
                onError = { connectionMessage = it; connectionError = true },
            )
        }
        onDispose {
            desktopDiscovery.stop()
            nearbyDesktops = emptyList()
        }
    }

    fun connectDesktop(hostId: String, hostName: String, origin: String) {
        scope.launch {
            busy = true
            try {
                idRepository.saveDesktopOrigin(origin)
                desktopAddress = idRepository.desktopOrigin
                val (requestId, expiresIn) = idRepository.requestDesktopAccess(hostId)
                showConnectionsDialog = false
                connectionMessage = "Pedido enviado a $hostName. Aprove no Cobre Desktop."
                connectionError = false
                val deadline = System.currentTimeMillis() + expiresIn * 1000L
                var grant: TvDesktopGrant? = null
                while (System.currentTimeMillis() < deadline && grant == null) {
                    delay(2500L)
                    grant = idRepository.pollDesktopAccess(hostId, requestId)
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
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 52.dp, vertical = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Cobre", color = Copper2, style = MaterialTheme.typography.headlineLarge)
                Text(if (accountEmail.isNotBlank()) "Cobre ID · $accountEmail" else "Música da sua biblioteca, na TV", color = Muted)
            }
            Button(onClick = { showConnectionsDialog = true }) { Text("Conexões") }
        }
        Text(connectionMessage, color = if (connectionError) Color(0xFFE58B78) else Muted, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))

        if (albums.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Panel).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Sua biblioteca aparece aqui", color = Color(0xFFECE6DF), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text("Conecte sua conta, escolha um Desktop online e aprove o acesso nele. O áudio segue direto do computador para esta TV.", color = Muted, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { showConnectionsDialog = true }, enabled = !busy) { Text("Configurar conexões") }
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

    if (showConnectionsDialog) {
        AlertDialog(
            onDismissRequest = { showConnectionsDialog = false }, title = { Text("Conexões") },
            text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Configure aqui o Cobre Server, sua conta e o computador que fornece a música. Na mesma rede, a TV encontra o Desktop; fora de casa, use o endereço Tailscale.", color = Muted)
                Text("1 · Cobre Server", color = Copper2, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = serverAddress, onValueChange = { serverAddress = it }, label = { Text("Endereço do Server") }, placeholder = { Text("http://100.x.y.z:8080") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        try {
                            idRepository.saveServerUrl(serverAddress); serverAddress = idRepository.serverUrl
                            scope.launch { runCatching { idRepository.checkServer() }.onSuccess { ok -> connectionMessage = if (ok) "Cobre Server encontrado." else "O endereço não respondeu como Cobre Server."; connectionError = !ok }.onFailure { e -> connectionMessage = e.message ?: "Não foi possível alcançar o Server."; connectionError = true } }
                        } catch (error: Exception) { connectionMessage = error.message ?: "Endereço inválido."; connectionError = true }
                    }, enabled = !busy) { Text("Salvar e testar") }
                    Button(onClick = {
                        if (serverAddress.isBlank()) { connectionMessage = "Configure primeiro o endereço do Cobre Server."; connectionError = true }
                        else {
                            showConnectionsDialog = false
                            pairingJob?.cancel()
                            pairingJob = scope.launch {
                                busy = true; connectionMessage = "Solicitando pareamento…"
                                try {
                                    idRepository.saveServerUrl(serverAddress)
                                    serverAddress = idRepository.serverUrl
                                    val challenge = idRepository.requestPairing(); pairingChallenge = challenge
                                    connectionMessage = "Aprove o código no Cobre Mobile ou Desktop."
                                    val session = idRepository.awaitApproval(challenge)
                                    accountEmail = session.email; pairingChallenge = null
                                    connectionMessage = "Cobre ID conectado · ${session.email}"; connectionError = false
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { pairingChallenge = null; connectionMessage = error.message ?: "Não foi possível conectar ao Cobre ID."; connectionError = true }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy && serverAddress.isNotBlank()) { Text(if (busy) "Aguardando…" else if (accountEmail.isBlank()) "Conectar Cobre ID" else "Vincular TV") }
                }
                if (accountEmail.isNotBlank()) {
                    Text("Conta conectada · $accountEmail", color = Color(0xFFECE6DF))
                    Button(onClick = {
                        scope.launch { busy = true; runCatching { idRepository.logout() }; albums = emptyList(); activeGrantToken = ""; accountEmail = ""; connectionMessage = "Sessão encerrada nesta TV."; connectionError = false; busy = false }
                    }, enabled = !busy) { Text("Sair da conta") }
                }
                Text("2 · Computador", color = Copper2, style = MaterialTheme.typography.titleMedium)
                Text("O áudio vai direto do Desktop para a TV. Para conexão remota, instale Tailscale nos dois aparelhos e use o IP 100.x ou nome MagicDNS do Desktop. A aprovação continua sendo feita no Desktop.", color = Muted)
                OutlinedTextField(value = desktopAddress, onValueChange = { desktopAddress = it }, label = { Text("Endereço do Desktop") }, placeholder = { Text("http://100.x.y.z:8787") }, singleLine = true)
                Button(onClick = {
                    try { idRepository.saveDesktopOrigin(desktopAddress); desktopAddress = idRepository.desktopOrigin; connectionMessage = "Endereço do Desktop salvo."; connectionError = false }
                    catch (error: Exception) { connectionMessage = error.message ?: "Endereço inválido."; connectionError = true }
                }, enabled = !busy) { Text("Salvar endereço") }
                Text("Encontrados nesta rede", color = Copper2, style = MaterialTheme.typography.titleMedium)
                if (nearbyDesktops.isEmpty()) Text("Procurando computadores na rede local…", color = Muted)
                nearbyDesktops.forEach { nearby ->
                    Button(
                        onClick = { connectDesktop(nearby.hostId, nearby.name, nearby.origin) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(nearby.name) }
                }
                Text("Computadores da conta · acesso requer aprovação no Desktop", color = Copper2, style = MaterialTheme.typography.titleMedium)
                if (desktopHosts.isEmpty()) Text("Nenhum Desktop registrado nesta conta.", color = Muted)
                desktopHosts.forEach { host ->
                    Button(onClick = {
                        connectDesktop(host.id, host.name, desktopAddress)
                    }, enabled = host.online && !busy && desktopAddress.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("${host.name}${if (host.online) " · Online" else " · Offline"}")
                    }
                }
            } },
            confirmButton = { Button(onClick = { showConnectionsDialog = false }) { Text("Fechar") } },
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
