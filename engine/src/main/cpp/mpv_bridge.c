#include "mpv_bridge.h"
#include "log_util.h"

#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef int (*av_jni_set_java_vm_fn)(void *vm, void *log_ctx);

static JavaVM *g_vm = NULL;

static MpvContext *ctx_from_handle(jlong handle) {
    if (handle == 0) {
        return NULL;
    }
    return (MpvContext *)(intptr_t)handle;
}

static void check_and_clear_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

/*
 * Convert arbitrary UTF-8 (including 4-byte emoji or malformed tag bytes) into
 * UTF-16 so ART CheckJNI never aborts on non-Modified-UTF-8 sequences.
 */
static jstring cstr_to_jstring(JNIEnv *env, const char *str) {
    if (str == NULL) {
        return NULL;
    }
    if (str[0] == '\0') {
        jchar empty = 0;
        jstring empty_str = (*env)->NewString(env, &empty, 0);
        if (empty_str == NULL) {
            check_and_clear_exception(env);
        }
        return empty_str;
    }

    size_t len = strlen(str);
    jchar *utf16 = malloc((len + 1) * sizeof(jchar));
    if (utf16 == NULL) {
        return NULL;
    }

    size_t out = 0;
    const unsigned char *p = (const unsigned char *)str;
    const unsigned char *end = p + len;

    while (p < end) {
        uint32_t cp = 0xFFFD;
        unsigned char c = p[0];

        if (c < 0x80) {
            cp = c;
            p += 1;
        } else if ((c & 0xE0) == 0xC0 && p + 1 < end &&
                   (p[1] & 0xC0) == 0x80) {
            uint32_t v = ((uint32_t)(c & 0x1F) << 6) | (uint32_t)(p[1] & 0x3F);
            if (v >= 0x80) {
                cp = v;
                p += 2;
            } else {
                p += 1;
            }
        } else if ((c & 0xF0) == 0xE0 && p + 2 < end &&
                   (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80) {
            uint32_t v = ((uint32_t)(c & 0x0F) << 12) |
                         ((uint32_t)(p[1] & 0x3F) << 6) |
                         (uint32_t)(p[2] & 0x3F);
            if (v >= 0x800 && !(v >= 0xD800 && v <= 0xDFFF)) {
                cp = v;
                p += 3;
            } else {
                p += 1;
            }
        } else if ((c & 0xF8) == 0xF0 && p + 3 < end &&
                   (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80 &&
                   (p[3] & 0xC0) == 0x80) {
            uint32_t v = ((uint32_t)(c & 0x07) << 18) |
                         ((uint32_t)(p[1] & 0x3F) << 12) |
                         ((uint32_t)(p[2] & 0x3F) << 6) |
                         (uint32_t)(p[3] & 0x3F);
            if (v >= 0x10000 && v <= 0x10FFFF) {
                cp = v;
                p += 4;
            } else {
                p += 1;
            }
        } else {
            p += 1;
        }

        if (cp <= 0xFFFF) {
            utf16[out++] = (jchar)cp;
        } else {
            cp -= 0x10000;
            utf16[out++] = (jchar)(0xD800 + (cp >> 10));
            utf16[out++] = (jchar)(0xDC00 + (cp & 0x3FF));
        }
    }

    jstring result = (*env)->NewString(env, utf16, (jsize)out);
    free(utf16);
    if (result == NULL) {
        check_and_clear_exception(env);
    }
    return result;
}

static void observe_core_properties(mpv_handle *mpv) {
    struct {
        const char *name;
        mpv_format format;
    } props[] = {
        { "time-pos", MPV_FORMAT_DOUBLE },
        { "duration", MPV_FORMAT_DOUBLE },
        { "pause", MPV_FORMAT_FLAG },
        { "paused-for-cache", MPV_FORMAT_FLAG },
        { "cache-buffering-state", MPV_FORMAT_INT64 },
        { "speed", MPV_FORMAT_DOUBLE },
        { "video-params/aspect", MPV_FORMAT_DOUBLE },
        { "video-params/w", MPV_FORMAT_INT64 },
        { "video-params/h", MPV_FORMAT_INT64 },
        { "track-list", MPV_FORMAT_STRING },
        { "chapter-list", MPV_FORMAT_STRING },
        { "chapter", MPV_FORMAT_INT64 },
        { "eof-reached", MPV_FORMAT_FLAG },
        { "media-title", MPV_FORMAT_STRING },
    };

    size_t count = sizeof(props) / sizeof(props[0]);
    for (size_t i = 0; i < count; i++) {
        int err = mpv_observe_property(mpv, 0, props[i].name, props[i].format);
        if (err < 0) {
            LOGW("Failed to observe property %s: %s", props[i].name, mpv_error_string(err));
        }
    }
}

static void dispatch_property_change(JNIEnv *env, MpvContext *ctx, const mpv_event_property *prop) {
    if (prop == NULL || prop->name == NULL) {
        return;
    }

    jstring jname = cstr_to_jstring(env, prop->name);
    if (jname == NULL) {
        check_and_clear_exception(env);
        return;
    }

    switch (prop->format) {
        case MPV_FORMAT_STRING: {
            const char *val = (prop->data != NULL) ? *(const char *const *)prop->data : NULL;
            jstring jval = (val != NULL) ? cstr_to_jstring(env, val) : NULL;
            if (val != NULL && jval == NULL) {
                break;
            }
            (*env)->CallVoidMethod(env, ctx->callback_global_ref, ctx->on_prop_string, jname, jval);
            if (jval != NULL) {
                (*env)->DeleteLocalRef(env, jval);
            }
            check_and_clear_exception(env);
            break;
        }
        case MPV_FORMAT_NONE:
            (*env)->CallVoidMethod(env, ctx->callback_global_ref, ctx->on_prop_string, jname, NULL);
            check_and_clear_exception(env);
            break;
        case MPV_FORMAT_FLAG:
            if (prop->data != NULL) {
                int flag = *(const int *)prop->data;
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_prop_bool,
                    jname,
                    flag ? JNI_TRUE : JNI_FALSE
                );
                check_and_clear_exception(env);
            }
            break;
        case MPV_FORMAT_INT64:
            if (prop->data != NULL) {
                int64_t val = *(const int64_t *)prop->data;
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_prop_long,
                    jname,
                    (jlong)val
                );
                check_and_clear_exception(env);
            }
            break;
        case MPV_FORMAT_DOUBLE:
            if (prop->data != NULL) {
                double val = *(const double *)prop->data;
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_prop_double,
                    jname,
                    (jdouble)val
                );
                check_and_clear_exception(env);
            }
            break;
        default:
            break;
    }

    (*env)->DeleteLocalRef(env, jname);
}

static void *mpv_event_loop(void *arg) {
    MpvContext *ctx = (MpvContext *)arg;
    if (ctx == NULL || g_vm == NULL) {
        return NULL;
    }

    JNIEnv *env = NULL;
    jint attach_res = (*g_vm)->AttachCurrentThread(g_vm, &env, NULL);
    if (attach_res != JNI_OK || env == NULL) {
        LOGE("AttachCurrentThread failed: %d", attach_res);
        return NULL;
    }

    while (atomic_load(&ctx->running)) {
        mpv_event *ev = mpv_wait_event(ctx->mpv, -1);
        if (!atomic_load(&ctx->running)) {
            break;
        }
        if (ev == NULL || ev->event_id == MPV_EVENT_NONE) {
            continue;
        }

        switch (ev->event_id) {
            case MPV_EVENT_PROPERTY_CHANGE:
                dispatch_property_change(env, ctx, (const mpv_event_property *)ev->data);
                break;

            case MPV_EVENT_START_FILE:
            case MPV_EVENT_FILE_LOADED:
            case MPV_EVENT_SEEK:
            case MPV_EVENT_PLAYBACK_RESTART:
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_event,
                    (jint)ev->event_id,
                    0,
                    0
                );
                check_and_clear_exception(env);
                break;

            case MPV_EVENT_END_FILE: {
                const mpv_event_end_file *eof = (const mpv_event_end_file *)ev->data;
                jint reason = eof != NULL ? (jint)eof->reason : 0;
                jint error_code = eof != NULL ? (jint)eof->error : 0;
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_event,
                    (jint)ev->event_id,
                    reason,
                    error_code
                );
                check_and_clear_exception(env);
                break;
            }

            case MPV_EVENT_SHUTDOWN:
                (*env)->CallVoidMethod(
                    env,
                    ctx->callback_global_ref,
                    ctx->on_event,
                    (jint)MPV_EVENT_SHUTDOWN,
                    0,
                    0
                );
                check_and_clear_exception(env);
                atomic_store(&ctx->running, false);
                break;

            default:
                break;
        }
    }

    (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;

    void *sym = dlsym(RTLD_DEFAULT, "av_jni_set_java_vm");
    if (sym == NULL) {
        void *avcodec = dlopen("libavcodec.so", RTLD_NOW | RTLD_NOLOAD);
        if (avcodec != NULL) {
            sym = dlsym(avcodec, "av_jni_set_java_vm");
            dlclose(avcodec);
        }
    }

    if (sym != NULL) {
        av_jni_set_java_vm_fn set_java_vm = NULL;
        memcpy(&set_java_vm, &sym, sizeof(set_java_vm));
        int rc = set_java_vm(vm, NULL);
        if (rc == 0) {
            LOGI("av_jni_set_java_vm succeeded");
        } else {
            LOGW("av_jni_set_java_vm returned %d", rc);
        }
    } else {
        LOGW("av_jni_set_java_vm symbol not found; hardware MediaCodec decoding may be unavailable");
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_jupiter_engine_MpvNative_nativeGetMpvVersion(JNIEnv *env, jobject thiz) {
    (void)thiz;
    unsigned long version = mpv_client_api_version();
    unsigned long major = version >> 16;
    unsigned long minor = version & 0xFFFFUL;

    char buf[32];
    snprintf(buf, sizeof(buf), "%lu.%lu", major, minor);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jlong JNICALL
Java_com_jupiter_engine_MpvNative_nativeCreate(
    JNIEnv *env,
    jobject thiz,
    jobject callback,
    jstring config_dir
) {
    (void)thiz;
    if (callback == NULL) {
        LOGE("nativeCreate called with null callback");
        return 0;
    }

    if (g_vm == NULL) {
        if ((*env)->GetJavaVM(env, &g_vm) != JNI_OK) {
            LOGE("Failed to obtain JavaVM");
            return 0;
        }
    }

    jclass cb_class = (*env)->GetObjectClass(env, callback);
    if (cb_class == NULL) {
        check_and_clear_exception(env);
        return 0;
    }

    jmethodID on_prop_string = (*env)->GetMethodID(
        env, cb_class, "onPropertyChangeString", "(Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID on_prop_long = (*env)->GetMethodID(
        env, cb_class, "onPropertyChangeLong", "(Ljava/lang/String;J)V");
    jmethodID on_prop_double = (*env)->GetMethodID(
        env, cb_class, "onPropertyChangeDouble", "(Ljava/lang/String;D)V");
    jmethodID on_prop_bool = (*env)->GetMethodID(
        env, cb_class, "onPropertyChangeBoolean", "(Ljava/lang/String;Z)V");
    jmethodID on_event = (*env)->GetMethodID(
        env, cb_class, "onEvent", "(III)V");

    (*env)->DeleteLocalRef(env, cb_class);

    if (on_prop_string == NULL || on_prop_long == NULL || on_prop_double == NULL ||
        on_prop_bool == NULL || on_event == NULL) {
        check_and_clear_exception(env);
        LOGE("Failed to resolve MpvEventCallback methods");
        return 0;
    }

    MpvContext *ctx = calloc(1, sizeof(MpvContext));
    if (ctx == NULL) {
        LOGE("Out of memory allocating MpvContext");
        return 0;
    }

    if (pthread_mutex_init(&ctx->surface_mutex, NULL) != 0) {
        free(ctx);
        return 0;
    }

    ctx->callback_global_ref = (*env)->NewGlobalRef(env, callback);
    if (ctx->callback_global_ref == NULL) {
        check_and_clear_exception(env);
        pthread_mutex_destroy(&ctx->surface_mutex);
        free(ctx);
        return 0;
    }

    ctx->on_prop_string = on_prop_string;
    ctx->on_prop_long = on_prop_long;
    ctx->on_prop_double = on_prop_double;
    ctx->on_prop_bool = on_prop_bool;
    ctx->on_event = on_event;
    atomic_init(&ctx->running, false);

    ctx->mpv = mpv_create();
    if (ctx->mpv == NULL) {
        LOGE("mpv_create() returned NULL");
        (*env)->DeleteGlobalRef(env, ctx->callback_global_ref);
        pthread_mutex_destroy(&ctx->surface_mutex);
        free(ctx);
        return 0;
    }

    if (config_dir != NULL) {
        const char *cfg = (*env)->GetStringUTFChars(env, config_dir, NULL);
        if (cfg != NULL) {
            if (cfg[0] != '\0') {
                mpv_set_option_string(ctx->mpv, "config-dir", cfg);
            }
            (*env)->ReleaseStringUTFChars(env, config_dir, cfg);
        } else {
            check_and_clear_exception(env);
        }
    }

    mpv_set_option_string(ctx->mpv, "config", "yes");
    mpv_set_option_string(ctx->mpv, "vo", "null");
    mpv_set_option_string(ctx->mpv, "ao", "audiotrack,opensles");
    mpv_set_option_string(ctx->mpv, "hwdec", "mediacodec-copy");
    mpv_set_option_string(ctx->mpv, "hwdec-codecs", "all");
    mpv_set_option_string(ctx->mpv, "gpu-context", "android");
    mpv_set_option_string(ctx->mpv, "opengl-es", "yes");
    mpv_set_option_string(ctx->mpv, "keep-open", "yes");
    mpv_set_option_string(ctx->mpv, "force-window", "no");

    return (jlong)(intptr_t)ctx;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeInitialize(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    int err = mpv_initialize(ctx->mpv);
    if (err < 0) {
        LOGE("mpv_initialize failed: %s (%d)", mpv_error_string(err), err);
        return err;
    }

    observe_core_properties(ctx->mpv);

    atomic_store(&ctx->running, true);
    int rc = pthread_create(&ctx->event_thread, NULL, mpv_event_loop, ctx);
    if (rc != 0) {
        atomic_store(&ctx->running, false);
        LOGE("pthread_create failed: %d", rc);
        return MPV_ERROR_GENERIC;
    }
    ctx->thread_started = true;

    return MPV_ERROR_SUCCESS;
}

JNIEXPORT void JNICALL
Java_com_jupiter_engine_MpvNative_nativeDestroy(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL) {
        return;
    }

    atomic_store(&ctx->running, false);

    if (ctx->mpv != NULL && ctx->thread_started) {
        mpv_wakeup(ctx->mpv);
        pthread_join(ctx->event_thread, NULL);
        ctx->thread_started = false;
    }

    pthread_mutex_lock(&ctx->surface_mutex);
    if (ctx->mpv != NULL) {
        int64_t zero = 0;
        mpv_set_option_string(ctx->mpv, "vo", "null");
        mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &zero);
    }
    if (ctx->surface_global_ref != NULL) {
        (*env)->DeleteGlobalRef(env, ctx->surface_global_ref);
        ctx->surface_global_ref = NULL;
    }
    pthread_mutex_unlock(&ctx->surface_mutex);

    if (ctx->mpv != NULL) {
        mpv_terminate_destroy(ctx->mpv);
        ctx->mpv = NULL;
    }

    if (ctx->callback_global_ref != NULL) {
        (*env)->DeleteGlobalRef(env, ctx->callback_global_ref);
        ctx->callback_global_ref = NULL;
    }

    pthread_mutex_destroy(&ctx->surface_mutex);
    free(ctx);
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeAttachSurface(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jobject surface
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || surface == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    pthread_mutex_lock(&ctx->surface_mutex);

    if (ctx->surface_global_ref != NULL) {
        int64_t zero = 0;
        mpv_set_option_string(ctx->mpv, "vo", "null");
        mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &zero);
        (*env)->DeleteGlobalRef(env, ctx->surface_global_ref);
        ctx->surface_global_ref = NULL;
    }

    ctx->surface_global_ref = (*env)->NewGlobalRef(env, surface);
    if (ctx->surface_global_ref == NULL) {
        check_and_clear_exception(env);
        pthread_mutex_unlock(&ctx->surface_mutex);
        return MPV_ERROR_NOMEM;
    }

    int64_t wid = (int64_t)(intptr_t)ctx->surface_global_ref;
    int err = mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (err >= 0) {
        err = mpv_set_option_string(ctx->mpv, "vo", "gpu");
    }
    if (err >= 0) {
        mpv_set_option_string(ctx->mpv, "vid", "auto");
    } else {
        int64_t zero = 0;
        mpv_set_option_string(ctx->mpv, "vo", "null");
        mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &zero);
        (*env)->DeleteGlobalRef(env, ctx->surface_global_ref);
        ctx->surface_global_ref = NULL;
    }

    pthread_mutex_unlock(&ctx->surface_mutex);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeDetachSurface(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    pthread_mutex_lock(&ctx->surface_mutex);

    int err = mpv_set_option_string(ctx->mpv, "vo", "null");
    int64_t zero = 0;
    int err2 = mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &zero);

    if (ctx->surface_global_ref != NULL) {
        (*env)->DeleteGlobalRef(env, ctx->surface_global_ref);
        ctx->surface_global_ref = NULL;
    }

    pthread_mutex_unlock(&ctx->surface_mutex);
    return err < 0 ? err : err2;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeLoadFile(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring uri_or_fd,
    jobjectArray options
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || uri_or_fd == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *uri = (*env)->GetStringUTFChars(env, uri_or_fd, NULL);
    if (uri == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }

    jsize opt_count = (options != NULL) ? (*env)->GetArrayLength(env, options) : 0;
    if (opt_count <= 0) {
        const char *cmd[] = { "loadfile", uri, "replace", NULL };
        int err = mpv_command(ctx->mpv, cmd);
        (*env)->ReleaseStringUTFChars(env, uri_or_fd, uri);
        return err;
    }

    jstring *j_opts = calloc((size_t)opt_count, sizeof(jstring));
    const char **c_opts = calloc((size_t)opt_count, sizeof(char *));
    if (j_opts == NULL || c_opts == NULL) {
        free(j_opts);
        free(c_opts);
        (*env)->ReleaseStringUTFChars(env, uri_or_fd, uri);
        return MPV_ERROR_NOMEM;
    }

    size_t total_len = 0;
    jsize valid_count = 0;
    for (jsize i = 0; i < opt_count; i++) {
        jstring jelem = (jstring)(*env)->GetObjectArrayElement(env, options, i);
        if ((*env)->ExceptionCheck(env)) {
            check_and_clear_exception(env);
            if (jelem != NULL) {
                (*env)->DeleteLocalRef(env, jelem);
            }
            for (jsize k = 0; k < valid_count; k++) {
                (*env)->ReleaseStringUTFChars(env, j_opts[k], c_opts[k]);
                (*env)->DeleteLocalRef(env, j_opts[k]);
            }
            free(j_opts);
            free(c_opts);
            (*env)->ReleaseStringUTFChars(env, uri_or_fd, uri);
            return MPV_ERROR_INVALID_PARAMETER;
        }
        if (jelem == NULL) {
            continue;
        }
        const char *celem = (*env)->GetStringUTFChars(env, jelem, NULL);
        if (celem == NULL) {
            check_and_clear_exception(env);
            (*env)->DeleteLocalRef(env, jelem);
            for (jsize k = 0; k < valid_count; k++) {
                (*env)->ReleaseStringUTFChars(env, j_opts[k], c_opts[k]);
                (*env)->DeleteLocalRef(env, j_opts[k]);
            }
            free(j_opts);
            free(c_opts);
            (*env)->ReleaseStringUTFChars(env, uri_or_fd, uri);
            return MPV_ERROR_NOMEM;
        }
        if (celem[0] == '\0') {
            (*env)->ReleaseStringUTFChars(env, jelem, celem);
            (*env)->DeleteLocalRef(env, jelem);
            continue;
        }
        j_opts[valid_count] = jelem;
        c_opts[valid_count] = celem;
        total_len += strlen(celem) + (valid_count > 0 ? 1 : 0);
        valid_count++;
    }

    int err;
    if (valid_count == 0) {
        const char *cmd[] = { "loadfile", uri, "replace", NULL };
        err = mpv_command(ctx->mpv, cmd);
    } else {
        char *joined = malloc(total_len + 1);
        if (joined == NULL) {
            err = MPV_ERROR_NOMEM;
        } else {
            size_t pos = 0;
            for (jsize i = 0; i < valid_count; i++) {
                if (i > 0) {
                    joined[pos++] = ',';
                }
                size_t slen = strlen(c_opts[i]);
                memcpy(joined + pos, c_opts[i], slen);
                pos += slen;
            }
            joined[pos] = '\0';

            const char *cmd[] = { "loadfile", uri, "replace", "-1", joined, NULL };
            err = mpv_command(ctx->mpv, cmd);
            free(joined);
        }
    }

    for (jsize i = 0; i < valid_count; i++) {
        (*env)->ReleaseStringUTFChars(env, j_opts[i], c_opts[i]);
        (*env)->DeleteLocalRef(env, j_opts[i]);
    }
    free(j_opts);
    free(c_opts);
    (*env)->ReleaseStringUTFChars(env, uri_or_fd, uri);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeCommand(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jobjectArray args
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || args == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    jsize len = (*env)->GetArrayLength(env, args);
    if (len <= 0) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    jstring *j_elems = calloc((size_t)len, sizeof(jstring));
    const char **c_args = calloc((size_t)len + 1, sizeof(char *));
    if (j_elems == NULL || c_args == NULL) {
        free(j_elems);
        free(c_args);
        return MPV_ERROR_NOMEM;
    }

    jsize acquired = 0;
    for (jsize i = 0; i < len; i++) {
        jstring jelem = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        if ((*env)->ExceptionCheck(env)) {
            check_and_clear_exception(env);
            if (jelem != NULL) {
                (*env)->DeleteLocalRef(env, jelem);
                jelem = NULL;
            }
        }
        if (jelem == NULL) {
            for (jsize k = 0; k < acquired; k++) {
                (*env)->ReleaseStringUTFChars(env, j_elems[k], c_args[k]);
                (*env)->DeleteLocalRef(env, j_elems[k]);
            }
            free(j_elems);
            free(c_args);
            return MPV_ERROR_INVALID_PARAMETER;
        }
        const char *celem = (*env)->GetStringUTFChars(env, jelem, NULL);
        if (celem == NULL) {
            check_and_clear_exception(env);
            (*env)->DeleteLocalRef(env, jelem);
            for (jsize k = 0; k < acquired; k++) {
                (*env)->ReleaseStringUTFChars(env, j_elems[k], c_args[k]);
                (*env)->DeleteLocalRef(env, j_elems[k]);
            }
            free(j_elems);
            free(c_args);
            return MPV_ERROR_NOMEM;
        }
        j_elems[i] = jelem;
        c_args[i] = celem;
        acquired++;
    }
    c_args[len] = NULL;

    int err = mpv_command(ctx->mpv, c_args);

    for (jsize i = 0; i < acquired; i++) {
        (*env)->ReleaseStringUTFChars(env, j_elems[i], c_args[i]);
        (*env)->DeleteLocalRef(env, j_elems[i]);
    }
    free(j_elems);
    free(c_args);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeSetOptionString(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jstring value
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL || value == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }
    const char *cvalue = (*env)->GetStringUTFChars(env, value, NULL);
    if (cvalue == NULL) {
        check_and_clear_exception(env);
        (*env)->ReleaseStringUTFChars(env, name, cname);
        return MPV_ERROR_NOMEM;
    }

    int err = mpv_set_option_string(ctx->mpv, cname, cvalue);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    (*env)->ReleaseStringUTFChars(env, value, cvalue);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeSetPropertyString(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jstring value
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL || value == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }
    const char *cvalue = (*env)->GetStringUTFChars(env, value, NULL);
    if (cvalue == NULL) {
        check_and_clear_exception(env);
        (*env)->ReleaseStringUTFChars(env, name, cname);
        return MPV_ERROR_NOMEM;
    }

    int err = mpv_set_property_string(ctx->mpv, cname, cvalue);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    (*env)->ReleaseStringUTFChars(env, value, cvalue);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeSetPropertyLong(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jlong value
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }

    int64_t val = (int64_t)value;
    int err = mpv_set_property(ctx->mpv, cname, MPV_FORMAT_INT64, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeSetPropertyDouble(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jdouble value
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }

    double val = (double)value;
    int err = mpv_set_property(ctx->mpv, cname, MPV_FORMAT_DOUBLE, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeSetPropertyBoolean(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jboolean value
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }

    int val = (value == JNI_TRUE) ? 1 : 0;
    int err = mpv_set_property(ctx->mpv, cname, MPV_FORMAT_FLAG, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return err;
}

JNIEXPORT jstring JNICALL
Java_com_jupiter_engine_MpvNative_nativeGetPropertyString(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return NULL;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return NULL;
    }

    char *val = mpv_get_property_string(ctx->mpv, cname);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    if (val == NULL) {
        return NULL;
    }

    jstring jval = cstr_to_jstring(env, val);
    mpv_free(val);
    return jval;
}

JNIEXPORT jlong JNICALL
Java_com_jupiter_engine_MpvNative_nativeGetPropertyLong(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return 0;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return 0;
    }

    int64_t val = 0;
    int err = mpv_get_property(ctx->mpv, cname, MPV_FORMAT_INT64, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    if (err < 0) {
        return 0;
    }
    return (jlong)val;
}

JNIEXPORT jdouble JNICALL
Java_com_jupiter_engine_MpvNative_nativeGetPropertyDouble(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return 0.0;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return 0.0;
    }

    double val = 0.0;
    int err = mpv_get_property(ctx->mpv, cname, MPV_FORMAT_DOUBLE, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    if (err < 0) {
        return 0.0;
    }
    return (jdouble)val;
}

JNIEXPORT jboolean JNICALL
Java_com_jupiter_engine_MpvNative_nativeGetPropertyBoolean(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return JNI_FALSE;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return JNI_FALSE;
    }

    int val = 0;
    int err = mpv_get_property(ctx->mpv, cname, MPV_FORMAT_FLAG, &val);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    if (err < 0) {
        return JNI_FALSE;
    }
    return val ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_jupiter_engine_MpvNative_nativeObserveProperty(
    JNIEnv *env,
    jobject thiz,
    jlong handle,
    jstring name,
    jint format
) {
    (void)thiz;
    MpvContext *ctx = ctx_from_handle(handle);
    if (ctx == NULL || ctx->mpv == NULL || name == NULL) {
        return MPV_ERROR_INVALID_PARAMETER;
    }

    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    if (cname == NULL) {
        check_and_clear_exception(env);
        return MPV_ERROR_NOMEM;
    }

    int err = mpv_observe_property(ctx->mpv, 0, cname, (mpv_format)format);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return err;
}
