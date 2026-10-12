package com.plyr.desktop.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests del historial de búsquedas de escritorio: misma política que
 * `SearchViewModel` en Android (sin duplicados por texto+motor, 50 más recientes).
 */
class SearchHistoryRepositoryTest {

    private lateinit var database: DesktopDatabase
    private lateinit var repository: SearchHistoryRepository

    @Before
    fun setUp() {
        database = DesktopDatabase.inMemory()
        repository = SearchHistoryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addSearch_guardaYDevuelveLoMasRecientePrimero() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("jazz", "youtube", timestamp = 20)

        assertEquals(listOf("jazz", "rock"), repository.recentSearches().map { it.query })
    }

    @Test
    fun addSearch_repetirElMismoTextoYMotor_noDuplica() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("rock", "youtube", timestamp = 20)

        val recent = repository.recentSearches()
        assertEquals(1, recent.size)
        assertEquals(20, recent.first().timestamp)
    }

    @Test
    fun addSearch_mismoTextoOtroMotor_sonDosEntradas() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("rock", "spotify", timestamp = 20)

        assertEquals(2, repository.recentSearches().size)
    }

    @Test
    fun deleteById_quitaSoloEsaEntrada() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("jazz", "youtube", timestamp = 20)
        val rock = repository.recentSearches().first { it.query == "rock" }

        repository.deleteById(rock.id)

        assertEquals(listOf("jazz"), repository.recentSearches().map { it.query })
    }

    @Test
    fun deleteByQuery_quitaEsaBusqueda() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("rock", "spotify", timestamp = 20)

        repository.deleteByQuery("rock", "youtube")

        assertEquals(listOf("spotify"), repository.recentSearches().map { it.searchEngine })
    }

    @Test
    fun clear_vaciaElHistorial() {
        repository.addSearch("rock", "youtube", timestamp = 10)
        repository.addSearch("jazz", "youtube", timestamp = 20)

        repository.clear()

        assertTrue(repository.recentSearches().isEmpty())
    }

    @Test
    fun recentSearches_seLimitaA50() {
        repeat(55) { index -> repository.addSearch("query-$index", "youtube", timestamp = index.toLong()) }

        val recent = repository.recentSearches()
        assertEquals(50, recent.size)
        assertEquals("query-54", recent.first().query)
    }
}
