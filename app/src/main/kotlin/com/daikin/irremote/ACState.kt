package com.daikin.irremote

/**
 * ACState.kt
 *
 * Represents the complete state of the Daikin air conditioner.
 * Every time a button is pressed, the entire state is encoded into
 * an IR signal and transmitted. This mirrors how real Daikin remotes work —
 * each transmission carries the full state, not just the changed parameter.
 */

/**
 * AC operating mode.
 * Each mode corresponds to a specific bit pattern in the Daikin IR protocol.
 */
enum class ACMode(val code: Int, val displayName: String) {
    COOL(0x03, "Cool"),
    DRY(0x02, "Dry"),
    FAN(0x06, "Fan")
}

/**
 * Fan speed setting.
 * AUTO lets the AC choose the speed; others are fixed speeds.
 */
enum class FanSpeed(val code: Int, val displayName: String) {
    AUTO(0xA0, "Auto"),
    LOW(0x30, "Low"),
    MEDIUM(0x50, "Medium"),
    HIGH(0x70, "High")
}

/**
 * Holds the complete AC state.
 *
 * @property isPowerOn   Whether the AC is currently on or off.
 * @property temperature Current temperature setting in °C (range: 16–30).
 * @property mode        Current operating mode (Cool, Dry, Fan).
 * @property fanSpeed    Current fan speed (Auto, Low, Medium, High).
 */
data class ACState(
    var isPowerOn: Boolean = false,
    var temperature: Int = 24,       // Default 24°C
    var mode: ACMode = ACMode.COOL,
    var fanSpeed: FanSpeed = FanSpeed.AUTO
) {
    companion object {
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30
    }

    /**
     * Increase temperature by 1°C, capped at MAX_TEMP.
     */
    fun increaseTemp() {
        if (temperature < MAX_TEMP) temperature++
    }

    /**
     * Decrease temperature by 1°C, floored at MIN_TEMP.
     */
    fun decreaseTemp() {
        if (temperature > MIN_TEMP) temperature--
    }

    /**
     * Toggle power state on/off.
     */
    fun togglePower() {
        isPowerOn = !isPowerOn
    }
}
