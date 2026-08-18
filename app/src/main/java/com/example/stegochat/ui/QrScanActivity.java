package com.example.stegochat.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.stegochat.R;
import com.example.stegochat.StegoApplication;
import com.example.stegochat.crypto.CryptoEngine;
import com.example.stegochat.db.AppDatabase;
import com.example.stegochat.db.Contact;
import com.example.stegochat.domain.MessageProcessor;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.security.PublicKey;
import java.util.UUID;

public class QrScanActivity extends AppCompatActivity {

    private AppDatabase db;
    private final String matrixToken = "TWÓJ_TOKEN_MATRIX";
    private final String matrixRoomId = "!twójRoomId:matrix.org";
    private final long channelSeed = 12345L;

    // Skaner aparatu - wywołanie processScannedData
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    processScannedData(result.getContents());
                }
            });

    // Wybór pliku z galerii - wywołanie processScannedData
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    decodeQrFromGallery(imageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

        db = ((StegoApplication) getApplication()).getDatabase();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Twój Kod QR / Dodaj");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView qrImageView = findViewById(R.id.hugeQrImageView);
        Button scanButton = findViewById(R.id.scanCameraButton);
        Button galleryButton = findViewById(R.id.scanGalleryButton);

        generateAndDisplayQr(qrImageView);

        scanButton.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("Skieruj aparat na kod QR znajomego");
            options.setBeepEnabled(true);
            barcodeLauncher.launch(options);
        });

        galleryButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            galleryLauncher.launch(intent);
        });
    }

    private void generateAndDisplayQr(ImageView imageView) {
        // Generowanie zredukowanego, skompresowanego przez GZIP kodu QR
        String compressedQrData = CryptoEngine.getCompressedPublicKeyQrString();
        if (compressedQrData != null) {
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(compressedQrData, BarcodeFormat.QR_CODE, 1000, 1000);
                imageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void decodeQrFromGallery(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bMap = BitmapFactory.decodeStream(inputStream);
            if (bMap == null) return;

            int intArray[] = new int[bMap.getWidth() * bMap.getHeight()];
            bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
            RGBLuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);

            if (result != null) {
                // Wywołanie processScannedData dla odczytu z galerii
                processScannedData(result.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Nie znaleziono kodu QR na zdjęciu", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Kluczowa metoda obsługująca i walidująca zeskanowany skompresowany ciąg GZIP
     */
    private void processScannedData(String scannedData) {
        try {
            // Dekompresja GZIP i zamiana na standardowy klucz publiczny
            PublicKey pubKey = CryptoEngine.decodeCompressedPublicKeyFromQr(scannedData);
            String standardBase64Key = CryptoEngine.encodePublicKey(pubKey);

            // Przejście do okna nadania nazwy kontaktowi
            showNameContactDialog(standardBase64Key);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "To nie jest prawidłowy kod QR StegoChat!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNameContactDialog(String scannedKeyBase64) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nowy Kontakt");
        builder.setMessage("Podaj nazwę dla dodawanego znajomego:");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Dodaj", (dialog, which) -> {
            String contactName = input.getText().toString().trim();
            if (contactName.isEmpty()) contactName = "Znajomy";

            onQRCodeScanned(scannedKeyBase64, contactName);
        });
        builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void onQRCodeScanned(String scannedPubKeyBase64, String customContactName) {
        new Thread(() -> {
            try {
                Contact newContact = new Contact(scannedPubKeyBase64);
                newContact.name = customContactName;
                newContact.conversationId = UUID.randomUUID().toString();
                db.contactDao().insertContact(newContact);

                runOnUiThread(() -> Toast.makeText(this, "Zapisano kontakt!", Toast.LENGTH_SHORT).show());

                PublicKey myPubKey = CryptoEngine.getMyPublicKey();
                String myPubKeyBase64 = CryptoEngine.encodePublicKey(myPubKey);
                PublicKey recipientKey = CryptoEngine.decodePublicKey(scannedPubKeyBase64);

                MessageProcessor.processAndSendMessage(
                        myPubKeyBase64, newContact.conversationId, recipientKey,
                        matrixRoomId, matrixToken, channelSeed, true, db
                ).join();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}