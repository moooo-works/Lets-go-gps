# MockGPS - Android Mock Location Tool

A professional-grade Android application for developers and QA engineers to simulate GPS locations and movements. Built with modern Android technologies (Kotlin, Jetpack Compose, Hilt, Room, Coroutines).

## Features

### Core Capabilities
*   **Single Point Mocking**: simply drag the map to position the crosshair and start mocking a fixed location.
*   **Route Simulation**:
    *   Create multi-point routes by adding waypoints from the center crosshair.
    *   Simulate movement along the route with adjustable speed (slider control).
    *   Presets for Walking (5 km/h), Cycling (15 km/h), and Driving (40 km/h).
    *   Play, Pause, and Stop/Clear route controls.
*   **Saved Locations**: Automatically saves mocked locations to a local history (Room Database) for easy reference.
*   **Mock Location Status**: Clear visual indicators for IDLE, MOCKING, and SIMULATING states.

### Technical Stack
*   **Language**: Kotlin
*   **UI**: Jetpack Compose (Material Design 3)
*   **Architecture**: MVVM + Clean Architecture principles
*   **DI**: Hilt
*   **Database**: Room
*   **Maps**: Google Maps SDK for Android (Compose)
*   **Concurrency**: Kotlin Coroutines & Flows
*   **Testing**: JUnit, Mockk, Robolectric

## Setup Instructions

### 1. Google Maps API Key
To run this app, you need a valid Google Maps API Key enabled for **Maps SDK for Android**.

1.  Open `local.properties` in the project root (create if it doesn't exist).
2.  Add your key:
    ```properties
    MAPS_API_KEY=your_api_key_here
    ```
    Alternatively, set an environment variable `MAPS_API_KEY`.

### 2. Developer Options
This app requires **Mock Location** permission which must be granted via Android Developer Options.

1.  Enable **Developer Options** on your device (tap Build Number 7 times).
2.  Go to **System > Developer Options**.
3.  Scroll down to **Select mock location app**.
4.  Select **MockGPS**.

### 3. Build & Run
Run the app from Android Studio or command line:
```bash
./gradlew installDebug
```

## Usage Guide

### Single Point Mocking
1.  Launch the app.
2.  Pan the map until the desired location is under the red crosshair.
3.  Tap **Start Mocking**. The app will set the system location to this point.
4.  A marker "Mocking Here" will appear.
5.  Tap **Stop Mocking** to disable.

### Route Simulation
1.  Position the crosshair at the start point.
2.  Tap **Add Point**. A cyan marker appears.
3.  Move to the next point and tap **Add Point**. Repeat for all waypoints.
4.  A blue polyline will connect the points.
5.  Adjust speed using the slider or Transport Mode chips (Walk/Cycle/Drive).
6.  Tap **Play Route**. The app will simulate movement along the path.
7.  Tap **Pause** to pause or **Clear** to remove the route.

## Testing
Run unit tests:
```bash
./gradlew test
```
Note: Robolectric tests require a configured environment.

## License
MIT
