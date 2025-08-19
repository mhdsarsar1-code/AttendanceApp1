package com.example.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val client = OkHttpClient()

    // 👉 HIER deine Apps Script Web-App URL einsetzen:
    private val scriptUrl = "HIER_DEINE_GOOGLE_APPS_SCRIPT_URL_EINFUEGEN"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val employeeIdInput = findViewById<EditText>(R.id.employeeIdInput)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val statusText = findViewById<TextView>(R.id.statusText)
        val checkInButton = findViewById<Button>(R.id.checkInButton)
        val checkOutButton = findViewById<Button>(R.id.checkOutButton)

        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    statusText.text = "Biometrie OK – Aktion auswählen."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    statusText.text = "Biometrie Fehler: $errString"
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    statusText.text = "Biometrie fehlgeschlagen – nochmal versuchen."
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerabdruck bestätigen")
            .setSubtitle("Bitte authentifizieren, um zu stempeln")
            .setNegativeButtonText("Abbrechen")
            .build()

        checkInButton.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
            performActionAfterBiometric("Check-In", employeeIdInput.text.toString(), nameInput.text.toString(), statusText)
        }
        checkOutButton.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
            performActionAfterBiometric("Check-Out", employeeIdInput.text.toString(), nameInput.text.toString(), statusText)
        }
    }

    private fun performActionAfterBiometric(action: String, id: String, name: String, statusText: TextView) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Standort-Berechtigung fehlt."
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                val locStr = if (location != null) "${location.latitude},${location.longitude}" else "unbekannt"
                sendToSheet(id, name, action, locStr, statusText)
            }
            .addOnFailureListener {
                statusText.text = "Standort konnte nicht geholt werden."
            }
    }

    private fun sendToSheet(id: String, name: String, action: String, location: String, statusText: TextView) {
        if (scriptUrl.startsWith("HIER_") || scriptUrl.contains("EINFUEGEN")) {
            statusText.text = "Bitte Apps-Script URL in MainActivity.kt eintragen."
            return
        }

        val json = JSONObject().apply {
            put("id", if (id.isBlank()) "unbekannt" else id)
            put("name", if (name.isBlank()) "unbekannt" else name)
            put("action", action)
            put("location", location)
        }

        // WICHTIG: 'text/plain' vermeidet CORS-Preflight bei Apps Script
        val body = RequestBody.create("text/plain; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder().url(scriptUrl).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Fehler beim Senden: ${e.message}" }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    statusText.text = if (response.isSuccessful) "Gespeichert: $action" else "Serverfehler: ${response.code}"
                }
            }
        })
    }
}
