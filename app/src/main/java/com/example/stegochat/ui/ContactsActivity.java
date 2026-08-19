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
import androidx.appcompat.widget.Toolbar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.security.PublicKey;
import java.util.UUID;

public class ContactsActivity extends AppCompatActivity {

    private AppDatabase db;
    private ImageView myQrImageView;
    private RecyclerView contactsRecyclerView;

    private final String matrixToken = "mct_9EdOHRAQ9PAEucY8YmXUtMhDDoDQKN_nDZD13";
    private final String matrixRoomId = "!PhcUBJdMvnzrXbIrFe:matrix.org";
    private final long channelSeed = 12345L;

    // Skaner aparatu
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    showNameContactDialog(result.getContents());
                }
            });

    // Wybór pliku z galerii
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
        setContentView(R.layout.activity_contacts);

        db = ((StegoApplication) getApplication()).getDatabase();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Kontakty");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Deklaracja i powiązanie RecyclerView z plikiem XML (activity_contacts.xml)
        RecyclerView recyclerView = findViewById(R.id.contactsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ContactsAdapter adapter = new ContactsAdapter();
        adapter.setOnContactClickListener(contact -> {
            // Zapisujemy ID wybranej konwersacji jako aktywną i wracamy do MainActivity
            android.content.SharedPreferences prefs = getSharedPreferences("stego_prefs", MODE_PRIVATE);
            prefs.edit().putString("last_conv_id", contact.conversationId).apply();

            finish(); // Powrót do głównego okna czatu
        });

        adapter.setOnContactLongClickListener(contact -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Zmień nazwę kontaktu");

            final EditText input = new EditText(this);
            input.setText(contact.name);
            builder.setView(input);

            builder.setPositiveButton("Zapisz", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    new Thread(() -> {
                        db.contactDao().updateContactName(contact.pubKeyBase64, newName);
                    }).start();
                }
            });
            builder.setNegativeButton("Anuluj", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        recyclerView.setAdapter(adapter);

        // Obsługa przycisku "+" w widoku kontaktów (otwiera ekran QR)
        Button addContactButton = findViewById(R.id.addContactButton);
        addContactButton.setOnClickListener(v -> {
            startActivity(new Intent(ContactsActivity.this, QrScanActivity.class));
        });

        // Inicjalizacja domyślnego kontaktu "JA"
        new Thread(() -> {
            try {
                String myKey = CryptoEngine.encodePublicKey(CryptoEngine.getMyPublicKey());
                if (db.contactDao().getContactByKey(myKey) == null) {
                    Contact selfContact = new Contact(myKey);
                    selfContact.name = "JA (Notatnik / Sam ze sobą)";
                    selfContact.conversationId = "self_conversation";
                    db.contactDao().insertContact(selfContact);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        db.contactDao().getAllContacts().observe(this, adapter::setContacts);
    }
    private void generateAndDisplayMyQrCode() {
        try {
            PublicKey myKey = CryptoEngine.getMyPublicKey();
            String myPubKeyBase64 = CryptoEngine.encodePublicKey(myKey);

            // Zwiększona rozdzielczość (600x600), by skaner łatwiej odczytał gęsty kod RSA
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(myPubKeyBase64, BarcodeFormat.QR_CODE, 600, 600);
            myQrImageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Błąd generowania QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeQrFromGallery(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bMap = BitmapFactory.decodeStream(inputStream);
            if (bMap == null) {
                Toast.makeText(this, "Nie udało się wczytać obrazu", Toast.LENGTH_SHORT).show();
                return;
            }

            int intArray[] = new int[bMap.getWidth() * bMap.getHeight()];
            bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
            RGBLuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);

            if (result != null) {
                showNameContactDialog(result.getText());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Nie znaleziono poprawnego kodu QR na zdjęciu", Toast.LENGTH_SHORT).show();
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
                        myPubKeyBase64,
                        null,
                        newContact.conversationId,
                        recipientKey,
                        matrixRoomId,
                        matrixToken,
                        channelSeed,
                        true, // isHandshake = true
                        db
                ).join();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Błąd wymiany kluczy", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

}