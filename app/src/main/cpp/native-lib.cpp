#include <jni.h>
#include <string>

// Funkcja deszyfrująca XOR w locie
std::string deobfuscate(const char* encrypted, int length, char key) {
    std::string decrypted(length, '\0');
    for (int i = 0; i < length; ++i) {
        decrypted[i] = encrypted[i] ^ key;
    }
    return decrypted;
}

// Pobieranie tokenu (klucz XOR: 0x42)
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_stegochat_network_ApiClient_getMatrixToken(JNIEnv* env, jobject) {

    // HEX(mat_aYWKJ9yC50d2WyOxz4MyC649Owl9P3_UQv8d1) ^ 42 =
    // 2F23361D231B1509087B3B0177722670153B0D3A38760F3B0174767B0D352E7B12711D1713347A2673
    const char encToken[] = {
            0x2F, 0x23, 0x36, 0x1D, 0x23, 0x1B, 0x15, 0x09,
            0x08, 0x7B, 0x3B, 0x01, 0x77, 0x72, 0x26, 0x70,
            0x15, 0x3B, 0x0D, 0x3A, 0x38, 0x76, 0x0F, 0x3B,
            0x01, 0x74, 0x76, 0x7B, 0x0D, 0x35, 0x2E, 0x7B,
            0x12, 0x71, 0x1D, 0x17, 0x13, 0x34, 0x7A, 0x26,
            0x73
    };

    std::string token = deobfuscate(encToken, sizeof(encToken), 0x42);

    return env->NewStringUTF(token.c_str());
}

// Pobieranie Room ID (klucz XOR: 0x4B)
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_stegochat_network_ApiClient_getRoomId(JNIEnv* env, jobject /* this */) {

    // Z-XOR-owany ciąg: !PhcUBJdMvnzrXbIrFe:matrix.org
    const char encRoom[] = {
            0x6A, 0x1B, 0x23, 0x28, 0x1E, 0x09, 0x01, 0x2F,
            0x06, 0x3D, 0x25, 0x31, 0x39, 0x13, 0x29, 0x02,
            0x39, 0x0D, 0x2E, 0x71, 0x26, 0x2A, 0x3F, 0x39,
            0x22, 0x33, 0x65, 0x24, 0x39, 0x2C
    };

    std::string roomId = deobfuscate(encRoom, sizeof(encRoom), 0x4B);

    return env->NewStringUTF(roomId.c_str());
}