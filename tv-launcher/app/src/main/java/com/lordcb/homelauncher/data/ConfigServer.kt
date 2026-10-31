package com.lordcb.homelauncher.data

import android.content.Context
import android.net.ConnectivityManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * A tiny web page the TV serves on the local network while Settings is open,
 * so the Home Assistant URL and long token can be pasted from a phone instead
 * of typed with a remote.
 */
class ConfigServer(
    private val currentUrl: () -> String,
    private val onSave: (url: String, token: String) -> Unit,
) {
    @Volatile private var server: ServerSocket? = null

    fun start(): Boolean = try {
        val ss = ServerSocket(PORT)
        server = ss
        Thread({
            while (!ss.isClosed) {
                try {
                    ss.accept().use { handle(it) }
                } catch (_: Exception) {
                }
            }
        }, "config-server").apply { isDaemon = true }.start()
        true
    } catch (_: Exception) {
        false
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val (method, path) = requestLine.split(' ').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
        val html = if (method == "POST" && path.startsWith("/save")) {
            val buf = CharArray(contentLength.coerceIn(0, 16_384))
            var read = 0
            while (read < buf.size) {
                val n = reader.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            val form = String(buf, 0, read).split('&').associate {
                val k = it.substringBefore('=')
                val v = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                k to v.trim()
            }
            val url = form["url"].orEmpty()
            val token = form["token"].orEmpty()
            if (url.isNotBlank() && token.isNotBlank()) {
                onSave(url, token)
                page("Saved", "<p>Your TV is connecting to Home Assistant now. You can close this page.</p>")
            } else {
                page("Missing details", "<p>Please fill in both fields.</p><p><a href=\"/\">Go back</a></p>")
            }
        } else {
            page(
                "Home Launcher setup",
                """
                <form method="post" action="/save">
                  <label>Home Assistant URL</label>
                  <input name="url" value="${escape(currentUrl())}" placeholder="http://homeassistant.local:8123" autocapitalize="off" autocorrect="off">
                  <label>Long-lived access token</label>
                  <textarea name="token" rows="5" placeholder="Paste token here" autocapitalize="off" autocorrect="off"></textarea>
                  <p class="hint">Create a token in Home Assistant: your profile &rarr; Security &rarr; Long-lived access tokens.</p>
                  <button type="submit">Send to TV</button>
                </form>
                """.trimIndent()
            )
        }
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(bytes)
        out.flush()
    }

    private fun page(title: String, body: String) = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#0a0e14;color:#f5f7fa;margin:0;padding:24px}
          main{max-width:520px;margin:0 auto}
          h1{font-weight:500}
          label{display:block;margin:18px 0 6px;color:#9aa5b4}
          input,textarea{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #2a3442;background:#161d27;color:#f5f7fa;font-size:16px}
          button{margin-top:20px;width:100%;padding:14px;border:0;border-radius:10px;background:#7cc4ff;color:#0a0e14;font-size:17px;font-weight:600}
          .hint{color:#9aa5b4;font-size:14px} a{color:#7cc4ff}
        </style></head><body><main><h1>$title</h1>$body</main></body></html>
    """.trimIndent()

    private fun escape(s: String) =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        const val PORT = 8099

        fun localIpAddress(context: Context): String? = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val props = cm?.getLinkProperties(cm.activeNetwork)
            props?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
