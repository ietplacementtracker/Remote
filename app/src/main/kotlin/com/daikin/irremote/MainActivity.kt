package com.daikin.irremote

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity.kt
 *
 * The main (and only) screen of the Daikin IR Remote app.
 *
 * UI overview:
 *  - A temperature display shows the current set temperature (°C).
 *  - Power button toggles AC on/off and updates the button colour.
 *  - Temperature up/down buttons adjust the set temperature within 16–30 °C.
 *  - Mode buttons (Cool / Dry / Fan) switch the operating mode.
 *  - Fan speed buttons (Auto / Low / Medium / High) switch the fan speed.
 *
 * Every button press:
 *  1. Updates the ACState object.
 *  2. Calls IRController.transmit(state) to send the full state over IR.
 *  3. Refreshes the UI to reflect the new state.
 */
class MainActivity : AppCompatActivity() {

    // ── Core objects ─────────────────────────────────────────────────────
    private lateinit var irController: IRController
    private val acState = ACState()         // Starts powered off, 24 °C, Cool, Auto

    // ── UI references ────────────────────────────────────────────────────
    private lateinit var tvTemperature: TextView
    private lateinit var tvPowerStatus: TextView
    private lateinit var btnPower: Button

    // Mode buttons
    private lateinit var btnCool: Button
    private lateinit var btnDry: Button
    private lateinit var btnFan: Button

    // Fan speed buttons
    private lateinit var btnFanAuto: Button
    private lateinit var btnFanLow: Button
    private lateinit var btnFanMedium: Button
    private lateinit var btnFanHigh: Button

    // Temperature buttons
    private lateinit var btnTempUp: Button
    private lateinit var btnTempDown: Button

    // ─────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialise the IR controller
        irController = IRController(this)

        // Warn user if device has no IR blaster
        if (!irController.hasIRBlaster()) {
            Toast.makeText(
                this,
                "Warning: This device does not have an IR blaster.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Wire up all views from the layout
        bindViews()

        // Attach click listeners to all buttons
        setupListeners()

        // Draw the initial UI state (power off, defaults shown)
        updateUI()
    }

    /**
     * Find and store references to all views declared in activity_main.xml.
     */
    private fun bindViews() {
        tvTemperature  = findViewById(R.id.tvTemperature)
        tvPowerStatus  = findViewById(R.id.tvPowerStatus)
        btnPower       = findViewById(R.id.btnPower)

        btnCool        = findViewById(R.id.btnCool)
        btnDry         = findViewById(R.id.btnDry)
        btnFan         = findViewById(R.id.btnFan)

        btnFanAuto     = findViewById(R.id.btnFanAuto)
        btnFanLow      = findViewById(R.id.btnFanLow)
        btnFanMedium   = findViewById(R.id.btnFanMedium)
        btnFanHigh     = findViewById(R.id.btnFanHigh)

        btnTempUp      = findViewById(R.id.btnTempUp)
        btnTempDown    = findViewById(R.id.btnTempDown)
    }

    /**
     * Attach click listeners to each button.
     * Each listener mutates ACState, sends the IR signal, then refreshes the UI.
     */
    private fun setupListeners() {

        // ── Power ──────────────────────────────────────────────────────
        btnPower.setOnClickListener {
            acState.togglePower()
            sendIR()
            updateUI()
        }

        // ── Temperature ────────────────────────────────────────────────
        btnTempUp.setOnClickListener {
            acState.increaseTemp()
            sendIR()
            updateUI()
        }

        btnTempDown.setOnClickListener {
            acState.decreaseTemp()
            sendIR()
            updateUI()
        }

        // ── Mode ───────────────────────────────────────────────────────
        btnCool.setOnClickListener {
            acState.mode = ACMode.COOL
            sendIR()
            updateUI()
        }

        btnDry.setOnClickListener {
            acState.mode = ACMode.DRY
            sendIR()
            updateUI()
        }

        btnFan.setOnClickListener {
            acState.mode = ACMode.FAN
            sendIR()
            updateUI()
        }

        // ── Fan speed ──────────────────────────────────────────────────
        btnFanAuto.setOnClickListener {
            acState.fanSpeed = FanSpeed.AUTO
            sendIR()
            updateUI()
        }

        btnFanLow.setOnClickListener {
            acState.fanSpeed = FanSpeed.LOW
            sendIR()
            updateUI()
        }

        btnFanMedium.setOnClickListener {
            acState.fanSpeed = FanSpeed.MEDIUM
            sendIR()
            updateUI()
        }

        btnFanHigh.setOnClickListener {
            acState.fanSpeed = FanSpeed.HIGH
            sendIR()
            updateUI()
        }
    }

    /**
     * Transmit the current AC state over IR.
     * Shows a brief message if the device has no IR blaster.
     */
    private fun sendIR() {
        if (!irController.hasIRBlaster()) {
            Toast.makeText(this, "No IR blaster available", Toast.LENGTH_SHORT).show()
            return
        }
        irController.transmit(acState)
    }

    /**
     * Refresh all UI elements to reflect the current ACState.
     * Called after every button press.
     */
    private fun updateUI() {
        // ── Temperature display ────────────────────────────────────────
        tvTemperature.text = "${acState.temperature}°C"

        // ── Power status label ─────────────────────────────────────────
        tvPowerStatus.text = if (acState.isPowerOn) "ON" else "OFF"
        tvPowerStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (acState.isPowerOn) R.color.power_on else R.color.power_off
            )
        )

        // ── Power button colour ────────────────────────────────────────
        btnPower.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (acState.isPowerOn) R.color.power_on else R.color.power_off
        )

        // ── Mode buttons: highlight the selected mode ──────────────────
        setButtonSelected(btnCool,   acState.mode == ACMode.COOL)
        setButtonSelected(btnDry,    acState.mode == ACMode.DRY)
        setButtonSelected(btnFan,    acState.mode == ACMode.FAN)

        // ── Fan speed buttons: highlight the selected speed ────────────
        setButtonSelected(btnFanAuto,   acState.fanSpeed == FanSpeed.AUTO)
        setButtonSelected(btnFanLow,    acState.fanSpeed == FanSpeed.LOW)
        setButtonSelected(btnFanMedium, acState.fanSpeed == FanSpeed.MEDIUM)
        setButtonSelected(btnFanHigh,   acState.fanSpeed == FanSpeed.HIGH)

        // ── Disable temp buttons at limits ────────────────────────────
        btnTempUp.isEnabled   = acState.temperature < ACState.MAX_TEMP
        btnTempDown.isEnabled = acState.temperature > ACState.MIN_TEMP
    }

    /**
     * Apply selected/unselected visual styling to a button.
     *
     * @param button   The button to style.
     * @param selected True if this button represents the currently active option.
     */
    private fun setButtonSelected(button: Button, selected: Boolean) {
        button.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (selected) R.color.selected_button else R.color.unselected_button
        )
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.selected_text else R.color.unselected_text
            )
        )
    }
}
