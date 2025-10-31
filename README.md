# Banana Rip - Location-Based Supermarket Finder

This Android app combines banana ripeness detection using TensorFlow Lite with location-based services to find nearby supermarkets.

## Features

- **Banana Ripeness Detection**: Uses a pre-trained TensorFlow Lite model to classify banana ripeness from photos
- **Location Services**: Gets current location and finds nearby supermarkets using Google Places API
- **Supermarket Information**: Displays supermarket names, addresses, ratings, and review counts

## Setup Instructions

### 1. Google Maps API Key Setup

To use the location and Places API functionality, you need to set up a Google Maps API key:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the following APIs:
   - Maps SDK for Android
   - Places API
   - Location Services API
4. Create credentials (API Key)
5. Replace `YOUR_GOOGLE_MAPS_API_KEY` in the following files:
   - `app/src/main/AndroidManifest.xml` (line with `com.google.android.geo.API_KEY`)
   - `app/src/main/java/imat4045/s20014759/banana_rip/LocationService.java` (line with `Places.initialize`)

### 2. Permissions

The app requires the following permissions:
- Camera access (for taking photos)
- Location access (for finding nearby supermarkets)
- Internet access (for Places API calls)

### 3. Building the App

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run the app on a device or emulator

## Usage

1. **Banana Detection**:
   - Tap "Select Banana Photo" to choose an image from your gallery
   - The app will analyze the image and display the ripeness classification

2. **Find Nearby Supermarkets**:
   - Tap "Find Nearby Supermarkets" to get your current location
   - Grant location permission when prompted
   - The app will search for supermarkets within a 5km radius
   - Results will show supermarket names, addresses, ratings, and review counts

## Technical Details

- **Location Service**: Uses Google's FusedLocationProviderClient for accurate location
- **Places API**: Searches for nearby stores and filters for supermarkets
- **TensorFlow Lite**: Pre-trained model for banana ripeness classification
- **UI**: Scrollable layout with separate sections for image analysis and location services

## Dependencies

- Google Play Services Location (21.0.1)
- Google Play Services Maps (18.2.0)
- Google Places API (3.3.0)
- TensorFlow Lite
- ImagePicker library

## Notes

- The app requires an active internet connection for Places API calls
- Location services work best outdoors or with good GPS signal
- The supermarket detection filters for common supermarket chains and keywords
- Make sure to keep your API key secure and set appropriate usage restrictions in Google Cloud Console 