package com.jupiter.engine

import android.view.Surface

interface MpvEventCallback {
    fun onPropertyChangeString(name: String, value: String?)
    fun onPropertyChangeLong(name: String, value: Long)
    fun onPropertyChangeDouble(name: String, value: Double)
    fun onPropertyChangeBoolean(name: String, value: Boolean)
    fun onEvent(eventId: Int, reason: Int, errorCode: Int)
}

object MpvNative {
    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("avutil")
        System.loadLibrary("swresample")
        System.loadLibrary("swscale")
        System.loadLibrary("avcodec")
        System.loadLibrary("avformat")
        System.loadLibrary("avfilter")
        System.loadLibrary("avdevice")
        System.loadLibrary("mpv")
        System.loadLibrary("jupitermpv")
    }

    const val FORMAT_NONE = 0
    const val FORMAT_STRING = 1
    const val FORMAT_FLAG = 3
    const val FORMAT_INT64 = 4
    const val FORMAT_DOUBLE = 5

    const val EVENT_SHUTDOWN = 1
    const val EVENT_START_FILE = 6
    const val EVENT_END_FILE = 7
    const val EVENT_FILE_LOADED = 8
    const val EVENT_SEEK = 20
    const val EVENT_PLAYBACK_RESTART = 21

    const val END_FILE_REASON_EOF = 0
    const val END_FILE_REASON_STOP = 2
    const val END_FILE_REASON_QUIT = 3
    const val END_FILE_REASON_ERROR = 4
    const val END_FILE_REASON_REDIRECT = 5

    external fun nativeGetMpvVersion(): String
    external fun nativeCreate(callback: MpvEventCallback, configDir: String): Long
    external fun nativeInitialize(handle: Long): Int
    external fun nativeDestroy(handle: Long)
    external fun nativeAttachSurface(handle: Long, surface: Surface): Int
    external fun nativeDetachSurface(handle: Long): Int
    external fun nativeLoadFile(handle: Long, uriOrFd: String, options: Array<String>?): Int
    external fun nativeCommand(handle: Long, args: Array<String>): Int
    external fun nativeSetOptionString(handle: Long, name: String, value: String): Int
    external fun nativeSetPropertyString(handle: Long, name: String, value: String): Int
    external fun nativeSetPropertyLong(handle: Long, name: String, value: Long): Int
    external fun nativeSetPropertyDouble(handle: Long, name: String, value: Double): Int
    external fun nativeSetPropertyBoolean(handle: Long, name: String, value: Boolean): Int
    external fun nativeGetPropertyString(handle: Long, name: String): String?
    external fun nativeGetPropertyLong(handle: Long, name: String): Long
    external fun nativeGetPropertyDouble(handle: Long, name: String): Double
    external fun nativeGetPropertyBoolean(handle: Long, name: String): Boolean
    external fun nativeObserveProperty(handle: Long, name: String, format: Int): Int
}
