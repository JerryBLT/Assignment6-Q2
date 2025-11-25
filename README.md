# Assignment6-Q2
This project implements a Compass (using magnetometer + accelerometer) and a Digital Level (using gyroscope). The UI is built in Jetpack Compose and updates in real time based on sensor readings. A full Simulation Mode is included so the app can be tested easily on the Android Emulator, which does not have real sensor hardware.

## Features

**Compass**
- Uses accelerometer + magnetometer fusion
- Computes orientation via:
SensorManager.getRotationMatrix()
SensorManager.getOrientation()
- Displays a Canvas-based rotating needle
- Heading displayed in degrees (0–360°)

**Digital Level**
- Uses gyroscope (Sensor.TYPE_GYROSCOPE)
- Integrates angular velocity to compute:
Roll
Pitch
- Includes a fun bubble level gauge (Canvas)
- Colorful animated UI based on heading for visual interest

## How It Works
**Compass**
- Accelerometer gives gravity vector
- Magnetometer gives Earth magnetic field
- Rotation matrix → azimuth (heading)

**Digital Level**
- Gyroscope gives rotation rate
- Integrate over time → roll/pitch angle estimates
- Move a “bubble” inside a circular frame like a physical level

## Get Started
- Clone the github repository
- Open it in Android Studio Kotlin
