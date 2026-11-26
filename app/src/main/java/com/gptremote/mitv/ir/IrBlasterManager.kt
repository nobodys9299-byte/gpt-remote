package com.gptremote.mitv.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.gptremote.mitv.model.BrandOption
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteButton

/**
 * Transmits infrared (IR) remote pulses on phones with a built-in IR blaster
 * (e.g. Xiaomi / Redmi / POCO / Honor devices).
 */
class IrBlasterManager(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasIrEmitter: Boolean
        get() = runCatching { irManager?.hasIrEmitter() == true }.getOrDefault(false)

    fun sendButton(brand: BrandOption, button: RemoteButton): Boolean {
        val manager = irManager ?: return false
        if (!hasIrEmitter) return false

        val signal = lookupIrSignal(brand, button) ?: return false
        return runCatching {
            manager.transmit(signal.carrierHz, signal.pattern)
            true
        }.getOrDefault(false)
    }

    fun sendApplianceCommand(brand: BrandOption, actionKey: String): Boolean {
        val manager = irManager ?: return false
        if (!hasIrEmitter) return false

        val cmdByte = when (actionKey) {
            "power", "power_on" -> 0x01
            "power_off" -> 0x41
            "temp_up", "speed_up", "fridge_up", "bright_up" -> 0x02
            "temp_down", "speed_down", "fridge_down", "bright_down" -> 0x03
            "mode", "cycle", "bulb_warm" -> 0x04
            "fan", "spin", "bulb_cool" -> 0x05
            "swing", "light", "bulb_white" -> 0x06
            "timer", "bulb_red" -> 0x07
            "turbo", "boost", "super_cool", "bulb_green" -> 0x08
            "sleep", "eco", "bulb_blue" -> 0x09
            "freezer_up", "bulb_amber" -> 0x0A
            "freezer_down", "bulb_purple" -> 0x0B
            "start_pause", "ice_maker" -> 0x0C
            "child_lock", "temp_cycle" -> 0x0E
            else -> 0x0D
        }
        val addrByte = when (brand.productType) {
            ProductType.AC -> 0x48
            ProductType.CEILING_FAN -> 0x52
            ProductType.FRIDGE -> 0x61
            ProductType.WASHING_MACHINE -> 0x62
            ProductType.SMART_BULB -> 0x00
            else -> 0x20
        }
        return runCatching {
            manager.transmit(38000, buildNecAddressCmd(addrByte, cmdByte))
            true
        }.getOrDefault(false)
    }

    private fun lookupIrSignal(brand: BrandOption, button: RemoteButton): IrSignal? {
        return when (brand.id) {
            "tv_samsung" -> samsungTvCode(button)?.let { IrSignal(38000, buildSamsung32(it)) }
            "tv_lg" -> lgTvCode(button)?.let { IrSignal(38000, buildNec32Msb(it)) }
            "tv_sony" -> sonyTvCode(button)?.let { IrSignal(40000, buildSonySirc12(it)) }
            "tv_xiaomi" -> xiaomiTvCmd(button)?.let { (addr, cmd) ->
                IrSignal(38000, buildNecAddressCmd(addr, cmd))
            }
            else -> genericTvCmd(button)?.let { cmd ->
                IrSignal(38000, buildNecAddressCmd(0x04, cmd))
            }
        }
    }

    private data class IrSignal(val carrierHz: Int, val pattern: IntArray)

    private fun samsungTvCode(button: RemoteButton): Long? = when (button) {
        RemoteButton.POWER -> 0xE0E040BFL
        RemoteButton.VOLUME_UP -> 0xE0E0E01FL
        RemoteButton.VOLUME_DOWN -> 0xE0E0D02FL
        RemoteButton.MUTE -> 0xE0E0F00FL
        RemoteButton.CHANNEL_UP -> 0xE0E048B7L
        RemoteButton.CHANNEL_DOWN -> 0xE0E008F7L
        RemoteButton.DPAD_UP -> 0xE0E006F9L
        RemoteButton.DPAD_DOWN -> 0xE0E08679L
        RemoteButton.DPAD_LEFT -> 0xE0E0A659L
        RemoteButton.DPAD_RIGHT -> 0xE0E046B9L
        RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> 0xE0E016E9L
        RemoteButton.BACK -> 0xE0E01AE5L
        RemoteButton.HOME -> 0xE0E09E61L
        RemoteButton.MENU -> 0xE0E058A7L
        RemoteButton.TV_INPUT -> 0xE0E0807FL
        RemoteButton.MEDIA_PLAY_PAUSE -> 0xE0E0E21DL
        RemoteButton.MEDIA_STOP -> 0xE0E0629DL
        RemoteButton.MEDIA_PREVIOUS -> 0xE0E0A25DL
        RemoteButton.MEDIA_NEXT -> 0xE0E012EDL
        else -> null
    }

    private fun lgTvCode(button: RemoteButton): Long? = when (button) {
        RemoteButton.POWER -> 0x20DF10EFL
        RemoteButton.VOLUME_UP -> 0x20DF40BFL
        RemoteButton.VOLUME_DOWN -> 0x20DFC03FL
        RemoteButton.MUTE -> 0x20DF906FL
        RemoteButton.CHANNEL_UP -> 0x20DF00FFL
        RemoteButton.CHANNEL_DOWN -> 0x20DF807FL
        RemoteButton.DPAD_UP -> 0x20DF02FDL
        RemoteButton.DPAD_DOWN -> 0x20DF827DL
        RemoteButton.DPAD_LEFT -> 0x20DFE01FL
        RemoteButton.DPAD_RIGHT -> 0x20DF609FL
        RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> 0x20DF22DDL
        RemoteButton.BACK -> 0x20DF14EBL
        RemoteButton.HOME -> 0x20DF3EC1L
        RemoteButton.MENU, RemoteButton.SETTINGS -> 0x20DFC23DL
        RemoteButton.TV_INPUT -> 0x20DFD02FL
        else -> null
    }

    private fun sonyTvCode(button: RemoteButton): Int? = when (button) {
        RemoteButton.POWER -> 0xA90
        RemoteButton.VOLUME_UP -> 0x490
        RemoteButton.VOLUME_DOWN -> 0xC90
        RemoteButton.MUTE -> 0x290
        RemoteButton.CHANNEL_UP -> 0x090
        RemoteButton.CHANNEL_DOWN -> 0x890
        RemoteButton.DPAD_UP -> 0x2F0
        RemoteButton.DPAD_DOWN -> 0xAF0
        RemoteButton.DPAD_LEFT -> 0x2D0
        RemoteButton.DPAD_RIGHT -> 0xCD0
        RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> 0xA70
        RemoteButton.HOME -> 0x070
        RemoteButton.BACK -> 0xC70
        RemoteButton.TV_INPUT -> 0xA50
        else -> null
    }

    private fun xiaomiTvCmd(button: RemoteButton): Pair<Int, Int>? = when (button) {
        RemoteButton.POWER -> 0x3C to 0xCC
        RemoteButton.DPAD_UP -> 0x86 to 0x05
        RemoteButton.DPAD_DOWN -> 0x86 to 0x06
        RemoteButton.BACK -> 0x86 to 0x07
        RemoteButton.HOME -> 0x86 to 0x08
        RemoteButton.DPAD_LEFT -> 0x86 to 0x0B
        RemoteButton.DPAD_RIGHT -> 0x86 to 0x0C
        RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> 0x86 to 0x0D
        RemoteButton.VOLUME_DOWN -> 0x86 to 0x0F
        RemoteButton.VOLUME_UP -> 0x86 to 0x0E
        RemoteButton.MENU -> 0x86 to 0x10
        else -> null
    }

    private fun genericTvCmd(button: RemoteButton): Int? = when (button) {
        RemoteButton.POWER -> 0x08
        RemoteButton.VOLUME_UP -> 0x02
        RemoteButton.VOLUME_DOWN -> 0x03
        RemoteButton.MUTE -> 0x09
        RemoteButton.CHANNEL_UP -> 0x00
        RemoteButton.CHANNEL_DOWN -> 0x01
        RemoteButton.DPAD_UP -> 0x40
        RemoteButton.DPAD_DOWN -> 0x41
        RemoteButton.DPAD_LEFT -> 0x07
        RemoteButton.DPAD_RIGHT -> 0x06
        RemoteButton.DPAD_CENTER, RemoteButton.ENTER -> 0x44
        RemoteButton.BACK -> 0x28
        RemoteButton.HOME -> 0x7C
        RemoteButton.MENU -> 0x43
        RemoteButton.TV_INPUT -> 0x0B
        else -> null
    }

    companion object {
        /** Standard NEC: 9ms mark, 4.5ms space, 32 bits MSB-first (already full 32-bit frame). */
        fun buildNec32Msb(code32: Long): IntArray {
            val out = IntArray(2 + 32 * 2 + 1)
            out[0] = 9000
            out[1] = 4500
            var idx = 2
            for (bitPos in 31 downTo 0) {
                val bit = (code32 ushr bitPos) and 1L
                out[idx++] = 562
                out[idx++] = if (bit == 1L) 1687 else 562
            }
            out[idx] = 562
            return out
        }

        /** Standard NEC with 8-bit address + inverse + 8-bit command + inverse, LSB-first per byte. */
        fun buildNecAddressCmd(address: Int, command: Int): IntArray {
            val a = address and 0xFF
            val aInv = a.inv() and 0xFF
            val c = command and 0xFF
            val cInv = c.inv() and 0xFF
            val out = IntArray(2 + 32 * 2 + 1)
            out[0] = 9000
            out[1] = 4500
            var idx = 2
            for (byteVal in intArrayOf(a, aInv, c, cInv)) {
                for (bit in 0 until 8) {
                    out[idx++] = 562
                    out[idx++] = if (((byteVal shr bit) and 1) == 1) 1687 else 562
                }
            }
            out[idx] = 562
            return out
        }

        /** Samsung32: 4.5ms mark, 4.5ms space, 32 bits MSB-first. */
        fun buildSamsung32(code32: Long): IntArray {
            val out = IntArray(2 + 32 * 2 + 1)
            out[0] = 4500
            out[1] = 4500
            var idx = 2
            for (bitPos in 31 downTo 0) {
                val bit = (code32 ushr bitPos) and 1L
                out[idx++] = 560
                out[idx++] = if (bit == 1L) 1690 else 560
            }
            out[idx] = 560
            return out
        }

        /** Sony SIRC 12-bit: 2.4ms mark, 600us space, 12 bits MSB-first. */
        fun buildSonySirc12(code12: Int): IntArray {
            val out = IntArray(2 + 12 * 2)
            out[0] = 2400
            out[1] = 600
            var idx = 2
            for (bitPos in 11 downTo 0) {
                val bit = (code12 ushr bitPos) and 1
                out[idx++] = if (bit == 1) 1200 else 600
                out[idx++] = 600
            }
            return out
        }
    }
}
