package com.plyr.desktop.database

import com.plyr.desktop.sync.BackupConfig
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistencia de los tombs y de la carpeta de copia sobre `Settings` (M10).
 *
 * Usa [MapSettings] para no tocar las preferencias reales. Comprueba en
 * particular que las claves son las mismas que usa Android (`TombstoneConfig`),
 * que es lo que permite que una copia creada en un dispositivo se entienda en el
 * otro.
 */
class SettingsTombstoneStoreTest {

    @Test
    fun losTombsSobrevivenAUnNuevoAlmacen() {
        val settings = MapSettings()
        SettingsTombstoneStore(settings).apply {
            addDeletedPlaylistId("p1")
            addDeletedPlaylistId("p2")
            addRemovedLikedTrackKey("yt-a")
        }

        val reopened = SettingsTombstoneStore(settings)
        assertEquals(setOf("p1", "p2"), reopened.deletedPlaylistIds())
        assertEquals(setOf("yt-a"), reopened.removedLikedTrackKeys())
    }

    @Test
    fun usaLasMismasClavesQueAndroid() {
        val settings = MapSettings()
        SettingsTombstoneStore(settings).apply {
            addDeletedPlaylistId("p1")
            addRemovedLikedTrackKey("yt-a")
        }

        // Nombres exactos de `TombstoneConfig` en Android.
        assertEquals("p1", settings.getStringOrNull("deleted_playlist_ids"))
        assertEquals("yt-a", settings.getStringOrNull("removed_liked_track_keys"))
    }

    @Test
    fun quitarElUltimoElementoBorraLaClave() {
        val settings = MapSettings()
        val store = SettingsTombstoneStore(settings)
        store.addDeletedPlaylistId("p1")
        store.removeDeletedPlaylistId("p1")

        assertTrue(store.deletedPlaylistIds().isEmpty())
        assertNull(settings.getStringOrNull("deleted_playlist_ids"))
    }

    @Test
    fun laCarpetaDeCopiaSePersisteYSePuedeBorrar() {
        val settings = MapSettings()
        val config = BackupConfig(settings)

        assertFalse(config.isConfigured)
        assertNull(config.folderPath())

        config.folder = "/tmp/plyr-backup"
        config.hash = "abc"
        assertTrue(config.isConfigured)
        assertEquals("/tmp/plyr-backup", BackupConfig(settings).folder)
        assertEquals("abc", BackupConfig(settings).hash)

        config.folder = "   "
        assertFalse(config.isConfigured)
        assertNull(config.folderPath())
    }
}
