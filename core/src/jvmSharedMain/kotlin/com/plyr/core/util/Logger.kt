package com.plyr.core.util

import java.util.logging.Level
import java.util.logging.Logger as JdkLogger

/**
 * Logger mínimo compartido por los targets JVM (escritorio) y Android.
 *
 * Sustituye a `android.util.Log` en el código que vive en `:core`. En el JVM de
 * escritorio escribe vía `java.util.logging`; al compilar para Android, ese mismo
 * backend canaliza al log del sistema, de modo que el comportamiento es
 * equivalente sin depender del SDK de Android.
 */
object Logger {
    private val logger: JdkLogger = JdkLogger.getLogger("plyr")

    fun d(tag: String, message: String) {
        logger.fine("[$tag] $message")
    }

    fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        logger.log(Level.WARNING, "[$tag] $message", throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        logger.log(Level.SEVERE, "[$tag] $message", throwable)
    }
}
