#ifndef JUPITER_MPV_BRIDGE_H
#define JUPITER_MPV_BRIDGE_H

#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <mpv/client.h>

typedef struct MpvContext {
    mpv_handle *mpv;
    pthread_t event_thread;
    atomic_bool running;
    bool thread_started;
    pthread_mutex_t surface_mutex;
    jobject callback_global_ref;
    jmethodID on_prop_string;
    jmethodID on_prop_long;
    jmethodID on_prop_double;
    jmethodID on_prop_bool;
    jmethodID on_event;
    jobject surface_global_ref;
} MpvContext;

#endif /* JUPITER_MPV_BRIDGE_H */
