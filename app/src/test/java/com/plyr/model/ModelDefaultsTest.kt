package com.plyr.model

import com.plyr.network.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de defaults y constructores de los modelos de grupo y recomendación.
 */
class ModelDefaultsTest {

    @Test
    fun group_preservesProvidedFields() {
        val group = Group(name = "Fiesta", groupType = "private", inviteCode = "ABC123")
        assertEquals("Fiesta", group.name)
        assertEquals("private", group.groupType)
        assertEquals("ABC123", group.inviteCode)
    }

    @Test
    fun group_defaultsIdAndCreatedAt() {
        val group = Group(name = "General", groupType = "general")
        assertFalse(group.id.isBlank())
        assertNull(group.inviteCode)
        assertTrue(group.createdAt in 0..System.currentTimeMillis())
    }

    @Test
    fun group_defaultsIdIsUnique() {
        val a = Group(name = "A", groupType = "general")
        val b = Group(name = "B", groupType = "general")
        assertFalse(a.id == b.id)
    }

    @Test
    fun groupMember_preservesProvidedFields() {
        val member = GroupMember(groupId = "g1", nickname = "Josep")
        assertEquals("g1", member.groupId)
        assertEquals("Josep", member.nickname)
        assertFalse(member.id.isBlank())
        assertTrue(member.joinedAt in 0..System.currentTimeMillis())
    }

    @Test
    fun recommendation_defaults() {
        val rec = Recommendation(groupId = "g1", nickname = "Ana", url = "https://open.spotify.com/track/x")
        assertEquals(0, rec.likes)
        assertEquals(0, rec.dislikes)
        assertEquals(0, rec.reportCount)
        assertNull(rec.comment)
        assertFalse(rec.id.isBlank())
        assertTrue(rec.createdAt in 0..System.currentTimeMillis())
    }

    @Test
    fun recommendation_preservesProvidedFields() {
        val rec = Recommendation(
            groupId = "g1",
            nickname = "Ana",
            url = "https://open.spotify.com/track/x",
            comment = "Genial",
            likes = 3,
            dislikes = 1
        )
        assertEquals("Genial", rec.comment)
        assertEquals(3, rec.likes)
        assertEquals(1, rec.dislikes)
    }

    @Test
    fun song_isDataClassWithEquality() {
        val a = Song("Título", "Artista")
        val b = Song("Título", "Artista")
        val c = Song("Otro", "Artista")
        assertEquals(a, b)
        assertFalse(a == c)
        assertNotNull(a.title)
        assertNotNull(a.artist)
    }
}
