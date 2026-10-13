package com.plyr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B20 (resto): la animación de `NfcButton` se calculaba dentro de la composición
 * mutando una lista de estado por frame. Ahora el frame es un Int y el dibujo sale
 * de esta lógica pura.
 */
class NfcPulseTest {

    @Test
    fun firstRingAppearsAfterSpawnInterval() {
        assertEquals("           ", NfcPulse.textAt(0))
        assertEquals("           ", NfcPulse.textAt(1))
        assertEquals("           ", NfcPulse.textAt(2))
    }

    @Test
    fun singleRingIsADotInTheCenter() {
        assertEquals("     •     ", NfcPulse.textAt(3))
    }

    @Test
    fun ringGrowsIntoParenthesis() {
        // radio 1: los dos extremos se separan del centro
        assertEquals("    ( )    ", NfcPulse.textAt(4))
        assertEquals("   (   )   ", NfcPulse.textAt(5))
    }

    @Test
    fun ringsAreSpawnedEveryThreeFrames() {
        // frame 6: nace un anillo en el centro y el anterior ya va por radio 3
        assertEquals("  (  •  )  ", NfcPulse.textAt(6))
    }

    @Test
    fun outermostRingReachesBothEdges() {
        // frame 8: el anillo más viejo ya ocupa los dos extremos del ancho
        assertEquals("(  (   )  )", NfcPulse.textAt(8))
        // frame 9: ese anillo salió y queda el nuevo en el centro
        assertEquals("  (  •  )  ", NfcPulse.textAt(9))
    }

    @Test
    fun textAlwaysHasFullWidth() {
        (0 until NfcPulse.FRAME_CYCLE).forEach { frame ->
            assertEquals(
                "Frame $frame con ancho ${NfcPulse.textAt(frame).length}",
                NfcPulse.WIDTH,
                NfcPulse.textAt(frame).length
            )
        }
    }

    @Test
    fun animationLoopsWithoutJumps() {
        (0 until NfcPulse.FRAME_CYCLE).forEach { frame ->
            assertEquals(
                "El ciclo no es continuo en el frame $frame",
                NfcPulse.textAt(frame),
                NfcPulse.textAt(frame + NfcPulse.FRAME_CYCLE)
            )
        }
    }

    @Test
    fun ringsNeverGrowBeyondTheCenter() {
        (0 until NfcPulse.FRAME_CYCLE).forEach { frame ->
            NfcPulse.ringsAt(frame).forEach { radius ->
                assertTrue("Anillo fuera de rango en el frame $frame", radius in 0..NfcPulse.WIDTH / 2)
            }
        }
    }

    @Test
    fun atMostThreeRingsAliveAtOnce() {
        (0 until NfcPulse.FRAME_CYCLE).forEach { frame ->
            assertTrue(
                "Demasiados anillos en el frame $frame",
                NfcPulse.ringsAt(frame).size <= 3
            )
        }
    }

    @Test
    fun someFrameAlwaysAnimates() {
        val frames = (0 until NfcPulse.FRAME_CYCLE).map { NfcPulse.textAt(it) }.toSet()
        assertTrue("La animación no cambia nunca", frames.size > 1)
    }

    @Test
    fun onlyAllowedCharactersAreUsed() {
        (0 until NfcPulse.FRAME_CYCLE).forEach { frame ->
            NfcPulse.textAt(frame).forEach { ch ->
                assertTrue("Carácter inesperado '$ch' en el frame $frame", ch in " ()\u2022 ")
            }
        }
    }
}
