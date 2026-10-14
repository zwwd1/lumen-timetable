package com.tyust.course.scvtc

import android.content.Context
import androidx.room.*
import cn.scvtc.campus.core.*
import com.tyust.course.academic.*
import com.tyust.course.manager.UserManager
import com.tyust.course.schedule.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Entity(tableName="school_schedules", primaryKeys=["account","semester"])
data class ScvtcSnapshot(val account:String,val semester:String,val payload:String,val fetchedAt:Long,val recipe:String="")
@Dao interface ScvtcDao {
 @Query("SELECT EXISTS(SELECT 1 FROM school_schedules) OR EXISTS(SELECT 1 FROM school_services)") suspend fun hasLocalRecords():Boolean
 @Query("SELECT * FROM school_schedules WHERE account=:account AND semester=:semester") suspend fun read(account:String,semester:String):ScvtcSnapshot?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(row:ScvtcSnapshot)
 @Query("UPDATE school_schedules SET recipe='' WHERE account=:account") suspend fun clearRecipes(account:String)
}
@Entity(tableName="school_services",primaryKeys=["account","semester","module"])
data class ScvtcServiceSnapshot(val account:String,val semester:String,val module:String,val payload:String,val fetchedAt:Long)
@Dao interface ScvtcServicesDao {
 @Query("SELECT * FROM school_services WHERE account=:account AND semester=:semester AND module=:module")suspend fun read(account:String,semester:String,module:String):ScvtcServiceSnapshot?
 @Insert(onConflict=OnConflictStrategy.REPLACE)suspend fun put(row:ScvtcServiceSnapshot)
}
@Database(entities=[ScvtcSnapshot::class,ScvtcServiceSnapshot::class],version=2,exportSchema=false)
abstract class ScvtcDatabase:RoomDatabase(){abstract fun schedules():ScvtcDao;abstract fun services():ScvtcServicesDao}

/** Request credentials stay in Keystore-encrypted app-private storage; exports never include them. */
private object RecipeVault {
 private val cipher get()=cn.scvtc.campus.LocalCipher(ScvtcRuntime.context,"scvtc-next-query")
 fun seal(text:String):String=cipher.seal(text)
 fun open(text:String):String=cipher.open(text)
 fun sealPayload(scope:String,text:String):String="encrypted:v2:"+cipher.seal(text,scope)
 fun openPayload(scope:String,text:String):String {
  if(text.startsWith("encrypted:v2:"))return cipher.open(text.removePrefix("encrypted:v2:"),scope)
  if(text.startsWith("encrypted:v1:"))return cipher.open(text.removePrefix("encrypted:v1:"),scope)
  return text // First-release plaintext records remain readable.
 }
}
object ScvtcRuntime {
 private val applicationJobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 lateinit var context:Context;private set
 lateinit var db:ScvtcDatabase;private set
 val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
 val revision=MutableStateFlow(0)
 val status=MutableStateFlow("登录官方教务后同步；离线课表保存在本机")
 val mutex=Mutex()
 private lateinit var progress:NativeSyncProgress
 val syncState get()=progress.state
 private val jobLock=Any()
 private val serviceJobs=mutableMapOf<String,Deferred<Map<String,Extraction>>>()
 val taskRunning=MutableStateFlow(false)
 private var firstSync:Job?=null
 private var loginJob:Deferred<cn.scvtc.campus.JwxtStudent>?=null
 private var frameworkRecovery:Job?=null
 val loginState=MutableStateFlow(cn.scvtc.campus.NativeLoginState())
 /** The caller observes an application task; leaving a page does not cancel it. */
 fun signIn(a:String,password:String):Deferred<cn.scvtc.campus.JwxtStudent> = synchronized(jobLock) {
  loginJob?.takeIf{!it.isCompleted}?.let{return@synchronized it}
  require(a.matches(Regex("[0-9]{6,20}")) && password.length in 1..512){"请输入学号和密码"}
  loginState.value=cn.scvtc.campus.NativeLoginState(a,busy=true,message="正在连接学校并核验学生身份…")
  firstSync?.cancel();frameworkRecovery?.cancel();serviceJobs.values.toList().forEach{it.cancel()}
  val job=applicationJobs.async(start=CoroutineStart.LAZY) {
   try {
    val student=networkMutex.withLock {
     val memory=cn.scvtc.campus.OfficialLoginMemory(context)
     memory.rememberInput(a,password)
     val api=cn.scvtc.campus.JwxtApi(headers={memory.apiHeaders(a,it)})
     val verified=cn.scvtc.campus.CasAuthManager(context,api).signIn(a,password) { message ->
      loginState.value=cn.scvtc.campus.NativeLoginState(a,busy=true,message=message)
     }
     acceptVerifiedIdentity(verified)
     verified
    }
    loginState.value=cn.scvtc.campus.NativeLoginState(a,verified=true,message="学校身份已核验，正在后台读取课表与其他数据")
    student
   } catch(e:CancellationException) {
    loginState.value=cn.scvtc.campus.NativeLoginState(a,message="已停止本次登录，原数据保留");throw e
   } catch(e:Exception) {
    val stage=(e as? cn.scvtc.campus.JwxtAuthenticationRequired)?.stage
    val extra=stage in setOf(cn.scvtc.campus.AuthenticationStage.CHALLENGE,cn.scvtc.campus.AuthenticationStage.NOTICE,cn.scvtc.campus.AuthenticationStage.FORM,cn.scvtc.campus.AuthenticationStage.CALLBACK,cn.scvtc.campus.AuthenticationStage.SESSION)
    loginState.value=cn.scvtc.campus.NativeLoginState(a,requiresVerification=extra,
     message=when(stage) {
      cn.scvtc.campus.AuthenticationStage.CHALLENGE->"学校显示了验证码或二次认证，请在学校认证页完成；已填信息保留"
      cn.scvtc.campus.AuthenticationStage.NOTICE->"学校有账号安全提示，请在官方页面确认后继续"
      cn.scvtc.campus.AuthenticationStage.REJECTED->"学校未接受账号或密码，请检查输入后重试"
      cn.scvtc.campus.AuthenticationStage.FORM->"学校登录表单未准备好，请重试；也可打开学校认证页继续"
      cn.scvtc.campus.AuthenticationStage.CALLBACK->"学校认证回调等待超时，请重试；原数据保留"
      cn.scvtc.campus.AuthenticationStage.SESSION->"学校会话尚未建立，可补充认证；原数据保留"
      null->"这次登录未完成，请检查网络或稍后重试；原数据保留"
     })
    throw e
   }
  }
  loginJob=job;taskRunning.value=true
  job.invokeOnCompletion{cause->
   synchronized(jobLock){if(loginJob===job)loginJob=null;updateTaskRunning()}
   if(cause==null)startNativeSync()
  }
  job.start();job
 }
 suspend fun cancelSignIn(){
  val tasks=synchronized(jobLock){listOfNotNull(loginJob,firstSync,frameworkRecovery)+serviceJobs.values.toList()}
  tasks.forEach{it.cancel()};tasks.forEach{it.join()}
 }
 fun canRestoreFrameworkSession():Boolean {
  if(!::context.isInitialized||account.isBlank())return false
  val user=UserManager.getInstance()
  return user.currentSchool?.id=="scvtc"&&user.studentId==account&&
   cn.scvtc.campus.OfficialLoginMemory(context).inputPassword(account).isNotEmpty()
 }
 /** The existing session coordinator owns deduplication and scope installation;
  * this operation uses the same school session and credential store as sync. */
 fun restoreFrameworkSession(a:String,onDone:(Result<String>)->Unit):Job = synchronized(jobLock) {
  val job=applicationJobs.launch(start=CoroutineStart.LAZY) {
   try {
    val cookie=networkMutex.withLock {
     fun current():Boolean=account==a&&UserManager.getInstance().currentSchool?.id=="scvtc"&&UserManager.getInstance().studentId==a
     check(a.isNotBlank()&&current())
     val memory=cn.scvtc.campus.OfficialLoginMemory(context)
     memory.restoreSession(a)
     val api=cn.scvtc.campus.JwxtApi(headers={memory.apiHeaders(a,it)})
     val auth=cn.scvtc.campus.CasAuthManager(context,api)
     val student=cn.scvtc.campus.JwxtSessionManager(api,auth::restoreJwxt).verifiedStudent(a)
     check(current());memory.confirmed(student.account)
     withContext(Dispatchers.Main){check(current());android.webkit.CookieManager.getInstance().getCookie(cn.scvtc.campus.JwxtApi.BASE).orEmpty().also{check(it.isNotBlank())}}
    }
    onDone(Result.success(cookie))
   }catch(e:CancellationException){onDone(Result.failure(e));throw e}
   catch(e:Exception){onDone(Result.failure(e))}
  }
  frameworkRecovery=job;updateTaskRunning()
  job.invokeOnCompletion{synchronized(jobLock){if(frameworkRecovery===job)frameworkRecovery=null;updateTaskRunning()}}
  job.start();job
 }
 /** Identity is durable even when the school calendar is temporarily unavailable. */
 internal suspend fun acceptVerifiedIdentity(student:cn.scvtc.campus.JwxtStudent)=withContext(Dispatchers.Main){
  val a=student.account
  val t=if(a==account)semester else prefs.getString("term:$a","").orEmpty()
  prefs.edit().putString("account",a).putString("semester",t).apply()
  registerVerifiedIdentity(a,t,listOf("studentName","name","xm","姓名")
   .firstNotNullOfOrNull{student.fields[it]?.takeIf(String::isNotBlank)}.orEmpty())
  revision.value++
 }
 fun startNativeSync(mode:String="range"){
  synchronized(jobLock){
   if(taskRunning.value||syncState.value.busy)return
   val a=account;val t=semester
   val job=applicationJobs.launch(start=CoroutineStart.LAZY){
    try{
     check(a.isNotBlank()&&a==account&&t==semester)
     if(t.isBlank()){
      status.value="学校身份已核验，正在读取学期…"
      networkMutex.withLock {
       val memory=cn.scvtc.campus.OfficialLoginMemory(context)
       val api=cn.scvtc.campus.JwxtApi(headers={memory.apiHeaders(a,it)})
       val auth=cn.scvtc.campus.CasAuthManager(context,api)
       val calendar=cn.scvtc.campus.JwxtSessionManager(api,auth::restoreJwxt).withSession(a){api.calendar().first}
       withContext(Dispatchers.Main){check(a==account&&semester.isBlank());confirm(a,calendar.semester);revision.value++}
      }
     }
     check(a==account);synchronize(mode)
    }
    catch(e:CancellationException){throw e}
    catch(e:Exception){status.value="同步未完成；已有数据保留"}
   }
   firstSync=job;taskRunning.value=true
   job.invokeOnCompletion{synchronized(jobLock){if(firstSync===job)firstSync=null;updateTaskRunning()}}
   job.start()
  }
 }
 private fun updateTaskRunning(){taskRunning.value=loginJob?.isCompleted==false||firstSync?.isCompleted==false||frameworkRecovery?.isCompleted==false||serviceJobs.values.any{!it.isCompleted}}
 fun stopNativeSync(){synchronized(jobLock){firstSync?.cancel();serviceJobs.values.toList().forEach{it.cancel()}};status.value="已停止本次同步，已保存的数据保留"}
 private val networkMutex=Mutex()
 fun refreshService(module:String){
  serviceRead(listOf(module))
 }
 fun refreshAcademics(){refreshService("grades");refreshService("credits")}
 /** Awaiting a shared application task never ties its network/save work to a page. */
 private fun serviceRead(modules:List<String>):Deferred<Map<String,Extraction>> = synchronized(jobLock){
  val a=account;val t=semester;val key="$a|$t|${modules.sorted().joinToString(",")}"
  serviceJobs[key]?.takeIf{!it.isCompleted}?:applicationJobs.async(start=CoroutineStart.LAZY){
   try{readServices(modules,a,t)}
   catch(e:CancellationException){throw e}
   catch(e:Exception){status.value="此服务未更新；原缓存保留";throw e}
  }.also{job->
   serviceJobs[key]=job;taskRunning.value=true
   job.invokeOnCompletion{synchronized(jobLock){if(serviceJobs[key]===job)serviceJobs.remove(key);updateTaskRunning()}}
   job.start()
  }
 }
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
 fun initialize(c:Context){context=c.applicationContext;progress=NativeSyncProgress(context);progress.restore(account,semester);db=Room.databaseBuilder(context,ScvtcDatabase::class.java,"scvtc-native.db").addMigrations(object:androidx.room.migration.Migration(1,2){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS school_services (account TEXT NOT NULL, semester TEXT NOT NULL, module TEXT NOT NULL, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(account,semester,module))")}}).build()}
 private val prefs get()=context.getSharedPreferences("scvtc_profile",Context.MODE_PRIVATE)
 val account get()=prefs.getString("account","").orEmpty()
 suspend fun hasLocalRecords():Boolean=db.schedules().hasLocalRecords()
 val semester get()=prefs.getString("semester","").orEmpty()
 fun confirm(account:String,term:String){require(account.matches(Regex("[0-9]{6,20}"))){"请确认当前官方账号的学号"};require(term.matches(Regex("20[0-9]{2}-20[0-9]{2}-[12]"))){"请确认学校页面中的学期"};prefs.edit().putString("account",account).putString("semester",term).putString("term:$account",term).apply()}
 /** Persist native login eligibility before course reading, so closing the page
  * or restarting the process cannot strand the first authenticated sync. */
 internal fun registerVerifiedIdentity(a:String,t:String,name:String){
  require(a.isNotBlank()&&a==account&&t==semester){"官方身份与当前账号/学期不一致"}
  val user=UserManager.getInstance();val previousId=user.studentId
  user.currentSchool=user.getSchoolById("scvtc");user.studentId=a
  if(name.isNotBlank() || previousId!=a)user.studentName=name
  if(!user.isLoggedIn||user.sessionState.state.value.expired||previousId!=a)
   user.saveWebViewLogin(android.webkit.CookieManager.getInstance().getCookie(School.ORIGIN).orEmpty())
 }
 fun selectNativeAccount(a:String){
  if(!::context.isInitialized || !a.matches(Regex("[0-9]{6,20}")) || a==account)return
  val previous=account;val previousTerm=semester
  if(previous.isNotBlank()){
   if(previousTerm.isNotBlank())prefs.edit().putString("term:$previous",previousTerm).apply()
   clearAuthentication(previous,forgetCredentials=false)
  }
  prefs.edit().putString("account",a).putString("semester",prefs.getString("term:$a","")).remove("lastAttempt").apply()
 }
 suspend fun snapshot():ScvtcSnapshot?=db.schedules().read(account,semester)
 private fun scope(a:String,t:String,m:String)="$a|$t|$m"
 private fun decode(a:String,t:String,m:String,payload:String):Extraction?=try {
  json.decodeFromString<Extraction>(RecipeVault.openPayload(scope(a,t,m),payload))
 }catch(e:cn.scvtc.campus.LocalCipher.Unavailable){
  val recoveryKey="unreadable-encrypted:$a:$t:$m"
  if(!prefs.contains(recoveryKey))check(prefs.edit().putString(recoveryKey,payload).commit()){"未能保留原加密记录，请重试"}
  status.value=e.message.orEmpty()
  null
 }
 private fun encode(value:Extraction)=RecipeVault.sealPayload(scope(value.account,value.semester,value.module),json.encodeToString(value))
 suspend fun extraction():Extraction?=snapshot()?.let{decode(it.account,it.semester,"schedule",it.payload)}
 suspend fun save(next:Extraction,recipe:QueryRecipe?=null,finishBatch:Boolean=true,valid:()->Boolean={true}):Extraction = mutex.withLock {
  val a=next.account;val t=next.semester
  fun current()=a==account && t==semester && valid()
  require(current()){"账号或学期已经切换"}
  require(next.meetings.isNotEmpty() || next.emptyConfirmed){next.warnings.joinToString("；").ifBlank{"页面结构与解析规则不匹配"}}
  require(next.warnings.isEmpty()){next.warnings.joinToString("；")}
  check(valid()){"页面已切换，旧查询未保存"}
  val old=db.schedules().read(a,t)
  val previous=old?.let{decode(a,t,"schedule",it.payload)}
  val merged=MergeRules.merge(previous,next)
  // Durable rollback checkpoint is written before the replacement transaction.
  if(previous?.meetings?.isNotEmpty()==true)check(prefs.edit().putString(backupKey(a,t),encode(previous)).putLong(backupKey(a,t)+":time",old!!.fetchedAt).commit()){"未能备份原课表，取消覆盖"}
  val sealed=recipe?.takeIf{it.coverageWeeks.containsAll((1..28).toList()) && ScvtcSchoolAdapter().validateReadRecipe(it)}?.let{RecipeVault.seal(json.encodeToString(it))}?:old?.recipe.orEmpty()
  val fetchedAt=System.currentTimeMillis()
  db.withTransaction {
   check(current()){"账号、学期或查询已改变"}
   db.schedules().put(ScvtcSnapshot(a,t,encode(merged),fetchedAt,sealed))
   check(current())
  }
  check(current())
  project(merged,finishBatch,fetchedAt)
  withContext(Dispatchers.Main){revision.value++;status.value="已保存 ${merged.meetings.size} 个课程安排 · 已读取 ${merged.coverageWeeks.size} 周"}
  cn.scvtc.campus.CloudBackup.get(context).publish(merged,System.currentTimeMillis())
  merged
 }
 suspend fun service(module:String,term:String=semester):Extraction?=db.services().read(account,term,module)?.let{decode(it.account,it.semester,it.module,it.payload)}
 suspend fun allGrades():Extraction?=service("grades","all")?:service("grades")
 suspend fun serviceSavedAt(module:String,a:String=account,t:String=semester):Long=db.services().read(a,t,module)?.fetchedAt?:0
 private val serviceStatus get()=ServiceReadStatusStore(context.getSharedPreferences("school_service_status",0))
 internal suspend fun serviceStatuses(a:String=account,t:String=semester):List<ServiceReadStatus> =
  (listOf("authentication","schedule")+cn.scvtc.campus.JwxtServices.endpoints.keys).map{module->
   val savedAt=when(module){"authentication"->0L;"schedule"->db.schedules().read(a,t)?.fetchedAt?:0L;else->db.services().read(a,t,module)?.fetchedAt?:0L}
   serviceStatus.read(a,t,module,savedAt)
  }
 suspend fun saveService(next:Extraction,replace:Boolean,valid:()->Boolean):Extraction=mutex.withLock{
  require(next.module!="schedule"&&next.account==account&&next.semester==semester)
  require(next.warnings.isEmpty() && (next.records.isNotEmpty()||next.links.isNotEmpty()||next.full&&next.emptyConfirmed)){"服务页尚无可识别记录，原记录保留"}
  val a=next.account;val t=next.semester
  val previous=if(replace)null else db.services().read(a,t,next.module)?.let{decode(a,t,next.module,it.payload)}
  val merged=MergeRules.merge(previous,next)
  val fetchedAt=System.currentTimeMillis()
  val copies=if(next.module=="grades")listOf(merged.copy(semester="all"))+
   (merged.records.mapNotNull{it.fields["学期"]?.takeIf(String::isNotBlank)}+t).distinct().map{term->
    val rows=merged.records.filter{it.fields["学期"]==term}
    merged.copy(semester=term,records=rows,emptyConfirmed=rows.isEmpty())
   }else listOf(merged)
  val encrypted=copies.map{data->ScvtcServiceSnapshot(a,data.semester,data.module,encode(data),fetchedAt)}
  db.withTransaction{check(valid() && account==a && semester==t);encrypted.forEach{db.services().put(it)}}
  withContext(Dispatchers.Main){revision.value++;status.value="已保存 ${merged.records.size} 条服务记录"};merged
 }
 private fun backupKey(a:String,t:String)="last-good:$a:$t"
 suspend fun restoreLastGood():Boolean = mutex.withLock {
  val a=account;val t=semester
  if(a.isBlank()||t.isBlank())return@withLock false
  val saved=db.schedules().read(a,t)
  val local=saved?.let{runCatching{decode(a,t,"schedule",it.payload)}.getOrNull()}
  if(local!=null && ScvtcScheduleProjection.canRestore(local,a,t)){
   if(a==account && t==semester){project(local,true,saved.fetchedAt);withContext(Dispatchers.Main){revision.value++}}
   return@withLock false
  }
  val backup=prefs.getString(backupKey(a,t),null)?:return@withLock false
  val e=runCatching{decode(a,t,"schedule",backup)}.getOrNull()?:return@withLock false
  if(e.account!=a||e.semester!=t||e.meetings.isEmpty())return@withLock false
  ScheduleWriteGuard.validate(null,e)
  val fetchedAt=prefs.getLong(backupKey(a,t)+":time",0L)
  db.withTransaction{check(a==account&&t==semester);db.schedules().put(ScvtcSnapshot(a,t,encode(e),fetchedAt,saved?.recipe.orEmpty()))}
  if(a==account&&t==semester){project(e,true,fetchedAt);withContext(Dispatchers.Main){revision.value++;status.value="已恢复上一份有效课表"}};true
 }
 private suspend fun project(e:Extraction,finishBatch:Boolean,fetchedAt:Long)=withContext(Dispatchers.IO){
  val user=UserManager.getInstance()
  val active=user.currentSchool?.id=="scvtc" && user.studentId==e.account
  val key=if(active)user.currentAccountStorageKey else "scvtc__${e.account}"
  val entries=e.meetings.map{AcademicScheduleEntry(it.name,it.teacher,it.room,it.day,it.startNode,it.endNode,it.weeks.joinToString("、"),it.stableId())}
  ScvtcScheduleProjection.save(ScheduleCacheStore(context.getSharedPreferences("schedule_cache",Context.MODE_PRIVATE)),key,e,fetchedAt)
  if(finishBatch && active)withContext(Dispatchers.Main){ScheduleWidgetUpdater.update(context);ScheduleJson.parse(AcademicStudyBridge.scheduleJson(entries))?.let{ScheduleReminderScheduler.get(context).updateSnapshot(key,e.semester,it.map{entry->entry.course})}}
 }
 internal suspend fun synchronizeBackground(attempt:Int){
  val a=account;val t=semester
  val modules=serviceRetryModules(attempt,db.schedules().read(a,t)?.fetchedAt?:0L,serviceStatuses(a,t))
  if(modules.isEmpty())synchronize()else readServices(modules,a,t)
 }
 suspend fun synchronize(mode:String="range"):Extraction=networkMutex.withLock {
  val requestedAccount=account;val requestedSemester=semester
  require(requestedAccount.isNotBlank() && requestedSemester.isNotBlank()){"请先打开官方课表并确认账号和学期"}
  progress.begin(requestedAccount,requestedSemester,cachedAt=snapshot()?.fetchedAt?:0)
  status.value="正在同步课表"
  var activeModule="authentication"
  serviceStatus.begin(requestedAccount,requestedSemester,activeModule)
  try {
   restoreLastGood()
   if(extraction()==null)cn.scvtc.campus.CloudBackup.get(context).restore(requestedAccount,requestedSemester)?.let{save(it)}
   require(mode in setOf("range","weekly"))
   val login=cn.scvtc.campus.OfficialLoginMemory(context)
   progress.phase(NativeSyncStage.AUTHENTICATING,"正在认证学校账号…")
   login.restoreSession(requestedAccount)
   val api=cn.scvtc.campus.JwxtApi(headers={url->login.apiHeaders(requestedAccount,url)})
   val auth=cn.scvtc.campus.CasAuthManager(context,api)
   val session=cn.scvtc.campus.JwxtSessionManager(api,auth::restoreJwxt)
   session.withSession(requestedAccount){student->
    fun current()=requestedAccount==account&&requestedSemester==semester
    check(current()){"账号或学期已切换"}
    login.confirmed(requestedAccount)
    serviceStatus.saved(requestedAccount,requestedSemester,"authentication",System.currentTimeMillis())
    activeModule="schedule";serviceStatus.begin(requestedAccount,requestedSemester,activeModule)
    progress.phase(NativeSyncStage.READING,"正在读取真实课表…")
    val (calendar,weeks)=api.calendar()
    require(calendar.semester==requestedSemester){"官方学期已改变，请确认新学期；原课表保留"}
    saveService(Extraction("studentInfo",requestedAccount,requestedSemester,source=cn.scvtc.campus.JwxtApi.IDENTITY,
     records=listOf(NativeRecord("学生基本信息",student.fields))),true,::current)
    val ranges=if(mode=="weekly")weeks.map{listOf(it)}else listOf(weeks)
    var result:Extraction?=null
    for((index,range)in ranges.withIndex()){
     currentCoroutineContext().ensureActive();check(current()){"账号或学期已切换"}
     val response=api.schedule(requestedAccount,requestedSemester,range)
     progress.phase(NativeSyncStage.WRITING,"正在保存课表…")
     result=save(response.extraction,response.recipe,index==ranges.lastIndex,::current)
     progress.committed(snapshot()?.fetchedAt?:error("课表事务未完成"))
     serviceStatus.saved(requestedAccount,requestedSemester,"schedule",snapshot()?.fetchedAt?:0L)
     progress.phase(NativeSyncStage.READING,"正在读取后续教务数据…")
    }
    withContext(Dispatchers.Main){
     check(current())
     val base=ScheduleRepository(context).timeBase(UserManager.getInstance().currentAccountStorageKey,requestedSemester,requestedSemester)
     if(base.firstWeekDate.isBlank())setFirstMonday(calendar.firstMonday)
     status.value="课表和学生信息已保存，正在同步成绩与考试"
    }
    val services=readServiceBatch(cn.scvtc.campus.JwxtServices.endpoints.keys.toList(),{module->
     currentCoroutineContext().ensureActive();check(current())
     activeModule=module;serviceStatus.begin(requestedAccount,requestedSemester,module)
     progress.phase(NativeSyncStage.READING,"正在读取${School.service(module)?.title?:"教务数据"}…")
      saveService(api.service(module,requestedAccount,requestedSemester),true,::current)
      serviceStatus.saved(requestedAccount,requestedSemester,module,serviceSavedAt(module,requestedAccount,requestedSemester))
    },{module,e->
      serviceStatus.failed(requestedAccount,requestedSemester,module,e)
    })
    activeModule=""
    status.value="已保存 ${weeks.size} 周课表与学生信息；${services.saved.size} 项服务完成"+if(services.failed.isNotEmpty())"，${services.failed.size} 项未完成，原缓存保留"else""
    if(!services.complete)progress.partial("课表已更新；${services.failed.joinToString("、"){School.service(it)?.title?:"教务数据"}}未更新，保留上次记录")
    services.retryableFailure?.let { throw it }
    if(services.complete)progress.complete()
    result?:error("课表读取未完成")
   }
  }
  catch(e:CancellationException){progress.phase(NativeSyncStage.CACHE,"已停止 · 使用本机缓存");throw e}
  catch(e:Exception){
   if(activeModule.isNotBlank())serviceStatus.failed(requestedAccount,requestedSemester,activeModule,e)
   if(e is cn.scvtc.campus.JwxtAuthenticationRequired)serviceStatus.failed(requestedAccount,requestedSemester,"authentication",e)
   if(progress.state.value.stage!=NativeSyncStage.PARTIAL){progress.failure(e);status.value="同步未完成，已保留离线课表"}
   throw e
  }
 }
 suspend fun synchronizeService(module:String):Extraction=serviceRead(listOf(module)).await().getValue(module)
 private suspend fun readServices(modules:List<String>,a:String,t:String):Map<String,Extraction> = networkMutex.withLock{
  require(modules.isNotEmpty()&&modules.all{it in cn.scvtc.campus.JwxtServices.endpoints})
  require(a.isNotBlank()&&t.isNotBlank()){"请先连接学校教务"}
  check(a==account&&t==semester){"账号或学期已切换"}
  progress.begin(a,t,if(modules.size>1)"教务数据"else School.service(modules.single())?.title?:"教务数据",serviceSavedAt(modules.first(),a,t))
  progress.phase(NativeSyncStage.AUTHENTICATING,"正在认证学校账号…")
  serviceStatus.begin(a,t,"authentication")
  try {
  val login=cn.scvtc.campus.OfficialLoginMemory(context);login.restoreSession(a)
  val api=cn.scvtc.campus.JwxtApi(headers={url->login.apiHeaders(a,url)})
  val auth=cn.scvtc.campus.CasAuthManager(context,api)
  cn.scvtc.campus.JwxtSessionManager(api,auth::restoreJwxt).withSession(a){
   check(a==account&&t==semester)
   login.confirmed(a)
   serviceStatus.saved(a,t,"authentication",System.currentTimeMillis())
   progress.phase(NativeSyncStage.READING,"正在读取真实教务数据…")
   val results=linkedMapOf<String,Extraction>()
   val batch=readServiceBatch(modules,{module->
    currentCoroutineContext().ensureActive();check(a==account&&t==semester)
    serviceStatus.begin(a,t,module)
    progress.phase(NativeSyncStage.READING,"正在读取${School.service(module)?.title?:"教务数据"}…")
    val result=api.service(module,a,t)
    progress.phase(NativeSyncStage.WRITING,"正在保存教务数据…")
    results[module]=saveService(result,true){a==account&&t==semester}
    progress.committed(serviceSavedAt(module,a,t))
    serviceStatus.saved(a,t,module,serviceSavedAt(module,a,t))
   },{module,e->serviceStatus.failed(a,t,module,e)})
   if(!batch.complete){
    if(batch.saved.isNotEmpty())progress.partial("${batch.saved.joinToString("、"){School.service(it)?.title?:it}}已更新；其余保留上次记录")
    throw batch.retryableFailure?:IllegalStateException("部分教务数据未更新，原缓存保留")
   }
   progress.complete();status.value=modules.joinToString("、"){School.service(it)?.title?:"教务数据"}+"已更新"
   results
  }
  }catch(e:CancellationException){progress.phase(NativeSyncStage.CACHE,"已停止 · 使用本机缓存");throw e}
  catch(e:Exception){
   if(e is cn.scvtc.campus.JwxtAuthenticationRequired)serviceStatus.failed(a,t,"authentication",e)
   if(progress.state.value.stage!=NativeSyncStage.PARTIAL)progress.failure(e)
   throw e
  }
 }
 fun cachedSemesters():List<String> = org.json.JSONArray(prefs.getString("officialTerms:$account","[]")).let { list ->
  (0 until list.length()).map { list.getString(it) }
 }
 suspend fun officialSemesters():List<String> = networkMutex.withLock {
  val a=account;val t=semester;require(a.isNotBlank()&&t.isNotBlank())
  val cacheKey="officialTerms:$a"
  val cached=runCatching{org.json.JSONArray(prefs.getString(cacheKey,"[]")).let{list->(0 until list.length()).map{list.getString(it)}}}.getOrDefault(emptyList())
  try {
   val login=cn.scvtc.campus.OfficialLoginMemory(context);login.restoreSession(a)
   val api=cn.scvtc.campus.JwxtApi(headers={url->login.apiHeaders(a,url)})
   val auth=cn.scvtc.campus.CasAuthManager(context,api)
   cn.scvtc.campus.JwxtSessionManager(api,auth::restoreJwxt).withSession(a){
    check(a==account&&t==semester)
    val terms=api.semesters();prefs.edit().putString(cacheKey,org.json.JSONArray(terms).toString()).apply();terms
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){
   check(a==account&&t==semester)
   if(cached.isNotEmpty())cached else listOf(t)
  }
 }
 fun setFirstMonday(date:java.time.LocalDate){
  require(date.dayOfWeek==java.time.DayOfWeek.MONDAY)
  val key=UserManager.getInstance().currentAccountStorageKey;require(account.isNotBlank()&&semester.isNotBlank())
  val base=ScheduleRepository(context).timeBase(key,semester,semester)
  ScheduleReminderScheduler.get(context).updateTimeBase(key,semester,base.copy(firstWeekDate=date.toString()))
  ScheduleWidgetUpdater.update(context);revision.value++
 }
 @JvmOverloads
 fun clearAuthentication(a:String,forgetCredentials:Boolean=true){
  if(!::context.isInitialized || !::db.isInitialized)return
  synchronized(jobLock){loginJob?.cancel();frameworkRecovery?.cancel()}
  stopNativeSync()
  if(forgetCredentials)cn.scvtc.campus.OfficialLoginMemory(context).forget(a)
  ScvtcSyncWork.cancel(context)
  applicationJobs.launch(Dispatchers.Main.immediate){
   ScvtcWebSession.close()
   android.webkit.CookieManager.getInstance().removeAllCookies{android.webkit.CookieManager.getInstance().flush()}
   android.webkit.WebStorage.getInstance().deleteAllData()
  }
  if(forgetCredentials)applicationJobs.launch{mutex.withLock{db.schedules().clearRecipes(a)}}
  if(account==a)prefs.edit().remove("account").remove("semester").remove("lastAttempt").apply()
  revision.value++;status.value="已退出，账号离线内容保留在本机"
 }
 fun logout(){UserManager.getInstance().clearLoginState()}
}
class ScvtcNativeAdapter(private val key:String):AcademicStudyAdapter {
 private fun checkScope(){val user=UserManager.getInstance();require(user.currentSchool?.id=="scvtc" && user.studentId==ScvtcRuntime.account && key==user.currentAccountStorageKey){"账号已经切换，请确认当前官方账号与学期"}}
 override suspend fun catalog():AcademicStudyCatalog {checkScope();val term=ScvtcRuntime.semester;if(term.isBlank())throw AcademicException(AcademicStatus.SESSION_EXPIRED,"请先打开川职官方课表完成登录和同步");val terms=ScvtcRuntime.officialSemesters();checkScope();return AcademicStudyCatalog(terms.map{AcademicTerm(it)},AcademicTerm(term))}
 override suspend fun schedule(term:AcademicTerm):List<AcademicScheduleEntry>{checkScope();require(term.id==ScvtcRuntime.semester){"请在官方页选择所需学期"};val e=ScvtcRuntime.synchronize();return e.meetings.map{AcademicScheduleEntry(it.name,it.teacher,it.room,it.day,it.startNode,it.endNode,it.weeks.joinToString("、"),it.stableId())}}
 private suspend fun records(module:String,term:AcademicTerm?):List<NativeRecord>{
  checkScope()
  val saved=ScvtcRuntime.synchronizeService(module)
  checkScope()
  return saved.records.filter{term==null || it.fields["学期"].isNullOrBlank() || it.fields["学期"]==term.id}
 }
 private fun NativeRecord.field(vararg names:String):String = names.firstNotNullOfOrNull { name -> fields.entries.firstOrNull{it.key.replace(" ","")==name}?.value?.takeIf{it.isNotBlank()} }.orEmpty()
 suspend fun cachedGrades():AcademicGradeReport? {
  checkScope()
  val saved=ScvtcRuntime.allGrades() ?: return null
  checkScope()
  return gradeReport(saved.records)
 }
 override suspend fun grades(term:AcademicTerm?):AcademicGradeReport = gradeReport(records("grades",term))
 private fun gradeReport(records:List<NativeRecord>):AcademicGradeReport {
  val values=records.mapNotNull{r->
   val name=r.field("课程名称","课程名","名称");val score=r.field("成绩","总评成绩","最终成绩","分数")
   if(name.isBlank()||score.isBlank())null else AcademicGrade(name,score,r.field("学分"),r.field("绩点","课程绩点"),type=r.field("课程性质","课程类型","修读类型"),term=r.field("学期","学年学期"),code=r.field("课程代码","课程编号","课程号"),detail=r.fields.entries.joinToString("\n"){it.key+"："+it.value})
  }
  return AcademicGradeReport(values,totalCredits=cn.scvtc.campus.core.CreditSummary.from(records).display)
 }
 override suspend fun exams(term:AcademicTerm):List<AcademicExam> {
  val values=records("exams",term).mapNotNull{r->
   val name=r.field("课程名称","课程名","考试科目");val time=r.field("考试时间","时间","考试日期")
   if(name.isBlank()||time.isBlank())null else AcademicExam(name,time,r.field("考试地点","考场","地点","教室"),seat=r.field("座位号","座号"),examName=r.field("考试名称","考试类型"))
  }
  return values
 }
}
