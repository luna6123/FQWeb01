package me.fycz.fqweb.constant

import de.robv.android.xposed.XposedHelpers
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass

object Config {

    const val TRAVERSAL_CONFIG_URL =
        "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val dragonClassloader by lazy { GlobalApp.getClassloader() }

    // 公共辅助方法
    private fun tryClassOrDefault(primary: String, fallback: String): String =
        runCatching {
            primary.findClass(dragonClassloader)
            primary
        }.getOrElse { fallback }

    /** -------- 版本映射表定义 -------- */
    private val recyclerAdapterMap = mapOf(
        532 to "com.dragon.read.base.recyler.c",
        57932 to "com.dragon.read.recyler.c"
    )

    private val settingItemQSNMap = mapOf(
        532 to "com.dragon.read.component.biz.impl.mine.settings.a.g",
        57932 to "com.dragon.read.component.biz.impl.mine.settings.a.k"
    )

    private val settingItemStrFieldMap = mapOf(
        532 to "i",
        57932 to "i",
        58332 to "j"
    )

    private val readerFullRequestMap = mapOf(
        532 to "%s.e",
        57932 to "%s.e",
        58332 to "%s.f"
    )

    private val rpcApiPackageMap = mapOf(
        532 to "com.dragon.read.rpc.a",
        57932 to "com.dragon.read.rpc"
    )

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    /** -------- 版本适配逻辑 -------- */
    val settingRecyclerAdapterClz by lazy {
        recyclerAdapterMap[versionCode]
            ?: tryClassOrDefault("com.dragon.read.recyler.c", "com.dragon.read.base.recyler.c")
    }

    val settingItemQSNClz by lazy {
        settingItemQSNMap[versionCode]
            ?: tryClassOrDefault(
                "com.dragon.read.component.biz.impl.mine.settings.a.k",
                "com.dragon.read.component.biz.impl.mine.settings.a.g"
            )
    }

    val settingItemStrFieldName by lazy {
        settingItemStrFieldMap[versionCode] ?: run {
            val clz = "com.dragon.read.pages.mine.settings.e".findClass(dragonClassloader)
            val iField = XposedHelpers.findField(clz, "i")
            if (iField.type == CharSequence::class.java) "i" else "j"
        }
    }

    val readerFullRequestClz by lazy {
        val pkg = rpcApiPackage
        val fullRequestClz = "$rpcModelPackage.FullRequest".findClass(dragonClassloader)
        readerFullRequestMap[versionCode]?.format(pkg) ?: runCatching {
            XposedHelpers.findMethodExact("$pkg.e", dragonClassloader, "a", fullRequestClz)
            "$pkg.e"
        }.getOrElse { "$pkg.f" }
    }

    val rpcApiPackage by lazy {
        rpcApiPackageMap[versionCode] ?: runCatching {
            "com.dragon.read.rpc.a".findClass(dragonClassloader)
            "com.dragon.read.rpc"
        }.getOrElse {
            "com.dragon.read.rpc.a.a".findClass(dragonClassloader)
            "com.dragon.read.rpc.a"
        }
    }

    val versionCode: Int by lazy {
        try {
            val pm = GlobalApp.application!!.packageManager
            val info = pm.getPackageInfo(GlobalApp.application!!.packageName, 0)
            info.versionCode
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
