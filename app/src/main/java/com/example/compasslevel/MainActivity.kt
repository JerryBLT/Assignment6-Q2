package com.example.compasslevel

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compasslevel.ui.theme.CompassLevelTheme
import kotlin.math.min

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Buffers to hold raw accelerometer (gravity) and magnetometer data for compass calculation.
    private val gravity = FloatArray(3)
    private val geomag = FloatArray(3)
    private var hasGravity = false
    private var hasGeomag = false

    // Compose mutable state backing fields for UI to reactively display heading,
    private var _heading by mutableFloatStateOf(0f)     // compass heading in degrees [0, 360)
    private var _roll by mutableFloatStateOf(0f)        // device roll angle in degrees
    private var _pitch by mutableFloatStateOf(0f)        // device pitch angle in degrees

    // Timestamp to integrate gyroscope angular velocity between sensor updates
    private var lastGyroTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sensor manager and obtain references to required sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Set the Compose UI content with theme and pass current sensor state for rendering
        setContent {
            CompassLevelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompassAndLevelScreen(
                        heading = _heading,
                        roll = _roll,
                        pitch = _pitch
                    )
                }
            }
        }
    }

    override fun onResume() {
        // Register sensor listeners with UI delay to balance responsiveness and battery use
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister all listeners to save power when activity not in foreground
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Update gravity vector for compass orientation
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                updateCompassHeading() // Calculate heading if both sensor data available
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Update geomagnetic vector for compass orientation
                System.arraycopy(event.values, 0, geomag, 0, 3)
                hasGeomag = true
                updateCompassHeading()
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Integrate gyroscope angular velocity to update digital level tilt
                updateGyroTilt(event)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required to handle sensor accuracy changes for this implementation
    }

    // --- Compass calculation ---
    // Calculate compass heading in degrees based on accelerometer and magnetometer data

    private fun updateCompassHeading() {
        if (!hasGravity || !hasGeomag) return

        val R = FloatArray(9)
        val I = FloatArray(9)
        if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)

            // orientation[0] = azimuth (radians), relative to magnetic north
            val azimuthRad = orientation[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0) azimuthDeg += 360f // Normalize to [0, 360)

            _heading = azimuthDeg
        }
    }

    // --- Digital level using gyroscope ---
    // // Incrementally update roll and pitch angles by integrating gyroscope angular velocity

    private fun updateGyroTilt(event: SensorEvent) {
        val timestamp = event.timestamp
        if (lastGyroTimestamp != 0L) {
            val dt = (timestamp - lastGyroTimestamp) / 1_000_000_000f // seconds
            // Gyro values are angular velocity (rad/s) around x, y, z
            val wx = event.values[0]
            val wy = event.values[1]

            val radToDeg = 57.2958f // 180/pi
            // Update roll and pitch by integrating angular velocity over elapsed time
            _roll += wx * dt * radToDeg
            _pitch += wy * dt * radToDeg
        }
        lastGyroTimestamp = timestamp
    }
}

// Composable screen displaying compass and digital level UI elements with background reacting to heading
@Composable
fun CompassAndLevelScreen(
    heading: Float,
    roll: Float,
    pitch: Float
) {
    // Background color changes subtly based on heading to add visual interest
    val bgColor = Color(
        red = (0.3f + (heading / 360f) * 0.7f),
        green = 0.2f,
        blue = 0.5f + ((360f - heading) / 360f) * 0.5f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "Compass & Digital Level",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        // Display compass widget pointing to heading
        CompassView(heading = heading)

        // Display digital level showing roll and pitch angles
        DigitalLevelView(roll = roll, pitch = pitch)
    }
}

// Composable displaying the compass UI including heading text and rotating needle inside a circle
@Composable
fun CompassView(heading: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Heading: ${heading.toInt()}°",
            color = Color.White,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasSize = size
                val radius = min(canvasSize.width, canvasSize.height) / 2f * 0.9f
                val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                // Draw outer circle frame for compass
                drawCircle(
                    color = Color.Yellow,
                    radius = radius,
                    center = center
                )

                // Rotate the red needle to point to current heading (0° up)
                rotate(degrees = heading) {
                    val needleEnd = Offset(
                        center.x,
                        center.y - radius * 0.85f
                    )
                    drawLine(
                        color = Color.Red,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 10f
                    )
                }

                // Center circle to anchor the needle visually
                drawCircle(
                    color = Color.Black,
                    radius = 14f,
                    center = center
                )
            }
        }
    }
}

// Composable rendering a digital level display showing roll and pitch with a simple "bubble" indicator
@Composable
fun DigitalLevelView(roll: Float, pitch: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFFFF))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Digital Level",
            color = Color.Black,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Roll: ${"%.1f".format(roll)}°",
            color = Color.Cyan,
            fontSize = 18.sp
        )
        Text(
            text = "Pitch: ${"%.1f".format(pitch)}°",
            color = Color.Green,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bubble level visualization constrained within UI bounds based on normalized roll & pitch values
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxOffset = size.minDimension / 3f

                // Normalize tilt to [-1, 1] range to keep bubble on-screen
                val normRoll = (roll / 45f).coerceIn(-1f, 1f)
                val normPitch = (pitch / 45f).coerceIn(-1f, 1f)

                val bubbleCenter = Offset(
                    x = center.x + normRoll * maxOffset,
                    y = center.y + normPitch * maxOffset
                )

                // Outer circle frame for bubble level
                drawCircle(
                    color = Color.Yellow,
                    radius = maxOffset * 1.1f,
                    center = center
                )

                // Red "bubble" circle representing device tilt
                drawCircle(
                    color = Color.Red,
                    radius = maxOffset / 4f,
                    center = bubbleCenter
                )
            }
        }
    }
}