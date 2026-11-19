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
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var albums by remember { mutableStateOf(emptyList<Album>()) }
    LaunchedEffect(source) { albums = source.albums() }
    var selected by remember { mutableStateOf<Album?>(null) }
    var playing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Cobre", color = Copper2, style = MaterialTheme.typography.headlineLarge)
        Text("Biblioteca", color = Muted, modifier = Modifier.padding(bottom = 20.dp))

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
