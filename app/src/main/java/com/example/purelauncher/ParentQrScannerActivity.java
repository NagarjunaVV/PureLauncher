package com.example.purelauncher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParentQrScannerActivity extends AppCompatActivity {

    static final String EXTRA_QR_PAYLOAD = "qr_payload";

    private PreviewView previewView;
    private ImageButton btnFlash;
    private ImageButton btnGallery;
    private ImageButton btnBack;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean torchEnabled = false;
    private boolean handledResult = false;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String[]> galleryPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parent_qr_scanner);

        previewView = findViewById(R.id.previewView);
        btnFlash = findViewById(R.id.btnFlash);
        btnGallery = findViewById(R.id.btnGallery);
        btnBack = findViewById(R.id.btnBack);

        barcodeScanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
        );
        cameraExecutor = Executors.newSingleThreadExecutor();

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );

        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleGalleryImage
        );

        btnBack.setOnClickListener(v -> finish());
        btnGallery.setOnClickListener(v -> galleryPickerLauncher.launch(new String[]{"image/*"}));
        btnFlash.setOnClickListener(v -> toggleTorch());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();
                bindUseCases(cameraProvider);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to start camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        btnFlash.setEnabled(camera.getCameraInfo().hasFlashUnit());
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (handledResult) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (handledResult || barcodes == null) {
            return;
        }
        for (Barcode barcode : barcodes) {
            String rawValue = barcode.getRawValue();
            if (rawValue != null && !rawValue.trim().isEmpty()) {
                handledResult = true;
                returnResult(rawValue.trim());
                return;
            }
        }
    }

    private void handleGalleryImage(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "No image selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap bitmap;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                bitmap = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri));
            } else {
                bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }

            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(this::handleBarcodes)
                    .addOnFailureListener(e -> Toast.makeText(this, "Unable to decode QR image.", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            return;
        }
        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
    }

    private void returnResult(String payload) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_QR_PAYLOAD, payload);
        setResult(RESULT_OK, intent);
        finish();
    }
}
