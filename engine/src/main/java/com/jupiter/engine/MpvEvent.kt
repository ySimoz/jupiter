package com.jupiter.engine

sealed class MpvEvent {
    data object StartFile : MpvEvent()
    data object FileLoaded : MpvEvent()
    data class EndFile(val reason: Int, val errorCode: Int) : MpvEvent() {
        val isEof: Boolean get() = reason == MpvNative.END_FILE_REASON_EOF
        val isError: Boolean get() = reason == MpvNative.END_FILE_REASON_ERROR
    }

    data object Seek : MpvEvent()
    data object PlaybackRestart : MpvEvent()
    data object Shutdown : MpvEvent()
    data class Error(val message: String, val errorCode: Int = 0) : MpvEvent()
}
