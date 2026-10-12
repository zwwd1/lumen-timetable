package cn.scvtc.campus

import android.net.Uri
import cn.scvtc.campus.core.School

/** A CAS landing is a reason to visit the protected JWXT entry, never proof of
 * a student login. Only the real student API can complete authentication. */
class OfficialAuthNavigation {
    private var returned=false
    fun reset(){returned=false}
    fun continueFrom(page:String):String? {
        if(returned||!isCasLanding(page))return null
        returned=true
        return School.HOME
    }
    companion object {
        fun isCasLanding(page:String):Boolean=runCatching {
            val u=Uri.parse(page)
            u.scheme=="https"&&u.host=="cas.scvtc.edu.cn"&&u.port in setOf(-1,443)&&u.userInfo==null&&
                u.path=="/cas/H5/index.html"&&
                u.fragment.orEmpty().substringBefore('?').trimEnd('/')=="/personCenter"
        }.getOrDefault(false)
    }
}
