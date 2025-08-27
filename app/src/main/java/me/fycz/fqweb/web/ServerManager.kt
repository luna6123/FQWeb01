package me.fycz.fqweb.web

import me.fycz.fqweb.utils.SPUtils
import me.fycz.fqweb.utils.log

class ServerManager private constructor() {
    private lateinit var httpServer: HttpServer
    private lateinit var frpcServer: FrpcServer
    private var isInitialized = false

    companion object {
        val instance by lazy { ServerManager() }
    }

    @Synchronized
    fun initialize() {
        if (!isInitialized) {
            httpServer = HttpServer(SPUtils.getInt("port", 9999))
            frpcServer = FrpcServer()
            isInitialized = true
        }
    }

    @Synchronized
    fun startServers() {
        if (!isInitialized) initialize()
        try {
            // 独立判断 httpServer
            if (!httpServer.isAlive && SPUtils.getBoolean("autoStart", false)) {
                httpServer.start()
            }
            // 独立判断 frpcServer
            if (!frpcServer.isAlive() && SPUtils.getBoolean("traversal", false)) {
                frpcServer.start()
            }
        } catch (e: Throwable) {
            log(e)
        }
    }

    @Synchronized
    fun restartHttpServer(newPort: Int = SPUtils.getInt("port", 9999)) {
        if (!isInitialized) initialize()
        if (httpServer.isAlive) {
            if (httpServer.listeningPort != newPort) {
                httpServer.stop()
                httpServer = HttpServer(newPort)
                httpServer.start()
            } else if (!httpServer.isAlive) {
                // 端口相同但服务挂了
                httpServer.start()
            }
        } else {
            httpServer = HttpServer(newPort)
            httpServer.start()
        }
    }

    @Synchronized
    fun stopServers() {
        if (!isInitialized) return
        if (httpServer.isAlive) httpServer.stop()
        if (frpcServer.isAlive()) frpcServer.stop()
    }

    fun getServerStatus(): String {
        if (!isInitialized) return "未开启"
        return if (httpServer.isAlive) {
            "已开启(http://127.0.0.1:${httpServer.listeningPort})"
        } else {
            "未开启"
        }
    }

    fun isHttpServerAlive(): Boolean = isInitialized && httpServer.isAlive

    fun isFrpcServerAlive(): Boolean = isInitialized && frpcServer.isAlive()
}
