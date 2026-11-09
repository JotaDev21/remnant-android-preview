package com.liferpg.data.sync

/** The public preview uses the local synchronization implementation. */
object SupabaseConfig {
    const val URL = ""
    const val ANON_KEY = ""

    val configurado: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}
