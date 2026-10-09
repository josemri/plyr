package com.plyr.database

/**
 * Origen real de una lista, persistido en base de datos.
 *
 * Evita la deducción heurística basada en description (editable por el usuario).
 */
enum class PlaylistSource {
    /** Lista creada en la app (local). No compartible. */
    LOCAL,

    /** Lista importada o nativa de Spotify. Compartible como URL de Spotify. */
    SPOTIFY,

    /** Lista de YouTube (PL/UU/FL/RD u otras reconocidas). Compartible como playlist de YouTube. */
    YOUTUBE,

    /** Origen no identificable con certeza. No compartible. */
    UNKNOWN,
}
