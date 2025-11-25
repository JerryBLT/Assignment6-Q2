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
import androidx.compose.material3.*
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

    // Compose mutable state backing fields for UI to reactively display heading,  roll, and pitch angles.
    private var _heading by mutableFloatStateOf(0f)  // compass heading in degrees [0, 360)
    private var _roll by mutableFloatStateOf(0f)   // device roll angle in degrees
    private var _pitch by mutableFloatStateOf(0f)  // device pitch angle in degrees

    // Timestamp to integrate gyroscope angular velocity between sensor updates
    private var lastGyroTimestamp: Long = 0L

    // Simulation mode toggle for testing UI without real sensor input
    private var simulateMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sensor manager and obtain references to required sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Set the Compose UI content with theme and pass current sensor state for rendering.
        setContent {
            CompassLevelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompassAndLevelScreen(
                        heading = _heading,
                        roll = _roll,
                        pitch = _pitch,
                        simulateMode = simulateMode,
                        onToggleSim = { simulateMode = !simulateMode }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!simulateMode) {
            // Register sensor listeners with UI delay for balanced responsiveness and battery use
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
    }

    override fun onPause() {
        super.onPause()
        // Unregister all listeners to save power when activity is not in foreground
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (simulateMode) return // Ignore real sensor events if in simulation mode

        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Update gravity vector for compass orientation calculation
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                updateCompassHeading()  // Calculate heading if both sensor data available
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Update geomagnetic vector for compass orientation calculation
                System.arraycopy(event.values, 0, geomag, 0, 3)
                hasGeomag = true
                updateCompassHeading()
            }
            Sensor.TYPE_GYROSCOPE -> {
                // Use gyroscope angular velocity to update digital level tilt angles
                updateGyroTilt(event)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not required to handle sensor accuracy changes for this implementation
    }

    // Calculate compass heading in degrees based on accelerometer and magnetometer data.
    private fun updateCompassHeading() {
        if (!hasGravity || !hasGeomag) return

        val R = FloatArray(9)
        val I = FloatArray(9)

        if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)

            // orientation[0] is azimuth (radians) relative to magnetic north.
            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDeg < 0) azimuthDeg += 360f

            _heading = azimuthDeg
        }
    }

    // Incrementally update roll and pitch angles by integrating gyroscope angular velocity over time.
    private fun updateGyroTilt(event: SensorEvent) {
        val timestamp = event.timestamp

        if (lastGyroTimestamp != 0L) {
            val dt = (timestamp - lastGyroTimestamp) / 1_000_000_000f // Convert nanoseconds to seconds

            val wx = event.values[0] // Angular velocity around x-axis (roll)
            val wy = event.values[1] // Angular velocity around y-axis (pitch)

            val radToDeg = 57.2958f // Conversion factor from radians to degrees
            _roll += wx * dt * radToDeg
            _pitch += wy * dt * radToDeg
        }
        lastGyroTimestamp = timestamp
    }
}

// Composable screen displaying compass and digital level UI elements with simulation mode and sliders.
@Composable
fun CompassAndLevelScreen(
    heading: Float,
    roll: Float,
    pitch: Float,
    simulateMode: Boolean,
    onToggleSim: () -> Unit
) {
    // State variables for simulation sliders to manually adjust heading, roll, and pitch.
    var simHeading by remember { mutableFloatStateOf(heading) }
    var simRoll by remember { mutableFloatStateOf(roll) }
    var simPitch by remember { mutableFloatStateOf(pitch) }

    // Use simulated values or real sensor values depending on mode.
    val finalHeading = if (simulateMode) simHeading else heading
    val finalRoll = if (simulateMode) simRoll else roll
    val finalPitch = if (simulateMode) simPitch else pitch

    // Dynamic background color changes subtly based on heading for visual effect.
    val bgColor = Color(
        red = (0.3f + (finalHeading / 360f) * 0.7f),
        green = 0.2f,
        blue = 0.5f + ((360f - finalHeading) / 360f) * 0.5f
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
            "Compass & Digital Level",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        // Button to toggle simulation mode on or off
        Button(onClick = onToggleSim) {
            Text(if (simulateMode) "Disable Simulation" else "Enable Simulation")
        }

        if (simulateMode) {
            // Show sliders to manually adjust simulated sensor values
            SimulationSliders(
                simHeading = simHeading,
                simRoll = simRoll,
                simPitch = simPitch,
                onHeadingChange = { simHeading = it },
                onRollChange = { simRoll = it },
                onPitchChange = { simPitch = it }
            )
        }

        // Show compass visualization with current heading
        CompassView(finalHeading)
        // Show digital level visualization with current roll and pitch
        DigitalLevelView(finalRoll, finalPitch)
    }
}

@Composable
fun SimulationSliders(
    simHeading: Float,
    simRoll: Float,
    simPitch: Float,
    onHeadingChange: (Float) -> Unit,
    onRollChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    // Sliders to simulate heading, roll, and pitch values for testing UI without real sensors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("Simulate Heading: ${simHeading.toInt()}°", color = Color.White)
        Slider(value = simHeading, onValueChange = onHeadingChange, valueRange = 0f..360f)

        Text("Simulate Roll: ${simRoll.toInt()}°", color = Color.White)
        Slider(value = simRoll, onValueChange = onRollChange, valueRange = -45f..45f)

        Text("Simulate Pitch: ${simPitch.toInt()}°", color = Color.White)
        Slider(value = simPitch, onValueChange = onPitchChange, valueRange = -45f..45f)
    }
}

@Composable
fun CompassView(heading: Float) {
    // UI displaying the compass heading value and a rotating needle inside a circle
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Text(
            text = "Heading: ${heading.toInt()}°",
            color = Color.White,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {

                val canvasSize = size
                val radius = min(canvasSize.width, canvasSize.height) * 0.45f
                val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                // Draw outer yellow circle as compass frame
                drawCircle(Color.Yellow, radius, center)

                // Rotate red needle to point to current heading (0° is up)
                rotate(degrees = heading) {
                    val needleEnd = Offset(center.x, center.y - radius * 0.85f)
                    drawLine(Color.Red, center, needleEnd, strokeWidth = 10f)
                }

                // Draw a black center circle to anchor the needle visually
                drawCircle(Color.Black, 14f, center)
            }
        }
    }
}

@Composable
fun DigitalLevelView(roll: Float, pitch: Float) {
    // UI displaying roll and pitch values with a bubble indicator representing tilt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Digital Level", color = Color.Black, style = MaterialTheme.typography.titleLarge)

        Text("Roll: %.1f°".format(roll), color = Color.Cyan, fontSize = 18.sp)
        Text("Pitch: %.1f°".format(pitch), color = Color.Green, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {

            Canvas(modifier = Modifier.fillMaxSize()) {

                val center = Offset(size.width / 2f, size.height / 2f)
                val maxOffset = size.minDimension / 3f
                // Normalize roll and pitch tilt to range [-1, 1] for bubble positioning
                val bubbleX = center.x + (roll / 45f).coerceIn(-1f, 1f) * maxOffset
                val bubbleY = center.y + (pitch / 45f).coerceIn(-1f, 1f) * maxOffset
                // Draw outer yellow circle representing level outline
                drawCircle(Color.Yellow, maxOffset * 1.1f, center)
                // Draw red "bubble" circle indicating current tilt
                drawCircle(Color.Red, maxOffset / 4f, Offset(bubbleX, bubbleY))
            }
        }
    }
}