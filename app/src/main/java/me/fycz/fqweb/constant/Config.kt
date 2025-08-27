package me.fycz.fqweb.constant

import me.fycz.fqweb.constant.VersionManager

/**
 * @author fengyue
 * @date 2023/5/30 8:35
 * @description
 */
object Config {

    const val TRAVERSAL_CONFIG_URL = "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    // 从VersionManager获取版本相关配置
    val settingRecyclerAdapterClz: String get() = VersionManager.getCurrentConfig().settingRecyclerAdapterClz
    val settingItemQSNClz: String get() = VersionManager.getCurrentConfig().settingItemQSNClz
    val settingItemStrFieldName: String get() = VersionManager.getCurrentConfig().settingItemStrFieldName
    val readerFullRequestClz: String get() = VersionManager.getCurrentConfig().readerFullRequestClz
    val rpcApiPackage: String get() = VersionManager.getCurrentConfig().rpcApiPackage
    val versionCode: Int get() = VersionManager.currentVersion

    // 检查当前版本是否支持
    fun isSupportedVersion(): Boolean = VersionManager.isSupportedVersion()

    // 添加新的版本配置
    fun addVersionConfig(versionCode: Int, config: VersionManager.VersionConfig) = VersionManager.addVersionConfig(versionCode, config)
}
