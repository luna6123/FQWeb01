package me.fycz.fqweb.constant

import android.content.pm.PackageManager
import android.os.Build
import de.robv.android.xposed.XposedHelpers
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass

/**
 * 版本管理类，统一处理不同版本番茄小说的适配逻辑
 */
object VersionManager {

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    // 当前检测到的版本号（懒加载）
    val currentVersion: Int by lazy { fetchCurrentVersionInternal() }

    // 版本配置映射
    private val versionConfigs = mutableMapOf(
        532 to VersionConfig(
            settingRecyclerAdapterClz = "com.dragon.read.base.recyler.c",
            settingItemQSNClz = "com.dragon.read.component.biz.impl.mine.settings.a.g",
            settingItemStrFieldName = "i",
            readerFullRequestClz = "com.dragon.read.rpc.a.e",
            rpcApiPackage = "com.dragon.read.rpc.a"
        ),
        57932 to VersionConfig(
            settingRecyclerAdapterClz = "com.dragon.read.recyler.c",
            settingItemQSNClz = "com.dragon.read.component.biz.impl.mine.settings.a.k",
            settingItemStrFieldName = "i",
            readerFullRequestClz = "com.dragon.read.rpc.rpc.e",
            rpcApiPackage = "com.dragon.read.rpc.rpc"
        ),
        58332 to VersionConfig(
            settingRecyclerAdapterClz = "com.dragon.read.recyler.c",
            settingItemQSNClz = "com.dragon.read.component.biz.impl.mine.settings.a.k",
            settingItemStrFieldName = "j",
            readerFullRequestClz = "com.dragon.read.rpc.rpc.f",
            rpcApiPackage = "com.dragon.read.rpc.rpc"
        )
    )

    /**
     * 获取当前应用版本号，兼容 Android 9+
     */
    private fun fetchCurrentVersionInternal(): Int {
        val app = GlobalApp.application ?: return 0
        return try {
            val pm = app.packageManager
            val info = pm.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 检查当前版本是否支持
     */
    fun isSupportedVersion(): Boolean = currentVersion in versionConfigs

    /**
     * 获取当前版本的配置，如果不支持则返回动态探测的默认配置
     */
    fun getCurrentConfig(): VersionConfig =
        versionConfigs[currentVersion] ?: getDefaultConfig()

    /**
     * 动态探测默认配置（用于不支持的版本）
     */
    private fun getDefaultConfig(): VersionConfig {
        val loader = GlobalApp.getClassloader()

        val settingRecyclerAdapterClz = try {
            "com.dragon.read.recyler.c".findClass(loader)
            "com.dragon.read.recyler.c"
        } catch (_: ClassNotFoundException) {
            "com.dragon.read.base.recyler.c"
        }

        val settingItemQSNClz = try {
            "com.dragon.read.component.biz.impl.mine.settings.a.k".findClass(loader)
            "com.dragon.read.component.biz.impl.mine.settings.a.k"
        } catch (_: ClassNotFoundException) {
            "com.dragon.read.component.biz.impl.mine.settings.a.g"
        }

        val settingItemClz = "com.dragon.read.pages.mine.settings.e".findClass(loader)
        val settingItemStrFieldName = try {
            val iField = XposedHelpers.findField(settingItemClz, "i")
            if (iField.type == CharSequence::class.java) "i" else "j"
        } catch (_: NoSuchFieldException) {
            "j"
        }

        val rpcApiPackage = try {
            "com.dragon.read.rpc.rpc.a".findClass(loader)
            "com.dragon.read.rpc.rpc"
        } catch (_: ClassNotFoundException) {
            "com.dragon.read.rpc.a"
        }

        val readerFullRequestClz = try {
            val fullRequest = "$rpcModelPackage.FullRequest".findClass(loader)
            XposedHelpers.findMethodExact("$rpcApiPackage.e", loader, "a", fullRequest)
            "$rpcApiPackage.e"
        } catch (_: Throwable) {
            "$rpcApiPackage.f"
        }

        return VersionConfig(
            settingRecyclerAdapterClz = settingRecyclerAdapterClz,
            settingItemQSNClz = settingItemQSNClz,
            settingItemStrFieldName = settingItemStrFieldName,
            readerFullRequestClz = readerFullRequestClz,
            rpcApiPackage = rpcApiPackage
        )
    }

    /**
     * 添加新的版本配置
     */
    fun addVersionConfig(versionCode: Int, config: VersionConfig) {
        versionConfigs[versionCode] = config
    }

    /**
     * 版本配置数据类
     */
    data class VersionConfig(
        val settingRecyclerAdapterClz: String,
        val settingItemQSNClz: String,
        val settingItemStrFieldName: String,
        val readerFullRequestClz: String,
        val rpcApiPackage: String,
        val rpcModelPackage: String = VersionManager.rpcModelPackage
    )
}
