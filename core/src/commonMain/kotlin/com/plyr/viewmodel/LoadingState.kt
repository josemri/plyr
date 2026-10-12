package com.plyr.viewmodel

/**
 * Registro de operaciones en vuelo: cuántas hay y de quiénes son.
 *
 * Quien empieza una operación coge un token con [begin] y se lo lleva a [end]
 * cuando termina, sea cual sea la `generation` con la que empezó. Mientras
 * quede algún token, hay algo en vuelo. La clase no sabe qué es ese algo: en
 * el `PlayerViewModel` son dos cosas distintas, las cargas (B58) y las
 * transiciones (B59), cada una con su propia instancia.
 *
 * Sustituye a los `Boolean` que había antes, que solo cerraba quien los había
 * puesto y solo si su generación seguía vigente: un `generation++` ajeno (un
 * "añadir a la cola" o un cambio de lista justo mientras se resolvía) lo
 * dejaba clavado en `true`, y con él el spinner y los cinco controles de
 * reproducción deshabilitados para siempre (B58), o bien soltaba el candado
 * de transiciones mientras otra operación seguía trabajando y los saltos
 * volvían a caer en silencio (B59).
 *
 * Quien invalida el trabajo en curso llama a [supersede]: se lleva por delante
 * todos los tokens, porque ya sabe que esos resultados no se van a aplicar. La
 * retirada tardía de un token huérfano no encuentra nada que retirar, así que
 * no puede descuadrar al recuento de una operación más moderna.
 *
 * Se usa desde el hilo principal, como todo el resto de estado del
 * [PlayerViewModel]: los `begin()`/`end()` ocurren en `startAt` y en los
 * `finally` de `startAt` y de la corrutina de `playIndex`, que corren en
 * `Dispatchers.Main`.
 */
class LoadingState {
    private val held = HashSet<Int>()
    private var nextToken = 0

    /** Si hay alguna operación empezada y no terminada. */
    val isLoading: Boolean
        get() = held.isNotEmpty()

    /** Empieza una operación y devuelve el token que la identifica. */
    fun begin(): Int {
        val token = nextToken++
        held.add(token)
        return token
    }

    /** Termina la operación [token]; inocua si [supersede] ya la había retirado. */
    fun end(token: Int) {
        held.remove(token)
    }

    /** Invalida todas las operaciones en vuelo: sus resultados ya no se aplicarán. */
    fun supersede() {
        held.clear()
    }
}
