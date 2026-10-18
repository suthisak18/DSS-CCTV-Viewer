package th.dsolution.dsscctv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

data class CameraItem(
    val name: String,
    val ip: String,
    val rtspUrl: String,
    val username: String = "",
    val password: String = "",
    val onvifXAddr: String = ""
)

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var cameraList: LinearLayout
    private val cameras = mutableListOf<CameraItem>()
    private val players = mutableListOf<Pair<LibVLC, MediaPlayer>>()
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCameras()
        buildUi()
        renderCameras()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 24, 30))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val title = TextView(this).apply {
            text = "DSS CCTV Viewer"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(44)))

        status = TextView(this).apply {
            text = "พร้อมใช้งาน • เพิ่มกล้องด้วย IP หรือค้นหา ONVIF"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(status)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("ค้นหา ONVIF") { discoverOnvif() },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(makeButton("เพิ่มกล้อง IP") { showAddDialog(null) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        root.addView(buttons)

        val scroll = ScrollView(this)
        cameraList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(24))
        }
        scroll.addView(cameraList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun renderCameras() {
        stopPlayers()
        cameraList.removeAllViews()
        if (cameras.isEmpty()) {
            cameraList.addView(TextView(this).apply {
                text = "ยังไม่มีกล้อง\nกด “ค้นหา ONVIF” หรือ “เพิ่มกล้อง IP”"
                setTextColor(Color.LTGRAY); textSize = 16f
                gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40))
            })
            return
        }
        cameras.forEachIndexed { index, camera ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(35, 41, 49))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(TextView(this).apply {
                text = "${camera.name}  (${camera.ip})"
                textSize = 15f; setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            heading.addView(makeButton("ลบ") {
                cameras.removeAt(index); saveCameras(); renderCameras()
            }, LinearLayout.LayoutParams(dp(72), dp(42)))
            tile.addView(heading)

            val videoLayout = VLCVideoLayout(this)
            tile.addView(videoLayout, LinearLayout.LayoutParams(-1, dp(220)))
            cameraList.addView(tile, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) })
            startPlayer(camera, videoLayout)
        }
    }

    private fun startPlayer(camera: CameraItem, videoLayout: VLCVideoLayout) {
        try {
            val args = arrayListOf("--rtsp-tcp", "--network-caching=500", "--no-drop-late-frames")
            val lib = LibVLC(this, args)
            val player = MediaPlayer(lib)
            player.attachViews(videoLayout, null, false, false)
            val media = Media(lib, android.net.Uri.parse(camera.rtspUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":rtsp-tcp")
            media.addOption(":network-caching=500")
            if (camera.username.isNotBlank()) media.addOption(":rtsp-user=${camera.username}")
            if (camera.password.isNotBlank()) media.addOption(":rtsp-pwd=${camera.password}")
            player.media = media
            media.release()
            player.play()
            players.add(lib to player)
        } catch (e: Exception) {
            status.text = "เริ่มเล่นภาพไม่ได้: ${e.message ?: "ตรวจสอบ RTSP URL/บัญชีผู้ใช้"}"
        }
    }

    private fun stopPlayers() {
        players.forEach { (lib, player) ->
            try { player.stop(); player.detachViews(); player.release(); lib.release() } catch (_: Exception) {}
        }
        players.clear()
    }

    private fun showAddDialog(found: CameraItem?) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun field(hint: String, value: String = "", password: Boolean = false): EditText {
            val e = EditText(this).apply { this.hint = hint; setSingleLine(true); setText(value) }
            if (password) e.inputType = 129
            panel.addView(e, LinearLayout.LayoutParams(-1, dp(48)))
            return e
        }
        val name = field("ชื่อกล้อง", found?.name ?: "Camera")
        val ip = field("IP Address", found?.ip ?: "")
        val url = field("RTSP URL เช่น rtsp://192.168.1.10:554/Streaming/Channels/101",
            found?.rtspUrl ?: "")
        val user = field("Username", found?.username ?: "")
        val pass = field("Password", found?.password ?: "", true)
        AlertDialog.Builder(this)
            .setTitle(if (found == null) "เพิ่มกล้อง RTSP" else "เพิ่มกล้องที่พบ")
            .setView(panel)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("บันทึก") { _, _ ->
                val ipText = ip.text.toString().trim()
                val urlText = url.text.toString().trim()
                if (ipText.isBlank() || !urlText.startsWith("rtsp://")) {
                    Toast.makeText(this, "กรอก IP และ RTSP URL ให้ถูกต้อง", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                cameras.add(CameraItem(name.text.toString().ifBlank { "Camera" },
                    ipText, urlText, user.text.toString(), pass.text.toString(),
                    found?.onvifXAddr ?: ""))
                saveCameras()
                renderCameras()
            }.show()
    }

    private fun discoverOnvif() {
        status.text = "กำลังค้นหา ONVIF ในเครือข่ายเดียวกัน..."
        thread {
            val found = try { performDiscovery() } catch (e: Exception) {
                runOnUiThread { status.text = "ค้นหาไม่สำเร็จ: ${e.message ?: "ตรวจสอบ Wi-Fi/LAN"}" }
                emptyList()
            }
            runOnUiThread {
                if (found.isEmpty()) {
                    status.text = "ไม่พบกล้อง ONVIF • ตรวจสอบ Wi-Fi, VLAN และการเปิด ONVIF"
                    return@runOnUiThread
                }
                status.text = "พบอุปกรณ์ ${found.size} รายการ • ต้องใส่ RTSP URL ก่อนดูภาพ"
                val labels = found.map { "${it.name} — ${it.ip}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("อุปกรณ์ ONVIF ที่พบ")
                    .setItems(labels) { _, which -> showAddDialog(found[which]) }
                    .setNegativeButton("ปิด", null).show()
            }
        }
    }

    private fun performDiscovery(): List<CameraItem> {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("dss-onvif-discovery")
        lock.setReferenceCounted(false)
        lock.acquire()
        multicastLock = lock
        val found = linkedMapOf<String, CameraItem>()
        val message = """<?xml version="1.0" encoding="UTF-8"?>
            <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
            xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
            <e:Header><w:MessageID>uuid:${UUID.randomUUID()}</w:MessageID>
            <w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
            <w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>
            <e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body>
            </e:Envelope>""".trimIndent().toByteArray()
        val address = InetAddress.getByName("239.255.255.250")
        MulticastSocket().use { socket ->
            socket.soTimeout = 700
            socket.timeToLive = 2
            val packet = DatagramPacket(message, message.size, address, 3702)
            socket.send(packet)
            val deadline = System.currentTimeMillis() + 4500
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buf = ByteArray(8192)
                    val response = DatagramPacket(buf, buf.size)
                    socket.receive(response)
                    val xml = String(response.data, 0, response.length, Charsets.UTF_8)
                    val xaddr = Regex("<(?:\\w+:)?XAddrs[^>]*>(.*?)</(?:\\w+:)?XAddrs>",
                        RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
                    val scope = Regex("<(?:\\w+:)?Scopes[^>]*>(.*?)</(?:\\w+:)?Scopes>",
                        RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim().orEmpty()
                    if (!xaddr.isNullOrBlank()) {
                        val first = xaddr.split(Regex("\\s+")).first()
                        val ip = try { URL(first).host } catch (_: Exception) { response.address.hostAddress }
                        val friendly = scope.split(" ").firstOrNull { it.contains("name/") }
                            ?.substringAfterLast("name/")?.replace("%20", " ") ?: "ONVIF Camera"
                        found[ip] = CameraItem(friendly, ip, "", onvifXAddr = first)
                    }
                } catch (_: SocketTimeoutException) { /* wait for next response */ }
            }
        }
        try { lock.release() } catch (_: Exception) {}
        multicastLock = null
        return found.values.toList()
    }

    private fun saveCameras() {
        val array = JSONArray()
        cameras.forEach {
            array.put(JSONObject().put("name", it.name).put("ip", it.ip)
                .put("rtspUrl", it.rtspUrl).put("username", it.username)
                .put("password", it.password).put("onvifXAddr", it.onvifXAddr))
        }
        getSharedPreferences("cameras", MODE_PRIVATE).edit().putString("items", array.toString()).apply()
    }

    private fun loadCameras() {
        val raw = getSharedPreferences("cameras", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cameras.add(CameraItem(o.optString("name"), o.optString("ip"),
                    o.optString("rtspUrl"), o.optString("username"), o.optString("password"),
                    o.optString("onvifXAddr")))
            }
        } catch (_: Exception) {}
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopPlayers()
        try { multicastLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
