package com.plyr.core

/**
 * Configuración de red que la plataforma inyecta al arrancar.
 *
 * En Android los valores salen de `BuildConfig` (que a su vez puede recibir
 * overrides de `local.properties`/variables de entorno en tiempo de build); en
 * escritorio se fijarán al arrancar la app. El código compartido (`:core`) lee
 * siempre de aquí, sin depender del SDK de Android.
 */
object NetworkConfig {
    var supabaseUrl: String = "https://mpfioblwpghlkulsryzu.supabase.co"
    var supabaseAnonKey: String = "sb_publishable_CBiixr2FXsfTXwMhRFB5Qg_c5T7ePQ8"
    var youtubeRestrictedModeCookie: String = "PREF=f2=8000000"
}
