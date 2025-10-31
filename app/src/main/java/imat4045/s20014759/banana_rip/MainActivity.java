package imat4045.s20014759.banana_rip;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import com.github.dhaval2404.imagepicker.ImagePicker;
import android.provider.MediaStore;
import android.content.ClipData;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Toast;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.location.Address;
import android.location.Geocoder;
import com.google.android.libraries.places.api.model.Place;


public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView resultText;
    private Button selectButton;
    private Button getLocationButton;
    private Button findSupermarketsButton;
    private TextView locationStatusText;
    private TextView supermarketsText;
    private Interpreter tflite;
    private List<String> labels;
    private LocationService locationService;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int PICK_IMAGE_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        imageView = findViewById(R.id.imageView);
        resultText = findViewById(R.id.resultText);
        selectButton = findViewById(R.id.selectButton);
        getLocationButton = findViewById(R.id.getLocationButton);
        findSupermarketsButton = findViewById(R.id.findSupermarketsButton);
        locationStatusText = findViewById(R.id.locationStatusText);
        supermarketsText = findViewById(R.id.supermarketsText);

        // Initialize location service
        locationService = new LocationService(this);

        try {
            tflite = new Interpreter(loadModelFile());
            labels = loadLabels();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set up button click listeners
        selectButton.setOnClickListener(v -> {
            showImagePickerDialog();
        });

        getLocationButton.setOnClickListener(v -> {
            // Add debug logging
            locationService.debugLocationStatus();
            getCurrentLocation();
        });
        
        // Add long press for diagnosis
        getLocationButton.setOnLongClickListener(v -> {
            String diagnosis = locationService.diagnoseGPSIssues();
            locationStatusText.setText("GPS Diagnosis");
            supermarketsText.setText(diagnosis);
            return true;
        });

        findSupermarketsButton.setOnClickListener(v -> {
            findNearbySupermarkets();
        });

        // Check location permission on startup
        if (!checkLocationPermission()) {
            locationStatusText.setText("Location permission required");
        } else {
            locationStatusText.setText("Ready to get location");
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image Source");
        builder.setMessage("Choose how you want to select your banana photo:");
        
        builder.setPositiveButton("Gallery", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openGallery();
            }
        });
        
        builder.setNegativeButton("Camera", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openCamera();
            }
        });
        
        builder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void getCurrentLocation() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        locationStatusText.setText("Getting your location...");
        supermarketsText.setText("Please wait...\n\nUsing fallback location for testing.\nReal GPS will be attempted in background.");

        // Use a simple, reliable location method
        locationService.getCurrentLocation(new LocationService.OnLocationResultListener() {
            @Override
            public void onLocationResult(Location location) {
                runOnUiThread(() -> {
                    // Get address for the location
                    getAddressFromLocation(location, new LocationService.OnAddressResultListener() {
                        @Override
                        public void onAddressResult(String address) {
                            runOnUiThread(() -> {
                                String accuracy = location.getAccuracy() <= 100 ? " (High accuracy)" : 
                                               location.getAccuracy() <= 500 ? " (Medium accuracy)" : " (Low accuracy)";
                                String provider = location.getProvider();
                                String locationSource = provider != null && provider.contains("fallback") ? " (Fallback Location)" : " (Real GPS)";
                                locationStatusText.setText("Location: " + address + accuracy + locationSource);
                                supermarketsText.setText("Location found! Now click 'Find Nearby Supermarkets' to search.");
                            });
                        }
                        
                        @Override
                        public void onAddressError(String error) {
                            runOnUiThread(() -> {
                                String accuracy = location.getAccuracy() <= 100 ? " (High accuracy)" : 
                                               location.getAccuracy() <= 500 ? " (Medium accuracy)" : " (Low accuracy)";
                                String provider = location.getProvider();
                                String locationSource = provider != null && provider.contains("fallback") ? " (Fallback Location)" : " (Real GPS)";
                                locationStatusText.setText("Location: " + 
                                        String.format("%.4f, %.4f", location.getLatitude(), location.getLongitude()) +
                                        accuracy + locationSource);
                                supermarketsText.setText("Location found! Now click 'Find Nearby Supermarkets' to search.");
                            });
                        }
                    });
                });
            }

            @Override
            public void onLocationError(String error) {
                runOnUiThread(() -> {
                    locationStatusText.setText("Location Error");
                    supermarketsText.setText("Failed to get location:\n\n" + error + "\n\nTroubleshooting:\n" +
                            "• Check if location services are enabled\n" +
                            "• Move to an open area with clear sky view\n" +
                            "• Check app permissions in Settings\n" +
                            "• Try restarting the app\n" +
                            "• Check your internet connection");
                });
            }
        });
    }

    private void findNearbySupermarkets() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        locationStatusText.setText("Searching for nearby supermarkets...");
        supermarketsText.setText("Please wait...");

        // Get location first, then search for supermarkets
        locationService.getCurrentLocation(new LocationService.OnLocationResultListener() {
            @Override
            public void onLocationResult(Location location) {
                runOnUiThread(() -> {
                    // Get address for the location
                    getAddressFromLocation(location, new LocationService.OnAddressResultListener() {
                        @Override
                        public void onAddressResult(String address) {
                            runOnUiThread(() -> {
                                String accuracy = location.getAccuracy() <= 100 ? " (High accuracy)" : 
                                               location.getAccuracy() <= 500 ? " (Medium accuracy)" : " (Low accuracy)";
                                locationStatusText.setText("Location: " + address + accuracy);
                            });
                        }
                        
                        @Override
                        public void onAddressError(String error) {
                            runOnUiThread(() -> {
                                String accuracy = location.getAccuracy() <= 100 ? " (High accuracy)" : 
                                               location.getAccuracy() <= 500 ? " (Medium accuracy)" : " (Low accuracy)";
                                locationStatusText.setText("Location: " + 
                                        String.format("%.4f, %.4f", location.getLatitude(), location.getLongitude()) +
                                        accuracy);
                            });
                        }
                    });
                    
                    // Search for nearby supermarkets
                    locationService.findNearbySupermarkets(new LocationService.OnNearbyPlacesListener() {
                        @Override
                        public void onNearbyPlacesResult(List<Place> places) {
                            runOnUiThread(() -> {
                                if (places.isEmpty()) {
                                    supermarketsText.setText("No supermarkets found nearby.\n\n" +
                                            "Hong Kong Supermarkets (Fallback List):\n\n" +
                                            "1. Wellcome (惠康)\n" +
                                            "2. ParknShop (百佳)\n" +
                                            "3. City Super (city'super)\n" +
                                            "4. Taste (Taste)\n" +
                                            "5. 360 (360)\n" +
                                            "6. Market Place (Market Place)\n" +
                                            "7. Fusion (Fusion)\n" +
                                            "8. Great (Great)\n" +
                                            "9. Jasons (Jasons)\n" +
                                            "10. Oliver's (Oliver's)");
                                } else {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Nearby Supermarkets:\n\n");
                                    
                                    for (int i = 0; i < places.size(); i++) {
                                        Place place = places.get(i);
                                        sb.append(i + 1).append(". ").append(place.getName()).append("\n");
                                        
                                        if (place.getAddress() != null) {
                                            sb.append("   Address: ").append(place.getAddress()).append("\n");
                                        }
                                        
                                        if (place.getRating() != null) {
                                            sb.append("   Rating: ").append(place.getRating()).append("/5\n");
                                        }
                                        
                                        sb.append("\n");
                                    }
                                    
                                    supermarketsText.setText(sb.toString());
                                }
                            });
                        }

                        @Override
                        public void onNearbyPlacesError(String error) {
                            runOnUiThread(() -> {
                                supermarketsText.setText("Google Places API Error\n\n" +
                                        "Hong Kong Supermarkets (Fallback List):\n\n" +
                                        "1. Wellcome (惠康)\n" +
                                        "2. ParknShop (百佳)\n" +
                                        "3. City Super (city'super)\n" +
                                        "4. Taste (Taste)\n" +
                                        "5. 360 (360)\n" +
                                        "6. Market Place (Market Place)\n" +
                                        "7. Fusion (Fusion)\n" +
                                        "8. Great (Great)\n" +
                                        "9. Jasons (Jasons)\n" +
                                        "10. Oliver's (Oliver's)\n\n" +
                                        "Note: Enable Google Places API for real-time data.");
                            });
                        }
                    });
                });
            }

            @Override
            public void onLocationError(String error) {
                runOnUiThread(() -> {
                    locationStatusText.setText("Location error: " + error);
                    supermarketsText.setText("Failed to get location. Please try:\n" +
                            "1. Move to an open area\n" +
                            "2. Check location services are enabled\n" +
                            "3. Check app permissions\n" +
                            "4. Try again");
                });
            }
        });
    }

    private void getAddressFromLocation(Location location, LocationService.OnAddressResultListener listener) {
        if (!Geocoder.isPresent()) {
            if (listener != null) {
                listener.onAddressError("Geocoder not available");
            }
            return;
        }

        Geocoder geocoder = new Geocoder(this);
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressString = new StringBuilder();
                
                // Build address string
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) addressString.append(", ");
                    addressString.append(address.getAddressLine(i));
                }
                
                // If no address lines, try to build from components
                if (addressString.length() == 0) {
                    if (address.getThoroughfare() != null) {
                        addressString.append(address.getThoroughfare());
                    }
                    if (address.getLocality() != null) {
                        if (addressString.length() > 0) addressString.append(", ");
                        addressString.append(address.getLocality());
                    }
                    if (address.getAdminArea() != null) {
                        if (addressString.length() > 0) addressString.append(", ");
                        addressString.append(address.getAdminArea());
                    }
                    if (address.getCountryName() != null) {
                        if (addressString.length() > 0) addressString.append(", ");
                        addressString.append(address.getCountryName());
                    }
                }
                
                String result = addressString.toString();
                if (result.length() > 0) {
                    Log.d("MainActivity", "Address found: " + result);
                    if (listener != null) {
                        listener.onAddressResult(result);
                    }
                } else {
                    if (listener != null) {
                        listener.onAddressError("No address found");
                    }
                }
            } else {
                if (listener != null) {
                    listener.onAddressError("No address found");
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Geocoding error: " + e.getMessage());
            if (listener != null) {
                listener.onAddressError("Geocoding failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationStatusText.setText("Location permission granted");
            } else {
                locationStatusText.setText("Location permission denied");
                supermarketsText.setText("Cannot get location without permission");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                // Handle custom image picker
                Uri imageUri = data.getData();
                if (imageUri == null) {
                    // Camera result
                    Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        String result = classifyImage(bitmap);
                        resultText.setText(result);
                    }
                } else {
                    // Gallery result
                    imageView.setImageURI(imageUri);
                    try {
                        Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        String result = classifyImage(bitmap);
                        resultText.setText(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                        resultText.setText("Error processing image.");
                    }
                }
            }
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd("model.tflite").getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd("model.tflite").getStartOffset();
        long declaredLength = getAssets().openFd("model.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels() throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("labels.txt")));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private String classifyImage(Bitmap bitmap) {
        // Resize bitmap to model input size (e.g., 224x224)
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        // Prepare input
        float[][][][] input = new float[1][224][224][3];
        for (int x = 0; x < 224; x++) {
            for (int y = 0; y < 224; y++) {
                int pixel = resized.getPixel(x, y);
                input[0][y][x][0] = ((pixel >> 16) & 0xFF) / 255.0f;
                input[0][y][x][1] = ((pixel >> 8) & 0xFF) / 255.0f;
                input[0][y][x][2] = (pixel & 0xFF) / 255.0f;
            }
        }

        // Prepare output
        float[][] output = new float[1][labels.size()];
        tflite.run(input, output);

        // Find max
        int maxIdx = 0;
        float maxProb = output[0][0];
        for (int i = 1; i < labels.size(); i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIdx = i;
            }
        }
        return "Result: " + labels.get(maxIdx) + " (" + (maxProb * 100) + "%)";
    }

    @Override
    protected void onDestroy() {
        if (tflite != null) {
            tflite.close();
        }
        if (locationService != null) {
            locationService.stopLocationUpdates();
        }
        super.onDestroy();
    }
}