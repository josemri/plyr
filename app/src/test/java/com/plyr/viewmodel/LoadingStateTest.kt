package com.plyr.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [LoadingState], el registro de cargas en vuelo.
 *
 * El bug (B58): `isLoading` era un `Boolean` que solo cerraba quien lo había
 * puesto y solo si su `generation` seguía vigente. Un `generation++` ajeno
 * mientras se resolvía (añadir a la cola, cambiar de lista) dejaba el flag en
 * `true` para siempre: spinner permanente y los cinco controles de reproducción
 * deshabilitados, sin ningún error que lo explicara.
 *
 * Aquí se comprueba la propiedad que faltaba: **quien invalida el trabajo en
 * curso se lleva el estado de carga, y la retirada tardía de esa operación
 * huérfana no puede ni apagar una carga más moderna ni reencender la suya.**
 */
class LoadingStateTest {

    private val state = LoadingState()

    // --- Estado básico ---

    @Test
    fun recienCreada_noHayNadaCargando() {
        assertFalse(state.isLoading)
    }

    @Test
    fun begin_marcaQueHayCarga() {
        state.begin()
        assertTrue(state.isLoading)
    }

    @Test
    fun end_deLaPropiaOperacion_laApaga() {
        val token = state.begin()
        state.end(token)
        assertFalse(state.isLoading)
    }

    /**
     * Dos operaciones pueden estar cargando a la vez (`startAt` y una
     * re-resolución comparten `generation`): la primera que termina no debe
     * apagar el spinner de la que sigue trabajando. Con el `Boolean` anterior
     * las dos escribían el mismo flag y la primera en acabar lo ponía a `false`
     * a mitad de la resolución de la otra.
     */
    @Test
    fun dosCargasConcurrentes_noSePisan() {
        val primera = state.begin()
        val segunda = state.begin()

        state.end(primera)
        assertTrue(state.isLoading)

        state.end(segunda)
        assertFalse(state.isLoading)
    }

    // --- Invalidación de lo que está en vuelo ---

    @Test
    fun supersede_apagaTodoLoQueHabia() {
        state.begin()
        state.begin()

        state.supersede()

        assertFalse(state.isLoading)
    }

    @Test
    fun supersede_sinCargasEsInocua() {
        state.supersede()

        assertFalse(state.isLoading)
    }

    /**
     * El caso de B58: algo invalida el trabajo en vuelo (`setCurrentPlaylist`,
     * `clearPlayerState` o `startAt` hacen `generation++`) y esa invalidación se
     * lleva el estado de carga. Cuando la resolución huérfana acabe y retire su
     * token, no tiene que encontrar nada: si el flag siguiera siendo suyo, la
     * carga se reencendería al terminar; y si hubiera pasado a manos de otra
     * operación, la apagaría a mitad de camino.
     */
    @Test
    fun end_deUnaOperacionInvalidada_noTocaALaNueva() {
        val enVuelo = state.begin()
        state.supersede()
        assertFalse(state.isLoading)

        val nueva = state.begin()
        state.end(enVuelo)
        assertTrue(state.isLoading)

        state.end(nueva)
        assertFalse(state.isLoading)
    }

    /**
     * El flujo completo del síntoma: se pide un salto, llega un cambio de lista
     * y el spinner tiene que apagarse ya, sin esperar a que la resolución
     * huérfana termine (que puede tardar hasta `EXTRACTION_TIMEOUT_MS`).
     */
    @Test
    fun invalidarMientrasSeResuelve_elEstadoNoSeQuedaClavado() {
        val salto = state.begin()
        assertTrue(state.isLoading)

        state.supersede()
        assertFalse(state.isLoading)

        state.end(salto)
        assertFalse(state.isLoading)
    }

    /**
     * Después de una invalidación, la siguiente carga vuelve a registrar su
     * propio token: los tokens no se reciclan, que es lo que permite que la
     * retirada tardía de la anterior no le haga nada.
     */
    @Test
    fun trasSupersede_losTokensSiguenSiendoUnicos() {
        val anterior = state.begin()
        state.supersede()

        val posterior = state.begin()
        assertTrue(anterior != posterior)

        state.end(anterior)
        assertTrue(state.isLoading)

        state.end(posterior)
        assertFalse(state.isLoading)
    }
}
