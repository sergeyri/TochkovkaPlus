package ru.sergeyri.tochkovkaplusv2

import android.Manifest
import android.app.FragmentManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.support.v4.content.ContextCompat.startActivity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.support.v4.view.GravityCompat
import androidx.net.toUri


/**
 * Created by sergeyri on 07.02.18.
 */
class LockedUI : FragmentUI(), MainUI.PermissionResultListener {
    enum class LockType { UNDEFINED, NO_PERMISSIONS, OVERDUE }
    companion object {
        const val TAGNAME = "locked_ui"
        const val KEY_LOCKTYPE = "lock_type"
        const val REQUEST_PERMISSIONS = 998

        fun getInstance(fragmentManager: FragmentManager): LockedUI {
            var fragment = fragmentManager.findFragmentByTag(TAGNAME)
            if(fragment == null){
                fragment = LockedUI()
                fragment.arguments = Bundle().also { it.putString(KEY_LOCKTYPE, LockType.UNDEFINED.name) }
            }
            return fragment as LockedUI
        }
    }

    override fun onBack(): Boolean {
        activity.finish()
        return false
    }

    override fun filter(key: String) {}
    override fun onBgStart(bgName: String) {}
    override fun onBgResult(bgName: String, result: Boolean) {}
    override val xTagname: String = TAGNAME
    override val xFilterInputHint: String = ""
    override val xFilterVoiceDescription: String = ""

    private var mLockType: LockType = LockType.UNDEFINED
    private lateinit var mTvMsg0: TextView
    private lateinit var mTvMsg1: TextView
    private lateinit var mTvUnlockTip: TextView
    private lateinit var mBtnActive: Button

    val permissionDeniedStateList: MutableList<PermissionState> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if(arguments.getString(KEY_LOCKTYPE) != null) when(arguments.getString(KEY_LOCKTYPE)){
                LockType.NO_PERMISSIONS.name -> {
                    val permissions = arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    val deniedPermissionList: MutableList<String> = mutableListOf()
                    permissions.forEach {
                        if(ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED){
                            deniedPermissionList.add(it)
                            permissionDeniedStateList.add(PermissionState(it))
                        }
                    }
                    (activity as MainUI).requestPermissions(deniedPermissionList.toTypedArray(), REQUEST_PERMISSIONS, this)
                    mLockType = LockType.NO_PERMISSIONS
                }
                LockType.OVERDUE.name -> { mLockType = LockType.OVERDUE }
                else -> { mLockType = LockType.UNDEFINED }
            }
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.locked_ui, container, false)
        try {
            (activity as MainUI).lockDrawer()
            (activity as MainUI).mMainTool.lockFilterPanel()
            mTvMsg0 = view.findViewById(R.id.lock_msg_0)
            mTvMsg1 = view.findViewById(R.id.lock_msg_1)
            mTvUnlockTip = view.findViewById(R.id.unlock_tip)
            mBtnActive = view.findViewById(R.id.unlock_action)
            updateUI()
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        when(mLockType){
            LockType.NO_PERMISSIONS -> {
                val mainUI = (activity as MainUI)
                mainUI.mPermissionResultListener = this

                val permissions = arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                permissions.forEach {
                    val permission = it
                    if(ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED){
                        permissionDeniedStateList.add(PermissionState(it))
                        permissionDeniedStateList.removeAll { it.permission == permission }
                    }
                }

                if(permissionDeniedStateList.isEmpty()){
                    if(mainUI.appChecker.appPurchased()){
                        mainUI.onClickOpenUI(xGlob.mSheetOrderUI)
                    } else{
                        if(mainUI.appChecker.checkTimeTrial()){
                            if(xGlob.mFirstOpen){
                                mainUI.openDrawer()
                                xGlob.mFirstOpen = false
                            }

                            mainUI.onClickOpenUI(xGlob.mSheetOrderUI)
                        } else{ // overdue
                            mLockType = LockType.OVERDUE
                            updateUI()
                        }
                    }
                } else{
                    updateUI()
                }
            }
            LockType.OVERDUE -> {}
            LockType.UNDEFINED -> {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as MainUI).unlockDrawer()
        (activity as MainUI).mMainTool.unlockFilterPanel()
    }

    private fun onClickUnlockPermissionBtn(){
        if(permissionDeniedStateList.find { it.dontAsk } != null){
            val settingsIntent = Intent()
            settingsIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            settingsIntent.addCategory(Intent.CATEGORY_DEFAULT)
            settingsIntent.data = ("package:" + activity.packageName).toUri()
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            activity.startActivity(settingsIntent)
        } else{
            if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val permissions: MutableList<String> = mutableListOf()
                permissionDeniedStateList.forEach {
                    permissions.add(it.permission)
                }
                (activity as MainUI).requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS, this)
            } else{ toast(activity, R.string.error_sdCardFailed) }
        }
    }

    fun updateUI(){
        when(mLockType){
            LockType.NO_PERMISSIONS -> {
                mTvMsg0.visibility = if(permissionDeniedStateList.find { it.permission == Manifest.permission.GET_ACCOUNTS } != null) View.VISIBLE else View.GONE
                mTvMsg0.setText(R.string.desc_get_accounts_permission)
                mTvMsg1.visibility = if(permissionDeniedStateList.find { it.permission == Manifest.permission.WRITE_EXTERNAL_STORAGE } != null) View.VISIBLE else View.GONE
                mTvMsg1.setText(R.string.desc_external_storage_permission)
                mTvUnlockTip.visibility = if(permissionDeniedStateList.find { it.dontAsk } != null) View.VISIBLE else View.GONE
                mTvUnlockTip.setText(R.string.desc_unlock_permissions)
                mBtnActive.setText(if(permissionDeniedStateList.find { it.dontAsk } != null) R.string.btn_external_storage_permission_don_t_ask else R.string.btn_external_storage_permission)
                mBtnActive.setOnClickListener { onClickUnlockPermissionBtn() }
            }
            LockType.OVERDUE -> {
                mTvMsg0.visibility = View.VISIBLE
                mTvMsg0.setText(R.string.desc_overdue_locked_ui)
                mTvMsg1.visibility = View.GONE
                mTvUnlockTip.visibility = View.GONE
                mBtnActive.setText(R.string.btn_buy_app)
                mBtnActive.setOnClickListener {
                    val uri = Uri.parse(PREMIUMKEY_GOOGLEPLAY_ADDR)
                    val toPremium = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(toPremium)
                }
            }
            else -> {
                mTvMsg0.visibility = View.GONE
                mTvMsg1.visibility = View.GONE
                mTvUnlockTip.visibility = View.VISIBLE
            }
        }
    }

    override fun onPermissionResult(mainIU: MainUI, requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if(requestCode == REQUEST_PERMISSIONS){
            val mainUI = (activity as MainUI)
            if(permissions != null && grantResults != null && grantResults.isNotEmpty()){
                grantResults.forEachIndexed { index, result ->
                    if(permissions[index] == Manifest.permission.GET_ACCOUNTS){
                        if(result == PackageManager.PERMISSION_GRANTED){
                            permissionDeniedStateList.removeAll { it.permission == permissions[index] }
                            updateUI()
                        } else{
                            if (Build.VERSION.SDK_INT >= 23 && !shouldShowRequestPermissionRationale(permissions[index])){
                                val findedPerm = permissionDeniedStateList.find { it.permission == permissions[index] }
                                if(findedPerm != null){
                                    findedPerm.dontAsk = true
                                }
                            }
                            updateUI()
                        }
                    } else if(permissions[index] == Manifest.permission.WRITE_EXTERNAL_STORAGE){
                        if(result == PackageManager.PERMISSION_GRANTED){
                            permissionDeniedStateList.removeAll { it.permission == permissions[index] }
                            updateUI()
                        } else{
                            if (Build.VERSION.SDK_INT >= 23 && !shouldShowRequestPermissionRationale(permissions[index])){
                                val findedPerm = permissionDeniedStateList.find { it.permission == permissions[index] }
                                if(findedPerm != null){
                                    findedPerm.dontAsk = true
                                }
                            }
                            updateUI()
                        }
                    }

                    if(permissionDeniedStateList.isEmpty()){
                        if(mainUI.appChecker.appPurchased()){
                            mainUI.onClickOpenUI(xGlob.mSheetOrderUI)
                        } else{
                            if(mainUI.appChecker.checkTimeTrial()){
                                if(xGlob.mFirstOpen){
                                    mainUI.openDrawer()
                                    xGlob.mFirstOpen = false
                                }

                                mainUI.onClickOpenUI(xGlob.mSheetOrderUI)
                            } else{ // overdue
                                mLockType = LockType.OVERDUE
                                updateUI()
                            }
                        }
                    } else{
                        updateUI()
                    }
                }
            }
        }
    }

    class PermissionState(val permission: String, var dontAsk: Boolean=false)

}