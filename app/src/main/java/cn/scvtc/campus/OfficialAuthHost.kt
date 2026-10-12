package cn.scvtc.campus

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/** Keeps an application-owned authentication document attached to a real window
 * while the app is foregrounded. Rotation moves the same document; it does not
 * create another login or ask for an overlay permission. No credentials live here. */
object OfficialAuthHost {
    private var application:Application?=null
    private var foreground=WeakReference<Activity>(null)
    private val documents=mutableSetOf<Document>()

    fun install(app:Application) {
        if(application!=null)return
        application=app
        app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity:Activity) {
                foreground=WeakReference(activity)
                documents.toList().forEach{it.attach(activity)}
            }
            override fun onActivityPaused(activity:Activity) {
                if(foreground.get()===activity)foreground=WeakReference(null)
                documents.toList().filter{it.activity.get()===activity}.forEach{it.detach()}
            }
            override fun onActivityDestroyed(activity:Activity) {
                documents.toList().filter{it.activity.get()===activity}.forEach{it.detach()}
            }
            override fun onActivityCreated(activity:Activity,state:Bundle?){}
            override fun onActivityStarted(activity:Activity){}
            override fun onActivityStopped(activity:Activity){}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle){}
        })
    }

    fun open(context:Context):Document {
        val app=context.applicationContext as Application
        install(app)
        return Document(app).also { document ->
            documents+=document
            foreground.get()?.let(document::attach)
        }
    }

    class Document internal constructor(private val app:Application) {
        private val wrapper=MutableContextWrapper(app)
        val web=WebView(wrapper)
        internal var activity=WeakReference<Activity>(null)
        private val holder=object:FrameLayout(wrapper) {
            override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean=true
        }.apply {
            alpha=0f
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable=false
        }
        private var closed=false
        init {
            web.isFocusable=false
            web.isFocusableInTouchMode=false
            holder.addView(web,FrameLayout.LayoutParams(-1,-1))
            val metrics=app.resources.displayMetrics
            holder.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels,View.MeasureSpec.EXACTLY))
            holder.layout(0,0,metrics.widthPixels,metrics.heightPixels)
            web.onResume()
        }
        internal fun attach(owner:Activity) {
            if(closed||owner.isFinishing||owner.isDestroyed)return
            val root=owner.findViewById<ViewGroup>(android.R.id.content)?:return
            if(holder.parent===root)return
            detach()
            wrapper.baseContext=owner
            activity=WeakReference(owner)
            // Behind the native content, transparent, excluded from input and accessibility.
            root.addView(holder,0,ViewGroup.LayoutParams(-1,-1))
            web.onResume()
        }
        internal fun detach() {
            (holder.parent as? ViewGroup)?.removeView(holder)
            activity=WeakReference(null)
            wrapper.baseContext=app
        }
        fun close(rendererGone:Boolean=false) {
            if(closed)return
            closed=true
            documents.remove(this)
            detach()
            holder.removeView(web)
            if(!rendererGone)web.stopLoading()
            web.destroy()
        }
    }
}
