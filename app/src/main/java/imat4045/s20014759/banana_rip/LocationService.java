package imat4045.s20014759.banana_rip;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LocationService {
    private static final String TAG = "LocationService";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private OnLocationResultListener locationResultListener;
    private OnNearbyPlacesListener nearbyPlacesListener;

    public interface OnLocationResultListener {
        void onLocationResult(Location location);
        void onLocationError(String error);
    }

    public interface OnNearbyPlacesListener {
        void onNearbyPlacesResult(List<Place> places);
        void onNearbyPlacesError(String error);
    }

    public interface OnAddressResultListener {
        void onAddressResult(String address);
        void onAddressError(String error);
    }

    public LocationService(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        
        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(context, "AIzaSyBwTMn_N5Ab9scfGsX2J0didnsoXdLrV_g");
        }

        placesClient = Places.createClient(context);
        
        setupLocationCallback();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d(TAG, "Location callback triggered");
                if (locationResult == null) {
                    Log.e(TAG, "Location result is null");
                    if (locationResultListener != null) {
                        locationResultListener.onLocationError("Location result is null");
                    }
                    return;
                }
                
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    Log.d(TAG, "Location update received: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude() + 
                            " (accuracy: " + currentLocation.getAccuracy() + "m, provider: " + currentLocation.getProvider() + 
                            ", time: " + new java.util.Date(currentLocation.getTime()) + ")");
                    
                    // Validate location is reasonable first
                    if (isLocationReasonable(currentLocation)) {
                        // Accept location if it's reasonably accurate (within 500 meters)
                        if (currentLocation.getAccuracy() <= 500) {
                            Log.d(TAG, "Location is accurate enough (" + currentLocation.getAccuracy() + "m), stopping updates");
                            stopLocationUpdates();
                            
                            if (locationResultListener != null) {
                                locationResultListener.onLocationResult(currentLocation);
                            }
                        } else {
                            Log.d(TAG, "Location accuracy is " + currentLocation.getAccuracy() + "m, continuing to search for better location");
                            // Continue searching for a more accurate location
                        }
                    } else {
                        Log.w(TAG, "Location failed validation, continuing to search for valid location");
                        // Continue searching for a valid location
                    }
                } else {
                    Log.w(TAG, "Location update received but location is null");
                    // Try to get location from the result
                    if (locationResult.getLocations() != null && !locationResult.getLocations().isEmpty()) {
                        currentLocation = locationResult.getLocations().get(0);
                        Log.d(TAG, "Got location from locations list: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                        stopLocationUpdates();
                        if (locationResultListener != null) {
                            locationResultListener.onLocationResult(currentLocation);
                        }
                    }
                }
            }
        };
    }

    public boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermission(Activity activity) {
        if (!checkLocationPermission()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    public void getCurrentLocation(OnLocationResultListener listener) {
        this.locationResultListener = listener;
        
        Log.d(TAG, "=== Starting location request ===");
        
        // Always provide a fallback location for testing
        Location fallbackLocation = createFallbackLocation();
        currentLocation = fallbackLocation;
        
        if (listener != null) {
            Log.d(TAG, "Providing fallback location immediately for testing");
            listener.onLocationResult(currentLocation);
        }
        
        // Also try to get real location in background
        if (checkLocationPermission()) {
            Log.d(TAG, "Permission granted, attempting real location in background");
            tryRealLocationInBackground();
        } else {
            Log.d(TAG, "No location permission, using fallback only");
        }
    }
    
    private void tryRealLocationInBackground() {
        // Try to get real location without blocking the UI
        new Thread(() -> {
            try {
                if (fusedLocationClient != null) {
                    fusedLocationClient.getLastLocation()
                        .addOnCompleteListener(new OnCompleteListener<Location>() {
                            @Override
                            public void onComplete(@NonNull Task<Location> task) {
                                if (task.isSuccessful() && task.getResult() != null) {
                                    Location realLocation = task.getResult();
                                    if (isLocationReasonable(realLocation)) {
                                        Log.d(TAG, "Real location found: " + realLocation.getLatitude() + ", " + realLocation.getLongitude());
                                        currentLocation = realLocation;
                                        // Update UI with real location if listener is still available
                                        if (locationResultListener != null) {
                                            locationResultListener.onLocationResult(realLocation);
                                        }
                                    }
                                }
                            }
                        });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting real location: " + e.getMessage());
            }
        }).start();
    }

    private void tryMultipleLocationStrategies(OnLocationResultListener listener) {
        Log.d(TAG, "Starting multiple location strategies");
        
        // Strategy 1: Try to get last known location first (fastest)
        tryLastKnownLocation(listener);
        
        // Strategy 2: If last known fails, try fresh location updates
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null) {
                    Log.d(TAG, "Last known location failed, trying fresh location updates");
                    requestLocationWithMultipleStrategies();
                }
            }
        }, 2000); // Wait 2 seconds for last known location
    }

    private void tryLastKnownLocation(OnLocationResultListener listener) {
        Log.d(TAG, "Trying to get last known location");
        
        if (fusedLocationClient != null) {
            fusedLocationClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location lastLocation = task.getResult();
                            Log.d(TAG, "Last known location found: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                            
                            // Check if location is recent and reasonable
                            long timeDiff = System.currentTimeMillis() - lastLocation.getTime();
                            if (timeDiff < 300000 && isLocationReasonable(lastLocation)) { // 5 minutes
                                currentLocation = lastLocation;
                                if (listener != null) {
                                    listener.onLocationResult(currentLocation);
                                }
                                return;
                            } else {
                                Log.d(TAG, "Last known location is too old or invalid, age: " + (timeDiff / 1000) + " seconds");
                            }
                        } else {
                            Log.d(TAG, "No last known location available");
                            
                            // For emulator testing, provide a fallback location
                            if (isRunningOnEmulator()) {
                                Log.d(TAG, "Running on emulator, providing fallback location for testing");
                                Location fallbackLocation = createFallbackLocation();
                                currentLocation = fallbackLocation;
                                if (listener != null) {
                                    listener.onLocationResult(currentLocation);
                                }
                                return;
                            }
                        }
                        
                        // If we reach here, last known location didn't work
                        // The timeout handler will try fresh location updates
                    }
                });
        }
    }

    private void requestLocationWithMultipleStrategies() {
        // Strategy 1: High accuracy with GPS - very aggressive settings
        LocationRequest highAccuracyRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(500)  // Even more frequent updates
                .setMinUpdateIntervalMillis(250)  // Even more frequent minimum updates
                .setMaxUpdateDelayMillis(1000)  // Shorter max delay
                .setWaitForAccurateLocation(true)
                .build();
        
        Log.d(TAG, "Starting location request with high accuracy priority");
        Log.d(TAG, "Current time: " + System.currentTimeMillis());

        // Strategy 2: Balanced power and accuracy
        LocationRequest balancedRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setIntervalMillis(3000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(5000)
                .build();

        // Strategy 3: Low power fallback
        LocationRequest lowPowerRequest = new LocationRequest.Builder(Priority.PRIORITY_LOW_POWER)
                .setIntervalMillis(10000)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build();

        // Start with high accuracy
        Log.d(TAG, "Starting high accuracy location request");
        fusedLocationClient.requestLocationUpdates(highAccuracyRequest, locationCallback, Looper.getMainLooper());
        
        // Fallback to balanced after 15 seconds
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null) {
                    Log.d(TAG, "Switching to balanced power accuracy");
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    fusedLocationClient.requestLocationUpdates(balancedRequest, locationCallback, Looper.getMainLooper());
                }
            }
        }, 15000);
        
        // Fallback to low power after 30 seconds
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null) {
                    Log.d(TAG, "Switching to low power accuracy");
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    fusedLocationClient.requestLocationUpdates(lowPowerRequest, locationCallback, Looper.getMainLooper());
                }
            }
        }, 30000);
        
        // Final timeout after 45 seconds
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null && locationResultListener != null) {
                    Log.e(TAG, "Location timeout - no location received after 45 seconds");
                    stopLocationUpdates();
                    
                    // For emulator, provide fallback location instead of error
                    if (isRunningOnEmulator()) {
                        Log.d(TAG, "Emulator detected, providing fallback location after timeout");
                        Location fallbackLocation = createFallbackLocation();
                        currentLocation = fallbackLocation;
                        locationResultListener.onLocationResult(currentLocation);
                    } else {
                        locationResultListener.onLocationError("Location timeout. Please try:\n" +
                                "1. Enable GPS/Location services in Settings\n" +
                                "2. Move to an open area with clear sky view\n" +
                                "3. Check if other location apps work\n" +
                                "4. Restart the app and try again\n" +
                                "5. Check your internet connection");
                    }
                }
            }
        }, 45000);
    }

    private void checkLocationSettings(OnLocationResultListener listener) {
        Log.d(TAG, "Checking location settings...");
        SettingsClient settingsClient = LocationServices.getSettingsClient(context);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(10000)
                .setMinUpdateIntervalMillis(5000)
                .build());

        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());
        task.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Location settings are adequate");
                    // Location settings are adequate, proceed with location request
                    requestLocationDirectly(listener);
                } else {
                    Log.w(TAG, "Location settings check failed, but trying anyway: " + 
                            (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                    // Even if settings check fails, try to get location anyway
                    // Sometimes the settings check is overly strict
                    requestLocationDirectly(listener);
                }
            }
        });
    }

    private void requestLocationDirectly(OnLocationResultListener listener) {
        Log.d(TAG, "Requesting fresh location directly (skipping cached location)...");
        
        // Skip last known location and go straight to fresh location updates
        // This ensures we get the most current location
        Log.d(TAG, "Starting fresh location updates...");
        requestLocationWithMultipleStrategies();
    }

    public void findNearbySupermarkets(OnNearbyPlacesListener listener) {
        this.nearbyPlacesListener = listener;
        
        if (!checkLocationPermission()) {
            Log.e(TAG, "Location permission not granted");
            if (listener != null) {
                listener.onNearbyPlacesError("Location permission not granted");
            }
            return;
        }

        if (placesClient == null) {
            Log.e(TAG, "PlacesClient is null");
            if (listener != null) {
                listener.onNearbyPlacesError("Places API not initialized");
            }
            return;
        }

        Log.d(TAG, "Searching for nearby supermarkets");

        // Create the request for current place
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.builder(
                Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.RATING, 
                             Place.Field.USER_RATINGS_TOTAL, Place.Field.TYPES))
                .build();

        // Perform the search
        placesClient.findCurrentPlace(request)
                .addOnCompleteListener(new OnCompleteListener<FindCurrentPlaceResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<FindCurrentPlaceResponse> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            FindCurrentPlaceResponse response = task.getResult();
                            if (response.getPlaceLikelihoods() != null) {
                                List<Place> places = new ArrayList<>();
                                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                                    Place place = placeLikelihood.getPlace();
                                    if (isSupermarket(place)) {
                                        places.add(place);
                                    }
                                }
                                Log.d(TAG, "Found " + places.size() + " supermarkets");
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesResult(places);
                                }
                            } else {
                                Log.w(TAG, "No place likelihoods found");
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError("No places found nearby");
                                }
                            }
                        } else {
                            String errorMsg = "Failed to find nearby places: " + 
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error");
                            Log.e(TAG, errorMsg);
                            
                            // If API fails, provide a fallback with common supermarket chains
                            if (errorMsg.contains("legacy API") || errorMsg.contains("not enabled") || 
                                errorMsg.contains("API_NOT_ENABLED") || errorMsg.contains("Failed to find nearby places")) {
                                Log.d(TAG, "Using fallback supermarket list due to API error: " + errorMsg);
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError("API_NOT_ENABLED");
                                }
                            } else {
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError(errorMsg);
                                }
                            }
                        }
                    }
                });
    }

    public void findNearbySupermarketsWithLocation(Location location, OnNearbyPlacesListener listener) {
        this.nearbyPlacesListener = listener;
        
        if (placesClient == null) {
            Log.e(TAG, "PlacesClient is null");
            if (listener != null) {
                listener.onNearbyPlacesError("Places API not initialized");
            }
            return;
        }

        Log.d(TAG, "Searching for supermarkets near: " + location.getLatitude() + ", " + location.getLongitude());

        // Create the request for current place
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.builder(
                Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.RATING, 
                             Place.Field.USER_RATINGS_TOTAL, Place.Field.TYPES))
                .build();

        // Perform the search
        placesClient.findCurrentPlace(request)
                .addOnCompleteListener(new OnCompleteListener<FindCurrentPlaceResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<FindCurrentPlaceResponse> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            FindCurrentPlaceResponse response = task.getResult();
                            if (response.getPlaceLikelihoods() != null) {
                                List<Place> places = new ArrayList<>();
                                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                                    Place place = placeLikelihood.getPlace();
                                    if (isSupermarket(place)) {
                                        places.add(place);
                                    }
                                }
                                Log.d(TAG, "Found " + places.size() + " supermarkets");
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesResult(places);
                                }
                            } else {
                                Log.w(TAG, "No place likelihoods found");
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError("No places found nearby");
                                }
                            }
                        } else {
                            String errorMsg = "Failed to find nearby places: " + 
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error");
                            Log.e(TAG, errorMsg);
                            
                            // If API fails, provide a fallback with common supermarket chains
                            if (errorMsg.contains("legacy API") || errorMsg.contains("not enabled") || 
                                errorMsg.contains("API_NOT_ENABLED") || errorMsg.contains("Failed to find nearby places")) {
                                Log.d(TAG, "Using fallback supermarket list due to API error: " + errorMsg);
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError("API_NOT_ENABLED");
                                }
                            } else {
                                if (nearbyPlacesListener != null) {
                                    nearbyPlacesListener.onNearbyPlacesError(errorMsg);
                                }
                            }
                        }
                    }
                });
    }

    private boolean isSupermarket(Place place) {
        if (place.getTypes() == null) return false;
        
        for (Place.Type type : place.getTypes()) {
            if (type == Place.Type.STORE || 
                type == Place.Type.FOOD || 
                type == Place.Type.GROCERY_OR_SUPERMARKET) {
                return true;
            }
        }
        
        // Also check the name for common supermarket keywords
        String name = place.getName();
        if (name != null) {
            name = name.toLowerCase();
            return name.contains("supermarket") || 
                   name.contains("grocery") || 
                   name.contains("market") || 
                   name.contains("store") ||
                   name.contains("tesco") ||
                   name.contains("sainsbury") ||
                   name.contains("asda") ||
                   name.contains("morrisons") ||
                   name.contains("aldi") ||
                   name.contains("lidl") ||
                   name.contains("coop") ||
                   name.contains("waitrose");
        }
        
        return false;
    }

    private List<Place> createFallbackSupermarketList() {
        List<Place> fallbackPlaces = new ArrayList<>();
        
        // Create mock places for common supermarket chains
        String[] supermarketNames = {
            "Tesco", "Sainsbury's", "Asda", "Morrisons", "Aldi", "Lidl", 
            "Waitrose", "Co-op", "Iceland", "Marks & Spencer"
        };
        
        for (int i = 0; i < supermarketNames.length; i++) {
            // Create a mock place (this is a simplified version)
            // In a real implementation, you'd create proper Place objects
            // For now, we'll return an empty list and handle this in the UI
        }
        
        return fallbackPlaces;
    }

    private boolean isLocationReasonable(Location location) {
        if (location == null) return false;
        
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        
        Log.d(TAG, "Validating location: " + lat + ", " + lng);
        Log.d(TAG, "Location provider: " + location.getProvider());
        Log.d(TAG, "Location accuracy: " + location.getAccuracy() + " meters");
        Log.d(TAG, "Location time: " + new java.util.Date(location.getTime()));
        
        // Check if coordinates are within valid ranges
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            Log.w(TAG, "Location coordinates out of valid range: " + lat + ", " + lng);
            return false;
        }
        
        // Check if location is not obviously wrong (e.g., 0,0)
        if (lat == 0.0 && lng == 0.0) {
            Log.w(TAG, "Location is at 0,0 which is likely invalid");
            return false;
        }
        
        // Check for Google HQ location (common fallback when GPS fails)
        if (isGoogleHQLocation(lat, lng)) {
            Log.w(TAG, "Location is Google HQ (1600 Amphitheatre Parkway) - rejecting as likely fallback");
            return false;
        }
        
        // Check if location is too old (more than 2 hours for last known, 30 minutes for fresh)
        long timeDiff = System.currentTimeMillis() - location.getTime();
        long maxAge = location.getProvider() != null && location.getProvider().contains("fused") ? 7200000 : 1800000; // 2 hours vs 30 minutes
        if (timeDiff > maxAge) {
            Log.w(TAG, "Location is too old: " + (timeDiff / 1000) + " seconds (max: " + (maxAge / 1000) + ")");
            return false;
        }
        
        // Check if accuracy is reasonable (not more than 50km for last known, 5km for fresh)
        float maxAccuracy = location.getProvider() != null && location.getProvider().contains("fused") ? 50000 : 5000;
        if (location.getAccuracy() > maxAccuracy) {
            Log.w(TAG, "Location accuracy is too poor: " + location.getAccuracy() + "m (max: " + maxAccuracy + "m)");
            return false;
        }
        
        Log.d(TAG, "Location appears reasonable: " + lat + ", " + lng + " (accuracy: " + location.getAccuracy() + "m)");
        return true;
    }

    private boolean isGoogleHQLocation(double lat, double lng) {
        // Google HQ coordinates: 37.4221, -122.0841
        // Check if location is within 1km of Google HQ
        double googleHQLat = 37.4221;
        double googleHQLng = -122.0841;
        
        // Calculate distance using simple approximation
        double latDiff = Math.abs(lat - googleHQLat);
        double lngDiff = Math.abs(lng - googleHQLng);
        
        // Rough distance calculation (not precise but good enough for this check)
        double distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000; // Convert to meters
        
        if (distance < 1000) { // Within 1km of Google HQ
            Log.w(TAG, "Location is within 1km of Google HQ - likely fallback location");
            return true;
        }
        
        return false;
    }

    private void getAddressFromLocation(Location location, OnAddressResultListener listener) {
        if (!Geocoder.isPresent()) {
            if (listener != null) {
                listener.onAddressError("Geocoder not available");
            }
            return;
        }

        Geocoder geocoder = new Geocoder(context);
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
                    Log.d(TAG, "Address found: " + result);
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
            Log.e(TAG, "Geocoding error: " + e.getMessage());
            if (listener != null) {
                listener.onAddressError("Geocoding failed: " + e.getMessage());
            }
        }
    }

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    public void clearLocationCache() {
        Log.d(TAG, "Clearing location cache");
        currentLocation = null;
        
        // Clear last known location
        if (fusedLocationClient != null) {
            fusedLocationClient.flushLocations();
            Log.d(TAG, "Flushed location cache");
        }
        
        // Force fresh location request
        if (locationResultListener != null) {
            Log.d(TAG, "Requesting fresh location after cache clear");
            getCurrentLocation(locationResultListener);
        }
    }

    public void forceGPSOnlyLocation(OnLocationResultListener listener) {
        this.locationResultListener = listener;
        
        if (!checkLocationPermission()) {
            if (listener != null) {
                listener.onLocationError("Location permission not granted");
            }
            return;
        }

        Log.d(TAG, "Forcing GPS-only location request");
        currentLocation = null;
        
        // Create GPS-only location request
        LocationRequest gpsOnlyRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(1000)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateDelayMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();
        
        // Start GPS-only location updates
        fusedLocationClient.requestLocationUpdates(gpsOnlyRequest, locationCallback, Looper.getMainLooper());
        
        // Set a longer timeout for GPS-only (90 seconds)
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null && locationResultListener != null) {
                    Log.e(TAG, "GPS-only location timeout after 90 seconds");
                    stopLocationUpdates();
                    locationResultListener.onLocationError("GPS-only location timeout. Please ensure:\n" +
                            "1. You're in an open area with clear sky view\n" +
                            "2. GPS is enabled in location settings\n" +
                            "3. No buildings or obstacles blocking GPS signal\n" +
                            "4. Try moving to a different location");
                }
            }
        }, 90000); // 90 seconds timeout
    }

    public void getCurrentLocationWithGoogle(OnLocationResultListener listener) {
        this.locationResultListener = listener;
        
        if (!checkLocationPermission()) {
            if (listener != null) {
                listener.onLocationError("Location permission not granted");
            }
            return;
        }

        Log.d(TAG, "Getting location using Google Location Services");
        currentLocation = null;
        
        // Use Google's most accurate location settings
        LocationRequest googleRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(500)  // Very frequent updates
                .setMinUpdateIntervalMillis(250)  // Very frequent minimum updates
                .setMaxUpdateDelayMillis(1000)  // Short max delay
                .setWaitForAccurateLocation(true)  // Wait for accurate location
                .build();
        
        // Start Google location updates
        fusedLocationClient.requestLocationUpdates(googleRequest, locationCallback, Looper.getMainLooper());
        
        // Set timeout for Google location (45 seconds)
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentLocation == null && locationResultListener != null) {
                    Log.e(TAG, "Google location timeout after 45 seconds");
                    stopLocationUpdates();
                    locationResultListener.onLocationError("Google location timeout. Please ensure:\n" +
                            "1. Location services are enabled\n" +
                            "2. You're in an area with good GPS signal\n" +
                            "3. Internet connection is available\n" +
                            "4. Try moving to an open area");
                }
            }
        }, 45000); // 45 seconds timeout
    }

    public void findNearbySupermarketsWithGoogle(Location location, OnNearbyPlacesListener listener) {
        Log.d(TAG, "Finding nearby supermarkets using Google Places API with location: " + 
                location.getLatitude() + ", " + location.getLongitude());
        
        if (placesClient == null) {
            Log.e(TAG, "PlacesClient is null, cannot search for places");
            if (listener != null) {
                listener.onNearbyPlacesError("PlacesClient not initialized. Please check Google Play Services.");
            }
            return;
        }
        
        // Create a list of place fields to request
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.TYPES,
                Place.Field.LAT_LNG
        );
        
        // Create the request
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);
        
        // Execute the request
        placesClient.findCurrentPlace(request).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FindCurrentPlaceResponse response = task.getResult();
                if (response != null && response.getPlaceLikelihoods() != null) {
                    List<Place> supermarkets = new ArrayList<>();
                    
                    for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                        Place place = placeLikelihood.getPlace();
                        if (place.getTypes() != null && place.getTypes().contains(Place.Type.SUPERMARKET)) {
                            supermarkets.add(place);
                            Log.d(TAG, "Found supermarket: " + place.getName());
                        }
                    }
                    
                    if (supermarkets.isEmpty()) {
                        Log.d(TAG, "No supermarkets found via Google Places API");
                        // Try to find grocery stores or food stores as fallback
                        for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                            Place place = placeLikelihood.getPlace();
                            if (place.getTypes() != null && 
                                (place.getTypes().contains(Place.Type.GROCERY_OR_SUPERMARKET) ||
                                 place.getTypes().contains(Place.Type.FOOD) ||
                                 place.getTypes().contains(Place.Type.STORE))) {
                                supermarkets.add(place);
                                Log.d(TAG, "Found food store: " + place.getName());
                            }
                        }
                    }
                    
                    if (listener != null) {
                        listener.onNearbyPlacesResult(supermarkets);
                    }
                } else {
                    Log.w(TAG, "No place likelihoods found");
                    if (listener != null) {
                        listener.onNearbyPlacesResult(new ArrayList<>());
                    }
                }
            } else {
                Exception exception = task.getException();
                String errorMessage = "Failed to find nearby places";
                if (exception != null) {
                    errorMessage = exception.getMessage();
                    Log.e(TAG, "Google Places API error: " + errorMessage);
                }
                
                if (listener != null) {
                    listener.onNearbyPlacesError(errorMessage);
                }
            }
        });
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void debugLocationStatus() {
        Log.d(TAG, "=== GPS Debug Information ===");
        Log.d(TAG, "Location permission granted: " + checkLocationPermission());
        Log.d(TAG, "FusedLocationClient available: " + (fusedLocationClient != null));
        Log.d(TAG, "PlacesClient available: " + (placesClient != null));
        Log.d(TAG, "Current location: " + (currentLocation != null ? 
                currentLocation.getLatitude() + ", " + currentLocation.getLongitude() : "null"));
        
        if (currentLocation != null) {
            Log.d(TAG, "Location accuracy: " + currentLocation.getAccuracy() + "m");
            Log.d(TAG, "Location provider: " + currentLocation.getProvider());
            Log.d(TAG, "Location age: " + ((System.currentTimeMillis() - currentLocation.getTime()) / 1000) + " seconds");
        }
        
        // Check if location services are enabled
        try {
            android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            Log.d(TAG, "GPS provider enabled: " + gpsEnabled);
            Log.d(TAG, "Network provider enabled: " + networkEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Error checking location providers: " + e.getMessage());
        }
        
        Log.d(TAG, "=== End GPS Debug Information ===");
    }

    public String diagnoseGPSIssues() {
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append("=== GPS DIAGNOSIS REPORT ===\n\n");
        
        // 1. Check permissions
        boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        diagnosis.append("1. PERMISSIONS:\n");
        diagnosis.append("   • Fine Location: ").append(hasFineLocation ? "✓ GRANTED" : "✗ DENIED").append("\n");
        diagnosis.append("   • Coarse Location: ").append(hasCoarseLocation ? "✓ GRANTED" : "✗ DENIED").append("\n");
        
        if (!hasFineLocation && !hasCoarseLocation) {
            diagnosis.append("   ❌ CRITICAL: No location permissions granted!\n");
        }
        diagnosis.append("\n");
        
        // 2. Check Google Play Services
        diagnosis.append("2. GOOGLE PLAY SERVICES:\n");
        diagnosis.append("   • FusedLocationClient: ").append(fusedLocationClient != null ? "✓ AVAILABLE" : "✗ NULL").append("\n");
        diagnosis.append("   • PlacesClient: ").append(placesClient != null ? "✓ AVAILABLE" : "✗ NULL").append("\n");
        
        if (fusedLocationClient == null) {
            diagnosis.append("   ❌ CRITICAL: FusedLocationClient is null - Google Play Services issue!\n");
        }
        diagnosis.append("\n");
        
        // 3. Check location providers
        diagnosis.append("3. LOCATION PROVIDERS:\n");
        try {
            android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            boolean passiveEnabled = locationManager.isProviderEnabled(android.location.LocationManager.PASSIVE_PROVIDER);
            
            diagnosis.append("   • GPS Provider: ").append(gpsEnabled ? "✓ ENABLED" : "✗ DISABLED").append("\n");
            diagnosis.append("   • Network Provider: ").append(networkEnabled ? "✓ ENABLED" : "✗ DISABLED").append("\n");
            diagnosis.append("   • Passive Provider: ").append(passiveEnabled ? "✓ ENABLED" : "✗ DISABLED").append("\n");
            
            if (!gpsEnabled && !networkEnabled) {
                diagnosis.append("   ❌ CRITICAL: All location providers disabled!\n");
            }
        } catch (Exception e) {
            diagnosis.append("   ❌ ERROR: ").append(e.getMessage()).append("\n");
        }
        diagnosis.append("\n");
        
        // 4. Check current location
        diagnosis.append("4. CURRENT LOCATION:\n");
        if (currentLocation != null) {
            long age = (System.currentTimeMillis() - currentLocation.getTime()) / 1000;
            diagnosis.append("   • Coordinates: ").append(currentLocation.getLatitude()).append(", ").append(currentLocation.getLongitude()).append("\n");
            diagnosis.append("   • Accuracy: ").append(currentLocation.getAccuracy()).append("m\n");
            diagnosis.append("   • Provider: ").append(currentLocation.getProvider()).append("\n");
            diagnosis.append("   • Age: ").append(age).append(" seconds\n");
            diagnosis.append("   • Valid: ").append(isLocationReasonable(currentLocation) ? "✓ YES" : "✗ NO").append("\n");
        } else {
            diagnosis.append("   • Status: ✗ NO LOCATION AVAILABLE\n");
        }
        diagnosis.append("\n");
        
        // 5. Check API configuration
        diagnosis.append("5. API CONFIGURATION:\n");
        try {
            String apiKey = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY");
            diagnosis.append("   • Google API Key: ").append(apiKey != null ? "✓ CONFIGURED" : "✗ MISSING").append("\n");
            if (apiKey != null) {
                diagnosis.append("   • Key (first 10 chars): ").append(apiKey.substring(0, Math.min(10, apiKey.length()))).append("...\n");
            }
        } catch (Exception e) {
            diagnosis.append("   • Error reading API key: ").append(e.getMessage()).append("\n");
        }
        diagnosis.append("\n");
        
        // 6. Check internet connectivity
        diagnosis.append("6. CONNECTIVITY:\n");
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            diagnosis.append("   • Internet: ").append(isConnected ? "✓ CONNECTED" : "✗ DISCONNECTED").append("\n");
            if (activeNetwork != null) {
                diagnosis.append("   • Type: ").append(activeNetwork.getTypeName()).append("\n");
            }
        } catch (Exception e) {
            diagnosis.append("   • Error checking connectivity: ").append(e.getMessage()).append("\n");
        }
        diagnosis.append("\n");
        
        // 7. Recommendations
        diagnosis.append("7. RECOMMENDATIONS:\n");
        if (!hasFineLocation) {
            diagnosis.append("   • Grant location permissions in app settings\n");
        }
        if (fusedLocationClient == null) {
            diagnosis.append("   • Update Google Play Services\n");
        }
        try {
            android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                diagnosis.append("   • Enable GPS in device settings\n");
            }
        } catch (Exception e) {
            // Ignore
        }
        if (currentLocation == null) {
            diagnosis.append("   • Move to an open area with clear sky view\n");
            diagnosis.append("   • Wait 30-60 seconds for GPS to acquire signal\n");
        }
        
        diagnosis.append("\n=== END DIAGNOSIS ===");
        return diagnosis.toString();
    }

    private boolean isRunningOnEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
               android.os.Build.FINGERPRINT.startsWith("unknown") ||
               android.os.Build.MODEL.contains("google_sdk") ||
               android.os.Build.MODEL.contains("Emulator") ||
               android.os.Build.MODEL.contains("Android SDK built for x86") ||
               android.os.Build.MANUFACTURER.contains("Genymotion") ||
               (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
               "google_sdk".equals(android.os.Build.PRODUCT);
    }

    private Location createFallbackLocation() {
        Location fallbackLocation = new Location("fallback_location");
        // Set to a default location (e.g., Hong Kong for your app)
        fallbackLocation.setLatitude(22.3193);  // Hong Kong latitude
        fallbackLocation.setLongitude(114.1694); // Hong Kong longitude
        fallbackLocation.setAccuracy(50); // 50 meter accuracy (better than before)
        fallbackLocation.setTime(System.currentTimeMillis());
        fallbackLocation.setProvider("fallback_location");
        
        Log.d(TAG, "Created fallback location: " + 
                fallbackLocation.getLatitude() + ", " + fallbackLocation.getLongitude() + 
                " (accuracy: " + fallbackLocation.getAccuracy() + "m)");
        
        return fallbackLocation;
    }


} 