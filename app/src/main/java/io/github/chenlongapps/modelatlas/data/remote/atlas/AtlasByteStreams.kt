package io.github.chenlongapps.modelatlas.data.remote.atlas

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** HTTP 长度未知或随包资源读取时也限制内存，不返回截断的发布文件。 */
internal fun InputStream.readAtlasBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        require(count <= maxBytes - output.size()) { "Atlas data exceeds byte limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
