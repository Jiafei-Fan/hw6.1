package com.example.hw61_test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.hw61_test.ui.theme.Hw61_testTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resumeWithException
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {

    // FusedLocationProviderClient is used to request location updates.
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Define a LocationCallback to receive location updates.
    private lateinit var locationCallback: LocationCallback

    // Define a LocationRequest to specify update intervals, priority, etc.
    private lateinit var locationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create a LocationRequest with desired interval, priority, etc.
        locationRequest = LocationRequest.create().apply {
            interval = 10_000   // 10 seconds
            fastestInterval = 5_000  // 5 seconds
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        // Define how we handle new location data when it arrives.
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // We could handle each new location update here if we wanted
                // real-time location. But for this demo, we'll mostly rely
                // on the Compose state to manage UI updates.
            }
        }

        setContent {
            Hw61_testTheme {
                // Scaffold is a basic layout structure for Material 3
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // MainScreen composable is where we handle permission,
                    // show the map, markers, etc.
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If the user has already granted location permission,
        // start location updates.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates when the activity is not visible
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

//@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // This state will hold whether the user has granted location permission.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // This launcher will be used to request location permission from the user.
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
        if (!isGranted) {
            Log.e("MainScreen", "Location permission not granted.")
        }
    }

    // If we don't have permission, immediately request it.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // We'll store the user's current location in this state.
    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    // We'll also store the address info (reverse geocoding result).
    var addressInfo by remember { mutableStateOf("No address yet") }

    // For retrieving location updates in a Compose-friendly way, we can
    // use a side effect or manually request location from FusedLocationProviderClient.
    // Below is a simple approach that fetches the last known location once.
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                val location = fusedLocationClient.awaitLocation()
                location?.let { loc ->
                    userLocation = LatLng(loc.latitude, loc.longitude)
                    addressInfo = getAddressFromLocation(context, loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) {
                Log.e("MainScreen", "SecurityException: ${e.message}")
            } catch (e: Exception) {
                Log.e("MainScreen", "Error: ${e.message}")
            }
        }
    }

    // We'll keep track of additional markers that the user places by tapping.
    val customMarkers = remember { mutableStateListOf<LatLng>() }

    // Create a CameraPositionState to control or observe the map's camera.
    val cameraPositionState = rememberCameraPositionState()

    // If we have the user's location, move the camera to that position once.
    // We only want to move the camera the first time we get a location, so we can do a key-based effect.
    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 15f))
        }
    }


    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (hasLocationPermission) {
            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    // Add custom marker
                    customMarkers.add(latLng)
                    // Asynchronous reverse geocoding
                    coroutineScope.launch {
                        val newAddr = withContext(Dispatchers.IO) {
                            getAddressFromLocation(context, latLng.latitude, latLng.longitude)
                        }
                        addressInfo = newAddr
                    }
                }
            ) {
                // User's current location marker
                userLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "You Are Here",
                        snippet = addressInfo,
                        icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_AZURE
                        )
                    )
                }
                // Custom markers placed by user taps
                customMarkers.forEach { pos ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = "Custom Marker",
                        snippet = "Lat: ${pos.latitude}, Lng: ${pos.longitude}"
                    )
                }
            }
        } else {
            Text(
                text = "Location permission is required to show the map.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Display address at the bottom
        Text(
            text = addressInfo,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

/**
 * A helper function to do reverse geocoding: given latitude and longitude,
 * return a human-readable address (e.g. "123 Main St, City, Country").
 *
 * @param context The context used for Geocoder
 * @param latitude The latitude
 * @param longitude The longitude
 * @return A string representing the best-match address, or an empty string if not found
 */
fun getAddressFromLocation(context: android.content.Context, latitude: Double, longitude: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val address: Address = addresses[0]
            // Combine various address fields into one string
            val addressLine = address.getAddressLine(0)
            addressLine ?: "No address found"
        } else {
            "No address found"
        }
    } catch (e: Exception) {
        "Error getting address: ${e.message}"
    }
}

/**
 * An extension function that awaits the last location result from FusedLocationProviderClient
 * in a suspend manner, simplifying usage within LaunchedEffect or other coroutines.
 */
@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
suspend fun FusedLocationProviderClient.awaitLocation(): Location? {
    return suspendCancellableCoroutine { cont ->
        lastLocation
            .addOnSuccessListener { location ->
                cont.resume(location) {}
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }
}


@Composable
fun MapScreen() {
    val atasehir = LatLng(40.9971, 29.1007)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(atasehir, 15f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    )
}
