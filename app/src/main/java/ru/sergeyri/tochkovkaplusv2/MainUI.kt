package ru.sergeyri.tochkovkaplusv2

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.content.edit
import androidx.net.toUri
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.ContentViewEvent
import com.google.firebase.analytics.FirebaseAnalytics
import com.sergeyri.tpcore.TPNode
import io.fabric.sdk.android.Fabric
import java.io.File
import java.io.FileOutputStream

class Glob : Fragment() {
    companion object {
        const val TAGNAME = "glob"
        const val FML = "fml_filter"
        const val FML_ROUNDWOOD: String = "fml_rw"
        const val FML_UNIVERSAL: String = "fml_universal"
        fun getInstance(stateListener: OnStateListener, fragmentManager: FragmentManager): Glob {
            var glob = fragmentManager.findFragmentByTag(TAGNAME)
            if(glob == null){
                glob = Glob()
                glob.mOnStateListener = stateListener
                fragmentManager.beginTransaction().add(glob, TAGNAME).commit()
            }
            return glob as Glob
        }
    }

    interface OnStateListener {
        fun onInit() // вызывается при старте Glob-фрагмента
        fun onRefreshUI(ui: FragmentUI) // вызывается после окончания загрузки View текущего фрагмента
        fun onClickOpenUI(ui: FragmentUI)
    }

    lateinit var mFirebaseAnalytics: FirebaseAnalytics
    lateinit var mPrefs: SharedPreferences
    lateinit var mOnStateListener: OnStateListener
    lateinit var mNode: TPNode
    lateinit var mLockedUI: LockedUI
    lateinit var mSettingsUI: SettingsUI
    lateinit var mSheetOrderUI: SheetOrderUI
    lateinit var mSheetUI: SheetUI
    lateinit var mTmplOrderUI: TmplOrderUI
    var mUI: FragmentUI? = null
    var mFamilyName: String = FML_UNIVERSAL
    var mFirstOpen = false
    var mFirstOpenTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(activity)
        mOnStateListener = (activity as OnStateListener)
        mNode = com.sergeyri.tpcore.TPNode(activity)

        mLockedUI = LockedUI.getInstance(fragmentManager)
        mSettingsUI = SettingsUI.getInstance(fragmentManager)
        mSheetOrderUI = SheetOrderUI.getInstance(fragmentManager)
        mSheetUI = SheetUI.getInstance(fragmentManager)
        mTmplOrderUI = TmplOrderUI.getInstance(fragmentManager)

        mOnStateListener.onInit()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
    }

}



class MainUI : AppCompatActivity(), Glob.OnStateListener {
    companion object { const val VOICE_REQUEST_CODE = 1 }

	interface Tool{
        fun setToolbarTitle(title: String)
        fun onBack(): Boolean
        var filterIsActive: Boolean
		var filterKey: String
		val xFilterInputHint: String
		val xFilterVoiceDescription: String
		fun filter(key: String)
	}

    interface PermissionResultListener{
        fun onPermissionResult(mainIU: MainUI, requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?)
    }

    lateinit var glob: Glob
    var mPermissionResultListener: PermissionResultListener? = null
    val mToggle: ActionBarDrawerToggle by lazy {
        ActionBarDrawerToggle(this, mDrawerLayout, mMainTool.toolbar, R.string.open_drawer, R.string.close_drawer)
    }

    val mMainTool: MainTool by lazy { MainTool() }
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mNavigationView: NavigationView
    private lateinit var mBtnTmplListUI: LinearLayout
    private lateinit var mBtnSettingsUI: LinearLayout
    val appChecker: AppChecker by lazy { AppChecker() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mPermissionResultListener?.onPermissionResult(this, requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == RESULT_OK){
            when (requestCode) {
                VOICE_REQUEST_CODE -> {
                    val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (matches != null && matches.size > 0) {
                        val sb = StringBuilder().append("").append(matches[0]).toString()
                        mMainTool.toolbarFilterInput.setText(sb)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fabric.with(this, Crashlytics())
        Fabric.with(this, Answers())
        setContentView(R.layout.main_ui)

        try{
            glob = Glob.getInstance(this, fragmentManager)
            glob.mPrefs = getPreferences(Context.MODE_PRIVATE)

            mDrawerLayout = findViewById(R.id.main_ui)
            mNavigationView = findViewById(R.id.nav_view)
            mBtnTmplListUI = mNavigationView.findViewById(R.id.action_tmpllist_ui)
            mBtnTmplListUI.setOnClickListener { onOpenTmplOrderUI() }
            mBtnSettingsUI = mNavigationView.findViewById(R.id.action_settings_ui)
            mBtnSettingsUI.setOnClickListener { onOpenSettingsUI() }
            mNavigationView.setNavigationItemSelectedListener { menuItem: MenuItem -> navigationViewItemSelected(menuItem) }
            mDrawerLayout.addDrawerListener(mToggle)
            mToggle.syncState()
        } catch (e: UninitializedPropertyAccessException){
            finishMainUI()
        }
    }

    override fun onInit() { // вызывается один раз после Glob.onCreate
        try{
            val navMenuItemId = when(glob.mPrefs.getString(Glob.FML, Glob.FML_UNIVERSAL)){
                Glob.FML_ROUNDWOOD -> R.id.menuitem_roundwood
                else -> R.id.menuitem_universal
            }
            mNavigationView.setCheckedItem(navMenuItemId)

            glob.mFirstOpen = glob.mPrefs.getBoolean(FIRST_OPEN_KEY, true)
            glob.mPrefs.edit { this.putBoolean(FIRST_OPEN_KEY, false).apply() }

            val isGetAccountsPermission = (ContextCompat.checkSelfPermission(this@MainUI, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED)
            val isExtStoragePermission = (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
                    && (ContextCompat.checkSelfPermission(this@MainUI, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

            if(!isGetAccountsPermission || !isExtStoragePermission){
                onOpenLockedUI(LockedUI.LockType.NO_PERMISSIONS)
            } else{ // все разрешения есть
                if(appChecker.appPurchased()){ // приложение куплено
                    onClickOpenUI(glob.mSheetOrderUI)
                } else{
                    if(appChecker.checkTimeTrial()){
                        if(glob.mFirstOpenTime > 0){
                            val trialTimeLast = TRIAL_MAX - (System.currentTimeMillis() - glob.mFirstOpenTime)
                            val trialMsg: String = "${resources.getString(R.string.tip_trial_time_left_0)} " +
                                    "${trialTimeLast / (1000 * 60 * 60 * 24)} ${resources.getString(R.string.tip_trial_time_left_1)}"

                            toast(this, trialMsg, Toast.LENGTH_LONG)
                        }
                        onClickOpenUI(glob.mSheetOrderUI) // контент доступен в течение тестового периода
                    } else{
                        onOpenLockedUI(LockedUI.LockType.OVERDUE) // тестовый период закончен, заблокировать контент
                    }
                }
            }
        } catch (e: UninitializedPropertyAccessException){
            finishMainUI()
        }
    }

    override fun onRefreshUI(ui: FragmentUI) { // вызывается на FragmentUI.onViewCreated
        glob.mUI = ui
    }

    override fun onBackPressed() {
        if(glob.mUI == null){ super.onBackPressed() }
        else{
            val toolHandler: Tool = glob.mUI as Tool
            if(toolHandler.onBack()){
                if(glob.mUI!!.xTagname == SheetOrderUI.TAGNAME){
                    super.onBackPressed()
                } else{
                    onClickOpenUI(glob.mSheetOrderUI)
                }
            }
        }
    }

    override fun onClickOpenUI(ui: FragmentUI){
        mMainTool.setupFilterPanelState(false, false)
        openUI(ui)
    }

    private fun openUI(ui: FragmentUI){
        if(!ui.isAdded){
            mMainTool.toolbarFilterInput.setText("")
            fragmentManager.beginTransaction().replace(R.id.container_fragment_ui, ui, ui.xTagname).commit()
        }
    }

    fun requestPermissions(permissions: Array<String>, requestCode: Int, permissionResultListener: PermissionResultListener){
        mPermissionResultListener = permissionResultListener
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    fun lockDrawer(){
        mToggle.isDrawerIndicatorEnabled = false
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    fun unlockDrawer(){
        mToggle.isDrawerIndicatorEnabled = true
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    fun openDrawer(){
        mDrawerLayout.openDrawer(GravityCompat.START)
    }

    fun finishMainUI(){
        finish()
        startActivity(Intent(this, MainUI::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun navigationViewItemSelected(menuItem: MenuItem): Boolean {
        when(menuItem.itemId){
            R.id.menuitem_roundwood -> kotlin.run {
                glob.mPrefs.edit().putString(Glob.FML, Glob.FML_ROUNDWOOD).apply()
                if(glob.mSheetOrderUI.isAdded){
                    glob.mSheetOrderUI.fmlReload(Glob.FML_ROUNDWOOD)
                }
                else{ openUI(glob.mSheetOrderUI) }
            }
            R.id.menuitem_universal -> kotlin.run {
                glob.mPrefs.edit().putString(Glob.FML, Glob.FML_UNIVERSAL).apply()
                if(glob.mSheetOrderUI.isAdded){
                    glob.mSheetOrderUI.fmlReload(Glob.FML_UNIVERSAL)
                }
                else{ openUI(glob.mSheetOrderUI) }
            }
        }
        mDrawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun onOpenTmplOrderUI(){
        onClickOpenUI(glob.mTmplOrderUI)
        mDrawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun onOpenSettingsUI(){
        onClickOpenUI(glob.mSettingsUI)
        mDrawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun onOpenLockedUI(type: LockedUI.LockType){
        glob.mLockedUI.arguments.putString(LockedUI.KEY_LOCKTYPE, type.name)
        if(glob.mLockedUI.isAdded){
            glob.mLockedUI.update()
        } else{ onClickOpenUI(glob.mLockedUI) }
    }

    inner class MainTool {
        val toolbar: Toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
        val toolbarTitle: TextView by lazy { findViewById<TextView>(R.id.toolbar_title) }
        val toolbarFilterInput: EditText by lazy { findViewById<EditText>(R.id.search_input) }
        private val toolbarFilterPanel: LinearLayout by lazy { findViewById<LinearLayout>(R.id.toolbar_searchpanel) }
        private val filterButton: ImageButton by lazy{ findViewById<ImageButton>(R.id.search_btn) }
        private val filterMic: ImageButton by lazy { findViewById<ImageButton>(R.id.search_mic) }

        init{
            toolbarFilterInput.setInputListener(FilterListener())
            filterButton.setOnClickListener{ clickFilterButton() }
            filterMic.setOnClickListener { voiceFilterInputActivate() }
            val isActive: Boolean = if(glob.mUI != null) glob.mUI!!.filterIsActive else false
            setupFilterPanelState(isActive, false)
        }

        fun lockFilterPanel(){
            filterButton.isEnabled = false
        }

        fun unlockFilterPanel(){
            filterButton.isEnabled = true
        }

        fun setupFilterPanelState(newState: Boolean, animate: Boolean){
            if(newState){
                filterPanelOpen(animate)
            } else{
                filterPanelClose(animate)
            }
        }

        private fun clickFilterButton(){
            if(glob.mUI != null){
                val isActive: Boolean = glob.mUI!!.filterIsActive
                if(isActive) toolbarFilterInput.setText("")
                setupFilterPanelState(!isActive, true)
            }
        }

        private fun filterPanelOpen(animate: Boolean = false){
            if(glob.mUI != null){
                val filterCtx: Tool = glob.mUI as Tool
                filterCtx.filterIsActive = true
                toolbarTitle.visibility = View.GONE
                toolbarFilterPanel.visibility = View.VISIBLE

                toolbarFilterInput.requestFocus()
                toolbarFilterInput.hint = filterCtx.xFilterInputHint
                toolbarFilterInput.setText(filterCtx.filterKey)

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if(animate){
                    val anim = AnimationUtils.loadAnimation(this@MainUI, R.anim.scale_open)
                    anim.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationRepeat(p0: Animation?) {}
                        override fun onAnimationStart(p0: Animation?) {}
                        override fun onAnimationEnd(p0: Animation?) {
                            imm.showSoftInput(toolbarFilterInput, 0)
                        }
                    })
                    toolbarFilterPanel.startAnimation(anim)
                }
            }
        }

        private fun filterPanelClose(animate: Boolean = false){
            if(glob.mUI != null){
                val filterCtx: Tool = glob.mUI as Tool
                filterCtx.filterIsActive = false
                toolbarFilterPanel.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if(animate){
                    val anim = AnimationUtils.loadAnimation(this@MainUI, R.anim.scale_close)
                    anim.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationRepeat(p0: Animation?) {}
                        override fun onAnimationStart(p0: Animation?) {
                            toolbarTitle.visibility = View.INVISIBLE
                        }
                        override fun onAnimationEnd(p0: Animation?) {
                            toolbarTitle.visibility = View.VISIBLE
                            imm.hideSoftInputFromWindow(toolbarFilterInput.windowToken, 0)
                        }
                    })

                    toolbarFilterPanel.startAnimation(anim)
                } else {
                    toolbarTitle.visibility = View.VISIBLE
                    imm.hideSoftInputFromWindow(toolbarFilterInput.windowToken,0)
                }
            }
        }

        private fun voiceFilterInputActivate(){
            if(glob.mUI != null){
                val filterCtx: Tool = glob.mUI as Tool
                val intent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, filterCtx.xFilterVoiceDescription)

                try {
                    startActivityForResult(intent, VOICE_REQUEST_CODE)
                } catch(e: Exception) {
                    toast(this@MainUI, R.string.error_voice)
                }
            }
        }

        private inner class FilterListener : EditTextWatcher() {
            override fun afterTextChanged(e: Editable?) {
                if(glob.mUI != null && e != null){
                    val filterCtx = glob.mUI as Tool
                    val s = e.toString()
                    if(s != filterCtx.filterKey){
                        filterCtx.filter(s)
                        filterCtx.filterKey = s
                    }
                }
            }
        }
    }

    inner class AppChecker{

        fun appPurchased(): Boolean {
            var result = false
            val prefUserEmail: String? = glob.mPrefs.getString(USER_EMAIL_KEY, null)
            if(prefUserEmail == null){
                var purchased = findKeyApp()
                if(!purchased){
                    purchased = checkAbutdinEmail()
                }
                result = purchased
            } else{ result = true }
            return result
        }

        fun checkTimeTrial(): Boolean {
            val dir = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + EXTERNALDIR)
            if(!dir.exists()){
                dir.mkdirs()
            }
            return getStateFromFile(dir)
        }

        private fun findKeyApp(): Boolean {
            var result = false

            val cursor = contentResolver.query(PROVIDER_KEY_ADDRESS.toUri(), arrayOf(PROVIDER_KEY), null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val key = cursor.getString(cursor.getColumnIndex(PROVIDER_KEY))
                    result = (key == "tp2_unlocked")// Ключ найден
                }
                cursor.close()
            }
            return result
        }

        private fun getStateFromFile(rootDir: File): Boolean {
            val extFile = File(rootDir.absolutePath + File.separator + EXTERNALFILE)
            var time: Long? = null

            if(extFile.exists()){
                var timeText = extFile.readText()
                time = if(timeText.toLongOrNull() != null) timeText.toLong() else null
                if(time == null){
                    val inFile = File(filesDir.absolutePath + File.separator + INTERNALFILE)
                    if(inFile.exists()){
                        timeText = inFile.readText()
                    } else{
                        timeText = "0"
                        var outputStream: FileOutputStream? = null
                        try {
                            outputStream = openFileOutput(INTERNALFILE, Context.MODE_PRIVATE)
                            outputStream.write(timeText.toByteArray())
                        } catch (e: Exception) {
                            //
                        } finally { outputStream?.close() }
                    }
                    extFile.writeText(timeText)
                    time = timeText.toLongOrNull()
                }
            } else{
                val newExtFile = File(rootDir, EXTERNALFILE)
                val inFile = File(filesDir.absolutePath + File.separator + INTERNALFILE)
                val timeText: String
                if(inFile.exists()){
                    timeText = inFile.readText()
                } else{ log("is first open")
                    timeText = System.currentTimeMillis().toString()
                    var outputStream: FileOutputStream? = null
                    try {
                        outputStream = openFileOutput(INTERNALFILE, Context.MODE_PRIVATE)
                        outputStream.write(timeText.toByteArray())
                    } catch (e: Exception) {
                        //
                    } finally { outputStream?.close() }
                }
                newExtFile.writeText(timeText)
                time = if(timeText.toLongOrNull() != null) timeText.toLong() else null
            }

            if(time == null){
//                log("Системная запись в .extsys.json повреждена. Контент будет заблокирован! " +
//                        "Для разблокировки необходимо активировать полную версию!")
            } else{
                glob.mFirstOpenTime = time
            }

            return (time != null && ((System.currentTimeMillis() - time) <= TRIAL_MAX))
        }

        private fun checkAbutdinEmail(): Boolean {
            var result = false
            val abutdinEmail = "67abutdin@gmail.com"

            val gmailAccounts: Array<Account> = AccountManager.get(this@MainUI).getAccountsByType("com.google")

            val sb = StringBuilder()
            gmailAccounts.forEach {
                sb.append(it.name).append("\n")
            }

            if(gmailAccounts.find { it.name == abutdinEmail } != null){
                val edit = glob.mPrefs.edit()
                edit.putString(USER_EMAIL_KEY, abutdinEmail).apply()
                result = true
            }
            return result
        }

    }

}
