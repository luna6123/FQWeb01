package me.fycz.fqweb.ui

import android.widget.Toast
import android.app.AlertDialog
import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod // ✅ 新增
import android.widget.*
import me.fycz.fqweb.constant.Config // ✅ 改成实际包路径
import me.fycz.fqweb.utils.*
import me.fycz.fqweb.web.ServerManager
import de.robv.android.xposed.XposedHelpers // ✅ 改成官方 XposedHelpers

class SettingsUI(
    private val context: Context,
    private val adapter: Any?,
    private val settingView: Any,
    private val moduleRes: android.content.res.Resources
) {
    private lateinit var portEditText: EditText
    private lateinit var autoStartSwitch: Switch
    private lateinit var enableServerSwitch: Switch
    private lateinit var frpSwitch: Switch

    fun showDialog() {
        val textColor = android.graphics.Color.parseColor("#060606")
        val layoutRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        }

        layoutRoot.addView(createPortLayout(textColor))
        layoutRoot.addView(createAutoStartLayout(textColor))
        layoutRoot.addView(createEnableLayout(textColor))
        layoutRoot.addView(createFrpLayout(textColor))
        layoutRoot.addView(createGithubLayout())

        AlertDialog.Builder(context)
            .setTitle("番茄Web")
            .setView(layoutRoot)
            .setCancelable(true)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存设置") { _, _ -> handleSave() }
            .create()
            .show()
    }

    private fun createPortLayout(textColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        addView(TextView(context).apply {
            text = "服务端口："
            setTextColor(textColor)
            textSize = 16F
        })
        portEditText = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(5))
            setText(SPUtils.getInt("port", 9999).toString())
            setTextColor(textColor)
            textSize = 16F
            hint = "请输入1024-65535之间的值"
        }
        addView(portEditText)
    }

    private fun createAutoStartLayout(textColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        addView(TextView(context).apply {
            text = "随番茄自动启动服务："
            setTextColor(textColor)
            textSize = 16F
        })
        autoStartSwitch = Switch(context).apply {
            isChecked = SPUtils.getBoolean("autoStart", false)
        }
        addView(autoStartSwitch)
    }

    private fun createEnableLayout(textColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        addView(TextView(context).apply {
            text = "开启服务："
            setTextColor(textColor)
            textSize = 16F
        })
        enableServerSwitch = Switch(context).apply {
            isChecked = ServerManager.instance.isHttpServerAlive()
        }
        addView(enableServerSwitch)
    }

    private fun createFrpLayout(textColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        addView(TextView(context).apply {
            text = "内网穿透服务(Frp)："
            setTextColor(textColor)
            textSize = 16F
        })
        frpSwitch = Switch(context).apply {
            isChecked = SPUtils.getBoolean("traversal", false)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    showFrpDisclaimerDialog { agreed ->
                        if (!agreed) this.isChecked = false
                    }
                }
            }
        }
        addView(frpSwitch)
    }

    private fun createGithubLayout() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp2px(10F), dp2px(10F), dp2px(10F), dp2px(10F))
        addView(TextView(context).apply {
            text = Html.fromHtml("<a href=\"https://github.com/sunianOvO/FQWeb\">Github官网</a>")
            movementMethod = LinkMovementMethod.getInstance()
            textSize = 16F
        })
    }

    private fun showFrpDisclaimerDialog(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("内网穿透风险警告和免责声明")
            .setMessage(Html.fromHtml(readAssetToString("TraversalDisclaimer.html")))
            .setCancelable(false)
            .setPositiveButton("我已阅读并同意") { _, _ -> callback(true) }
            .setNegativeButton("不同意") { _, _ -> callback(false) }
            .show()
    }

    private fun handleSave() {
        val portInput = portEditText.text.toString()
        val port = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: run {
            ToastUtils.toast("端口无效，已使用默认值 9999")
            9999
        }

        SPUtils.putInt("port", port)
        SPUtils.putBoolean("autoStart", autoStartSwitch.isChecked)
        SPUtils.putBoolean("traversal", frpSwitch.isChecked)

        try {
            if (enableServerSwitch.isChecked) {
                ServerManager.instance.restartHttpServer(port)
            } else {
                ServerManager.instance.stopServers()
            }
            if (frpSwitch.isChecked) {
                if (!ServerManager.instance.isHttpServerAlive()) {
                    ServerManager.instance.restartHttpServer(port)
                }
                ServerManager.instance.startServers()
            }
            updateSettingView(enableServerSwitch.isChecked, port)
        } catch (e: Throwable) {
            log(e)
            Toast.makeText(context, e.localizedMessage ?: "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSettingView(enabled: Boolean, port: Int) {
        val ip = NetworkUtils.getLocalIPAddress()?.hostAddress ?: "localhost"
        val displayIp = if (ip.contains(":")) "[$ip]" else ip
        val statusText = if (enabled) "已开启(http://$displayIp:$port)" else "未开启"
        settingView.setObjectField(Config.settingItemStrFieldName, statusText)
        adapter?.callMethod("notifyItemChanged", 0)
    }

    private fun readAssetToString(filename: String): String {
        return XposedHelpers.assetAsByteArray(moduleRes, filename)
            .inputStream()
            .reader()
            .use { it.readText() }
    }

    private fun dp2px(dipValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dipValue * scale + 0.5f).toInt()
    }
}
