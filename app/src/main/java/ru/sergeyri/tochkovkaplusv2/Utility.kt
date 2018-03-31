package ru.sergeyri.tochkovkaplusv2

import android.accounts.Account
import android.accounts.AccountManager
import android.app.*
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.support.v4.view.ViewPager
import android.support.v7.widget.LinearLayoutManager
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.*

import com.sergeyri.tpcore.*

import org.json.simple.parser.JSONParser
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by sergeyri on 8/27/17.
 */
const val PROVIDER_KEY_ADDRESS = "content://ru.sergeyri.tp2.unlocked/access"
const val PROVIDER_KEY = "tp2_key"
const val PREMIUMKEY_GOOGLEPLAY_ADDR = "market://details?id=ru.sergeyri.tochkovkapluskeyv2"
const val HOWTO_1 = "https://youtu.be/k99DdBuHz5s"
const val HOWTO_2 = "https://youtu.be/P6XvuVwwbuI"

const val FIRST_OPEN_KEY = "first_open"
const val USER_EMAIL_KEY = "user_email"

const val INTERNALFILE = "intsys.json"
const val EXTERNALDIR = ".xtpsys"
const val EXTERNALFILE = ".extsys.json"
const val TRIAL_MAX = 864000000

const val TP_PREFS = "tp_prefs"
const val TP_PREFS_UNIV = "${TP_PREFS}_univ"
const val TP_PREFS_UNIV_GROUPS = "${TP_PREFS_UNIV}_groups"
const val TP_PREFS_RW = "${TP_PREFS}_rw"
const val TP_PREFS_RW_GOST270875 = "${TP_PREFS_RW}_gost270875"
const val TP_PREFS_RW_GOST270875_GROUPS = "${TP_PREFS_RW_GOST270875}_groups"
const val TP_PREFS_RW_ISO448083 = "${TP_PREFS_RW}_iso448083"
const val TP_PREFS_RW_ISO448083_GROUPS = "${TP_PREFS_RW_ISO448083}_groups"
const val SHEETNAMES = "sheetnames"
const val REGEX_PATTERN_FOR_TITLE = "[\\w\\s\\,\\:\\;\\-\\.\\(\\)\\#\\№]+"
const val REGEX_PATTERN_FOR_UNIT = "[\\w]{1,9}"

var fb_user_name = "unknown"

fun log(msg: String){ Log.d("Talitha", msg) }

fun toast(context: Context, msgResId: Int, len: Int = Toast.LENGTH_SHORT){ Toast.makeText(context, msgResId, len).show() }
fun toast(context: Context, msg: String, len: Int = Toast.LENGTH_SHORT){ Toast.makeText(context, msg, len).show() }

fun vibrate(context: Context, time: Long){
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
        vibrator.vibrate(VibrationEffect.createOneShot(time, VibrationEffect.DEFAULT_AMPLITUDE))
    } else{
        vibrator.vibrate(time)
    }
}

class DelayAutoCompleteTextView : AutoCompleteTextView {
    companion object {const val DEFAULT_AUTOCOMPLETE_DELAY: Long = 750}

    interface OnFilterStateListener{
        fun onPerformFiltering()
        fun onFilterComplete(count: Int)
    }

    private var mAutoCompleteDelay: Long = DEFAULT_AUTOCOMPLETE_DELAY
    lateinit var loadingIndicator: ProgressBar
    var mTimer: Timer? = null
    var mOnFilterStateListener: OnFilterStateListener? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun performFiltering(text: CharSequence?, keyCode: Int) {
        mOnFilterStateListener?.onPerformFiltering()
        loadingIndicator.visibility = View.VISIBLE
        if(mTimer != null){
            mTimer!!.cancel()
            mTimer!!.purge()
            mTimer = null
        }
        mTimer = Timer()
        mTimer?.schedule(FilterTimer({_: CharSequence?, _: Int -> super.performFiltering(text, keyCode)}), mAutoCompleteDelay)
    }

    override fun onFilterComplete(count: Int) {
        loadingIndicator.visibility = View.GONE
        mOnFilterStateListener?.onFilterComplete(count)
        super.onFilterComplete(count)
    }

    inner class FilterTimer(private val func:(CharSequence?, Int) -> Unit) : TimerTask(){
        override fun run() {
            func(null, 0)
        }
    }

}



class RWI(private val mContext: Context, private val mStandart: String){
    enum class St(val titleRes: Int){GOST_2708_75(R.string.gost_2708_75_title), ISO_4480_83(R.string.iso_4480_83_title)}
    companion object {
        const val KEY_STANDART: String = "rw_standart"
        const val KEY_LENGTH: String = "rw_length"
        const val KEY_ODDEVEN_FILTER = "rw_oddeven_filter"
        const val ALL_COMPONENTS = 0
        const val EVEN_COMPONENTS = 1
        const val ODD_COMPONENTS = 2
        val GOST_2708_75_LEN: FloatArray = floatArrayOf(1.0F,1.1F,1.2F,1.3F,1.4F,1.5F,1.6F,1.7F,1.8F,1.9F,2.0F,2.1F,2.2F,2.25F,2.3F,2.4F,2.5F,2.6F,2.7F,2.75F,2.8F,2.9F,3.0F,3.1F,3.2F,3.25F,3.3F,3.4F,3.5F,3.6F,3.7F,3.75F,3.8F,3.9F,4.0F,4.1F,4.2F,4.25F,4.3F,4.4F,4.5F,4.6F,4.7F,4.75F,4.8F,4.9F,5.0F,5.1F,5.2F,5.25F,5.3F,5.4F,5.5F,5.6F,5.7F,5.75F,5.8F,5.9F,6.0F,6.1F,6.2F,6.25F,6.3F,6.4F,6.5F,6.6F,6.7F,6.75F,6.8F,6.9F,7.0F,7.1F,7.2F,7.25F,7.3F,7.4F,7.5F,7.6F,7.7F,7.75F,7.8F,7.9F,8.0F,8.1F,8.2F,8.25F,8.3F,8.4F,8.5F,8.6F,8.7F,8.75F,8.8F,8.9F,9.0F,9.1F,9.2F,9.25F,9.3F,9.4F,9.5F)
        val ISO_4480_83_LEN: FloatArray = floatArrayOf(1.0F,1.5F,2.0F,2.5F,3.0F,3.5F,4.0F,4.5F,5.0F,5.5F,6.0F,6.5F,7.0F,7.5F,8.0F,8.5F,9.0F,9.5F)
    }

    val standartDataJson: org.json.simple.JSONObject

    init {standartDataJson = getStandartData()}

    fun getStandartData(): org.json.simple.JSONObject {
        val stFileName: String? = when(mStandart){
            St.GOST_2708_75.name -> "gost_2708_75.json"
            St.ISO_4480_83.name -> "iso_4480_83.json"
            else -> ""
        }

        val inputStream: InputStream = mContext.assets.open(stFileName)
        val jsonParser = JSONParser()
        return jsonParser.parse(InputStreamReader(inputStream, "UTF-8")) as org.json.simple.JSONObject
    }

    fun getCurrentLengthArray(): FloatArray {
        return when(mStandart){
            St.GOST_2708_75.name -> GOST_2708_75_LEN
            St.ISO_4480_83.name -> ISO_4480_83_LEN
            else -> floatArrayOf()
        }
    }

    fun getMinLen(): Float {
        return when(mStandart){
            St.GOST_2708_75.name -> GOST_2708_75_LEN[0]
            St.ISO_4480_83.name -> ISO_4480_83_LEN[0]
            else -> 0.0F
        }
    }

    fun getMaxLen(): Float {
        return when(mStandart){
            St.GOST_2708_75.name -> GOST_2708_75_LEN[GOST_2708_75_LEN.lastIndex]
            St.ISO_4480_83.name -> ISO_4480_83_LEN[ISO_4480_83_LEN.lastIndex]
            else -> 0.0F
        }
    }

    fun getMinDiameter(): Float {
        val keyList = standartDataJson.keys.toMutableList()
        keyList.sortBy { it.toString().toInt() }
        return keyList.last().toString().toFloat()
    }

    fun getMaxDiameter(): Float {
        val keyList = standartDataJson.keys.toMutableList()
        keyList.sortBy { it.toString().toInt() }
        return keyList[0].toString().toFloat()
    }

    fun getDiapazoneDiameterOf(diam: Float): IntArray {
        val result: IntArray = intArrayOf(-1, -1) // индексы min и max
        val keyList = standartDataJson.keys.toMutableList()
        keyList.sortBy { it.toString().toInt() }
        val diameterList: MutableList<Float> = mutableListOf()
        keyList.forEach {
            diameterList.add(it.toString().toFloat())
        }

        if(diam >= diameterList.first().toString().toFloat() && diam <= diameterList[diameterList.lastIndex].toString().toFloat()){
            var minRes = diameterList[diameterList.lastIndex].toString().toFloat()
            diameterList.forEach {
                val res = diam - it.toString().toFloat()
                if(res >= 0 && res < minRes) minRes = res
            }
            result[0] = diameterList.indexOf(diam-minRes)
            result[1] = if(minRes > 0 && result[0] < diameterList.lastIndex) result[0]+1 else result[0]
        }
        return result
    }

    fun getDiapazoneLenOf(len: Float): IntArray {
        val result: IntArray = intArrayOf(-1, -1) // индексы min и max
        val lengths: FloatArray? = when(mStandart){
            St.GOST_2708_75.name -> GOST_2708_75_LEN
            St.ISO_4480_83.name -> ISO_4480_83_LEN
            else -> null
        }

        if(lengths != null){
            if(len >= lengths.first() && len <= lengths[lengths.lastIndex]){
                var minRes = lengths[lengths.lastIndex]
                lengths.forEach {
                    val res = len - it
                    if(res >= 0 && res < minRes) minRes = res
                }
                result[0] = lengths.indexOf(len-minRes)
                result[1] = if(minRes > 0 && result[0] < lengths.lastIndex) result[0]+1 else result[0]
            }
        }
        return result
    }

    fun getInterpolatedCapAtLen(len: Float, minLength: Float, maxLength: Float, minCapacity: Double, maxCapacity: Double): Double {
        return if (minCapacity == maxCapacity) minCapacity else minCapacity + (len - minLength) / (maxLength - minLength) * (maxCapacity - minCapacity) / 1
    }

    fun getInterpolatedCapAtDiam(diam: Float, minDiameter: Float, maxDiameter: Float, minCapacity: Double, maxCapacity: Double): Double {
        return if (minCapacity == maxCapacity) minCapacity else minCapacity + (diam - minDiameter) / (maxDiameter - minDiameter) * (maxCapacity - minCapacity) / 1
    }

    fun getCapacityForILen(diameter: Int, length: Float): Double {
        var icap = 0.0
        val vols: org.json.simple.JSONArray = standartDataJson[diameter.toString()] as org.json.simple.JSONArray
        val lenDiapazone = getDiapazoneLenOf(length)
        if(lenDiapazone[0] != -1 && lenDiapazone[1] != -1){
            val lenArray = getCurrentLengthArray()
            val minLen = lenArray[lenDiapazone[0]]
            val maxLen = lenArray[lenDiapazone[1]]
            val minCap = vols[lenDiapazone[0]] as Double
            val maxCap = vols[lenDiapazone[1]] as Double
            icap = getInterpolatedCapAtLen(length, minLen, maxLen, minCap, maxCap).round()
        }
        return icap
    }

    fun getCapacityICapILen(diameter: Float, length: Float): Double {
        var icap = 0.0

        val diams = getDiapazoneDiameterOf(diameter)
        if(diams[0] != -1 && diams[1] != -1){
            val keyList = standartDataJson.keys.toMutableList()
            keyList.sortBy { it.toString().toInt() }
            val diameterMap: MutableMap<String, org.json.simple.JSONArray> = mutableMapOf()
            keyList.forEach {
                diameterMap[it.toString()] = standartDataJson[it.toString()] as org.json.simple.JSONArray
            }

            val minDiam = keyList[diams[0]]
            val maxDiam = keyList[diams[1]]
            val icaps: DoubleArray = doubleArrayOf(-1.0, -1.0)

            val volsDiam0: org.json.simple.JSONArray = diameterMap[minDiam.toString()]!!
            val lenDiapazoneDiam0 = getDiapazoneLenOf(length)
            if(lenDiapazoneDiam0[0] != -1 && lenDiapazoneDiam0[1] != -1){
                val lenArray = getCurrentLengthArray()
                val minLen = lenArray[lenDiapazoneDiam0[0]]
                val maxLen = lenArray[lenDiapazoneDiam0[1]]
                val minCap = volsDiam0[lenDiapazoneDiam0[0]] as Double
                val maxCap = volsDiam0[lenDiapazoneDiam0[1]] as Double
                icaps[0] = getInterpolatedCapAtLen(length, minLen, maxLen, minCap, maxCap).round()
            }

            val volsDiam1: org.json.simple.JSONArray = diameterMap[maxDiam.toString()]!!
            val lenDiapazoneDiam1 = getDiapazoneLenOf(length)
            if(lenDiapazoneDiam1[0] != -1 && lenDiapazoneDiam1[1] != -1){
                val lenArray = getCurrentLengthArray()
                val minLen = lenArray[lenDiapazoneDiam1[0]]
                val maxLen = lenArray[lenDiapazoneDiam1[1]]
                val minCap = volsDiam1[lenDiapazoneDiam1[0]] as Double
                val maxCap = volsDiam1[lenDiapazoneDiam1[1]] as Double
                icaps[1] = getInterpolatedCapAtLen(length, minLen, maxLen, minCap, maxCap).round()
            }

            icap = getInterpolatedCapAtDiam(diameter, minDiam.toString().toFloat(), maxDiam.toString().toFloat(), icaps[0], icaps[1]).round()
        }
        return icap
    }
}

class IncrementorView : TextView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setBg(color: Int){
        val drawable: GradientDrawable =  this.background as GradientDrawable
        drawable.setColor(color)
    }

    fun setTxt(color: Int){
        this.setTextColor(color)
    }
}

fun EditText.setInputListener(listener: EditTextWatcher){
    if(!(listener.isActive)){
        this.addTextChangedListener(listener)
        listener.isActive = true
    }
}



fun String.checkPattern(pattern: String): Boolean {
    return matches(pattern.toRegex())
}

fun Long.toDateTime(dateFormat: String): String {
    val formatter = SimpleDateFormat(dateFormat)
    val calendar: Calendar = Calendar.getInstance().also { it.timeInMillis = this }
    return formatter.format(calendar.time)
}

class BlockLinearLayoutManager(context: Context) : LinearLayoutManager(context){
    var scrollEnabled = true
    fun scrollEnabled(flag: Boolean) {
        this.scrollEnabled = flag
    }

    override fun canScrollVertically(): Boolean {
        return scrollEnabled && super.canScrollVertically()
    }
}

class BlockScrollView : HorizontalScrollView {
    var enableScrolling = true

    constructor(context: Context?): super(context)
    constructor(context: Context?, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle)

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if(enableScrolling) {
            super.onInterceptTouchEvent(ev)
        } else {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        return if(enableScrolling) {
            super.onTouchEvent(ev)
        } else { false }
    }
}

class WrappingViewPager(context: Context, attrs: AttributeSet) : ViewPager(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        var hms: Int = heightMeasureSpec
        if (mode == View.MeasureSpec.UNSPECIFIED || mode == View.MeasureSpec.AT_MOST) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            var height = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val h = child.measuredHeight
                if (h > height) height = h
            }
            hms = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthMeasureSpec, hms)
    }
}

abstract class EditTextWatcher : TextWatcher {
    var isActive: Boolean = false
    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
}

abstract class FragmentUI : Fragment(), MainUI.Tool {
    abstract fun onBgStart(bgName: String)
    abstract fun onBgResult(bgName: String, result: Boolean)
    abstract val xTagname: String

    lateinit var xGlob: Glob
    var process: BgProcess? = null
    var processRunning = false
    private val mProgressDialog: ProgressDialog = ProgressDialog()

    override var filterKey = ""
    override var filterIsActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try{
            retainInstance = true
            xGlob = (activity as MainUI).glob
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try{
            xGlob.mOnStateListener.onRefreshUI(this)
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(processRunning && process != null){
            process?.cancel(true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putBoolean(ProgressDialog.STATE, mProgressDialog.isResumed)
        outState?.putInt(ProgressDialog.MSG_ID, mProgressDialog.mMsgId)
        hidePD()
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(state: Bundle?) {
        super.onViewStateRestored(state)
        if(state != null && state.getBoolean(ProgressDialog.STATE)){
            showPD(state.getInt(ProgressDialog.MSG_ID))
        }
    }

    override fun onResume() {
        super.onResume()
        if(!processRunning && mProgressDialog.isCreated){
            mProgressDialog.dismiss()
        }
    }

    override fun setToolbarTitle(title: String){
        (activity as MainUI).mMainTool.toolbarTitle.text = title
    }

    fun showPD(msgId: Int){
        mProgressDialog.arguments = Bundle().also { it.putInt(ProgressDialog.MSG_ID, msgId) }
        mProgressDialog.show(fragmentManager, ProgressDialog.TAGNAME)
    }

    fun updatePD(msgId: Int){
        if(mProgressDialog.isResumed){
            mProgressDialog.update(msgId)
        }
    }

    fun hidePD(){
        if(mProgressDialog.isResumed){
            mProgressDialog.dismiss()
        }
    }

    fun startBg(task: TPNode.Task){
        if(!processRunning){
            process = BgProcess(task)
        }
    }

    inner class BgProcess(val task: TPNode.Task) : AsyncTask<Void, Boolean, Boolean>() {
        init{this.execute()}

        override fun onPreExecute() {
            super.onPreExecute()
            processRunning = true
            onBgStart(task.bgName)
        }

        override fun doInBackground(vararg params: Void?): Boolean {
            return task.suspendFun()
        }

        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)
            processRunning = false
            onBgResult(task.bgName, result)
        }

        override fun onCancelled(result: Boolean?) {
            super.onCancelled(result)
            processRunning = false
        }

    }

    class ProgressDialog : DialogFragment() {
        companion object {
            val TAGNAME = "progress_dialog"
            val MSG_ID = "progress_dialog_msg_id"
            val STATE = "progress_dialog_state"
        }

        var mMsgId: Int = R.string.msg_progressWait
        private lateinit var mMsgTv: TextView
        var isCreated = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            isCancelable = false
            retainInstance = true
            isCreated = true
        }

        override fun onStart() {
            super.onStart()
            if(dialog == null){return}
            dialog.window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            dialog.window.setBackgroundDrawableResource(R.drawable.z_scrollbar_transparent)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = inflater.inflate(R.layout.dialog_progress, container, false)
            mMsgId = arguments.getInt(MSG_ID)
            mMsgTv = view.findViewById(R.id.msg_tv)
            mMsgTv.text = resources.getString(mMsgId)
            return view
        }

        override fun onDestroyView() {
            if (dialog != null && retainInstance) {
                dialog.setDismissMessage(null)
            }
            super.onDestroyView()
        }

        override fun onDestroy() {
            super.onDestroy()
            isCreated = false
        }

        fun update(msgId: Int){
            this.mMsgId = msgId
            mMsgTv.text = resources.getString(msgId)
        }
    }

}