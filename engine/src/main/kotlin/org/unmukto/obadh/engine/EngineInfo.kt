package org.unmukto.obadh.engine

object EngineInfo {
    fun version(): String = String(ObadhNative.engineVersion(), Charsets.UTF_8)
    fun abiVersion(): Int = ObadhNative.abiVersion()
}
