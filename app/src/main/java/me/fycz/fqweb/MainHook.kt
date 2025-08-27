package me.fycz.fqweb

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.content.res.XModuleResources
import android.view.View // ✅ 新增
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook // ✅ 新增
import me.fycz.fqweb.constant.Config
import me.fycz.fqweb.ui.SettingsUI
import me.fycz.fqweb.utils.*
import me.fycz.fqweb.web.ServerManager
import java.util.LinkedList

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

    companion object {
        lateinit var moduleRes: Resources
    }

    private lateinit var modulePath: String

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleInitPackageResources(resParam: XC_InitPackageResources.InitPackageResourcesParam) {
        moduleRes = XModuleResources.createInstance(modulePath, resParam.res)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.dragon.read") {
            GlobalApp.initClassLoader(lpparam.classLoader)

            "com.dragon.read.app.MainApplication".hookAfterMethod(
                lpparam.classLoader,
                "onCreate"
            ) { param: XC_MethodHook.MethodHookParam ->
                val app = param.thisObject as Application
                if (lpparam.packageName == getProcessName(app)) {
                    GlobalApp.application = app
                    log("versionCode = ${Config.versionCode}")
                    SPUtils.init(app)
                    hookSetting(lpparam.classLoader)
                    hookUpdate(lpparam.classLoader)
                    ServerManager.instance.initialize()
                    SPUtils.putString("publicDomain", "未获取")
                    ServerManager.instance.startServers()
                }
            }
        }
    }

    private fun getProcessName(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses.firstOrNull { it.pid == android.os.Process.myPid() }?.processName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun hookSetting(classLoader: ClassLoader) {
        var adapter: Any? = null

        // Hook 设置列表，插入“Web服务”入口
        "com.dragon.read.component.biz.impl.mine.settings.SettingsActivity"
            .hookAfterMethod(
                classLoader,
                "a",
                Config.settingRecyclerAdapterClz.findClass(classLoader)
            ) { param: XC_MethodHook.MethodHookParam ->
                adapter = param.thisObject.getObjectField("b")
                val list = param.result as LinkedList<Any>
                if (list.none { item -> item.getObjectField("e") == "Web服务" }) {
                    val context = param.thisObject as Context
                    val setting = Config.settingItemQSNClz.findClass(classLoader).new(context)
                    setting.setObjectField("e", "Web服务")
                    setting.setObjectField(
                        Config.settingItemStrFieldName,
                        if (ServerManager.instance.isHttpServerAlive())
                            "已开启(http://${NetworkUtils.getLocalIPAddress()?.hostAddress ?: "localhost"}:${SPUtils.getInt("port", 9999)})"
                        else "未开启"
                    )
                    list.add(0, setting)
                }
            }

        // Hook 点击事件，打开 SettingsUI
        "${Config.settingItemQSNClz}\$1"
            .replaceMethod(
                classLoader,
                "a",
                View::class.java,
                "com.dragon.read.pages.mine.settings.e".findClass(classLoader),
                Int::class.java
            ) { param: XC_MethodHook.MethodHookParam ->
                val context = (param.args[0] as View).context
                if (param.args[1].getObjectField("e") == "Web服务") {
                    if (!SPUtils.getBoolean("disclaimer", false)) {
                        AlertDialog.Builder(context)
                            .setTitle("免责声明")
                            .setCancelable(true)
                            .setMessage(
                                XposedHelpers.assetAsByteArray(moduleRes, "disclaimer.txt")
                                    .inputStream().reader().use { r -> r.readText() }
                            )
                            .setPositiveButton("同意并继续") { _, _ ->
                                SettingsUI(context, adapter, param.args[1], moduleRes).showDialog()
                                SPUtils.putBoolean("disclaimer", true)
                            }
                            .setNegativeButton("不同意", null)
                            .show()
                    } else {
                        SettingsUI(context, adapter, param.args[1], moduleRes).showDialog()
                    }
                } else {
                    param.invokeOriginalMethod()
                }
            }
    }

    private fun hookUpdate(classLoader: ClassLoader) {
        val unhook = "com.dragon.read.update.d".replaceMethod(
            classLoader,
            "a",
            Int::class.java,
            "com.ss.android.update.OnUpdateStatusChangedListener"
        ) { _: XC_MethodHook.MethodHookParam -> }
        if (unhook != null) return
        "com.dragon.read.update.d".findClass(classLoader)
            .replaceMethod("a", Int::class.java) { _: XC_MethodHook.MethodHookParam -> }
    }
}
