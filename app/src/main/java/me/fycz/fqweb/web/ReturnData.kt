package me.fycz.fqweb.web

class ReturnData {

    var isSuccess: Boolean = false
        private set

    var errorMsg: String = "未知错误,请联系开发者!"
        private set

    var data: Any? = null
        private set

    // 新增：业务码与可选 MIME（用于二进制/下载类返回标注）
    var code: Int = 0
        private set

    var mime: String? = null
        private set

    fun setErrorMsg(errorMsg: String): ReturnData {
        this.isSuccess = false
        this.errorMsg = errorMsg
        this.code = if (code == 0) 400 else code
        this.mime = null
        this.data = null
        return this
    }

    fun setData(data: Any): ReturnData {
        this.isSuccess = true
        this.errorMsg = ""
        this.code = 0
        this.data = data
        return this
    }

    // 语义化构造
    fun fail(code: Int = 400, message: String): ReturnData {
        this.isSuccess = false
        this.errorMsg = message
        this.code = code
        this.data = null
        this.mime = null
        return this
    }

    fun ok(data: Any, mime: String? = null): ReturnData {
        this.isSuccess = true
        this.errorMsg = ""
        this.code = 0
        this.data = data
        this.mime = mime
        return this
    }
}
