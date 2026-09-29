package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ExportDigest], la pieza que decide si hace falta reescribir el
 * archivo de copia o no. Un falso "no ha cambiado" perdería cambios del
 * usuario, y un falso "ha cambiado" convertiría cada salida de la app en una
 * subida a Drive, así que ambos sentidos importan.
 */
class ExportDigestTest {

    private fun playlist(
        id: String = "youtube_1",
        name: String = "Mi lista",
        description: String? = "desc",
        coverEntry: String? = "covers/youtube_1.jpg",
        tracks: List<ExportTrack> = listOf(
            ExportTrack(
                position = 0,
                name = "Canción",
                artists = listOf("Artista"),
                remoteTrackId = "r1",
                youtubeVideoId = "v1"
            )
        )
    ) = ExportPlaylist(id, name, description, coverEntry, tracks)

    private fun hash(vararg playlists: Pair<ExportPlaylist, ByteArray?>): String =
        ExportDigest.Accumulator().apply {
            playlists.forEach { (playlist, cover) -> addPlaylist(playlist, cover) }
        }.hex()

    @Test
    fun `mismos datos dan la misma huella`() {
        assertEquals(hash(playlist() to byteArrayOf(1, 2, 3)), hash(playlist() to byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `la huella es un sha256 en hexadecimal`() {
        val hex = hash(playlist() to null)
        assertEquals(64, hex.length)
        assertTrue("solo hex en minúsculas: $hex", hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun `cambiar el nombre de la lista cambia la huella`() {
        assertNotEquals(
            hash(playlist(name = "A") to null),
            hash(playlist(name = "B") to null)
        )
    }

    @Test
    fun `cambiar el id de la lista cambia la huella`() {
        assertNotEquals(
            hash(playlist(id = "youtube_1") to null),
            hash(playlist(id = "youtube_2") to null)
        )
    }

    @Test
    fun `anadir una pista cambia la huella`() {
        val extra = ExportTrack(1, "Otra", listOf("B"), "r2", "v2")
        assertNotEquals(
            hash(playlist() to null),
            hash(playlist(tracks = playlist().tracks + extra) to null)
        )
    }

    @Test
    fun `cambiar los bytes de la portada cambia la huella`() {
        assertNotEquals(
            hash(playlist() to byteArrayOf(1, 2, 3)),
            hash(playlist() to byteArrayOf(1, 2, 4))
        )
    }

    @Test
    fun `sin portada y con portada vacia son cosas distintas`() {
        // Una descarga fallida deja la lista sin portada, y eso no es lo mismo
        // que una portada de cero bytes: la lista ha cambiado.
        assertNotEquals(
            hash(playlist() to null),
            hash(playlist() to byteArrayOf())
        )
    }

    @Test
    fun `descripcion nula y vacia se distinguen de null y de la cadena`() {
        // El caso que obliga a prefijar longitudes: sin el delimitador,
        // ("ab", null) y ("a", "b") colisionarían.
        assertNotEquals(
            hash(playlist(name = "ab", description = null) to null),
            hash(playlist(name = "a", description = "b") to null)
        )
        assertNotEquals(
            hash(playlist(name = "a", description = null) to null),
            hash(playlist(name = "a", description = "") to null)
        )
    }

    @Test
    fun `reordenar pistas cambia la huella`() {
        val first = ExportTrack(0, "A", listOf("X"), "r1", "v1")
        val second = ExportTrack(1, "B", listOf("Y"), "r2", "v2")
        assertNotEquals(
            hash(playlist(tracks = listOf(first, second)) to null),
            hash(playlist(tracks = listOf(second, first)) to null)
        )
    }

    @Test
    fun `el orden de las listas cambia la huella`() {
        val a = playlist(id = "a")
        val b = playlist(id = "b")
        assertNotEquals(hash(a to null, b to null), hash(b to null, a to null))
    }

    @Test
    fun `anadir una lista vacia cambia la huella`() {
        // El caso límite: una lista sin pistas ni portada no aporta campos, y
        // aun así tiene que contar como cambio.
        val empty = playlist(id = "vacia", tracks = emptyList(), coverEntry = null)
        assertNotEquals(hash(empty to null), hash(empty to null, empty to null))
    }
}
