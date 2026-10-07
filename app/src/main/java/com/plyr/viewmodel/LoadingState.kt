package com.plyr.viewmodel

/**
 * Registro de operaciones de carga en vuelo: cuántas hay y de quiénes son.
 *
 * Quien empieza a resolver URLs coge un token con [begin] y se lo lleva a [end]
 * cuando termina, sea cual sea la `generation` con la que empezó. Mientras
 * quede algún token, se está cargando.
 *
 * Sustituye al `Boolean` que había antes, que solo cerraba quien lo había
 * puesto y solo si su generación seguía vigente: un `generation++` ajeno (un
 * "añadir a la cola" o un cambio de lista justo mientras se resolvía) lo
 * dejaba clavado en `true`, y con él el spinner y los cinco controles de
 * reproducción deshabilitados para siempre (B58).
 *
 * Quien invalida el trabajo en curso llama a [supersede]: se lleva por delante
 * todos los tokens, porque ya sabe que esos resultados no se van a aplicar. La
 * retirada tardía de un token huérfano no encuentra nada que retirar, así que
 * no puede descuadrar al recuento de una operación más moderna.
 *
 * Se usa desde el hilo principal, como todo el resto de estado del
 * [PlayerViewModel]: los `begin()`/`end()` ocurren en `startAt` y en el `finally`
 * de la corrutina de `playIndex`, que corren en `Dispatchers.Main`.
 */
class LoadingState {
    private val held = HashSet<Int>()
    private var nextToken = 0

    /** Si hay alguna operación de carga aún no terminada. */
    val isLoading: Boolean
        get() = held.isNotEmpty()

    /** Empieza una operación de carga y devuelve el token que la identifica. */
    fun begin(): Int {
        val token = nextToken++
        held.add(token)
        return token
    }

    /** Termina la operación [token]; inocua si [supersede] ya la había retirado. */
    fun end(token: Int) {
        held.remove(token)
    }

    /** Invalida todas las cargas en vuelo: sus resultados ya no se aplicarán. */
    fun supersede() {
        held.clear()
    }
}
