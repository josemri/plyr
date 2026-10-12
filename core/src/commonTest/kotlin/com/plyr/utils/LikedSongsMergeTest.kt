package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la política de fusión de favoritos (B51).
 *
 * El bug: `liked_songs` se fusiona en vez de sobrescribirse, y la fusión era
 * solo aditiva, así que lo que el usuario acababa de quitar volvía en la
 * siguiente sincronización. Estos tests fijan la precedencia que lo evita.
 */
class LikedSongsMergeTest {

    private fun select(
        existing: Collection<String> = emptySet(),
        incoming: List<String>,
        removed: Collection<String> = emptySet(),
    ) = LikedSongsMerge.selectFreshIndexes(existing, incoming, removed)

    /** Este es el bug: el usuario quitó "yt-2" y el archivo lo trae. */
    @Test
    fun loQueElUsuarioQuito_noSeReinserta() {
        assertEquals(
            listOf(0, 2),
            select(incoming = listOf("yt-1", "yt-2", "yt-3"), removed = setOf("yt-2")),
        )
    }

    @Test
    fun unTombGanaAunQueLaPistaSigaEnElDispositivo() {
        // "La he quitado" es más específico que "ya estaba": si el tomb no se
        // respetara, la pista volvería en cuanto saliera del dispositivo y
        // regresara en el siguiente archivo.
        assertEquals(
            emptyList<Int>(),
            select(existing = setOf("yt-1"), incoming = listOf("yt-1"), removed = setOf("yt-1")),
        )
    }

    @Test
    fun loQueYaEsta_noSeDuplica() {
        assertEquals(
            listOf(2),
            select(existing = setOf("yt-1", "yt-2"), incoming = listOf("yt-1", "yt-2", "yt-3")),
        )
    }

    @Test
    fun duplicadosDentroDelPropioArchivo_soloSeAnadenUnaVez() {
        assertEquals(
            listOf(0),
            select(incoming = listOf("yt-1", "yt-1", "yt-1")),
        )
    }

    /** Sin tombs el comportamiento es el de siempre: solo descartar duplicados. */
    @Test
    fun sinTombs_elComportamientoNoCambia() {
        assertEquals(
            listOf(1, 3),
            select(existing = setOf("yt-1"), incoming = listOf("yt-1", "yt-2", "yt-1", "yt-4")),
        )
    }

    @Test
    fun todosTombeados_noSeAnadeNada() {
        assertEquals(
            emptyList<Int>(),
            select(incoming = listOf("yt-1", "yt-2"), removed = setOf("yt-1", "yt-2")),
        )
    }

    /**
     * El caso mezclado: parte viene ya, parte está tombada y parte es nueva. Es lo
     * que pasa en cada sincronización real, y donde se ven las tres reglas a la vez.
     */
    @Test
    fun mezcla_deLasTresCasillas() {
        assertEquals(
            listOf(2, 4),
            select(
                existing = setOf("yt-1"),
                incoming = listOf("yt-1", "yt-2", "yt-3", "yt-4", "yt-5"),
                removed = setOf("yt-2", "yt-4"),
            ),
        )
    }

    @Test
    fun vacios() {
        assertEquals(emptyList<Int>(), select(incoming = emptyList()))
        assertEquals(emptyList<Int>(), select(incoming = emptyList(), removed = setOf("yt-1")))
    }

    /** El orden del archivo es el orden de inserción, así que los índices bastan. */
    @Test
    fun conservaElOrdenDelArchivo() {
        assertEquals(
            listOf(0, 1, 2),
            select(incoming = listOf("yt-c", "yt-a", "yt-b"), removed = setOf("yt-z")),
        )
    }
}
