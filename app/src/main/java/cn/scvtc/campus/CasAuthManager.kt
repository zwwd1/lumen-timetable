package cn.scvtc.campus

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import cn.scvtc.campus.core.School
import kotlinx.coroutines.*

/** CAS owns password processing. Identity and school data use verified JSON
 * APIs; completing authentication never waits for a timetable or calendar. */
class CasAuthManager(private val context:Context,private val api:JwxtApi) {
    companion object {
        val JWXT_SSO_ENTRY=School.ORIGIN+"/jwgr/api/api/cas/login?pattern=teacher-login&returnUrl="+Uri.encode(School.HOME)
        const val PROVIDED_H5_ENTRY="https://cas.scvtc.edu.cn/cas/H5/index.html?service=https://www.shulin-soft.com:8267/casLogin.html#/"
    }
    suspend fun restoreJwxt(account:String) { authenticate(account,null) }
    suspend fun signIn(account:String,password:String,onProgress:(String)->Unit={}):JwxtStudent {
        require(account.matches(Regex("[0-9]{6,20}")) && password.length in 1..512){"请输入正确的学号和密码"}
        return authenticate(account,password,onProgress)
    }
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun authenticate(account:String,password:String?,onProgress:(String)->Unit={}):JwxtStudent=withContext(Dispatchers.Main) {
        val login=OfficialLoginMemory(context.applicationContext)
        val failure=CompletableDeferred<Unit>()
        val recoveryPages=mutableSetOf<String>()
        var submitted=password==null
        var targetReady=false
        var homeRetry=false
        var destroyed=false
        val web=WebView(context.applicationContext)
        web.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true
            useWideViewPort=true;loadWithOverviewMode=true
            allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webChromeClient=WebChromeClient()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
        val metrics=context.resources.displayMetrics
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,android.view.View.MeasureSpec.EXACTLY))
        web.layout(0,0,metrics.widthPixels,metrics.heightPixels);web.onResume()
        login.install(web){account}
        fun stage(code:String)=when(code) {
            "challenge" -> AuthenticationStage.CHALLENGE
            "notice" -> AuthenticationStage.NOTICE
            "rejected" -> AuthenticationStage.REJECTED
            "unavailable" -> AuthenticationStage.SESSION
            else -> AuthenticationStage.FORM
        }
        suspend fun pageState():String=suspendCancellableCoroutine { continuation ->
            if(destroyed){continuation.resumeWith(Result.success("outside"));return@suspendCancellableCoroutine}
            web.evaluateJavascript("window.__officialLoginMemory?.state() || 'loading'") { value ->
                if(continuation.isActive)continuation.resumeWith(Result.success(value.trim('"')))
            }
        }
        web.webViewClient=object:WebViewClient() {
            override fun onPageStarted(v:WebView,url:String?,icon:android.graphics.Bitmap?) {
                if(Uri.parse(url.orEmpty()).host=="cas.scvtc.edu.cn") {
                    targetReady=false;onProgress("正在打开学校登录页…")
                } else onProgress("正在建立教务会话…")
            }
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {
                if(!r.isForMainFrame)return false
                val u=r.url
                val approved=u.scheme=="https" && u.userInfo==null &&
                    (u.host in setOf("cas.scvtc.edu.cn","jwxt.scvtc.edu.cn")&&u.port in setOf(-1,443) ||
                        u.host=="www.shulin-soft.com"&&u.port==8267&&u.path=="/casLogin.html")
                if(!approved)failure.completeExceptionally(IllegalStateException("学校认证跳转到未确认来源"))
                if(approved && login.recoveryNavigation(v,account,u.toString()))return true
                return !approved
            }
            override fun onPageFinished(v:WebView,url:String) {
                if(destroyed)return
                login.prepare(v);CookieManager.getInstance().flush()
                val host=Uri.parse(url).host
                if(host=="www.shulin-soft.com"){v.loadUrl(JWXT_SSO_ENTRY);return}
                if(login.recoveryNavigation(v,account,url))return
                if(host=="jwxt.scvtc.edu.cn")targetReady=true
                if(host=="cas.scvtc.edu.cn" && recoveryPages.add(url)) {
                    onProgress("正在填写并提交学校认证…")
                    val completed:(Boolean)->Unit={restored->
                        if(!destroyed && restored) {
                            submitted=true;onProgress("认证已提交，正在核验学生身份…")
                        } else if(!destroyed && v.url==url) {
                            v.evaluateJavascript("window.__officialLoginMemory?.state() || 'loading'") { result ->
                                if(!destroyed && v.url==url)
                                    failure.completeExceptionally(JwxtAuthenticationRequired(stage(result.trim('"'))))
                            }
                        }
                    }
                    if(password==null)login.recover(v,account,background=true,onResult=completed)
                    else login.configure(v,account,password,background=true,onResult=completed)
                }
            }
            override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError) {
                if(r.isForMainFrame) {
                    login.failedNetwork(account)
                    failure.completeExceptionally(IllegalStateException("学校认证页面连接失败"))
                }
            }
            override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,response:WebResourceResponse) {
                if(!r.isForMainFrame)return
                if(r.url.host=="jwxt.scvtc.edu.cn" && r.url.path=="/api/cas/login" && !homeRetry) {
                    homeRetry=true;targetReady=false;v.loadUrl(School.HOME)
                } else failure.completeExceptionally(IllegalStateException("学校页面 HTTP "+response.statusCode))
            }
            override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean {
                destroyed=true;v.destroy()
                failure.completeExceptionally(IllegalStateException("学校认证页面已关闭，请重试"))
                return true
            }
        }
        try {
            withTimeout(55_000) {
                if(password!=null)clearLoginCookies()
                onProgress("正在连接川职教务…")
                web.loadUrl(JWXT_SSO_ENTRY)
                var probes=0
                while(true) {
                    if(failure.isCompleted)failure.await()
                    val currentUrl=web.url.orEmpty()
                    if(submitted && Uri.parse(currentUrl).host=="cas.scvtc.edu.cn") {
                        val state=pageState()
                        if(state in setOf("challenge","notice","rejected","unavailable"))throw JwxtAuthenticationRequired(stage(state))
                    }
                    if(!submitted || !targetReady){delay(400);continue}
                    onProgress("正在核验教务学生身份…")
                    try {
                        val student=api.student(account)
                        login.confirmed(student.account,promoteEnrollment=password!=null)
                        return@withTimeout student
                    } catch(e:JwxtAuthenticationRequired) {
                        probes++
                        if(!homeRetry) {
                            homeRetry=true;targetReady=false;web.loadUrl(School.HOME)
                        } else if(probes>=3) throw JwxtAuthenticationRequired(AuthenticationStage.SESSION)
                        delay(1200)
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("身份未核验")
            }
        } catch(e:TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            login.failedNetwork(account)
            throw JwxtAuthenticationRequired(AuthenticationStage.CALLBACK)
        } catch(e:CancellationException) {
            login.failedNetwork(account)
            throw e
        } finally {
            if(!destroyed){web.stopLoading();web.destroy();destroyed=true}
            CookieManager.getInstance().flush()
        }
    }
    private suspend fun clearLoginCookies() {
        val manager=CookieManager.getInstance()
        val locations=listOf(
            "https://cas.scvtc.edu.cn/cas/H5/index.html" to listOf("/","/cas","/cas/","/cas/H5"),
            JwxtApi.BASE to listOf("/","/jwgr","/jwgr/","/jwgr/api")
        )
        for((url,paths) in locations) {
            val names=manager.getCookie(url).orEmpty().split(';')
                .map{it.substringBefore('=').trim()}.filter{it.matches(Regex("[A-Za-z0-9_-]+"))}
            for(name in names)for(path in paths)suspendCancellableCoroutine<Unit>{continuation->
                manager.setCookie(url,"$name=; Path=$path; Max-Age=0; Secure"){
                    if(continuation.isActive)continuation.resumeWith(Result.success(Unit))
                }
            }
        }
        manager.flush()
    }
}

/** One recovery for an expired session; never reinterpret a timeout or data
 * parsing failure as a rejected password or erase the last good school data. */
class JwxtSessionManager(private val api:JwxtApi,private val recover:suspend (String)->Unit) {
    suspend fun verifiedStudent(account:String):JwxtStudent = try {api.student(account)}
    catch(e:JwxtAuthenticationRequired){recover(account);api.student(account)}
    suspend fun <T> withSession(account:String,read:suspend (JwxtStudent)->T):T {
        try {return read(api.student(account))}
        catch(e:JwxtAuthenticationRequired){recover(account);return read(api.student(account))}
    }
}
