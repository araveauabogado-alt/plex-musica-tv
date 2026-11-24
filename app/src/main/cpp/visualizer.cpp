// Puente entre Kotlin y projectM. Todas las funciones se llaman desde el hilo de OpenGL.
#include <jni.h>
#include <GLES3/gl3.h>
#include <projectM-4/projectM.h>

#define FN(name) Java_cl_plexmusica_tv_VisualizerNative_##name

extern "C" {

JNIEXPORT jlong JNICALL FN(create)(JNIEnv *, jclass, jint width, jint height, jint meshW, jint meshH, jint fps) {
    projectm_handle pm = projectm_create();
    if (pm == nullptr) return 0;
    projectm_set_window_size(pm, (size_t) width, (size_t) height);
    // Rendimiento antes que calidad: malla gruesa y fps limitados.
    projectm_set_mesh_size(pm, (size_t) meshW, (size_t) meshH);
    projectm_set_fps(pm, fps);
    // El cambio de preset lo controla la app.
    projectm_set_preset_duration(pm, 1000000.0);
    projectm_set_hard_cut_enabled(pm, false);
    projectm_set_preset_locked(pm, true);
    projectm_set_soft_cut_duration(pm, 2.0);
    projectm_set_aspect_correction(pm, true);
    return (jlong) pm;
}

JNIEXPORT void JNICALL FN(resize)(JNIEnv *, jclass, jlong handle, jint width, jint height) {
    if (handle == 0) return;
    projectm_set_window_size((projectm_handle) handle, (size_t) width, (size_t) height);
}

JNIEXPORT void JNICALL FN(loadPreset)(JNIEnv *env, jclass, jlong handle, jstring data, jboolean smooth) {
    if (handle == 0 || data == nullptr) return;
    const char *chars = env->GetStringUTFChars(data, nullptr);
    projectm_load_preset_data((projectm_handle) handle, chars, smooth == JNI_TRUE);
    env->ReleaseStringUTFChars(data, chars);
}

JNIEXPORT void JNICALL FN(addPcm)(JNIEnv *env, jclass, jlong handle, jfloatArray samples, jint frames) {
    if (handle == 0 || frames <= 0) return;
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    projectm_pcm_add_float((projectm_handle) handle, data, (unsigned int) frames, PROJECTM_STEREO);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT void JNICALL FN(render)(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
    projectm_opengl_render_frame((projectm_handle) handle);
}

JNIEXPORT void JNICALL FN(destroy)(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    projectm_destroy((projectm_handle) handle);
}

}
