package cn.scvtc.campus

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.*
import org.json.JSONObject

/** Credentials are captured only from a user's real submit, then bound to verified identity. */
class OfficialLoginMemory(private val context:Context) {
    private var disposed=false
    private val pendingCallbacks=mutableMapOf<Runnable,WebView>()
    private fun post(web:WebView,delay:Long,block:()->Unit) {
        if(disposed)return
        lateinit var action:Runnable
        action=Runnable { pendingCallbacks.remove(action);if(!disposed)block() }
        pendingCallbacks[action]=web
        web.postDelayed(action,delay)
    }
    fun dispose() {
        if(disposed)return
        disposed=true
        pendingCallbacks.forEach { (action,web)->web.removeCallbacks(action) }
        pendingCallbacks.clear()
    }
    private val origins=setOf("https://jwxt.scvtc.edu.cn","https://portal.scvtc.edu.cn","https://cas.scvtc.edu.cn")
    private val prefs=context.getSharedPreferences("official_login_memory",Context.MODE_PRIVATE)
    companion object {
        private const val ENROLLMENT_TTL=15*60*1000L
    }
    /** An encrypted short-lived candidate survives the visible WebView and is
     * promoted only by a matching authenticated student API response. */
    private fun stage(value:JSONObject):Boolean=runCatching {
        val account=value.getString("username")
        check(prefs.edit().putString("enrollment:$account",seal(account,
            JSONObject(value.toString()).put("capturedAt",System.currentTimeMillis()).toString())).commit())
        true
    }.getOrDefault(false)
    fun configured(account:String):Boolean=prefs.getString(account,null)?.let{open(account,it)}!=null
    /** Native form input is remembered separately from verified CAS credentials. */
    fun rememberInput(account:String,password:String) {
        require(account.matches(Regex("[0-9]{6,20}")) && password.length in 1..512)
        check(prefs.edit().putString("input:$account",seal("input:$account",
            JSONObject().put("username",account).put("password",password).toString()))
            .putString("last-input",seal("native-input:last",JSONObject().put("username",account).toString())).commit())
    }
    fun lastInputAccount():String=prefs.getString("last-input",null)?.let { open("native-input:last",it) }?.optString("username").orEmpty()
    fun inputPassword(account:String):String {
        val input=prefs.getString("input:$account",null)?.let { open("input:$account",it) }
            ?:prefs.getString(account,null)?.let { open(account,it) }
        return input?.takeIf { it.optString("username")==account }?.optString("password").orEmpty()
    }
    private fun enrollment(account:String):JSONObject? {
        val value=prefs.getString("enrollment:$account",null)?.let{open(account,it)}?:return null
        val age=System.currentTimeMillis()-value.optLong("capturedAt",0)
        if(age !in 0..ENROLLMENT_TTL || value.optString("username")!=account) {
            prefs.edit().remove("enrollment:$account").apply();return null
        }
        return value
    }
    private var lastAttempt=0L
    private var recoveringPage=""
    private var usedH5Entry=false
    /** Keep the user's verified mobile form binding when the JWXT SSO entry
     * redirects to the desktop CAS form. Do not send a password to a new form. */
    fun recoveryNavigation(web:WebView,account:String,url:String):Boolean {
        val current=Uri.parse(url)
        if(current.scheme!="https"||current.host!="cas.scvtc.edu.cn"||current.port !in setOf(-1,443)||current.path !in setOf("","/","/cas/WEB/index.html")||usedH5Entry)return false
        // Keep the actual JWXT service from the official SSO redirect. Sending
        // the user to an unrelated service loses the target Session handoff.
        val service=current.getQueryParameter("service")?:return false
        val callback=Uri.parse(service)
        if(callback.scheme!="https"||callback.host!="jwxt.scvtc.edu.cn"||callback.port !in setOf(-1,443)||callback.userInfo!=null||callback.path!="/api/cas/login")return false
        val mobile=Uri.parse("https://cas.scvtc.edu.cn/cas/H5/index.html").buildUpon().appendQueryParameter("service",service).fragment("/").build()
        usedH5Entry=true
        web.loadUrl(mobile.toString())
        return true
    }
    private fun trusted(url:String):Boolean=runCatching{Uri.parse(url).let{it.scheme=="https"&&it.port in setOf(-1,443)&&it.userInfo==null&&"https://${it.host}" in origins}}.getOrDefault(false)
    private val cipher=LocalCipher(context,"scvtc.official.login.v1")
    private fun seal(account:String,text:String):String= cipher.seal(text,account)
    private fun open(account:String,text:String):JSONObject?=runCatching { JSONObject(cipher.open(text,account)) }.getOrNull()
    /** Only observed headers for the three verified JSON endpoints are retained.
     * The per-account AES-GCM AAD prevents reuse in another account. */
    fun apiHeaders(account:String,url:String):Map<String,String> =
        prefs.getString("headers:$account:${Uri.parse(url).path}",null)?.let{open(account,it)}?.let{value->
            value.keys().asSequence().associateWith{value.getString(it)}
        }.orEmpty()
    private fun rememberHeaders(account:String,url:String,headers:JSONObject){
        if(Uri.parse(url).scheme!="https" || Uri.parse(url).host!="jwxt.scvtc.edu.cn" || url.substringBefore('?') !in setOf(JwxtApi.IDENTITY,JwxtApi.TERM,JwxtApi.SCHEDULE))return
        val safe=JSONObject()
        headers.keys().forEach{key->if(key.lowercase() in setOf("permission","authorization","x-auth-token","token")){
            val value=headers.optString(key);if(value.length in 1..8192)safe.put(key,value)
        }}
        runCatching{prefs.edit().putString("headers:$account:${Uri.parse(url).path}",seal(account,safe.toString())).commit()}
    }
    /** Session cookies are not included in exports or cloud backups. Restore only
     * an empty target-domain session; never overwrite a newly established SSO. */
    suspend fun restoreSession(account:String)=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){
        val manager=android.webkit.CookieManager.getInstance()
        if(manager.getCookie(JwxtApi.BASE).orEmpty().split(';').any{it.trim().startsWith("LYSESSIONID=")})return@withContext
        val saved=prefs.getString("session:$account",null)?.let{open(account,it)}?:return@withContext
        for(name in listOf("LYSESSIONID","user")){
            val value=saved.optString(name);if(value.isBlank()||value.contains(';')||value.contains('\n')||value.contains('\r'))continue
            kotlinx.coroutines.suspendCancellableCoroutine<Unit>{continuation->
                manager.setCookie(JwxtApi.BASE,"$name=$value; Path=/; Secure"+if(name=="LYSESSIONID")"; HttpOnly" else ""){
                    if(continuation.isActive)continuation.resumeWith(Result.success(Unit))
                }
            }
        }
        manager.flush()
    }
    private fun rememberSession(account:String){
        val value=JSONObject()
        android.webkit.CookieManager.getInstance().getCookie(JwxtApi.BASE).orEmpty().split(';').forEach{part->
            val name=part.substringBefore('=').trim();if(name in setOf("LYSESSIONID","user"))value.put(name,part.substringAfter('=',""))
        }
        if(value.optString("LYSESSIONID").isNotBlank())runCatching{prefs.edit().putString("session:$account",seal(account,value.toString())).commit()}
        android.webkit.CookieManager.getInstance().flush()
    }
    fun install(web:WebView,onIdentity:(String)->Unit={},onAuthLanding:(String)->Unit={},expectedAccount:()->String={""}) {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return
        WebViewCompat.addWebMessageListener(web,"OfficialLoginBridge",origins){_,message,origin,main,_->
            if(!disposed && main && origin.toString().trimEnd('/') in origins)runCatching {
                val raw=message.data.orEmpty();require(raw.length<=16_384)
                val m=JSONObject(raw)
                require(trusted(m.getString("page")) && web.url==m.getString("page"))
                if(m.optString("kind")=="cas-landing") {
                    require(origin.toString().trimEnd('/')=="https://cas.scvtc.edu.cn" && OfficialAuthNavigation.isCasLanding(m.getString("page")))
                    onAuthLanding(m.getString("page"));return@runCatching
                }
                require(m.getJSONObject("binding").getString("origin")==origin.toString().trimEnd('/'))
                require(m.getString("username").matches(Regex("[0-9]{6,20}")) && m.getString("password").length in 1..512)
                check(stage(m))
            }
        }
        var document="";var verified="";val staged=mutableMapOf<String,JSONObject>()
        WebViewCompat.addWebMessageListener(web,"JwxtHeaderBridge",setOf("https://jwxt.scvtc.edu.cn")){_,message,origin,main,_->
            if(!disposed && main && origin.toString().trimEnd('/')=="https://jwxt.scvtc.edu.cn")runCatching{
                val raw=message.data.orEmpty();require(raw.length<=2_600_000)
                val m=JSONObject(raw);require(m.getString("page")==web.url)
                // Hash navigation stays in the same document. Keep its verified
                // student identity so newly observed schedule headers are saved.
                // Reloads receive a fresh ID and must prove identity again.
                val doc=m.getString("documentId");require(doc.matches(Regex("[0-9a-fA-F-]{36}")))
                if(doc!=document){document=doc;verified="";staged.clear()}
                val url=m.getString("url").substringBefore('?');require(url in setOf(JwxtApi.IDENTITY,JwxtApi.TERM,JwxtApi.SCHEDULE))
                staged[url]=m.getJSONObject("headers")
                if(url==JwxtApi.IDENTITY){
                    verified=cn.scvtc.campus.core.OfficialIdentity.account(m.optString("identityBody"))
                    require(verified.isNotBlank()&&(expectedAccount().isBlank()||expectedAccount()==verified))
                }
                if(verified.isNotBlank()&&(expectedAccount().isBlank()||expectedAccount()==verified)){
                    staged.forEach{(path,headers)->rememberHeaders(verified,path,headers)};staged.clear()
                    if(url==JwxtApi.IDENTITY)onIdentity(verified)
                }
            }
        }
        val observer=context.assets.open("jwxt-auth-session.js").bufferedReader().use{it.readText()}
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,observer,setOf("https://jwxt.scvtc.edu.cn"))
        val script=context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()}
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,script,origins)
    }
    fun confirmed(account:String,promoteEnrollment:Boolean=false) {
        if(account.isBlank())return
        lastAttempt=0L
        recoveringPage=""
        usedH5Entry=false
        rememberSession(account)
        prefs.edit().remove("attempt:$account").remove("pending:$account").apply()
        if(!promoteEnrollment)return
        val value=enrollment(account)?:return
        if(account.isBlank()||value.optString("username")!=account)return
        recoveringPage=""
        runCatching{check(prefs.edit().putString(account,seal(account,value.toString())).remove("enrollment:$account").remove("attempt:$account").commit())}
    }
    fun prepare(web:WebView) {
        if(disposed)return
        if(trusted(web.url.orEmpty()) && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            web.evaluateJavascript(context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()},null)
        if(web.url?.startsWith("https://jwxt.scvtc.edu.cn/")==true && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            web.evaluateJavascript(context.assets.open("jwxt-auth-session.js").bufferedReader().use{it.readText()},null)
        if(trusted(web.url.orEmpty()) && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            web.evaluateJavascript("window.__officialLoginMemory?.reportLanding()",null)
    }
    /** Explicit native input is staged, and saved only after the real identity response. */
    fun configure(web:WebView,account:String,password:String,background:Boolean=false,onResult:(Boolean)->Unit) {
        awaitForm(web,background) { ready ->
            if(ready) configureReady(web,account,password,onResult) else onResult(false)
        }
    }
    private fun configureReady(web:WebView,account:String,password:String,onResult:(Boolean)->Unit) {
        if(disposed)return
        val page=web.url.orEmpty()
        if(!trusted(page)||!account.matches(Regex("[0-9]{6,20}"))||password.length !in 1..512){onResult(false);return}
        prepare(web)
        web.evaluateJavascript("JSON.stringify(window.__officialLoginMemory?.describe() || null)"){raw->
            if(disposed)return@evaluateJavascript
            val binding=runCatching{val value=org.json.JSONTokener(raw).nextValue() as? String;value?.let(::JSONObject)}.getOrNull()
            if(binding==null||web.url!=page||binding.optString("origin")!="https://${Uri.parse(page).host}"){onResult(false);return@evaluateJavascript}
            if(!stage(JSONObject().put("page",page).put("binding",binding).put("username",account).put("password",password))) {onResult(false);return@evaluateJavascript}
            prefs.edit().remove("pending:$account").remove("attempt:$account").apply();recoveringPage=""
            // Fill a human-challenge form, but only report automatic submission
            // when the official submit handler can actually be invoked.
            web.evaluateJavascript("window.__officialLoginMemory?.configure($binding,${JSONObject.quote(account)},${JSONObject.quote(password)},false)"){value->
                if(disposed)return@evaluateJavascript
                if(value!="true")prefs.edit().remove("enrollment:$account").apply()
                if(value!="true"){onResult(false);return@evaluateJavascript}
                // Vue updates the submit button after input events, on its next render.
                post(web,100) {
                    if(web.url!=page){onResult(false);return@post}
                    web.evaluateJavascript("window.__officialLoginMemory?.restore($binding,${JSONObject.quote(account)},${JSONObject.quote(password)})"){submitted->if(!disposed)onResult(submitted=="true")}
                }
            }
        }
    }
    /** A transport failure is not a rejected password; allow a later bounded attempt. */
    fun failedNetwork(account:String){recoveringPage="";usedH5Entry=false;lastAttempt=0L;prefs.edit().remove("pending:$account").remove("attempt:$account").apply()}
    /** The official click handler performs its own CAS encryption; no guessed password protocol. */
    fun recover(web:WebView,account:String,background:Boolean=false,onResult:(Boolean)->Unit) {
        awaitForm(web,background) { ready ->
            if(ready) recoverBoundForm(web,account,onResult) else onResult(false)
        }
    }
    private fun awaitForm(web:WebView,background:Boolean,onResult:(Boolean)->Unit) {
        val page=web.url.orEmpty()
        if(!trusted(page)){onResult(false);return}
        val deadline=android.os.SystemClock.elapsedRealtime()+12_000
        // CAS is a SPA: document load is earlier than its visible form mounting.
        // Wait for a recognisable form without sending credentials or clicking.
        fun ready(){
            if(disposed)return
            if(web.url!=page){onResult(false);return}
            prepare(web)
            if(background)web.evaluateJavascript("window.__officialLoginMemory?.setBackgroundMode()",null)
            web.evaluateJavascript("!!window.__officialLoginMemory?.describe()"){result->
                if(disposed)return@evaluateJavascript
                if(web.url!=page)onResult(false)
                else if(result=="true")onResult(true)
                else if(android.os.SystemClock.elapsedRealtime()<deadline)post(web,400){ready()}
                else onResult(false)
            }
        }
        ready()
    }
    private fun recoverBoundForm(web:WebView,account:String,onResult:(Boolean)->Unit) {
        if(disposed)return
        val url=web.url.orEmpty()
        if(url==recoveringPage && System.currentTimeMillis()-lastAttempt<120_000){onResult(true);return}
        lastAttempt=maxOf(lastAttempt,prefs.getLong("attempt:$account",0L))
        // A pending attempt is bounded by the same throttle. A failed login or
        // process death must not disable all future recoveries permanently.
        if(account.isBlank()||!trusted(url)||System.currentTimeMillis()-lastAttempt<120_000){onResult(false);return}
        val saved=enrollment(account)?:prefs.getString(account,null)?.let{open(account,it)}
        if(saved==null) {
            val password=inputPassword(account)
            if(password.isNotEmpty())configureReady(web,account,password,onResult) else onResult(false)
            return
        }
        if(saved.getJSONObject("binding").optString("origin")!="https://${Uri.parse(url).host}"){onResult(false);return}
        val script=context.assets.open("official-login-memory.js").bufferedReader().use{it.readText()}
        web.evaluateJavascript(script,null)
        web.evaluateJavascript("window.__officialLoginMemory?.matches(${saved.getJSONObject("binding")})"){matches->
            if(disposed)return@evaluateJavascript
            if(web.url!=url){onResult(false)}
            else if(matches!="true"){
                // Re-bind only through the current, whitelisted official CAS
                // adapter. It fills challenges but never submits through one.
                configureReady(web,account,saved.getString("password"),onResult)
            }
            else if(System.currentTimeMillis()-lastAttempt<120_000){onResult(false)}
            else {
                lastAttempt=System.currentTimeMillis()
                if(!prefs.edit().putLong("attempt:$account",lastAttempt).putBoolean("pending:$account",true).commit()){onResult(false);return@evaluateJavascript}
                recoveringPage=url
                web.evaluateJavascript("window.__officialLoginMemory?.restore(${saved.getJSONObject("binding")},${JSONObject.quote(saved.getString("username"))},${JSONObject.quote(saved.getString("password"))})"){if(!disposed)onResult(it=="true")}
            }
        }
    }
    fun forget(account:String){
        recoveringPage="";usedH5Entry=false
        val edit=prefs.edit().remove("input:$account").remove("enrollment:$account").remove(account).remove("attempt:$account").remove("pending:$account").remove("session:$account")
        if(lastInputAccount()==account)edit.remove("last-input")
        prefs.all.keys.filter{it.startsWith("headers:$account:")}.forEach(edit::remove);edit.apply()
    }
}
