package com.example.stegochat.crypto;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoEngine {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String ALIAS_IDENTITY = "stegochat_identity";
    private static final int GCM_TAG_LENGTH = 128;
    public static final int GCM_IV_LENGTH = 12;

    /**
     * Generuje parę kluczy RSA-4096 w bezpiecznym środowisku sprzętowym (TEE),
     * jeśli jeszcze nie istnieje.
     */
    public static void generateIdentityKeyIfNotExists() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS_IDENTITY)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ALIAS_IDENTITY,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT |
                            KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setKeySize(4096)
                    .build();
            kpg.initialize(spec);
            kpg.generateKeyPair();
        }
    }

    /**
     * Generuje jednorazowy klucz sesyjny AES-256 do zaszyfrowania payloadu.
     */
    public static SecretKey generateSessionKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Szyfruje dane symetrycznie (AES-256-GCM).
     */
    public static byte[] encryptAESGCM(byte[] plaintext, SecretKey secretKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Deszyfruje dane symetrycznie (AES-256-GCM).
     */
    public static byte[] decryptAESGCM(byte[] ciphertext, SecretKey secretKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Kapsułkuje (szyfruje) klucz sesyjny AES kluczem publicznym odbiorcy (RSA-OAEP).
     */
    public static byte[] encapsulateSessionKey(SecretKey sessionKey, PublicKey recipientPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
        return cipher.doFinal(sessionKey.getEncoded());
    }

    /**
     * Odszyfrowuje klucz sesyjny własnym kluczem prywatnym z Keystore.
     */
    public static byte[] decapsulateSessionKey(byte[] encryptedSessionKey) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS_IDENTITY, null);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedSessionKey);
    }

    /**
     * Podpisuje skrót wiadomości kluczem prywatnym (RSA-PSS).
     */
    public static byte[] signData(byte[] data) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS_IDENTITY, null);

        Signature signature = Signature.getInstance("SHA256withRSA/PSS");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }
}