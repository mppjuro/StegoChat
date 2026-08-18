package com.example.stegochat.domain;

import java.nio.ByteBuffer;

import com.example.stegochat.crypto.CryptoEngine;

/**
 * Reprezentuje surową, skompresowaną strukturę binarną wstrzykiwaną do obrazka.
 * Struktura:
 * [1 bajt: Wersja]
 * [12 bajtów: GCM IV]
 * [2 bajty: Długość zaszyfrowanego klucza (N)]
 * [N bajtów: Zaszyfrowany klucz sesyjny AES]
 * [2 bajty: Długość podpisu (M)]
 * [M bajtów: Podpis cyfrowy RSA]
 * [Reszta: Ciphertext (wiadomość AES-GCM)]
 */
public class StegoPayload {

    public static final byte VERSION = 1;

    public byte[] iv;
    public byte[] encryptedSessionKey;
    public byte[] signature;
    public byte[] ciphertext;

    public StegoPayload(byte[] iv, byte[] encryptedSessionKey, byte[] signature, byte[] ciphertext) {
        this.iv = iv;
        this.encryptedSessionKey = encryptedSessionKey;
        this.signature = signature;
        this.ciphertext = ciphertext;
    }

    /**
     * Zrzuca obiekt do jednowymiarowej tablicy bajtów gotowej do wstrzyknięcia.
     */
    public byte[] toBytes() {
        int totalLength = 1 + CryptoEngine.GCM_IV_LENGTH +
                2 + encryptedSessionKey.length +
                2 + signature.length +
                ciphertext.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.put(VERSION);
        buffer.put(iv);
        buffer.putShort((short) encryptedSessionKey.length);
        buffer.put(encryptedSessionKey);
        buffer.putShort((short) signature.length);
        buffer.put(signature);
        buffer.put(ciphertext);

        return buffer.array();
    }

    /**
     * Odtwarza obiekt z surowych bajtów wyciągniętych z obrazka.
     */
    public static StegoPayload fromBytes(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte version = buffer.get();
        if (version != VERSION) {
            throw new Exception("Nieobsługiwana wersja protokołu StegoPayload: " + version);
        }

        byte[] iv = new byte[CryptoEngine.GCM_IV_LENGTH];
        buffer.get(iv);

        short keyLen = buffer.getShort();
        byte[] encKey = new byte[keyLen];
        buffer.get(encKey);

        short sigLen = buffer.getShort();
        byte[] sig = new byte[sigLen];
        buffer.get(sig);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        return new StegoPayload(iv, encKey, sig, ciphertext);
    }
}