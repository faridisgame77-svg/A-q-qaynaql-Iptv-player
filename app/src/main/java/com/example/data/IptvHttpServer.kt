package com.example.data

import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class IptvHttpServer(
    private val port: Int = 8080,
    private val listener: HttpServerListener
) {
    interface HttpServerListener {
        fun onXtreamReceived(serverUrl: String, username: String, password: String)
        fun onM3uReceived(fileName: String, content: String)
        fun onM3uUrlReceived(url: String)
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(start = true, name = "IPTV-HttpServer") {
            try {
                serverSocket = ServerSocket(port)
                Log.d("IptvHttpServer", "Started local IPTV deployment server on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    thread {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("IptvHttpServer", "Server error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        var input: InputStream? = null
        var output: OutputStream? = null
        try {
            input = socket.getInputStream()
            output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
            
            // Read request line
            val requestLine = reader.readLine() ?: return
            Log.d("IptvHttpServer", "Req: $requestLine")
            
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            // Read headers
            var contentLength = 0
            var contentType = ""
            var line: String? = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                val lower = line.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter("content-length:").trim().toIntOrNull() ?: 0
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substringAfter("Content-Type:").trim()
                }
                line = reader.readLine()
            }

            if (method.equals("POST", ignoreCase = true)) {
                handlePost(input, contentLength, contentType, output)
            } else {
                handleGet(path, output)
            }
        } catch (e: Exception) {
            Log.e("IptvHttpServer", "Client handling error", e)
        } finally {
            try { socket.close() } catch (ex: Exception) {}
        }
    }

    private fun handleGet(path: String, output: OutputStream) {
        val printer = PrintWriter(output)
        
        // Return styled web submission portal
        val html = """
            <!DOCTYPE html>
            <html lang="az">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>X3M IPTV Deployment Portal</title>
                <style>
                    body {
                        font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
                        background: #11141e;
                        color: #e3e6ed;
                        margin: 0;
                        padding: 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container {
                        width: 100%;
                        max-width: 500px;
                        background: #181d29;
                        border-radius: 12px;
                        padding: 24px;
                        box-shadow: 0 8px 32px rgba(0,0,0,0.5);
                        border: 1px solid #2d3548;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 24px;
                    }
                    h1 {
                        color: #58a6ff;
                        font-size: 24px;
                        margin: 0 0 8px 0;
                    }
                    p {
                        color: #8b949e;
                        font-size: 14px;
                        margin: 0;
                    }
                    .tabs {
                        display: flex;
                        gap: 8px;
                        margin-bottom: 20px;
                        border-bottom: 1px solid #2d3548;
                        padding-bottom: 8px;
                    }
                    .tab-button {
                        flex: 1;
                        background: none;
                        border: none;
                        color: #8b949e;
                        padding: 8px 0;
                        font-weight: 600;
                        cursor: pointer;
                        text-align: center;
                        font-size: 14px;
                        border-bottom: 2px solid transparent;
                    }
                    .tab-button.active {
                        color: #58a6ff;
                        border-bottom: 2px solid #58a6ff;
                    }
                    .tab-content {
                        display: none;
                    }
                    .tab-content.active {
                        display: block;
                    }
                    .form-group {
                        margin-bottom: 16px;
                    }
                    label {
                        display: block;
                        font-size: 13px;
                        color: #8b949e;
                        margin-bottom: 6px;
                        font-weight: 500;
                    }
                    input[type="text"], input[type="password"], input[type="file"] {
                        width: 100%;
                        box-sizing: border-box;
                        background: #0d1117;
                        border: 1px solid #30363d;
                        border-radius: 6px;
                        padding: 12px;
                        color: #c9d1d9;
                        font-size: 14px;
                        outline: none;
                        transition: border-color 0.2s;
                    }
                    input:focus {
                        border-color: #58a6ff;
                    }
                    .file-upload-wrapper {
                        border: 2px dashed #30363d;
                        border-radius: 8px;
                        padding: 24px;
                        text-align: center;
                        background: #0d1117;
                        cursor: pointer;
                        position: relative;
                    }
                    .file-upload-wrapper:hover {
                        border-color: #58a6ff;
                    }
                    .btn {
                        width: 100%;
                        background: #21262d;
                        border: 1px solid #30363d;
                        color: #58a6ff;
                        font-size: 15px;
                        font-weight: 600;
                        padding: 12px;
                        border-radius: 6px;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .btn:hover {
                        background: #58a6ff;
                        color: #11141e;
                        border-color: #58a6ff;
                    }
                    .success-msg {
                        display: none;
                        background: rgba(46, 160, 67, 0.15);
                        border: 1px solid rgba(46, 160, 67, 0.4);
                        color: #3fb950;
                        padding: 12px;
                        border-radius: 6px;
                        margin-bottom: 16px;
                        font-size: 14px;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>X3M Portal</h1>
                        <p>Televizora məlumat göndərmək üçün aşağıdakı formları doldurun</p>
                    </div>
                    
                    <div id="success-box" class="success-msg">Məlumatlar uğurla TV-yə göndərildi!</div>

                    <div class="tabs">
                        <button class="tab-button active" onclick="switchTab('xtream')">Xtream Codes API</button>
                        <button class="tab-button" onclick="switchTab('m3u')">M3U Faylı və ya Linki</button>
                    </div>

                    <!-- Xtream Codes form -->
                    <div id="xtream-tab" class="tab-content active">
                        <form id="xtream-form" method="POST" action="/submit-xtream">
                            <input type="hidden" name="type" value="xtream">
                            <div class="form-group">
                                <label for="url">Server Portallı URL (Məsələn: http://iptvprov.com:8080)</label>
                                <input type="text" id="url" name="url" placeholder="http://..." required>
                            </div>
                            <div class="form-group">
                                <label for="username">İstifadəçi Adı (Username)</label>
                                <input type="text" id="username" name="username" placeholder="Username" required>
                            </div>
                            <div class="form-group">
                                <label for="password">Şifrə (Password)</label>
                                <input type="password" id="password" name="password" placeholder="Password" required>
                            </div>
                            <button type="submit" class="btn">TV-yə Göndər (Deploy)</button>
                        </form>
                    </div>

                    <!-- M3U Upload / Link Form -->
                    <div id="m3u-tab" class="tab-content">
                        <!-- URL Link deployment -->
                        <form id="m3u-url-form" method="POST" action="/submit-m3u-url" style="margin-bottom: 24px;">
                            <input type="hidden" name="type" value="m3u-url">
                            <div class="form-group">
                                <label for="m3u_url">M3U Playlist Linki (Məsələn: http://.../playlist.m3u)</label>
                                <input type="text" id="m3u_url" name="m3u_url" placeholder="http://..." required>
                            </div>
                            <button type="submit" class="btn">Linki TV-yə Göndər</button>
                        </form>

                        <!-- File Upload deployment -->
                        <form id="m3u-file-form" method="POST" action="/submit-m3u-file" enctype="multipart/form-data">
                            <input type="hidden" name="type" value="m3u-file">
                            <div class="form-group">
                                <label>M3U Playlist Faylı Seçin</label>
                                <div class="file-upload-wrapper">
                                    <input type="file" id="m3u_file" name="m3u_file" accept=".m3u,.m3u8" required>
                                </div>
                            </div>
                            <button type="submit" class="btn">Faylı TV-yə Göndər</button>
                        </form>
                    </div>
                </div>

                <script>
                    function switchTab(tabId) {
                        document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
                        document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
                        
                        if (tabId === 'xtream') {
                            document.querySelectorAll('.tab-button')[0].classList.add('active');
                            document.getElementById('xtream-tab').classList.add('active');
                        } else {
                            document.querySelectorAll('.tab-button')[1].classList.add('active');
                            document.getElementById('m3u-tab').classList.add('active');
                        }
                    }

                    // Simple AJax implementation to prevent redirecting page and show beautiful prompt
                    const handleFormSubmit = (formId) => {
                        const form = document.getElementById(formId);
                        form.addEventListener('submit', async (e) => {
                            e.preventDefault();
                            const formData = new FormData(form);
                            try {
                                const response = await fetch(form.action, {
                                    method: 'POST',
                                    body: formData
                                });
                                if (response.ok) {
                                    const successBox = document.getElementById('success-box');
                                    successBox.style.display = 'block';
                                    window.scrollTo({ top: 0, behavior: 'smooth' });
                                    setTimeout(() => {
                                        successBox.style.display = 'none';
                                    }, 5000);
                                } else {
                                    alert('Xəta baş verdi, zəhmət olmasa təkrar yoxlayın.');
                                }
                            } catch (err) {
                                console.error(err);
                                alert('TV-yə qoşulmaq mümkün olmadı.');
                            }
                        });
                    };

                    handleFormSubmit('xtream-form');
                    handleFormSubmit('m3u-url-form');
                    handleFormSubmit('m3u-file-form');
                </script>
            </body>
            </html>
        """.trimIndent()

        printer.println("HTTP/1.1 200 OK")
        printer.println("Content-Type: text/html; charset=UTF-8")
        printer.println("Content-Length: ${html.toByteArray().size}")
        printer.println("Connection: close")
        printer.println()
        printer.print(html)
        printer.flush()
    }

    private fun handlePost(
        input: java.io.InputStream,
        contentLength: Int,
        contentType: String,
        output: OutputStream
    ) {
        if (contentLength <= 0) {
            sendResponse(output, 400, "Content length must be > 0")
            return
        }

        // Read POST binary body safely into byte array
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var totalRead = 0
        while (totalRead < contentLength) {
            val toRead = java.lang.Math.min(buffer.size, contentLength - totalRead)
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            bos.write(buffer, 0, read)
            totalRead += read
        }
        val bodyBytes = bos.toByteArray()
        val bodyStr = String(bodyBytes, Charsets.UTF_8)

        Log.d("IptvHttpServer", "POST Content-Type: $contentType, length: $contentLength")

        // Triage Content Type (Multipart or Form URL Encoded manually parsed)
        if (contentType.contains("multipart/form-data")) {
            // Find boundary
            val boundaryPart = contentType.substringAfter("boundary=").trim()
            parseAndHandleMultipart(boundaryPart, bodyBytes)
        } else {
            // Parse plain form-urlencoded parameters
            val params = parseUrlEncoded(bodyStr)
            val type = params["type"]
            Log.d("IptvHttpServer", "POST query params type: $type")
            if (type == "xtream") {
                val url = params["url"] ?: ""
                val username = params["username"] ?: ""
                val password = params["password"] ?: ""
                if (url.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                    listener.onXtreamReceived(url, username, password)
                }
            } else if (type == "m3u-url") {
                val m3uUrl = params["m3u_url"] ?: ""
                if (m3uUrl.isNotEmpty()) {
                    listener.onM3uUrlReceived(m3uUrl)
                }
            }
        }

        sendResponse(output, 200, "SUCCESS")
    }

    private fun parseUrlEncoded(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        body.split("&").forEach { pair ->
            val p = pair.split("=")
            if (p.size == 2) {
                val key = java.net.URLDecoder.decode(p[0], "UTF-8")
                val value = java.net.URLDecoder.decode(p[1], "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    private fun parseAndHandleMultipart(boundary: String, bytes: ByteArray) {
        try {
            val boundaryStr = "--$boundary"
            val bodyString = String(bytes, Charsets.UTF_8)
            
            // If body is mostly text (usually holds for M3U playlists and params) we parse
            val parts = bodyString.split(boundaryStr)
            
            var m3uFileName = "playlist.m3u"
            var m3uContent = ""
            var xtreamUrl = ""
            var xtreamUser = ""
            var xtreamPass = ""
            var m3uUrl = ""
            var postType = ""

            for (part in parts) {
                if (part.trim() == "--" || part.isEmpty()) continue
                
                // Read field configuration in part
                val dispIndex = part.indexOf("Content-Disposition:")
                if (dispIndex == -1) continue
                
                val headersAndBody = part.substring(dispIndex)
                val splitIndex = headersAndBody.indexOf("\r\n\r\n")
                if (splitIndex == -1) continue
                
                val headers = headersAndBody.substring(0, splitIndex)
                val value = headersAndBody.substring(splitIndex + 4).trimEnd('\r', '\n')
                
                if (headers.contains("name=\"type\"")) {
                    postType = value.trim()
                } else if (headers.contains("name=\"url\"")) {
                    xtreamUrl = value.trim()
                } else if (headers.contains("name=\"username\"")) {
                    xtreamUser = value.trim()
                } else if (headers.contains("name=\"password\"")) {
                    xtreamPass = value.trim()
                } else if (headers.contains("name=\"m3u_url\"")) {
                    m3uUrl = value.trim()
                } else if (headers.contains("name=\"m3u_file\"")) {
                    // Extract original fileName if any
                    val filenameMatch = Regex("filename=\"([^\"]+)\"").find(headers)
                    if (filenameMatch != null) {
                        m3uFileName = filenameMatch.groupValues[1]
                    }
                    m3uContent = value
                }
            }

            Log.d("IptvHttpServer", "Multipart parsed: type=$postType, xtreamUrl=$xtreamUrl")

            if (postType == "xtream" || (xtreamUrl.isNotEmpty() && xtreamUser.isNotEmpty() && xtreamPass.isNotEmpty())) {
                listener.onXtreamReceived(xtreamUrl, xtreamUser, xtreamPass)
            } else if (postType == "m3u-url" || m3uUrl.isNotEmpty()) {
                listener.onM3uUrlReceived(m3uUrl)
            } else if (m3uContent.isNotEmpty()) {
                listener.onM3uReceived(m3uFileName, m3uContent)
            }
        } catch (e: Exception) {
            Log.e("IptvHttpServer", "Failed to parse multipart request", e)
        }
    }

    private fun sendResponse(output: OutputStream, code: Int, message: String) {
        val printer = PrintWriter(output)
        printer.println("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}")
        printer.println("Content-Type: text/plain; charset=UTF-8")
        printer.println("Content-Length: ${message.toByteArray().size}")
        printer.println("Access-Control-Allow-Origin: *")
        printer.println("Connection: close")
        printer.println()
        printer.print(message)
        printer.flush()
    }
}
