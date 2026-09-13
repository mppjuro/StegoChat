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

    // Z-XOR-owany ciąg: mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13
    const char encToken[] = {
            0x2F, 0x21, 0x36, 0x1D, 0x7B, 0x07, 0x26, 0x0D,
            0x0A, 0x10, 0x03, 0x13, 0x7B, 0x12, 0x03, 0x07,
            0x37, 0x21, 0x1B, 0x7A, 0x1B, 0x2F, 0x1A, 0x17,
            0x36, 0x0F, 0x2A, 0x06, 0x06, 0x2D, 0x06, 0x13,
            0x09, 0x0C, 0x1D, 0x2C, 0x06, 0x18, 0x06, 0x73, 0x71
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