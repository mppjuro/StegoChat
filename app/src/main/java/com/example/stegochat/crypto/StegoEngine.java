package com.example.stegochat.crypto;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class StegoEngine {
    private static final int MAGIC_NUMBER = 0x53544547; // "STEG" w HEX
    public static byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
        }
        return baos.toByteArray();
    }

    public static byte[] decompressGzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }

    // Algorytm Fisher-Yates (Knuth shuffle)
    private static int[] generateShuffledIndices(int capacity, long seed) {
        int[] indices = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            indices[i] = i;
        }
        Random random = new Random(seed);
        for (int i = capacity - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            int temp = indices[index];
            indices[index] = indices[i];
            indices[i] = temp;
        }
        return indices;
    }

    public static Bitmap embedData(Bitmap source, byte[] payload, long prngSeed) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        int capacity = width * height * 3; // R, G, B, stąd 3
        // Przygotowujemy dane: [4 bajty MAGIC] [4 bajty DŁUGOŚĆ] [PAYLOAD]
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length);
        buffer.putInt(MAGIC_NUMBER);
        buffer.putInt(payload.length);
        buffer.put(payload);
        byte[] dataToEmbed = buffer.array();

        int totalBitsToEmbed = dataToEmbed.length * 8;
        if (totalBitsToEmbed > capacity) {
            throw new Exception("Obraz jest zbyt mały, by pomieścić te dane!");
        }
        // Zrzut pamięci obrazka do jednowymiarowej tablicy (znacznie szybsze niż podwójna pętla getPixel)
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] shuffledIndices = generateShuffledIndices(capacity, prngSeed);
        // Nadpisywanie bitów
        for (int i = 0; i < totalBitsToEmbed; i++) {
            int index = shuffledIndices[i];
            int pixelIndex = index / 3;
            int channel = index % 3; // 0=Red, 1=Green, 2=Blue
            int bitVal = (dataToEmbed[i / 8] >> (7 - (i % 8))) & 1;
            int p = pixels[pixelIndex];
            int a = Color.alpha(p);
            int r = Color.red(p);
            int g = Color.green(p);
            int b = Color.blue(p);
            switch (channel) {
                case 0: r = (r & 0xFE) | bitVal; break;
                case 1: g = (g & 0xFE) | bitVal; break;
                case 2: b = (b & 0xFE) | bitVal; break;
            }
            pixels[pixelIndex] = Color.argb(a, r, g, b);
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    public static byte[] extractData(Bitmap source, long prngSeed) throws Exception {
        int width = source.getWidth();
        int height = source.getHeight();
        int capacity = width * height * 3;
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] shuffledIndices = generateShuffledIndices(capacity, prngSeed);
        java.util.function.Function<Integer, byte[]> readBits = (numBits) -> {
            byte[] out = new byte[numBits / 8];
            for (int i = 0; i < numBits; i++) {
                // Kontekst wywołania musi utrzymywać globalny wskaźnik przeczytanych bitów.
                // Aby uniknąć problemów ze stanem w lambdzie, zaimplementujemy odczyt liniowo poniżej.
            }
            return out;
        };

        // KROK 1: Odczyt 64 bitów (Magic + Długość)
        int bitsRead = 0;
        ByteBuffer headerBuffer = ByteBuffer.allocate(8);
        byte currentByte = 0;

        for (; bitsRead < 64; bitsRead++) {
            int index = shuffledIndices[bitsRead];
            int pixelIndex = index / 3;
            int channel = index % 3;
            int p = pixels[pixelIndex];

            int bitVal = 0;
            switch (channel) {
                case 0: bitVal = Color.red(p) & 1; break;
                case 1: bitVal = Color.green(p) & 1; break;
                case 2: bitVal = Color.blue(p) & 1; break;
            }

            currentByte = (byte) ((currentByte << 1) | bitVal);
            if ((bitsRead + 1) % 8 == 0) {
                headerBuffer.put(currentByte);
                currentByte = 0;
            }
        }

        headerBuffer.flip();
        int magic = headerBuffer.getInt();

        if (magic != MAGIC_NUMBER) {
            throw new Exception("Brak ukrytych danych lub błędny seed (Magic Number mismatch).");
        }

        int payloadLength = headerBuffer.getInt();
        if (payloadLength <= 0 || (payloadLength * 8 + 64) > capacity) {
            throw new Exception("Uszkodzony nagłówek (nieprawidłowa długość payloadu).");
        }

        // KROK 2: Odczyt Payloadu
        byte[] payload = new byte[payloadLength];
        currentByte = 0;
        int payloadBitsTarget = payloadLength * 8;

        for (int i = 0; i < payloadBitsTarget; i++, bitsRead++) {
            int index = shuffledIndices[bitsRead];
            int pixelIndex = index / 3;
            int channel = index % 3;
            int p = pixels[pixelIndex];

            int bitVal = 0;
            switch (channel) {
                case 0: bitVal = Color.red(p) & 1; break;
                case 1: bitVal = Color.green(p) & 1; break;
                case 2: bitVal = Color.blue(p) & 1; break;
            }

            currentByte = (byte) ((currentByte << 1) | bitVal);
            if ((i + 1) % 8 == 0) {
                payload[i / 8] = currentByte;
                currentByte = 0;
            }
        }
        return payload;
    }
}