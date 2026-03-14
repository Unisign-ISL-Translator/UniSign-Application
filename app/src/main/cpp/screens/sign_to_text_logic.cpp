#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL

Java_com_unisign_unisign_logic_SignToTextLogic_processCameraFrame(
        JNIEnv* env,
        jobject /* this */) {

    // Your computer vision and Hebrew translation logic will eventually go here
    std::string result = "Frame processed successfully by C++ engine!";

    return env->NewStringUTF(result.c_str());
}