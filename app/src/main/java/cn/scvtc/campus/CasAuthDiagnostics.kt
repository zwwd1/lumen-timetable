package cn.scvtc.campus

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Bounded local state observations. Never accepts page text, input values,
 * URLs, cookies or arbitrary exception messages into the diagnostic record. */
internal object CasAuthDiagnostics {
    const val TAG="ScvtcAuthState"
    val script="""(()=>{const known=x=>['账号安全提醒','提示','稍后修改','立即修改','稍后绑定','立即绑定','取消','去绑定','确定','确认','好的'].includes(x)?x:'other';const vis=e=>{for(let n=e;n instanceof Element;n=n.parentElement){let s=getComputedStyle(n);if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)===0)return false;}return true;};return JSON.stringify({state:window.__officialLoginMemory?.state()||'missing',host:location.hostname==='cas.scvtc.edu.cn'?'cas':location.hostname==='jwxt.scvtc.edu.cn'?'jwxt':'other',page:location.pathname==='/cas/H5/index.html'?'h5':location.pathname==='/cas/WEB/index.html'?'web':'other',route:['','#','#/','#/personCenter','#/changeWay','#/bindTel','#/loadingPage'].includes(location.hash)?location.hash:'other',modals:[...document.querySelectorAll('.ivu-modal')].filter(vis).map(m=>{let o=m.__vue__,modal=null;for(let i=0;o&&o.${'$'}options?.name!=='login'&&i<8;i++,o=o.${'$'}parent)if(o.${'$'}options?.name==='Modal')modal=o;let body=m.querySelector('.ivu-modal-body')?.textContent||'';return {owner:o?.${'$'}options?.name==='login'?'login':'other',componentVisible:modal?.visible??null,mustBind:o?.mustBind??null,needAutoLock:o?.needAutoLock??null,twoBtn:o?.twoBtn??null,title:known((m.querySelector('.ivu-modal-header')?.textContent||o?.noticeModalTitle||'').trim()),left:known((m.querySelector('.footer-left')?.textContent||'').trim()),right:known((m.querySelector('.footer-right')?.textContent||'').trim()),leftCallback:typeof o?.twoBtnLeftClick,phone:/手机号|绑定/.test(body),password:/密码/.test(body),expiry:/过期|有效期/.test(body),captcha:/验证码|安全验证|二次认证|人机验证/.test(body)};})});})()"""

    internal fun sanitized(value:String):JSONObject?=runCatching {
        require(value.length<=8192)
        val decoded=JSONTokener(value).nextValue() as? String?:return@runCatching null
        val input=JSONObject(decoded)
        fun enum(source:JSONObject,key:String,allowed:Set<String>)=source.optString(key).takeIf{it in allowed}?:"other"
        val result=JSONObject()
            .put("state",enum(input,"state",setOf("missing","form","loading","notice","optional-notice","challenge","handoff","rejected","unavailable","outside")))
            .put("host",enum(input,"host",setOf("cas","jwxt","other")))
            .put("page",enum(input,"page",setOf("h5","web","other")))
            .put("route",enum(input,"route",setOf("","#","#/","#/personCenter","#/changeWay","#/bindTel","#/loadingPage","other")))
        val notices=JSONArray();val rows=input.optJSONArray("modals")?:JSONArray()
        for(i in 0 until minOf(rows.length(),4)) {
            val row=rows.optJSONObject(i)?:continue
            val safe=JSONObject().put("owner",enum(row,"owner",setOf("login","other")))
            for(key in listOf("componentVisible","mustBind","needAutoLock","twoBtn","phone","password","expiry","captcha")) {
                val flag=row.opt(key)
                safe.put(key,if(flag is Boolean)flag else JSONObject.NULL)
            }
            for(key in listOf("title","left","right"))safe.put(key,enum(row,key,setOf("账号安全提醒","提示","稍后修改","立即修改","稍后绑定","立即绑定","取消","去绑定","确定","确认","好的","other")))
            safe.put("leftCallback",enum(row,"leftCallback",setOf("function","object","undefined","other")))
            notices.put(safe)
        }
        result.put("modals",notices)
    }.getOrNull()

    fun record(value:String){sanitized(value)?.let{Log.i(TAG,it.toString())}}
}
