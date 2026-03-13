package com.daikin.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

/**
 * IRController.kt
 *
 * Handles all IR signal generation and transmission for Daikin air conditioners.
 *
 * How Daikin IR signals work:
 * -  Carrier frequency: 38000 Hz (38 kHz), standard for most AC remotes.
 * -  The signal consists of two frames separated by a pause.
 * -  Frame 1: A fixed 8-byte header (same for all commands).
 * -  Frame 2: A 19-byte variable payload containing the full AC state.
 * -  Each byte is transmitted LSB-first (least significant bit first).
 * -  Timing is expressed in microseconds (µs), alternating between
 *    mark (IR LED on) and space (IR LED off) durations.
 *
 * Timing constants (in µs):
 *  - HEADER_MARK  / HEADER_SPACE : Start of each frame
 *  - BIT_MARK                    : Short mark separating bits
 *  - ONE_SPACE                   : Long space = bit value 1
 *  - ZERO_SPACE                  : Short space = bit value 0
 *  - FOOTER_MARK / INTER_FRAME_SPACE : End of frame 1, pause before frame 2
 */
class IRController(context: Context) {

    companion object {
        private const val TAG = "IRController"

        // ──────────────────────────────────────────
        // Carrier frequency for Daikin AC (38 kHz)
        // ──────────────────────────────────────────
        const val CARRIER_FREQUENCY = 38000

        // ──────────────────────────────────────────
        // Timing values in microseconds (µs)
        // ──────────────────────────────────────────
        private const val HEADER_MARK = 3650
        private const val HEADER_SPACE = 1623
        private const val BIT_MARK = 428
        private const val ONE_SPACE = 1280
        private const val ZERO_SPACE = 428
        private const val FOOTER_MARK = 428
        private const val INTER_FRAME_SPACE = 29000  // Gap between frame 1 and frame 2

        // ──────────────────────────────────────────
        // Daikin Frame 1: Fixed 8-byte header
        // This is always the same regardless of AC state.
        // Source: Reverse-engineered Daikin IR protocol (ARC433**)
        // ──────────────────────────────────────────
        private val FRAME1 = byteArrayOf(
            0x11, 0xDA.toByte(), 0x27, 0x00, 0xC5.toByte(), 0x00, 0x00, 0xD7.toByte()
        )

        // ──────────────────────────────────────────
        // Daikin Frame 2: 19-byte state payload template
        // Bytes that vary are filled in by buildFrame2().
        // Byte indices (0-based):
        //   0–5   : Fixed preamble
        //   6     : Power + Mode
        //   7     : Temperature  (°C * 2)
        //   8     : Fixed 0x80
        //   9     : Fan speed + Swing (0x00 = swing off)
        //   10–12 : Fixed bytes
        //   13    : On timer (0x00 = disabled)
        //   14    : Off timer (0x00 = disabled)
        //   15    : Timer flags (0x00 = all disabled)
        //   16–17 : Fixed bytes
        //   18    : Checksum (sum of bytes 0–17, mod 256)
        // ──────────────────────────────────────────
        private val FRAME2_TEMPLATE = byteArrayOf(
            0x11, 0xDA.toByte(), 0x27, 0x00, 0x00, 0x00,
            0x00,  // [6]  Power + Mode  — filled dynamically
            0x00,  // [7]  Temperature   — filled dynamically
            0x80.toByte(),
            0x00,  // [9]  Fan speed     — filled dynamically
            0x00, 0x00, 0x00,
            0x00,  // [13] On timer
            0x00,  // [14] Off timer
            0x00,  // [15] Timer flags
            0x00, 0xC0.toByte(),
            0x00   // [18] Checksum      — computed dynamically
        )

        // ──────────────────────────────────────────
        // Power bit flags used in byte 6 of frame 2
        // ──────────────────────────────────────────
        private const val POWER_ON_BIT = 0x01
        // Bit 0 = power on/off; bits 4–7 = mode code
    }

    // Get the system IR manager service
    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /**
     * Returns true if this device has an IR blaster.
     * Always check this before attempting to transmit.
     */
    fun hasIRBlaster(): Boolean {
        return irManager?.hasIrEmitter() == true
    }

    /**
     * Transmit the complete AC state as a Daikin IR signal.
     *
     * This builds both frames, converts them to mark/space timing arrays,
     * and sends them through the ConsumerIrManager.
     *
     * @param state The current ACState to encode and transmit.
     */
    fun transmit(state: ACState) {
        if (!hasIRBlaster()) {
            Log.w(TAG, "Device has no IR blaster — cannot transmit")
            return
        }

        // Build the complete 19-byte frame 2 with current AC state
        val frame2 = buildFrame2(state)

        // Convert both frames into mark/space timing arrays and concatenate
        val pattern = buildPattern(frame2)

        // Transmit via the Android ConsumerIrManager API
        try {
            irManager!!.transmit(CARRIER_FREQUENCY, pattern)
            Log.d(TAG, "IR signal transmitted: power=${state.isPowerOn}, " +
                    "temp=${state.temperature}°C, mode=${state.mode.displayName}, " +
                    "fan=${state.fanSpeed.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "IR transmission failed: ${e.message}", e)
        }
    }

    /**
     * Build frame 2 by encoding the AC state into the 19-byte payload.
     *
     * @param state The current AC state.
     * @return A 19-byte array representing the Daikin frame 2 payload.
     */
    private fun buildFrame2(state: ACState): ByteArray {
        val frame = FRAME2_TEMPLATE.copyOf()

        // ── Byte 6: Power bit (bit 0) + Mode code (bits 4–7) ──
        // Power on  → bit 0 = 1
        // Power off → bit 0 = 0
        // Mode code occupies the upper nibble (shifted left 4 bits)
        val powerBit = if (state.isPowerOn) POWER_ON_BIT else 0x00
        frame[6] = (powerBit or (state.mode.code shl 4)).toByte()

        // ── Byte 7: Temperature ──
        // Daikin encodes temperature as (°C - 10) * 2
        // Example: 24°C → (24 - 10) * 2 = 28 = 0x1C
        frame[7] = ((state.temperature - 10) * 2).toByte()

        // ── Byte 9: Fan speed ──
        // Lower nibble: 0x0 = no swing (fixed position)
        // Upper nibble: fan speed code
        // The fan speed enum already stores the combined byte value (e.g. 0xA0 = Auto)
        frame[9] = state.fanSpeed.code.toByte()

        // ── Byte 18: Checksum ──
        // The checksum is the sum of bytes 0–17, taken modulo 256.
        frame[18] = calculateChecksum(frame, 0, 17)

        return frame
    }

    /**
     * Calculate the Daikin checksum for a byte range.
     * Checksum = sum of bytes[startIndex..endIndex] mod 256.
     *
     * @param data       Byte array to checksum.
     * @param startIndex First byte to include (inclusive).
     * @param endIndex   Last byte to include (inclusive).
     * @return The checksum byte.
     */
    private fun calculateChecksum(data: ByteArray, startIndex: Int, endIndex: Int): Byte {
        var sum = 0
        for (i in startIndex..endIndex) {
            sum += data[i].toInt() and 0xFF  // Treat as unsigned
        }
        return (sum and 0xFF).toByte()
    }

    /**
     * Convert two Daikin frames into a single mark/space timing array
     * suitable for ConsumerIrManager.transmit().
     *
     * Structure:
     *   [Frame 1 header + data + footer]
     *   [Inter-frame space pause]
     *   [Frame 2 header + data + footer]
     *
     * @param frame2 The 19-byte frame 2 payload.
     * @return An int array of alternating mark/space durations in microseconds.
     */
    private fun buildPattern(frame2: ByteArray): IntArray {
        val timings = mutableListOf<Int>()

        // ── Frame 1 ──
        addFrame(timings, FRAME1)

        // Inter-frame pause between frame 1 and frame 2
        // The last entry of frame 1 is FOOTER_MARK, so we add the space here
        timings.add(INTER_FRAME_SPACE)

        // ── Frame 2 ──
        addFrame(timings, frame2)

        return timings.toIntArray()
    }

    /**
     * Encode a single byte array (frame) into the timing list.
     *
     * Each frame starts with a header mark/space, followed by each bit
     * of each byte (LSB first), and ends with a footer mark.
     *
     * @param timings Mutable list to append mark/space values to.
     * @param frame   Byte array to encode.
     */
    private fun addFrame(timings: MutableList<Int>, frame: ByteArray) {
        // Frame header: long mark + long space
        timings.add(HEADER_MARK)
        timings.add(HEADER_SPACE)

        // Encode each byte, LSB first
        for (byte in frame) {
            encodeByte(timings, byte.toInt() and 0xFF)
        }

        // Frame footer: short mark (the next item will be a space)
        timings.add(FOOTER_MARK)
    }

    /**
     * Encode a single byte (8 bits) into mark/space timings, LSB first.
     *
     * Bit = 1: BIT_MARK followed by ONE_SPACE  (long space)
     * Bit = 0: BIT_MARK followed by ZERO_SPACE (short space)
     *
     * @param timings Mutable list to append mark/space values to.
     * @param byte    The byte value (0–255) to encode.
     */
    private fun encodeByte(timings: MutableList<Int>, byte: Int) {
        for (bit in 0..7) {  // LSB first (bit 0 → bit 7)
            timings.add(BIT_MARK)
            if ((byte shr bit) and 0x01 == 1) {
                timings.add(ONE_SPACE)   // Logic '1'
            } else {
                timings.add(ZERO_SPACE)  // Logic '0'
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXTENDING WITH ADDITIONAL CODES
    // ══════════════════════════════════════════════════════════════════════
    //
    // To add more Daikin models or special commands:
    //
    // 1. Add new enum values in ACMode or FanSpeed if new modes/speeds apply.
    //
    // 2. For special one-shot commands (e.g., "Powerful", "Econo", "Sleep"),
    //    add a method like:
    //
    //    fun transmitPowerfulMode(state: ACState) {
    //        val frame = buildFrame2(state)
    //        frame[13] = 0x01  // Enable "Powerful" flag bit
    //        frame[18] = calculateChecksum(frame, 0, 17)
    //        val pattern = buildPattern(frame)
    //        irManager!!.transmit(CARRIER_FREQUENCY, pattern)
    //    }
    //
    // 3. To add a completely different Daikin model (e.g., BRC52B),
    //    create a separate method buildFrame2ForModelX() with the
    //    correct byte offsets for that model's protocol variant.
    //
    // 4. Verified IR hex strings from sites like LIRC, irdb, or
    //    RemotesCentral can be converted to mark/space arrays and
    //    transmitted directly using irManager!!.transmit().
    //
    // ══════════════════════════════════════════════════════════════════════
}
