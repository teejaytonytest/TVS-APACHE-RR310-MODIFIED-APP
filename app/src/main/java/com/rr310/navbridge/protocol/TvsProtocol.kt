package com.rr310.navbridge.protocol

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * TVS SmartXonnect protocol adapted for 2024 TVS Apache RR 310 (Quickshifter TFT).
 */
object TvsProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("5456534D-5647-5341-5342-454E544F5251")
    val CHAR_NOTIFY_UUID: UUID = UUID.fromString("00005354-0000-1000-8000-00805f9b34fb")
    val CHAR_WRITE_UUID: UUID = UUID.fromString("00005352-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val START_BYTE_NAV: Byte = 0x5A.toByte()
    const val DATA_ID_NAV: Byte = 0x4E.toByte()
    const val START_BYTE_TEXT: Byte = 0x5B.toByte()
    const val DATA_ID_TEXT: Byte = 0x53.toByte()
    const val XOR_KEY: Byte = 0xEA.toByte()

    enum class Pictogram(var code: Byte) {
        OFF(0),
        STRAIGHT(1),
        SLIGHT_RIGHT(2),
        TURN_RIGHT(3),
        SHARP_RIGHT(4),
        SLIGHT_LEFT(5),
        TURN_LEFT(6),
        SHARP_LEFT(7),
        U_TURN(8),
        U_TURN_LEFT(9),
        ROUNDABOUT(10),
        ROUNDABOUT_LEFT(11),
        KEEP_RIGHT(12),
        KEEP_LEFT(13),
        ARRIVED(14)
    }

    fun buildNavigationPacket(
        distanceMeters: Int,
        remainingDistanceMeters: Int,
        remainingTimeSeconds: Int,
        pictogram: Pictogram,
        isRerouting: Boolean = false,
        isMetric: Boolean = true
    ): ByteArray {
        return buildNavigationPacketRawCode(
            distanceMeters = distanceMeters,
            remainingDistanceMeters = remainingDistanceMeters,
            remainingTimeSeconds = remainingTimeSeconds,
            pictogramCode = pictogram.code,
            isRerouting = isRerouting,
            isMetric = isMetric
        )
    }

    fun buildNavigationPacketRawCode(
        distanceMeters: Int,
        remainingDistanceMeters: Int,
        remainingTimeSeconds: Int,
        pictogramCode: Byte,
        isRerouting: Boolean = false,
        isMetric: Boolean = true
    ): ByteArray {
        val raw = ByteArray(20)

        raw[0] = START_BYTE_NAV // 0x5A
        raw[1] = DATA_ID_NAV    // 0x4E ('N')

        // Bytes 2..3: Distance to upcoming maneuver (meters)
        val distClamped = distanceMeters.coerceIn(0, 0xFFFF)
        raw[2] = ((distClamped shr 8) and 0xFF).toByte()
        raw[3] = (distClamped and 0xFF).toByte()

        // Bytes 4..5: 2024 RR 310 cluster expects Byte 4 = Hours, Byte 5 = Minutes
        val totalMinutes = (remainingTimeSeconds / 60).coerceAtLeast(0)
        val hours = (totalMinutes / 60).coerceIn(0, 99)
        val minutes = (totalMinutes % 60).coerceIn(0, 59)
        raw[4] = hours.toByte()
        raw[5] = minutes.toByte()

        // Bytes 6..8: Remaining journey distance (3 bytes, meters)
        val remDistClamped = remainingDistanceMeters.coerceIn(0, 0xFFFFFF)
        raw[6] = ((remDistClamped shr 16) and 0xFF).toByte()
        raw[7] = ((remDistClamped shr 8) and 0xFF).toByte()
        raw[8] = (remDistClamped and 0xFF).toByte()

        // XOR encrypt bytes 2..8
        for (i in 2..8) {
            raw[i] = (raw[i].toInt() xor XOR_KEY.toInt()).toByte()
        }

        // Unencrypted pictogram code and metadata flags (bytes 9..17)
        raw[9] = (pictogramCode.toInt() xor XOR_KEY.toInt()).toByte()
        raw[10] = 1.toByte()
        raw[11] = if (isRerouting) 1.toByte() else 0.toByte()
        raw[12] = if (isMetric) 0.toByte() else 1.toByte()
        raw[13] = 0.toByte()
        raw[14] = 0.toByte()
        raw[15] = 0.toByte()
        raw[16] = 0.toByte()
        raw[17] = 0.toByte()

        // 1's complement checksum on bytes 0..17
        var sum = 0
        for (i in 0..17) {
            sum += (raw[i].toInt() and 0xFF)
        }
        raw[18] = ((255 - (sum % 256)) and 0xFF).toByte()
        raw[19] = 0xFF.toByte()

        return raw
    }

    fun buildClearNavigationPacket(): ByteArray {
        return buildNavigationPacket(
            distanceMeters = 0,
            remainingDistanceMeters = 0,
            remainingTimeSeconds = 0,
            pictogram = Pictogram.OFF
        )
    }

    fun buildStreetNamePacket(streetName: String): ByteArray {
        val packet = ByteArray(16)
        packet[0] = START_BYTE_TEXT
        packet[1] = DATA_ID_TEXT

        for (i in 2..15) {
            packet[i] = 0x20.toByte()
        }

        val trimmed = if (streetName.length > 14) streetName.substring(0, 14) else streetName
        val textBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        val copyLen = textBytes.size.coerceAtMost(14)
        System.arraycopy(textBytes, 0, packet, 2, copyLen)

        return packet
    }
}
