package ru.sergeyri.tochkovkaplusv2

import android.Manifest
import android.app.FragmentManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.support.v4.content.ContextCompat
import android.widget.CheckBox

import java.io.File


/**
 * Created by sergeyri on 08.02.18.
 */

class SettingsUI : FragmentUI(), MainUI.PermissionResultListener {
    companion object {
        const val TAGNAME = "settings_ui"
        const val KEY_MAX_COUNT_HISTORY = "max_count_history"
        const val MAX_COUNT_HISTORY = 3
        const val KEY_SOUND_ACTION = "sound_action"
        const val SOUND_ACTION = true
        const val KEY_VIBRATE_ACTION = "vibrate_action"
        const val VIBRATE_ACTION = true
        const val KEY_EXPORT_FOLDER = "export_folder"
        const val REQUEST_WRITE_EXTERNAL_STORAGE = 999
        val EXPORT_FOLDER = "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}Tochkovka+"
        const val KEY_SHEET_TITLE_DEFAULT = "sheet_title_default"
        const val KEY_SHEET_UNIT_DEFAULT = "sheet_unit_default"
        const val SHEET_UNIT_DEFAULT = "m3"

        fun getInstance(fragmentManager: FragmentManager): SettingsUI {
            var fragment = fragmentManager.findFragmentByTag(TAGNAME)
            if(fragment == null){fragment = SettingsUI()}
            return fragment as SettingsUI
        }

        fun getSheetTitleDefault(context: Context): String {
            return "${context.getString(R.string.def_prefixSheetTitle)} {${context.resources.getString(R.string.def_dateFormat0)}}"
        }
    }

    override val xTagname: String = TAGNAME
    override val xFilterInputHint: String = ""
    override val xFilterVoiceDescription: String = ""
    override fun filter(key: String) {}
    override fun onBgStart(bgName: String) {}
    override fun onBgResult(bgName: String, result: Boolean) {}
    override fun onBack(): Boolean {
        xGlob.mSheetOrderUI.isBack = true
        return true
    }

    private var mSoundActionPref: Boolean = SOUND_ACTION
    private var mVibrateActionPref: Boolean = VIBRATE_ACTION
    private lateinit var mTvMaxCountHistory: TextView
    var mMaxCountHistoryPref: Int = MAX_COUNT_HISTORY
    private lateinit var mTvFilePath: TextView
    var mExportFolderPref: String = EXPORT_FOLDER
    private lateinit var mTvSheetTitleDefault: TextView
    lateinit var mSheetTitleDefaultPref: String
    private lateinit var mTvSheetUnitDefault: TextView
    var mSheetUnitDefaultPref: String = SHEET_UNIT_DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            mSoundActionPref = xGlob.mPrefs.getBoolean(KEY_SOUND_ACTION, SOUND_ACTION)
            mVibrateActionPref = xGlob.mPrefs.getBoolean(KEY_VIBRATE_ACTION, VIBRATE_ACTION)
            mMaxCountHistoryPref = xGlob.mPrefs.getInt(KEY_MAX_COUNT_HISTORY, MAX_COUNT_HISTORY)
            mExportFolderPref = xGlob.mPrefs.getString(KEY_EXPORT_FOLDER, EXPORT_FOLDER)
            mSheetTitleDefaultPref = xGlob.mPrefs.getString(KEY_SHEET_TITLE_DEFAULT, getSheetTitleDefault(activity))
            mSheetUnitDefaultPref = xGlob.mPrefs.getString(KEY_SHEET_UNIT_DEFAULT, SHEET_UNIT_DEFAULT)
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.settings_ui, container, false)
        try{
            setToolbarTitle(resources.getString(R.string.title_settingsUI))
            val swSoundAction = view.findViewById<Switch>(R.id.pref_sound_action)
            swSoundAction.isChecked = mSoundActionPref
            swSoundAction.setOnCheckedChangeListener { buttonView, isChecked ->
                mSoundActionPref = isChecked
                val editor = xGlob.mPrefs.edit()
                editor.putBoolean(KEY_SOUND_ACTION, isChecked)
                editor.apply()
            }

            val swVibrateAction = view.findViewById<Switch>(R.id.pref_vibrate_action)
            swVibrateAction.isChecked = mVibrateActionPref
            swVibrateAction.setOnCheckedChangeListener { buttonView, isChecked ->
                mVibrateActionPref = isChecked
                val editor = xGlob.mPrefs.edit()
                editor.putBoolean(KEY_VIBRATE_ACTION, isChecked)
                editor.apply()
            }

            view.findViewById<LinearLayout>(R.id.ll_history).setOnClickListener {
                NumberPickerUI().show(fragmentManager, NumberPickerUI.TAGNAME)
            }
            mTvMaxCountHistory = view.findViewById(R.id.pref_max_count_history_previous)
            mTvMaxCountHistory.text = mMaxCountHistoryPref.toString()

            view.findViewById<LinearLayout>(R.id.ll_export_folderpath).setOnClickListener {
                if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                    val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    if(permission == PackageManager.PERMISSION_GRANTED){
                        FolderPicker().show(fragmentManager, FolderPicker.TAGNAME)
                    } else{ (activity as MainUI).requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE, this) }
                } else{
                    toast(activity, R.string.error_sdCardFailed)
                }
            }
            mTvFilePath = view.findViewById(R.id.pref_export_folder)
            mTvFilePath.text = mExportFolderPref

            view.findViewById<LinearLayout>(R.id.ll_sheet_title_default).setOnClickListener {
                SheetTitleDefaultPicker().show(fragmentManager, SheetTitleDefaultPicker.TAGNAME)
            }
            mTvSheetTitleDefault = view.findViewById(R.id.pref_sheet_title_default)
            mTvSheetTitleDefault.text = mSheetTitleDefaultPref
            view.findViewById<LinearLayout>(R.id.ll_sheet_unit_default).setOnClickListener {
                SheetUnitDefaultPicker().show(fragmentManager, SheetUnitDefaultPicker.TAGNAME)
            }
            mTvSheetUnitDefault = view.findViewById(R.id.pref_sheet_unit_default)
            mTvSheetUnitDefault.text = mSheetUnitDefaultPref
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    fun setMaxCountHistory(max: Int){
        mMaxCountHistoryPref = max
        mTvMaxCountHistory.text = max.toString()
        val editor = xGlob.mPrefs.edit()
        editor.putInt(KEY_MAX_COUNT_HISTORY, max)
        editor.apply()
    }

    fun setExportFolder(path: String){
        mExportFolderPref = path
        mTvFilePath.text = path
        val editor = xGlob.mPrefs.edit()
        editor.putString(KEY_EXPORT_FOLDER, path)
        editor.apply()
    }

    fun setSheetTitleDefault(title: String){
        mSheetTitleDefaultPref = title
        mTvSheetTitleDefault.text = title
        val editor = xGlob.mPrefs.edit()
        editor.putString(KEY_SHEET_TITLE_DEFAULT, title)
        editor.apply()
    }

    fun setSheetUnitDefault(unit: String){
        mSheetUnitDefaultPref = unit
        mTvSheetUnitDefault.text = unit
        val editor = xGlob.mPrefs.edit()
        editor.putString(KEY_SHEET_UNIT_DEFAULT, unit)
        editor.apply()
    }

    override fun onPermissionResult(mainIU: MainUI, requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if(requestCode == REQUEST_WRITE_EXTERNAL_STORAGE){
            if(grantResults != null
                    && grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                FolderPicker().show(fragmentManager, FolderPicker.TAGNAME)
            }
        }
    }

}