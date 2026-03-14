#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL

Java_com_unisign_unisign_logic_FavoritesLogic_dummyFunction(
        JNIEnv* env,
        jobject /* this */) {

    // Your computer vision and Hebrew translation logic will eventually go here
    std::string result = "Test!";

    return env->NewStringUTF(result.c_str());
}