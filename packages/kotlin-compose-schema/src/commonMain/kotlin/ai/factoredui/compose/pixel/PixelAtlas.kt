package ai.factoredui.compose.pixel

import kotlin.io.encoding.Base64

class EncodedSheet(val width: Int, val height: Int, val palette: IntArray, val packedIndices: List<String>)

class ArgbSheet(val width: Int, val height: Int, val argb: IntArray) {
    fun crop(x: Int, y: Int, cropWidth: Int, cropHeight: Int): ArgbSheet {
        val pixels = IntArray(cropWidth * cropHeight)
        for (row in 0 until cropHeight) argb.copyInto(pixels, row * cropWidth, (y + row) * width + x, (y + row) * width + x + cropWidth)
        return ArgbSheet(cropWidth, cropHeight, pixels)
    }
}

class PixelAtlas(val manifest: PixelManifest, encoded: Map<String, List<EncodedSheet>>) {
    private val decoded = encoded.mapValues { (_, sheets) -> lazy { sheets.map(::decodeSheet) } }

    fun sheets(variant: String): List<ArgbSheet> = decoded.getValue(variant).value
}

private const val LITERAL_LIMIT = 128
private const val REPEAT_BASE = 257

fun unpackBits(packed: ByteArray, size: Int): ByteArray {
    val out = ByteArray(size)
    var index = 0
    var written = 0
    while (index < packed.size) {
        val header = packed[index].toInt() and 0xFF
        if (header < LITERAL_LIMIT) {
            packed.copyInto(out, written, index + 1, index + header + 2)
            written += header + 1
            index += header + 2
        } else {
            out.fill(packed[index + 1], written, written + REPEAT_BASE - header)
            written += REPEAT_BASE - header
            index += 2
        }
    }
    check(written == size) { "unpacked $written bytes, expected $size" }
    return out
}

fun decodeSheet(sheet: EncodedSheet): ArgbSheet {
    val indices = unpackBits(Base64.decode(sheet.packedIndices.joinToString("")), sheet.width * sheet.height)
    return ArgbSheet(sheet.width, sheet.height, IntArray(indices.size) { sheet.palette[indices[it].toInt() and 0xFF] })
}

fun swapPalette(sheet: ArgbSheet, from: List<Int>, to: List<Int>): ArgbSheet {
    val replacement = from.zip(to).toMap()
    return ArgbSheet(sheet.width, sheet.height, IntArray(sheet.argb.size) { replacement[sheet.argb[it]] ?: sheet.argb[it] })
}

fun swappedSprite(atlas: PixelAtlas, sprite: PixelSprite, rampName: String): ArgbSheet {
    val pixels = atlas.sheets(sprite.variant)[sprite.sheet].crop(sprite.x, sprite.y, sprite.width, sprite.height)
    val swap = atlas.manifest.swaps[sprite.swap] ?: return pixels
    val target = swap.ramps[rampName] ?: return pixels
    return swapPalette(pixels, swap.ramps.getValue(swap.base), target)
}

val EMBEDDED_PIXEL_ATLAS: PixelAtlas by lazy {
    PixelAtlas(
        manifest = parsePixelManifest(decodeEmbeddedManifest(PIXEL_MANIFEST_BASE64)),
        encoded = PIXEL_SHEETS,
    )
}
