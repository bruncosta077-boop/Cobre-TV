package app.cobre.tv

data class Album(val id: String, val title: String, val artist: String, val coverUrl: String? = null)

/**
 * De onde a TV pega a música. Ainda NÃO definido no projeto.
 * Opção a avaliar: o acesso remoto em rede local que o Cobre Desktop já tem
 * (não é streaming pela internet). Implemente esta interface quando decidir.
 */
interface MusicSource {
    suspend fun albums(): List<Album>
}

/** Dados de exemplo só para a interface funcionar. */
class FakeMusicSource : MusicSource {
    override suspend fun albums() = (1..8).map { Album("fake$it", "Álbum de exemplo $it", "Artista ${'A' + it - 1}") }
}
