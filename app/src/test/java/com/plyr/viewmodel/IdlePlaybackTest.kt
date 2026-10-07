package com.plyr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class IdlePlaybackTest {

    @Test
    fun noIdle_resume() {
        assertEquals(
            IdlePlayback.Action.Resume,
            IdlePlayback.decide(
                isIdle = false,
                mediaItemCount = 0,
                queueSize = 2,
                currentIndex = 0,
            ),
        )
    }

    @Test
    fun idleConItems_preparaYReproduce() {
        assertEquals(
            IdlePlayback.Action.PrepareAndPlay,
            IdlePlayback.decide(
                isIdle = true,
                mediaItemCount = 3,
                queueSize = 5,
                currentIndex = 2,
            ),
        )
    }

    @Test
    fun idleSinItemsConCola_reiniciaEnCurrentIndex() {
        assertEquals(
            IdlePlayback.Action.RestartAt(1),
            IdlePlayback.decide(
                isIdle = true,
                mediaItemCount = 0,
                queueSize = 3,
                currentIndex = 1,
            ),
        )
    }

    @Test
    fun idleSinItemsSinCola_nothing() {
        assertEquals(
            IdlePlayback.Action.Nothing,
            IdlePlayback.decide(
                isIdle = true,
                mediaItemCount = 0,
                queueSize = 0,
                currentIndex = -1,
            ),
        )
    }

    @Test
    fun currentIndexFueraDeRango_anclaEn0() {
        // B63: después de stopAtQueueEnd, currentIndex podía seguir en la última
        // posición aunque la cola ya no está preparada.
        assertEquals(
            IdlePlayback.Action.RestartAt(0),
            IdlePlayback.decide(
                isIdle = true,
                mediaItemCount = 0,
                queueSize = 2,
                currentIndex = 5,
            ),
        )

        assertEquals(
            IdlePlayback.Action.RestartAt(0),
            IdlePlayback.decide(
                isIdle = true,
                mediaItemCount = 0,
                queueSize = 2,
                currentIndex = -10,
            ),
        )
    }
}
