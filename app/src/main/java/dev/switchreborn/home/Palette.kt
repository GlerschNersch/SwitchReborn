package dev.switchreborn.home

/** The two console themes, sampled from captures (docs/RE_NOTES.md). */
class Palette(
    val dark: Boolean,
    val bg: Int,
    val text: Int,
    val subtext: Int,
    val rule: Int,
    val button: Int,
    val buttonShadow: Int,
    val icon: Int,
    val title: Int,
    val pulseLo: Int,
    val pulseHi: Int,
    val frameGap: Int,
    val emptySlot: Int,
    val emptyEdge: Int,
    val scrim: Int,
    val popup: Int,
    val popupRule: Int,
    val news: Int,
    val shop: Int,
    val album: Int,
) {
    val online = 0xFFCF0514.toInt()
    val newDot = 0xFF12F8C4.toInt()
    val ledOn = 0xFF7FD321.toInt()
    val ledOff = 0xFF808080.toInt()

    companion object {
        val DARK = Palette(
            dark = true,
            bg = 0xFF2A2C29.toInt(),
            text = 0xFFFFFFFF.toInt(),
            subtext = 0xFFB9BBB8.toInt(),
            rule = 0xFFC1C3C0.toInt(),
            button = 0xFF4D4F4C.toInt(),
            buttonShadow = 0x00000000,
            icon = 0xFFD8DAD7.toInt(),
            title = 0xFF2FB3E6.toInt(),
            pulseLo = 0xFF338CAF.toInt(),
            pulseHi = 0xFF9AF1EF.toInt(),
            frameGap = 0xFF081B29.toInt(),
            emptySlot = 0xFF333532.toInt(),
            emptyEdge = 0xFF3B3D3A.toInt(),
            scrim = 0xB0000000.toInt(),
            popup = 0xFF464845.toInt(),
            popupRule = 0xFF5E605D.toInt(),
            news = 0xFFE75A40.toInt(),
            shop = 0xFFF4AD17.toInt(),
            album = 0xFF3498EA.toInt(),
        )
        val LIGHT = Palette(
            dark = false,
            bg = 0xFFEBEBEB.toInt(),
            text = 0xFF3A3A3A.toInt(),
            subtext = 0xFF7A7A7A.toInt(),
            rule = 0xFF3A3A3A.toInt(),
            button = 0xFFFFFFFF.toInt(),
            buttonShadow = 0x22000000,
            icon = 0xFF737575.toInt(),
            title = 0xFF1FB6AF.toInt(),
            pulseLo = 0xFF2DE4D6.toInt(),
            pulseHi = 0xFFB8FBFF.toInt(),
            frameGap = 0xFFFFFFFF.toInt(),
            emptySlot = 0xFFEDEDED.toInt(),
            emptyEdge = 0xFFF8F8F8.toInt(),
            scrim = 0x80000000.toInt(),
            popup = 0xFFF5F5F5.toInt(),
            popupRule = 0xFFDADADA.toInt(),
            news = 0xFFFF3953.toInt(),
            shop = 0xFFF59F14.toInt(),
            album = 0xFF1677FA.toInt(),
        )
    }
}
