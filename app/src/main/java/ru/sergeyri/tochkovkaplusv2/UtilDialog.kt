package ru.sergeyri.tochkovkaplusv2

import android.Manifest
import android.annotation.TargetApi
import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.*
import android.print.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputType
import android.util.DisplayMetrics
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.itextpdf.text.*
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.sergeyri.tpcore.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.Format

/**
 * Created by sergeyri on 1/11/18.
 */

class SheetUnitDefaultPicker : SetterUI(){
    companion object {val TAGNAME = "sheet_unit_picker_ui"}

    private lateinit var mEtUnit: EditText

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogset_sheet_unit_default, null, false)
        mEtUnit = view.findViewById(R.id.et_unit)
        mEtUnit.setText((activity as MainUI).glob.mSettingsUI.mSheetUnitDefaultPref)
        view.findViewById<Button>(R.id.btn_unit_clear).setOnClickListener {mEtUnit.setText("", TextView.BufferType.EDITABLE)}
        return view
    }

    override fun onClickPositive() {
        (activity as MainUI).glob.mSettingsUI.setSheetUnitDefault(mEtUnit.text.toString())
        dismiss()
    }
}

class SheetTitleDefaultPicker : SetterUI(){
    companion object {val TAGNAME = "sheet_title_picker_ui"}

    private lateinit var mEtTitle: EditText

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogset_sheet_title_default, null, false)
        mEtTitle = view.findViewById(R.id.et_title)
        mEtTitle.setText((activity as MainUI).glob.mSettingsUI.mSheetTitleDefaultPref)
        view.findViewById<Button>(R.id.btn_title_clear).setOnClickListener {mEtTitle.setText("", TextView.BufferType.EDITABLE)}
        return view
    }

    override fun onClickPositive() {
        (activity as MainUI).glob.mSettingsUI.setSheetTitleDefault(mEtTitle.text.toString())
        dismiss()
    }
}

class FolderPicker : DialogUI() {
    companion object {val TAGNAME = "folder_picker_ui"}
    private lateinit var mSdCard: File
    private lateinit var mAdapter: FolderAdapter
    private lateinit var mBtnBack: ImageButton
    private lateinit var mRvFolder: RecyclerView
    private var sdIsAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED){
            mSdCard = Environment.getExternalStorageDirectory()
            mAdapter = FolderAdapter(mSdCard)
            mAdapter.update(mSdCard.listFiles().filter { it.isDirectory })
            sdIsAvailable = (mSdCard.list() != null)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogset_folderchooser, container, false)
        mRvFolder = view.findViewById(R.id.rv_folder)
        if(sdIsAvailable){
            mRvFolder.layoutManager = LinearLayoutManager(activity)
            mRvFolder.adapter = mAdapter
        }

        mBtnBack = view.findViewById(R.id.btn_back)
        mBtnBack.setOnClickListener { onClickBack() }
        view.findViewById<Button>(R.id.action_close_dialogui).setOnClickListener { onClickCancel() }
        view.findViewById<Button>(R.id.action_positive)
                .also{it.setText(R.string.dialog_actionSelectCurrentPath)}
                .setOnClickListener { onClickPositive() }
        return view
    }

    private fun onClickBack(){
        mAdapter.goToParent()
    }

    private fun onClickCancel(){
        dismiss()
    }

    private fun onClickPositive() {
        val path = mAdapter.currentPath.absolutePath
        (activity as MainUI).glob.mSettingsUI.setExportFolder(path)
        dismiss()
    }

    override fun setDialogSize() {
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        dispWidth = metrics.widthPixels
        dispHeight = metrics.heightPixels

        val factoryWidthDefault = if(isPortrait()) DIALOG_WIDTH_PORT_FACTORY else DIALOG_WIDTH_LAND_FACTORY
        var factoryWidth: Float? = getString(R.string.def_dialogWidthFactory).toFloatOrNull()
        factoryWidth = if(factoryWidth == null) factoryWidthDefault else factoryWidth.toFloat()

        val factoryHeightDefault = if(isPortrait()) DIALOG_HEIGHT_PORT_FACTORY else DIALOG_HEIGHT_LAND_FACTORY
        var factoryHeight: Float? = getString(R.string.def_dialogHeightFactory).toFloatOrNull()
        factoryHeight = if(factoryHeight == null) factoryHeightDefault else factoryHeight.toFloat()

        val dialogWidth = (dispWidth * factoryWidth).toInt()
        val dialogHeight = (dispHeight * factoryHeight).toInt()
        dialog.window.setLayout(dialogWidth, dialogHeight)
    }

    inner class FolderAdapter(sdCard: File) : RecyclerView.Adapter<FolderAdapter.ItemHolder>() {
        val itemList: MutableList<File> = mutableListOf()
        var currentPath: File = sdCard

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.dialogset_folderchooser_item, parent, false)
            return ItemHolder(view)
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val file = itemList[position]
            mBtnBack.isEnabled = (file.parentFile != mSdCard)
            holder.tvTitle.text = file.name.toString()
            holder.tvTitle.isEnabled = file.listFiles().filter { it.isDirectory }.isNotEmpty()
        }

        fun goToParent(){
            if(currentPath != mSdCard){
                currentPath = currentPath.parentFile
                update(currentPath.listFiles().filter { it.isDirectory })
            }
        }

        fun update(list: List<File>){
            itemList.clear()
            itemList.addAll(list)
            notifyDataSetChanged()
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val tvTitle: TextView = itemView.findViewById(R.id.folder_title)

            init {
                tvTitle.setOnClickListener { stepInwards() }
                itemView.findViewById<Button>(R.id.folder_selector).setOnClickListener {
                    onClickSelector()
                }
            }

            private fun onClickSelector(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val file = itemList[adapterPosition]
                    currentPath = file
                    notifyDataSetChanged()
                    onClickPositive()
                }
            }

            private fun stepInwards(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val file = itemList[adapterPosition]
                    currentPath = file
                    update(file.listFiles().filter { it.isDirectory })
                }
            }
        }
    }
}

class DipCreator : SetterUI(){
    companion object {
        val TAGNAME = "dip_creator_ui"
        val DIAMETER_ARRAY = "diameter_array"
        val DIP_JSONARRAY = "dip_jsonarray"
    }

    private lateinit var mMinNp: NumberPicker
    private lateinit var mMaxNp: NumberPicker
    private lateinit var mDiameterSrc: List<String>
    private lateinit var mDipSrc: JSONArray
    
    private val invertedPairs: MutableList<Pair<Int, Int>> = mutableListOf()
    private val mMinDst: MutableList<String> = mutableListOf()
    private var mDelay = 1500
    private var mDelayTaskIsRunning = false
    private var mDelayTask: DelayTask? = null
    private var mMinIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(arguments != null){
            mDiameterSrc = arguments.getStringArray(DIAMETER_ARRAY).map { it }
            mDipSrc = JSONArray(arguments.getString(DIP_JSONARRAY))

            log("dipSrc: $mDipSrc")

            var fm = 0
            if(mDipSrc.length() == 0){
                invertedPairs.add(Pair(0, mDiameterSrc.lastIndex-1))
//                mMinDst.addAll(mDiameterSrc.subList(0, mDiameterSrc.lastIndex-1))
            } else{
                (0 until mDipSrc.length()).map { mDipSrc.getJSONObject(it) }.forEachIndexed { index, jsonObject ->
                    val minInd = mDiameterSrc.indexOf(jsonObject.getString(Sheet.GS_MIN))
                    val maxInd = mDiameterSrc.indexOf(jsonObject.getString(Sheet.GS_MAX))

                    var befFirstInd = -1
                    var befLastInd = -1
                    if(minInd > 1){
                        befFirstInd = fm
                        befLastInd = minInd-1
                    }

                    if(befFirstInd != -1 && befLastInd != -1 && ((befLastInd-befFirstInd) > 0)){
                    	invertedPairs.add(Pair(befFirstInd, befLastInd))
                        //mMinDst.addAll(mDiameterSrc.subList(befFirstInd, befLastInd))
                    }

                    if(maxInd < mDiameterSrc.lastIndex-1){
                        fm = maxInd+1
                        if(index == mDipSrc.length()-1){
                        	val aftFirstInd = fm
                        	val aftLastInd = mDiameterSrc.lastIndex-1
                            if(aftLastInd-aftFirstInd > 0){
                                invertedPairs.add(Pair(aftFirstInd, aftLastInd))
                            }
                        }
                    }

                }//end dip cycle
            }

            invertedPairs.forEach {
                log("invPair: ${mDiameterSrc[it.first]}-${mDiameterSrc[it.second]}")
                mMinDst.addAll(mDiameterSrc.subList(it.first, it.second))
            }
        }
    }

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dip_creator_ui, null, false)
        mMinNp = view.findViewById(R.id.min_np)
        mMaxNp = view.findViewById(R.id.max_np)

        if(mDiameterSrc.isNotEmpty()){
            val diameters: Array<String> = mMinDst.toTypedArray()
            mMinNp.displayedValues = diameters
            mMinNp.minValue = 0
            mMinNp.maxValue = diameters.size-1
            mMinNp.setOnValueChangedListener { picker, oldInd, newInd -> onChangeMinIndex(oldInd, newInd) }
            mMinNp.value = 0
            mMinNp.wrapSelectorWheel = false
            mMaxNp.setFormatter { value -> mDiameterSrc[value] }
            mMaxNp.wrapSelectorWheel = false
            mMinIndex = mMinNp.value
            if(!mDelayTaskIsRunning){
                mDelayTask = DelayTask()
            }
        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        if(mDelayTask != null){
            mDelayTask?.cancel(true)
        }
    }

    private fun onChangeMinIndex(oldInd: Int, newInd: Int){
        mDelay = 1000
        mMinIndex = newInd
        if(!mDelayTaskIsRunning){
            mDelayTask = DelayTask()
        }
    }

    private fun updMaxList(): Pair<Int, Int> {
        val selectedInd = mDiameterSrc.indexOfFirst { it == mMinDst[mMinIndex] }

        val pair = invertedPairs.find { (selectedInd in it.first..it.second) }
        var startInd = 0
        var endInd = 0

        log("pair--${pair?.first}:${pair?.second}")

        if(pair != null){
        	startInd = selectedInd+1
        	endInd = if((pair.second+1) == mDiameterSrc.lastIndex) pair.second+1 else pair.second
        }

        return Pair(startInd, endInd)
    }

    override fun onClickPositive() {
        if(!mDelayTaskIsRunning){
            val dip = JSONObject()
            dip.put(Sheet.GS_MIN, mMinDst[mMinNp.value])
            dip.put(Sheet.GS_MAX, mDiameterSrc[mMaxNp.value])
            (activity as MainUI).glob.mSheetUI.setDip(dip)
            dismiss()
        }
    }

    inner class DelayTask : AsyncTask<Void, Void, Pair<Int, Int>>(){
        init{execute()}

        override fun onPreExecute() {
            super.onPreExecute()
            mDelayTaskIsRunning = true
        }

        override fun doInBackground(vararg params: Void?): Pair<Int, Int> {
            while(mDelay > 0 && !isCancelled){
                SystemClock.sleep(500)
                mDelay -= 500
            }
            return updMaxList()
        }

        override fun onPostExecute(result: Pair<Int, Int>) {
            super.onPostExecute(result)
            mMaxNp.minValue = result.first
            mMaxNp.maxValue = result.second
            mMaxNp.value = result.first
            mDelayTaskIsRunning = false
        }
    }
}

class NumberPickerUI : SetterUI(){
    companion object {val TAGNAME = "number_picker_ui"}

    private lateinit var mNumberPicker: NumberPicker

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogset_max_count_history_previous, null, false)
        mNumberPicker = view.findViewById(R.id.np_max_count_history_previous)
        mNumberPicker.minValue = 3
        mNumberPicker.maxValue = 10
        mNumberPicker.post {
            mNumberPicker.value = (activity as MainUI).glob.mSettingsUI.mMaxCountHistoryPref
        }
        mNumberPicker.wrapSelectorWheel = false
        return view
    }

    override fun onClickPositive() {
        val value = mNumberPicker.value
        (activity as MainUI).glob.mSettingsUI.setMaxCountHistory(value)
        dismiss()
    }

}

class RwTmplInfo : TmplInfo(){
    companion object {val TAGNAME = "rw_tmpl_info"}

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialoginfo_tmpl_rw, null, false)
        view.findViewById<TextView>(R.id.title).text = mTmpl.title
        view.findViewById<TextView>(R.id.unit).text = mTmpl.unit

        val rwStandart = mTmpl.ext.getString(RWI.KEY_STANDART)
        val standartTitle = when(rwStandart){
            RWI.St.GOST_2708_75.name -> resources.getString(RWI.St.GOST_2708_75.titleRes)
            RWI.St.ISO_4480_83.name -> resources.getString(RWI.St.ISO_4480_83.titleRes)
            else -> null
        }

        view.findViewById<TextView>(R.id.rw_standart).text = standartTitle
        val length = mTmpl.ext.getDouble(RWI.KEY_LENGTH)
        view.findViewById<TextView>(R.id.rw_length).text = length.toString()

        val rvGroupInfo: RecyclerView = view.findViewById(R.id.list_gmi)
        rvGroupInfo.layoutManager = LinearLayoutManager(activity)
        rvGroupInfo.adapter = mGroupInfoAdapter
        return view
    }
}

class UnivTmplInfo : TmplInfo(){
    companion object {val TAGNAME = "univ_tmpl_info"}

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialoginfo_tmpl_univ, null, false)
        view.findViewById<TextView>(R.id.title).text = mTmpl.title
        view.findViewById<TextView>(R.id.unit).text = mTmpl.unit

        val rvGroupInfo: RecyclerView = view.findViewById(R.id.list_gmi)
        rvGroupInfo.layoutManager = LinearLayoutManager(activity)
        rvGroupInfo.adapter = mGroupInfoAdapter
        return view
    }
}

class RwSheetInfo : SheetInfo(){
    companion object {val TAGNAME = "rw_sheet_info"}

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialoginfo_sheet_rw, null, false)
        view.findViewById<TextView>(R.id.title).text = mSheet.title
        view.findViewById<TextView>(R.id.created).text = mSheet.createDate.toDateTime(resources.getString(R.string.def_dateFormat0))
        view.findViewById<TextView>(R.id.changed).text = mSheet.changeDate.toDateTime(resources.getString(R.string.def_dateFormat0))
        view.findViewById<TextView>(R.id.unit).text = mSheet.unit
        val rwStandart = mSheet.ext.getString(RWI.KEY_STANDART)
        val standartTitle = when(rwStandart){
            RWI.St.GOST_2708_75.name -> resources.getString(RWI.St.GOST_2708_75.titleRes)
            RWI.St.ISO_4480_83.name -> resources.getString(RWI.St.ISO_4480_83.titleRes)
            else -> null
        }

        view.findViewById<TextView>(R.id.rw_standart).text = standartTitle
        val length = mSheet.ext.getDouble(RWI.KEY_LENGTH)
        view.findViewById<TextView>(R.id.rw_length).text = length.toString()
        view.findViewById<TextView>(R.id.comment).text = mSheet.comment
        val rvGroupInfo: RecyclerView = view.findViewById(R.id.list_gmi)
        rvGroupInfo.layoutManager = LinearLayoutManager(activity)
        rvGroupInfo.adapter = mGroupInfoAdapter
        return view
    }
}

class UnivSheetInfo : SheetInfo(){
    companion object {val TAGNAME = "univ_sheet_info"}

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialoginfo_sheet_univ, null, false)
        view.findViewById<TextView>(R.id.title).text = mSheet.title
        view.findViewById<TextView>(R.id.created).text = mSheet.createDate.toDateTime(resources.getString(R.string.def_dateFormat0))
        view.findViewById<TextView>(R.id.changed).text = mSheet.changeDate.toDateTime(resources.getString(R.string.def_dateFormat0))
        view.findViewById<TextView>(R.id.unit).text = mSheet.unit
        view.findViewById<TextView>(R.id.comment).text = mSheet.comment
        val rvGroupInfo: RecyclerView = view.findViewById(R.id.list_gmi)
        rvGroupInfo.layoutManager = LinearLayoutManager(activity)
        rvGroupInfo.adapter = mGroupInfoAdapter
        return view
    }
}

abstract class SheetInfo : InfoUI(){
    companion object {const val SRC_DATA = "src_data"}

    protected lateinit var mSheet: Sheet
    protected lateinit var mGroupInfoAdapter: GroupInfoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mSheet = Sheet.fromJson(JSONObject(arguments.getString(SRC_DATA)))
        mGroupInfoAdapter = GroupInfoAdapter(mSheet.listGMI)
    }

    inner class GroupInfoAdapter(listGMI: List<Group.GroupMeta>) : RecyclerView.Adapter<GroupInfoAdapter.ItemHolder>() {
        private val itemList: MutableList<Group.GroupMeta> = mutableListOf()
        init {itemList.addAll(listGMI)}

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.dialoginfo_grouplist_item, parent, false)
            return ItemHolder(view)
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.vGmiTheme.setBackgroundColor(item.theme.bg)
            holder.tvGmiTitle.text = item.title
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val vGmiTheme: View = itemView.findViewById(R.id.groupinfo_theme)
            val tvGmiTitle: TextView = itemView.findViewById(R.id.groupinfo_title)
        }
    }
}

abstract class TmplInfo : InfoUI(){
    companion object {val SRC_DATA = "src_data"}

    protected lateinit var mTmpl: Tmpl
    protected lateinit var mGroupInfoAdapter: GroupInfoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTmpl = Tmpl.fromJson(JSONObject(arguments.getString(SRC_DATA)))
        mGroupInfoAdapter = GroupInfoAdapter(mTmpl.listGMI)
    }

    inner class GroupInfoAdapter(listGMI: List<Group.GroupMeta>) : RecyclerView.Adapter<GroupInfoAdapter.ItemHolder>() {
        private val itemList: MutableList<Group.GroupMeta> = mutableListOf()
        init {itemList.addAll(listGMI)}

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.dialoginfo_grouplist_item, parent, false)
            return ItemHolder(view)
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.vGmiTheme.setBackgroundColor(item.theme.bg)
            holder.tvGmiTitle.text = item.title
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val vGmiTheme: View = itemView.findViewById(R.id.groupinfo_theme)
            val tvGmiTitle: TextView = itemView.findViewById(R.id.groupinfo_title)
        }
    }
}

class TmplBuilder : DialogUI() {
    companion object {
        const val TAGNAME = "tmpl_builder"
        const val SRC_DATA = "src_data"
    }

    lateinit var mEtTitle: TextView
    lateinit var mBtnTitleClear: Button
    lateinit var mBtnCancel: Button
    lateinit var mBtnPositive: Button
    lateinit var oJsonSrc: JSONObject // входные данные (для edit-режима и default-значений)
    var titleBgRes: Int = R.drawable.input_bg

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putString(Tmpl.KEY_TITLE, mEtTitle.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oJsonSrc = getSrcJson()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogtb_ui, container, false)
        val titleBar = view.findViewById<TextView>(R.id.dialog_title)
        titleBar.setText(R.string.title_dialogTmplEdit)
        mEtTitle = view.findViewById(R.id.et_title)
        val title = if(savedInstanceState == null) oJsonSrc.getString(Tmpl.KEY_TITLE) else savedInstanceState.getString(Tmpl.KEY_TITLE)
        mEtTitle.text = title
        mBtnTitleClear = view.findViewById(R.id.btn_title_clear)
        mBtnTitleClear.setOnClickListener { mEtTitle.setText("", TextView.BufferType.EDITABLE) }
        mBtnCancel = view.findViewById(R.id.action_close_dialogui)
        mBtnCancel.setOnClickListener { onClickCancelAction() }
        mBtnPositive = view.findViewById(R.id.action_positive)
        mBtnPositive.setOnClickListener { onClickPositiveAction() }
        return view
    }

    private fun onClickCancelAction(){
        dismiss()
    }

    private fun onClickPositiveAction(){
        var errorToastMsg: Int? = null
        val tempTitle = mEtTitle.text.toString()
        titleBgRes = R.drawable.input_bg
        if(!tempTitle.checkPattern(REGEX_PATTERN_FOR_TITLE)){
            errorToastMsg = R.string.error_incorrectData
            titleBgRes = R.drawable.input_bg_error
        }

        if(errorToastMsg == null){
            val tmpl: Tmpl = Tmpl.fromJson(oJsonSrc)
            tmpl.title = tempTitle
            val glob = (activity as MainUI).glob
            glob.mTmplOrderUI.startBg(TPNode.Task(TmplOrderUI.BgType.TMPL_EDIT.name, {
                glob.mTmplOrderUI.tmplOperator.edit(tmpl)
            }))
            dismiss()
        } else{
            toast(activity, errorToastMsg, Toast.LENGTH_SHORT)
        }
    }

    private fun getSrcJson(): JSONObject = JSONObject(arguments.getString(SRC_DATA))

}

class DeleteTmplUI : ConfirmUI() {
    companion object { val TAGNAME = "confirm_ui" }
    override val msg: Int = R.string.desc_tmplDelete

    override fun positiveHandler() {
        val ui = (activity as MainUI).glob.mTmplOrderUI
        val selectedTmplList = ui.mTmplAdapter.getSelectedTmplList()
        ui.startBg(TPNode.Task(TmplOrderUI.BgType.TMPL_DELETE.name, { ui.tmplOperator.delete(selectedTmplList) }))
        dismiss()
    }
}

class ComponentClearDataUI : ConfirmUI() {
    companion object { val TAGNAME = "confirm_ui" }
    override val msg: Int = R.string.desc_componentClear

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val tvMsg: TextView = view.findViewById(R.id.msg)
        tvMsg.setTextColor(Color.parseColor(resources.getString(R.string.def_warnTextColor)))
        return view
    }

    override fun positiveHandler() {
        val ui = (activity as MainUI).glob.mSheetUI
        val selectedComponents = ui.mCalculatorAdapter.getSelectedComponentList()
        ui.startBg(TPNode.Task(SheetUI.BgType.COMPONENT_CLEAR.name, { ui.mComponentOperator.clearData(selectedComponents) }))
        dismiss()
    }
}

class DeleteComponentUI : ConfirmUI() {
    companion object { val TAGNAME = "confirm_ui" }
    override val msg: Int = R.string.desc_componentDelete

    override fun positiveHandler() {
        val ui = (activity as MainUI).glob.mSheetUI
        val selectedComponents = ui.mCalculatorAdapter.getSelectedComponentList()
        ui.startBg(TPNode.Task(SheetUI.BgType.COMPONENT_DELETE.name, { ui.mComponentOperator.delete(selectedComponents) }))
        dismiss()
    }
}

class DeleteSheetUI : ConfirmUI() {
    companion object { val TAGNAME = "confirm_ui" }
    override val msg: Int = R.string.desc_sheetDelete

    override fun positiveHandler() {
        val so = (activity as MainUI).glob.mSheetOrderUI
        val selectedSheets = so.mSheetListAdapter.getSelectedSheets()
        so.startBg(TPNode.Task(SheetOrderUI.BgType.SHEETLIST_DELETE.name, { so.sheetOperator.delete(selectedSheets) }))
        dismiss()
    }
}

class SaveTmplUI : ConfirmUI() {
    companion object { val TAGNAME = "confirm_ui" }

    override val msg: Int = R.string.desc_sheetSaveTmpl

    override fun positiveHandler() {
        val so = (activity as MainUI).glob.mSheetOrderUI
        val selectedSheets = so.mSheetListAdapter.getSelectedSheets()
        so.startBg(TPNode.Task(SheetOrderUI.BgType.TMPL_SAVE.name, {
            selectedSheets.forEach {so.tmplOperator.create(it)}
            return@Task true
        }))
        dismiss()
    }
}

abstract class SetterUI : DialogUI() {
    abstract fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View
    abstract fun onClickPositive()

    private lateinit var mDialogTitle: TextView
    private lateinit var mBtnCancel: Button
    private lateinit var mBtnPositive: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_setter, container, false)
        mDialogTitle = view.findViewById(R.id.dialog_title)
        mBtnCancel = view.findViewById(R.id.action_close_dialogui)
        mBtnCancel.setOnClickListener { onClickCancelAction() }
        mBtnPositive = view.findViewById(R.id.action_positive)
        mBtnPositive.setOnClickListener { onClickPositiveAction() }

        view.findViewById<LinearLayout>(R.id.ll_info_container).addView(getExtendedView(inflater, savedInstanceState))
        return view
    }

    private fun onClickCancelAction(){
        dismiss()
    }

    private fun onClickPositiveAction(){
        onClickPositive()
    }

}

abstract class InfoUI : DialogUI() {
    abstract fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialoginfo, container, false)
        view.findViewById<LinearLayout>(R.id.ll_info_container).addView(getExtendedView(inflater, savedInstanceState))
        view.findViewById<Button>(R.id.action_positive).setOnClickListener { dismiss() }
        return view
    }
}

abstract class ConfirmUI : DialogUI() {
    abstract val msg: Int
    abstract fun positiveHandler()

    private lateinit var mTvMsg: TextView
    private lateinit var mBtnCancel: Button
    private lateinit var mBtnPositive: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_confirm, container, false)
        mTvMsg = view.findViewById(R.id.msg)
        mTvMsg.setText(msg)
        mBtnCancel = view.findViewById(R.id.action_close_dialogui)
        mBtnCancel.setOnClickListener { onClickCancelAction() }
        mBtnPositive = view.findViewById(R.id.action_positive)
        mBtnPositive.setOnClickListener { onClickPositiveAction() }
        return view
    }

    private fun onClickCancelAction(){
        dismiss()
    }

    private fun onClickPositiveAction(){
        positiveHandler()
    }
}



class RwComponentBuilder : ComponentBuilder() {
    companion object {
        const val TAGNAME = "component_creator_roundwood_ui"
        const val CAPDEFAULT = "capacity_default"
        const val CAPSETTINGS_STATE = "capacity_settings_state"
    }

    lateinit var mTitleCnt: LinearLayout
    lateinit var mEtTitle: EditText
    lateinit var mBtnTitleClear: Button
    lateinit var mSwtCapSettings: Switch
    lateinit var mCapDefaultCnt: LinearLayout
    lateinit var mEtCapDefault: EditText
    lateinit var mCapGroupDataCnt: LinearLayout
    var mEtCapGroupDataMap: MutableMap<String, EtHub> = mutableMapOf()// (<GroupMeta.sid, EditText>)
    val mSrcListGD: MutableList<Group.GroupData> = mutableListOf()
    var mCapSettingsIsOpened: Boolean = false
    lateinit var mStandart: String
    lateinit var rwi: RWI
    var mRwLength: Float = 0F
    var titleBgRes: Int = R.drawable.input_bg
    var capDefaultBgRes: Int = R.drawable.input_bg
    var mMinDiameter: Float = 0F
    var mMaxDiameter: Float = 0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStandart = mParent.ext.getString(RWI.KEY_STANDART)
        mRwLength = mParent.ext.getDouble(RWI.KEY_LENGTH).toFloat()
        rwi = RWI(activity, mStandart)
        mMinDiameter = rwi.getMinDiameter()
        mMaxDiameter = rwi.getMaxDiameter()
        val capJsonArray = oJsonSrc.getJSONArray(Component.KEY_GROUPDATA_LIST)
        (0 until capJsonArray.length()).map { capJsonArray[it] as JSONObject }.forEach {
            val groupData = Group.GroupData.fromJson(it)
            mSrcListGD.add(groupData)
        }
    }

    @Throws(UninitializedPropertyAccessException::class)
    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view: LinearLayout = inflater.inflate(R.layout.dialogcb_ui_rw, null, false) as LinearLayout
        mTitleCnt = view.findViewById(R.id.ll_title_container)
        mEtTitle = view.findViewById(R.id.et_title)
        val title = if(savedInstanceState == null) oJsonSrc.getString(Component.KEY_TITLE) else savedInstanceState.getString(Component.KEY_TITLE)
        mEtTitle.setText(title)
        mBtnTitleClear = view.findViewById(R.id.btn_title_clear)
        mBtnTitleClear.setOnClickListener { mEtTitle.setText("", TextView.BufferType.EDITABLE) }

        mSwtCapSettings = view.findViewById(R.id.swt_capsettings)
        mSwtCapSettings.setOnCheckedChangeListener { buttonView, isChecked -> buttonView.onOpenCapSettings(isChecked) }
        mCapDefaultCnt = view.findViewById(R.id.ll_capdefault_container)
        mEtCapDefault = view.findViewById(R.id.et_capdefault)
        val capDefault = if(savedInstanceState == null) mSrcListGD.first().capacity.toString() else savedInstanceState.getString(CAPDEFAULT)
        mEtCapDefault.setText(capDefault)
        mCapGroupDataCnt = view.findViewById(R.id.ll_capgroupdata_container)

        mParent.listGMI.forEach {
            val groupInfo = it
            val capSettingsView = inflater.inflate(R.layout.dialogcb_groupdata_cap, null, false)
            val tvGroupTitle: TextView = capSettingsView.findViewById(R.id.tv_group_title)
            tvGroupTitle.text = it.title
            val etCapacity: EditText = capSettingsView.findViewById(R.id.et_group_capacity)
            etCapacity.id = "capacity${groupInfo.sid}".hashCode()
            val etCount: EditText = capSettingsView.findViewById(R.id.et_group_count)
            etCount.id = "countTotal0${groupInfo.sid}".hashCode()
            val etHub: EtHub
            if(savedInstanceState == null){
                etHub = EtHub()
                etHub.etCapacity = etCapacity
                etHub.etCount = etCount
                val groupData = mSrcListGD.find { it.sid == groupInfo.sid }
                if(groupData != null){
                    etHub.etCapacity.setText(groupData.capacity.toString(), TextView.BufferType.EDITABLE)
                    val count = if(mSrcType == SheetUI.BgType.COMPONENT_EDIT.name) groupData.count.toString() else ""
                    etHub.etCount.setText(count, TextView.BufferType.EDITABLE)
                }
                mEtCapGroupDataMap[groupInfo.sid] = etHub
            } else{
                val arraySavedGD = savedInstanceState.getStringArray(groupInfo.sid)
                etHub = mEtCapGroupDataMap[groupInfo.sid]!!
                etHub.etCapacity.setText(arraySavedGD[0], TextView.BufferType.EDITABLE)
                etHub.etCount.setText(arraySavedGD[1], TextView.BufferType.EDITABLE)
            }
            mCapGroupDataCnt.addView(capSettingsView)
        }

        val capSettingsIsOpened: Boolean
        if(savedInstanceState != null){
            capSettingsIsOpened = savedInstanceState.getBoolean(CAPSETTINGS_STATE)
            mSwtCapSettings.isChecked = capSettingsIsOpened
        } else{
            var isDifferent = false
            mSrcListGD.forEach {
                if(mSrcListGD.first().capacity != it.capacity){
                    isDifferent = true
                    return@forEach
                }
            }
            capSettingsIsOpened = isDifferent
            mSwtCapSettings.isChecked = isDifferent
        }

        mEtTitle.setInputListener(TitleInputListener())
        mEtCapDefault.setInputListener(CapDefaultInputListener())
        setCapSettingsState(capSettingsIsOpened)
        return view
    }

    override fun checkExtendedData(): Int? {
        var result: Int? = null

        var errorToastMsg: Int? = null
        val tempTitle = mEtTitle.text.toString()
        titleBgRes = R.drawable.input_bg
        capDefaultBgRes = R.drawable.input_bg
        if(!tempTitle.checkPattern(REGEX_PATTERN_FOR_TITLE)){
            errorToastMsg = R.string.error_incorrectData
            titleBgRes = R.drawable.input_bg_error
        }

        val tempListGD: MutableList<Group.GroupData> = mutableListOf()
        if(mSwtCapSettings.isChecked){
            mEtCapGroupDataMap.forEach {
                it.value.etCapacity.id = "capacity${it.key}".hashCode()
                it.value.etCount.id = "countTotal0${it.key}".hashCode()
                val key = it.key

                it.value.capacityBgRes = R.drawable.input_bg
                it.value.countBgRes = R.drawable.input_bg
                val capacity = it.value.etCapacity.text.toString().toDoubleOrNull()
                val count = if(it.value.etCount.text.toString().isEmpty()) 0 else it.value.etCount.text.toString().toIntOrNull()

                if(capacity == null || capacity == 0.0){
                    errorToastMsg = R.string.error_incorrectData
                    it.value.capacityBgRes = R.drawable.input_bg_error
                }
                if(count == null || count < 0){
                    errorToastMsg = R.string.error_incorrectData
                    it.value.countBgRes = R.drawable.input_bg_error
                }
                if(errorToastMsg == null){
                    val srcGroupData = mSrcListGD.find { it.sid == key}
                    if(srcGroupData != null){
                        val groupData = Group.GroupData(srcGroupData.sid, capacity!!, count!!)
                        groupData.listH.addAll(srcGroupData.listH)
                        tempListGD.add(groupData)
                    }
                }
            }
        } else {
            val capacity = mEtCapDefault.text.toString().toDoubleOrNull()
            if(capacity == null || capacity == 0.0){
                errorToastMsg = R.string.error_incorrectData
                capDefaultBgRes = R.drawable.input_bg_error
            } else {
                mParent.listGMI.forEach {
                    val groupInfo = it
                    val groupData = mSrcListGD.find { it.sid == groupInfo.sid}
                    if(groupData != null){
                        groupData.capacity = capacity
                        tempListGD.add(groupData)
                    }
                }
            }
        }

        if(errorToastMsg == null){
            oJsonOut.put(Component.KEY_TITLE, tempTitle)
            val gdJsonArray = JSONArray()
            tempListGD.forEach {
                val groupData = it
                val srcGroupData = mSrcListGD.find { groupData.sid == it.sid }
                if(srcGroupData != null){
                    val diff = groupData.count - srcGroupData.count
                    if(diff < 0){ // delete
                        (0 until -(diff)).forEach {
                            groupData.listH.removeAt(groupData.listH.lastIndex)
                        }
                    } else if(diff > 0){ // add
                        var historyDate = System.currentTimeMillis()
                        (0 until diff).forEach {
                            groupData.listH.add(Group.History(groupData.sid, historyDate))
                            historyDate += 1
                        }
                    }
                }
                gdJsonArray.put(Group.GroupData.toJson(groupData))
            }
            oJsonOut.put(Component.KEY_GROUPDATA_LIST, gdJsonArray)
        } else {result = errorToastMsg}

        mTitleCnt.setBackgroundResource(titleBgRes)
        if(mSwtCapSettings.isChecked){
            mEtCapGroupDataMap.forEach {
                it.value.etCapacity.setBackgroundResource(it.value.capacityBgRes)
                it.value.etCount.setBackgroundResource(it.value.countBgRes)
            }
        } else{mEtCapDefault.setBackgroundResource(capDefaultBgRes)}
        return result
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        if(outState != null){
            outState.putString(Component.KEY_TITLE, mEtTitle.text.toString())
            outState.putString(CAPDEFAULT, mEtCapDefault.text.toString())
            mEtCapGroupDataMap.forEach {
                outState.putStringArray(it.key, arrayOf(it.value.etCapacity.text.toString(), it.value.etCount.text.toString()))
            }
            outState.putBoolean(CAPSETTINGS_STATE, mCapSettingsIsOpened)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mEtTitle.windowToken,0)
        super.onDismiss(dialog)
    }

    private fun setCapSettingsState(open: Boolean){
        mCapDefaultCnt.visibility = if(open) View.GONE else View.VISIBLE
        mCapGroupDataCnt.visibility = if(open) View.VISIBLE else View.GONE
        mSwtCapSettings.isChecked = open
    }

    private fun CompoundButton.onOpenCapSettings(isChecked: Boolean){
        setCapSettingsState(isChecked)
    }

    inner class TitleInputListener : EditTextWatcher(){
        override fun afterTextChanged(s: Editable?) {
            try{
                val interpolatedCapacity = rwi.getCapacityICapILen(s.toString().toFloat(), mRwLength)
                mEtCapDefault.setText(interpolatedCapacity.toString())
                mEtCapGroupDataMap.forEach {
                    it.value.etCapacity.setText(interpolatedCapacity.toString())
                }
            } catch (nfe: NumberFormatException){
                log("number format exception")
            }
        }
    }

    inner class CapDefaultInputListener : EditTextWatcher(){
        override fun afterTextChanged(s: Editable?) {
            if(mSwtCapSettings.isChecked && s != null){
                try{
                    mEtCapGroupDataMap.forEach {
                        it.value.etCapacity.setText(s.toString())
                    }
                } catch (nfe: NumberFormatException){
                    log("number format exception")
                }
            }
        }
    }

    inner class EtHub {
        lateinit var etCapacity: EditText
        lateinit var etCount: EditText
        var capacityBgRes:Int = R.drawable.input_bg
        var countBgRes:Int = R.drawable.input_bg
    }
}

class UnivComponentBuilder : ComponentBuilder(), DelayAutoCompleteTextView.OnFilterStateListener {
    companion object {
        const val TAGNAME = "component_creator_universal_ui"
        const val CAPDEFAULT = "capacity_default"
        const val CAPSETTINGS_STATE = "capacity_settings_state"
    }

    lateinit var mTitleCnt: LinearLayout
    lateinit var mAcTitle: DelayAutoCompleteTextView
    lateinit var mAcTitleClearBtn: Button
    lateinit var mSwtCapSettings: Switch
    lateinit var mCapDefaultCnt: LinearLayout
    lateinit var mEtCapDefault: EditText
    lateinit var mCapGroupDataCnt: LinearLayout
    var mEtCapGroupDataMap: MutableMap<String, EtHub> = mutableMapOf()// (<GroupMeta.sid, EditText>)
    val mSrcListGD: MutableList<Group.GroupData> = mutableListOf()
    var mCapSettingsIsOpened: Boolean = false
    lateinit var mAdapter: AutoCompleteAdapter
    var titleBgRes: Int = R.drawable.input_bg
    var capDefaultBgRes: Int = R.drawable.input_bg

    override fun onPerformFiltering() {
        mAcTitleClearBtn.visibility = View.GONE
    }

    override fun onFilterComplete(count: Int) {
        mAcTitleClearBtn.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAdapter = UnivAutoCompleteAdapter()
        val capJsonArray = oJsonSrc.getJSONArray(Component.KEY_GROUPDATA_LIST)
        (0 until capJsonArray.length()).map { capJsonArray[it] as JSONObject }.forEach {
            val groupData = Group.GroupData.fromJson(it)
            mSrcListGD.add(groupData)
        }
    }

    @Throws(UninitializedPropertyAccessException::class)
    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogcb_ui_univ, null, false)
        mTitleCnt = view.findViewById(R.id.ll_title_container)
        mAcTitle = view.findViewById(R.id.ac_title)
        mAcTitle.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        val title = if(savedInstanceState == null) oJsonSrc.getString(Component.KEY_TITLE) else savedInstanceState.getString(Component.KEY_TITLE)
        mAcTitle.setText(title)
        mAcTitle.loadingIndicator = view.findViewById(R.id.pb_title_autocomplete_wait)
        mAcTitle.mOnFilterStateListener = this
        mAcTitle.threshold = 3
        mAcTitle.setAdapter(mAdapter)
        mAcTitle.setOnItemClickListener { parent, _, position, _ -> onAutoCompleteItemClick(parent, position)}
        mAcTitleClearBtn = view.findViewById(R.id.btn_title_clear)
        mAcTitleClearBtn.setOnClickListener { mAcTitle.setText("", TextView.BufferType.EDITABLE) }
        mSwtCapSettings = view.findViewById(R.id.swt_capsettings)
        mSwtCapSettings.setOnCheckedChangeListener { button, isChecked -> button.onOpenCapSettings(isChecked) }
        mCapDefaultCnt = view.findViewById(R.id.ll_capdefault_container)
        mEtCapDefault = view.findViewById(R.id.et_capdefault)
        mCapGroupDataCnt = view.findViewById(R.id.ll_capgroupdata_container)

        mParent.listGMI.forEach {
            val groupInfo = it
            val capSettingsView = inflater.inflate(R.layout.dialogcb_groupdata_cap, null, false)
            val tvGroupTitle: TextView = capSettingsView.findViewById(R.id.tv_group_title)
            tvGroupTitle.text = it.title
            val etCapacity: EditText = capSettingsView.findViewById(R.id.et_group_capacity)
            etCapacity.id = "capacity${groupInfo.sid}".hashCode()
            val etCount: EditText = capSettingsView.findViewById(R.id.et_group_count)
            etCount.id = "countTotal0${groupInfo.sid}".hashCode()
            val etHub: EtHub
            if(savedInstanceState == null){
                etHub = EtHub()
                etHub.etCapacity = etCapacity
                etHub.etCount = etCount
                val groupData = mSrcListGD.find { it.sid == groupInfo.sid }
                if(groupData != null){
                    etHub.etCapacity.setText(groupData.capacity.toString(), TextView.BufferType.EDITABLE)
                    val count = if(mSrcType == SheetUI.BgType.COMPONENT_EDIT.name) groupData.count.toString() else ""
                    etHub.etCount.setText(count, TextView.BufferType.EDITABLE)
                }
                mEtCapGroupDataMap[groupInfo.sid] = etHub
            } else{
                val arraySavedGD = savedInstanceState.getStringArray(groupInfo.sid)
                etHub = mEtCapGroupDataMap[groupInfo.sid]!!
                etHub.etCapacity.setText(arraySavedGD[0], TextView.BufferType.EDITABLE)
                etHub.etCount.setText(arraySavedGD[1], TextView.BufferType.EDITABLE)
            }

            mCapGroupDataCnt.addView(capSettingsView)
        }

        val capSettingsIsOpened: Boolean
        if(savedInstanceState != null){
            capSettingsIsOpened = savedInstanceState.getBoolean(CAPSETTINGS_STATE)
            mSwtCapSettings.isChecked = capSettingsIsOpened
            mEtCapDefault.setText(savedInstanceState.getString(CAPDEFAULT))
        } else{
            var isDifferent = false
            mSrcListGD.forEach {
                if(mSrcListGD.first().capacity != it.capacity){
                    isDifferent = true
                    return@forEach
                }
            }
            capSettingsIsOpened = isDifferent
            mSwtCapSettings.isChecked = isDifferent
            mEtCapDefault.setText(mSrcListGD.first().capacity.toString())
        }

        mEtCapDefault.setInputListener(CapDefaultInputListener())
        setCapSettingsState(capSettingsIsOpened)
        return view
    }

    override fun checkExtendedData(): Int? {
        var result: Int? = null

        var errorToastMsg: Int? = null
        val tempTitle = mAcTitle.text.toString()
        titleBgRes = R.drawable.input_bg
        capDefaultBgRes = R.drawable.input_bg
        if(!tempTitle.checkPattern(REGEX_PATTERN_FOR_TITLE)){
            errorToastMsg = R.string.error_incorrectData
            titleBgRes = R.drawable.input_bg_error
        }

        val tempListGD: MutableList<Group.GroupData> = mutableListOf()
        if(mSwtCapSettings.isChecked){
            mEtCapGroupDataMap.forEach {
                it.value.capacityBgRes = R.drawable.input_bg
                it.value.countBgRes = R.drawable.input_bg
                val capacity = it.value.etCapacity.text.toString().toDoubleOrNull()
                val count = if(it.value.etCount.text.toString().isEmpty()) 0 else it.value.etCount.text.toString().toIntOrNull()

                log("univ cap: $capacity, countTotal0: $count")

                if(capacity == null || capacity == 0.0){
                    errorToastMsg = R.string.error_incorrectData
                    it.value.capacityBgRes = R.drawable.input_bg_error
                }
                if(count == null || count < 0){
                    errorToastMsg = R.string.error_incorrectData
                    it.value.countBgRes = R.drawable.input_bg_error
                }
                if(errorToastMsg == null){
                    tempListGD.add(Group.GroupData(it.key, capacity!!, count!!))
                }
            }
        } else {
            val capacity = mEtCapDefault.text.toString().toDoubleOrNull()
            if(capacity == null || capacity == 0.0){
                errorToastMsg = R.string.error_incorrectData
                capDefaultBgRes = R.drawable.input_bg_error
            } else {
                mParent.listGMI.forEach {
                    val groupInfo = it
                    val groupData = mSrcListGD.find { it.sid == groupInfo.sid}
                    if(groupData != null){
                        groupData.capacity = capacity
                        tempListGD.add(groupData)
                    }
                }
            }
        }

        if(errorToastMsg == null){
            oJsonOut.put(Component.KEY_TITLE, tempTitle)
            val gdJsonArray = JSONArray()
            tempListGD.forEach {
                val groupData = it
                var historyDate = System.currentTimeMillis()
                (0 until it.count).forEach {
                    groupData.listH.add(Group.History(groupData.sid, historyDate))
                    historyDate += 1
                }
                gdJsonArray.put(Group.GroupData.toJson(groupData))
            }
            oJsonOut.put(Component.KEY_GROUPDATA_LIST, gdJsonArray)
        } else {result = errorToastMsg}

        mTitleCnt.setBackgroundResource(titleBgRes)
        if(mSwtCapSettings.isChecked){
            mEtCapGroupDataMap.forEach {
                it.value.etCapacity.setBackgroundResource(it.value.capacityBgRes)
                it.value.etCount.setBackgroundResource(it.value.countBgRes)
            }
        } else{mEtCapDefault.setBackgroundResource(capDefaultBgRes)}
        return result
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        if(outState != null){
            outState.putString(Component.KEY_TITLE, mAcTitle.text.toString())
            outState.putString(CAPDEFAULT, mEtCapDefault.text.toString())
            mEtCapGroupDataMap.forEach {
                outState.putStringArray(it.key, arrayOf(it.value.etCapacity.text.toString(), it.value.etCount.text.toString()))
            }
            outState.putBoolean(CAPSETTINGS_STATE, mCapSettingsIsOpened)
        }
        super.onSaveInstanceState(outState)
    }

    private fun onAutoCompleteItemClick(parent: AdapterView<*>, position: Int){
        val item = parent.getItemAtPosition(position) as AutoCompleteAdapter.Item
        mAcTitle.setText(item.tmplComponent.title)
        val zeroDoubleStr = resources.getString(R.string.def_zeroDouble)
        val zeroIntStr = resources.getString(R.string.def_zeroInt)
        var capAsMonotonus = zeroDoubleStr
        mParent.listGMI.forEach {
            val groupInfo = it
            val itemGroupInfo = item.parentTmpl.listGMI.find { groupInfo.title == it.title }
            var capacity = zeroDoubleStr
            if(itemGroupInfo != null){ // найден подходящий GroupInfo в шаблоне
                val itemGroupData = item.tmplComponent.findDataOf(itemGroupInfo.sid)
                if(itemGroupData != null){
                    capacity = itemGroupData.capacity.toString()
                }
            }

            mEtCapGroupDataMap[groupInfo.sid]?.etCapacity?.setText(capacity.toString(), TextView.BufferType.EDITABLE)
            mEtCapGroupDataMap[groupInfo.sid]?.etCount?.setText(zeroIntStr, TextView.BufferType.EDITABLE)
            capAsMonotonus = capacity
        }

        val diff = mEtCapGroupDataMap.isDifferentValues()
        mEtCapDefault.setText((if(diff) zeroDoubleStr else capAsMonotonus), TextView.BufferType.EDITABLE)
        setCapSettingsState(diff)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(savedInstanceState != null){
            mAcTitle.post {mAcTitle.dismissDropDown()}
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mAcTitle.windowToken,0)
        super.onDismiss(dialog)
    }

    private fun setCapSettingsState(open: Boolean){
        mCapDefaultCnt.visibility = if(open) View.GONE else View.VISIBLE
        mCapGroupDataCnt.visibility = if(open) View.VISIBLE else View.GONE
        mSwtCapSettings.isChecked = open
    }

    private fun Map<String, EtHub>.isDifferentValues(): Boolean {
        var result = false
        var fval: String? = null
        this.forEach {
            if(fval == null){ fval = it.value.etCapacity.text.toString() }
            if(fval != it.value.etCapacity.text.toString()){
                result = true
                return@forEach
            }
        }
        return result
    }

    private fun CompoundButton.onOpenCapSettings(isChecked: Boolean){
        setCapSettingsState(isChecked)
    }

    inner class UnivAutoCompleteAdapter : AutoCompleteAdapter() {
        override fun findList(constraint: CharSequence?): List<Item> {
            val result: MutableList<Item> = mutableListOf()
            val dbInstance = (activity as MainUI).glob.mNode.database
            val tmplList = dbInstance.getTmpllist(mParent.family)
            tmplList.forEach {
                val tmpl = it
                val tmplComponentList = dbInstance.getTmplComponentList(it.sid)
                tmplComponentList.filter { it.title.contains(constraint.toString(), true) }.forEach {
                    result.add(Item(it, tmpl))
                }
            }
            return result
        }
    }

    inner class EtHub {
        lateinit var etCapacity: EditText
        lateinit var etCount: EditText
        var capacityBgRes:Int = R.drawable.input_bg
        var countBgRes:Int = R.drawable.input_bg
    }

    inner class CapDefaultInputListener : EditTextWatcher(){
        override fun afterTextChanged(s: Editable?) {
            if(s != null){
                try{
                    mEtCapGroupDataMap.forEach {
                        it.value.etCapacity.setText(s.toString())
                    }
                } catch (nfe: NumberFormatException){
                    log("number format exception")
                }
            }
        }
    }
}

abstract class ComponentBuilder : DialogUI(){
    companion object {
        const val SRC_TYPE = "src_type" // тип диалога (create, import, edit)
        const val SRC_DATA = "src_data"
        const val PARENT = "parent_obj"
    }

    abstract fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View
    abstract fun checkExtendedData(): Int? // проверка дочерних данных и возврат ID string-ресурса сообщения об ошибке

    lateinit var mImplementedCnt: ScrollView
    lateinit var mBtnCancel: Button
    lateinit var mBtnPositive: Button

    lateinit var mLayoutInflater: LayoutInflater
    lateinit var mParent: Sheet
    lateinit var mSrcType: String
    lateinit var oJsonSrc: JSONObject
    lateinit var oJsonOut: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenLock(true)
        mSrcType = arguments.getString(SRC_TYPE)
        oJsonSrc = getSrcJson()
        mParent = Sheet.fromJson(JSONObject(arguments.getString(PARENT)))
        mLayoutInflater = LayoutInflater.from(activity)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: LinearLayout = inflater?.inflate(R.layout.dialogcb_ui, container, false) as LinearLayout
        try{
            val titleBar = view.findViewById<TextView>(R.id.dialog_title)
            titleBar.setText(when(mSrcType){
                SheetUI.BgType.COMPONENT_EDIT.name -> R.string.title_dialogComponentEdit
                else -> R.string.title_dialogComponentCreate
            })

            mImplementedCnt = view.findViewById(R.id.implemented_comtainer)
            mImplementedCnt.addView(getExtendedView(inflater, savedInstanceState))
            mBtnCancel = view.findViewById(R.id.action_close_dialogui)
            mBtnCancel.setOnClickListener { onClickCancelAction() }
            mBtnPositive = view.findViewById(R.id.action_positive)
            mBtnPositive.setOnClickListener { onClickPositiveAction() }
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        screenLock(false)
    }

    private fun onClickCancelAction(){
        dismiss()
    }

    private fun onClickPositiveAction(){
        oJsonOut = createJsonOut()
        var errorToastMsg: Int? = null
        val childError = checkExtendedData()
        if(childError != null){
            errorToastMsg = childError
        }

        if(errorToastMsg == null){
            val component: Component = Component.fromJson(oJsonOut)
            val glob = (activity as MainUI).glob
            glob.mSheetUI.startBg(TPNode.Task(mSrcType, {
                when(mSrcType){
                    SheetUI.BgType.COMPONENT_EDIT.name -> onEditComponent(glob, component)
                    SheetUI.BgType.COMPONENT_CREATE.name -> onCreateComponent(glob, component)
                    else -> false
                }
            }))
            dismiss()
        } else{
            toast(activity, errorToastMsg, Toast.LENGTH_SHORT)
        }
    }

    private fun onEditComponent(glob: Glob, component: Component): Boolean {
        val fbBundle = Bundle()
        fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "onEditComponent")
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT, component.title)
        glob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)
        return glob.mSheetUI.mComponentOperator.edit(component)
    }

    private fun onCreateComponent(glob: Glob, component: Component): Boolean {
        val fbBundle = Bundle()
        fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "onCreateComponent")
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT, component.title)
        glob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)
        return glob.mSheetUI.mComponentOperator.create(component)
    }

    private fun getSrcJson(): JSONObject = JSONObject(arguments.getString(SRC_DATA))

    private fun createJsonOut(): JSONObject {
        val out = JSONObject()
        val createDate: Long = if(mSrcType == SheetUI.BgType.COMPONENT_EDIT.name) oJsonSrc.getLong(Component.KEY_CREATE_DATE) else System.currentTimeMillis()
        val changeDate: Long = System.currentTimeMillis()
        out.put(Component.KEY_SID, Component.PREFIX + createDate)
        out.put(Component.KEY_CREATE_DATE, createDate)
        out.put(Component.KEY_CHANGE_DATE, changeDate)
        out.put(Component.KEY_TITLE, oJsonSrc.getString(Component.KEY_TITLE))
        out.put(Component.KEY_GROUPDATA_LIST, oJsonSrc.getJSONArray(Component.KEY_GROUPDATA_LIST))
        return out
    }

    private fun screenLock(locked: Boolean){
        if(locked){
            val confOrientation = resources.configuration.orientation
            if(confOrientation == Configuration.ORIENTATION_LANDSCAPE){
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else{
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
        } else{
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    abstract inner class AutoCompleteAdapter : BaseAdapter(), Filterable {
        inner class Item(val tmplComponent: TmplComponent, val parentTmpl: Tmpl)
        abstract fun findList(constraint: CharSequence?): List<Item>

        private val mInflater: LayoutInflater = LayoutInflater.from(activity)
        private val itemList: MutableList<Item> = mutableListOf()

        override fun getItem(position: Int): Item = itemList[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getCount(): Int = itemList.size
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: mInflater.inflate(R.layout.dialogcb_autocomplete_item, parent, false)
            val item = itemList[position]
            val componentTitle: TextView = view.findViewById(R.id.component_title)
            componentTitle.text = item.tmplComponent.title
            val parentTitle: TextView = view.findViewById(R.id.parent_title)
            parentTitle.text = item.parentTmpl.title
            return view
        }

        override fun getFilter(): Filter {
            return object : Filter(){
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val filterResults = FilterResults()
                    if (constraint != null) {
                        val items: List<Item> = findList(constraint)
                        filterResults.values = items
                        filterResults.count = items.size
                    }
                    return filterResults
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    if (results != null && results.count > 0) {
                        itemList.clear()
                        itemList.addAll(results.values as List<Item>)
                        notifyDataSetChanged()
                    } else {notifyDataSetInvalidated()}
                }
            }
        }
    }

}



class RwSheetBuilder : SheetBuilder() {
    companion object {
        const val TAGNAME = "rw_sheet_builder"
        const val RECALC_VISIBILITY = "recalc_visibility"
        const val CHB_EVEN_STATE = "even_state"
        const val CHB_ODD_STATE = "odd_state"
        const val TP_PREF_RWLEN = "pref_rwlen"
    }

    lateinit var mRwCnt: LinearLayout
    lateinit var mSbRwLength: SeekBar
    lateinit var mEtRwLength: EditText
    lateinit var mTvRecalcComponents: TextView
    lateinit var mChbEven: CheckBox
    lateinit var mChbOdd: CheckBox
    lateinit var mExt: org.json.JSONObject
    lateinit var mStandart: String
    lateinit var rwi: RWI
    var rwLengthBgRes: Int = R.drawable.input_bg

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mExt = oJsonSrc.getJSONObject(Sheet.KEY_EXT)
        if(!mExt.has(RWI.KEY_ODDEVEN_FILTER)){
            mExt.put(RWI.KEY_ODDEVEN_FILTER, RWI.ALL_COMPONENTS)
        }

        mStandart = mExt.getString(RWI.KEY_STANDART)
        rwi = RWI(activity, mStandart)
        mPrefNameRemGroups = when(mStandart){
            RWI.St.ISO_4480_83.name -> TP_PREFS_RW_ISO448083_GROUPS
            RWI.St.GOST_2708_75.name -> TP_PREFS_RW_GOST270875_GROUPS
            else -> TP_PREFS_UNIV_GROUPS
        }
    }

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogsb_ui_rw, null, false)
        mRwCnt = view.findViewById(R.id.rw_container)
        mRwCnt.setBackgroundResource(rwLengthBgRes)
        mSbRwLength = view.findViewById(R.id.sb_rw_length)
        mSbRwLength.max = rwi.getMaxLen().toInt() * 10
        mSbRwLength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(seekBar: SeekBar){mEtRwLength.clearFocus()}
            override fun onStopTrackingTouch(seekBar: SeekBar){
                val bgType = arguments.getString(SRC_TYPE)
                if((bgType == SheetOrderUI.BgType.SHEET_EDIT.name || bgType == SheetOrderUI.BgType.SHEET_IMPORT.name)
                        && !mEtRwLength.isFocused){
                    val srcRwLength = (mExt.getDouble(RWI.KEY_LENGTH) * 10).toInt()
                    val isChanged = (seekBar.progress > srcRwLength || seekBar.progress < srcRwLength)
                    mTvRecalcComponents.visibility = if(isChanged) View.VISIBLE else View.GONE
                }
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean){
                if(fromUser && mEtRwLength.isFocused){ mEtRwLength.clearFocus() }
                if(!mEtRwLength.isFocused){
                    mEtRwLength.setText((progress / 10.0).toString())
                }
            }
        })
        mEtRwLength = view.findViewById(R.id.et_rw_length)
        mEtRwLength.setBackgroundResource(rwLengthBgRes)
        mEtRwLength.addTextChangedListener(RwLengthInputListener())
        mTvRecalcComponents = view.findViewById(R.id.recalc_components_warn)
        mChbEven = view.findViewById(R.id.even_flag)
        mChbEven.setOnCheckedChangeListener { buttonView, isChecked -> onChangeOddEven(buttonView, isChecked) }
        mChbOdd = view.findViewById(R.id.odd_flag)
        mChbOdd.setOnCheckedChangeListener { buttonView, isChecked -> onChangeOddEven(buttonView, isChecked) }

        if(savedInstanceState == null){
            val rwLength = mExt.getDouble(RWI.KEY_LENGTH).toFloat()
            mSbRwLength.progress = (rwLength * 10).toInt()
            mEtRwLength.setText(rwLength.toString())
            val oddevenState = mExt.getInt(RWI.KEY_ODDEVEN_FILTER)
            mChbEven.isChecked = (oddevenState == RWI.EVEN_COMPONENTS) || (oddevenState == RWI.ALL_COMPONENTS)
            mChbOdd.isChecked = (oddevenState == RWI.ODD_COMPONENTS) || (oddevenState == RWI.ALL_COMPONENTS)
            mChbEven.isEnabled = mChbOdd.isChecked
            mChbOdd.isEnabled = mChbEven.isChecked
        }else{
            val rwLength = savedInstanceState.getFloat(RWI.KEY_LENGTH)
            mSbRwLength.progress = (rwLength * 10).toInt()
            mEtRwLength.setText(rwLength.toString())
            mTvRecalcComponents.visibility = savedInstanceState.getInt(RECALC_VISIBILITY)
            mChbEven.isChecked = savedInstanceState.getBoolean(CHB_EVEN_STATE)
            mChbOdd.isChecked = savedInstanceState.getBoolean(CHB_ODD_STATE)
            mChbEven.isEnabled = mChbOdd.isChecked
            mChbOdd.isEnabled = mChbEven.isChecked
        }
        return view
    }

    override fun checkExtendedData(): Int? {
        var result: Int? = null
        val tmpRwLength = mEtRwLength.text.toString()
        var errorToastMsg: Int? = null
        rwLengthBgRes = R.drawable.input_bg

        val length = tmpRwLength.toDoubleOrNull()
        if(length == null || length < rwi.getMinLen() || length > rwi.getMaxLen()){
            errorToastMsg = R.string.error_rwDataLong
            rwLengthBgRes = R.drawable.input_bg_error
        }

        val oddevenFilter = if(mChbEven.isChecked && !mChbOdd.isChecked){
            RWI.EVEN_COMPONENTS
        } else if(!mChbEven.isChecked && mChbOdd.isChecked){
            RWI.ODD_COMPONENTS
        } else{
            RWI.ALL_COMPONENTS
        }

        if(errorToastMsg == null){
            length as Double
            try{
                val editor = (activity as MainUI).glob.mPrefs.edit()
                editor.putFloat(RwSheetBuilder.TP_PREF_RWLEN, length.toFloat())
                editor.apply()
            } catch (e: NullPointerException){}

            val ext = mExt.also { it.put(RWI.KEY_LENGTH, length.toFloat()) }
                    .also { it.put(RWI.KEY_ODDEVEN_FILTER, oddevenFilter) }
            oJsonOut.put(Sheet.KEY_EXT, ext)
        } else {
            result = errorToastMsg
        }
        mRwCnt.setBackgroundResource(rwLengthBgRes)
        mEtRwLength.setBackgroundResource(rwLengthBgRes)
        return result
    }

    override fun onImportSheet(glob: Glob, out: Sheet): Boolean {
        var result = false
        if(super.onCreateSheet(glob, out)){
            val successList: MutableList<Boolean> = mutableListOf()
            val date = oJsonSrc.getLong(Sheet.KEY_CREATE_DATE)
            val tmplComponentList = glob.mNode.database.getTmplComponentList(Tmpl.PREFIX + date)
            val keyList = rwi.standartDataJson.keys.toMutableList()
            keyList.sortBy { it.toString().toInt() }

            tmplComponentList.forEach {
                val tmplComponent = it
                val tmpListGD: MutableList<Group.GroupData> = mutableListOf()
                it.listGD.forEach{
                    val groupDataSid = it.sid
                    if(out.listGMI.find{it.sid == groupDataSid} != null){
                        tmpListGD.add(it)
                    }
                }
                out.listGMI.forEach {
                    val groupInfoSid = it.sid
                    if(tmpListGD.find { it.sid == groupInfoSid } == null){
                        tmpListGD.add(Group.GroupData(groupInfoSid, tmplComponent.listGD[0].capacity, 0))
                    }
                }

                val key = keyList.find { it == tmplComponent.title }
                if(key != null){
                    if(mTvRecalcComponents.visibility == View.VISIBLE){
                        val rwLength = mExt.getDouble(RWI.KEY_LENGTH).toFloat()
                        val icap = rwi.getCapacityICapILen(key.toString().toFloat(), rwLength)
                        tmpListGD.forEach {
                            it.capacity = icap
                        }
                    }
                }

                val co = glob.mNode.ComponentOperator(out)
                successList.add(co.create(Component(it.title, tmpListGD)))
            }
            result = (successList.size == tmplComponentList.size)
        }
        return result
    }

    override fun onCreateSheet(glob: Glob, out: Sheet): Boolean {
        var result = false

        if(super.onCreateSheet(glob, out)){
            val successList: MutableList<Boolean> = mutableListOf()
            val keyList = rwi.standartDataJson.keys.toMutableList()
            keyList.sortBy { it.toString().toInt() }

            val rwLength = mExt.getDouble(RWI.KEY_LENGTH).toFloat()
            keyList.forEach {
                val key = it.toString()
                val icap = rwi.getCapacityForILen(key.toInt(), rwLength)

                val tmpListGD: MutableList<Group.GroupData> = mutableListOf()
                out.listGMI.forEach {
                    tmpListGD.add(Group.GroupData(it.sid, icap, 0))
                }

                val co = glob.mNode.ComponentOperator(out)
                successList.add(co.create(Component(key, tmpListGD)))
            }
            result = (successList.size == keyList.size)
        }
        return result
    }

    override fun onEditSheet(glob: Glob, out: Sheet): Boolean {
        var result = false

        if(glob.mSheetOrderUI.sheetOperator.edit(out)){
            val successList: MutableList<Boolean> = mutableListOf()
            val componentList = glob.mNode.database.getComponentList(out.sid)
            val keyList = rwi.standartDataJson.keys.toMutableList()
            keyList.sortBy { it.toString().toInt() }

            componentList.forEach { // изменение данных в изделиях (добавление/удаление GroupData)
                val component = it
                // изменение ComponentList в редактируемой таблице
                val tmpListGD: MutableList<Group.GroupData> = mutableListOf()
                component.listGD.forEach{
                    val groupDataSid = it.sid
                    if(out.listGMI.find{it.sid == groupDataSid} != null){
                        tmpListGD.add(it)
                    }
                }
                out.listGMI.forEach {
                    val groupInfoSid = it.sid
                    if(tmpListGD.find { it.sid == groupInfoSid } == null){
                        tmpListGD.add(Group.GroupData(groupInfoSid, component.listGD[0].capacity, 0))
                    }
                }

                val key = keyList.find { it == component.title }
                if(key != null){
                    if(mTvRecalcComponents.visibility == View.VISIBLE){
                        val rwLength = mExt.getDouble(RWI.KEY_LENGTH).toFloat()
                        val icap = rwi.getCapacityICapILen(key.toString().toFloat(), rwLength)
                        tmpListGD.forEach {
                            it.capacity = icap
                        }
                    }
                }

                it.listGD.clear()
                it.listGD.addAll(tmpListGD)
                val co = glob.mNode.ComponentOperator(out)
                successList.add(co.edit(component))
            }
            result = true
        }
        return result
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        try{
            val rwLength = (mEtRwLength.text.toString()).toFloat()
            outState?.putFloat(RWI.KEY_LENGTH, rwLength)
            outState?.putInt(RECALC_VISIBILITY, mTvRecalcComponents.visibility)
            outState?.putBoolean(CHB_EVEN_STATE, mChbEven.isChecked)
            outState?.putBoolean(CHB_ODD_STATE, mChbOdd.isChecked)
        } catch(e: NumberFormatException){
            log("Error NumberFormatException RwSheetBuilder")
        }
        super.onSaveInstanceState(outState)
    }

    private fun onChangeOddEven(button: CompoundButton, checked: Boolean){
        if(button.id == R.id.even_flag){
            mChbOdd.isEnabled = checked
        } else if(button.id == R.id.odd_flag){
            mChbEven.isEnabled = checked
        }
    }

    inner class RwLengthInputListener : EditTextWatcher() {
        override fun afterTextChanged(e: Editable?) {
            val bgType = arguments.getString(SRC_TYPE)
            try{
                if((bgType == SheetOrderUI.BgType.SHEET_EDIT.name || bgType == SheetOrderUI.BgType.SHEET_IMPORT.name) && mEtRwLength.isFocused){
                    val srcRwLength = mExt.getDouble(RWI.KEY_LENGTH)
                    val isChanged = ((mEtRwLength.text.toString()).toDouble() > srcRwLength || (mEtRwLength.text.toString()).toDouble() < srcRwLength)
                    mTvRecalcComponents.visibility = if(isChanged) View.VISIBLE else View.GONE
                }
                val rwLength = (mEtRwLength.text.toString()).toDouble()
                mSbRwLength.progress = (rwLength * 10).toInt()
            } catch(e: NumberFormatException){
                log("Error NumberFormatException RwSheetBuilder")
            }
        }
    }
}

class UnivSheetBuilder : SheetBuilder() {
    companion object {
        val TAGNAME = "universal_sheet_builder"
    }

    lateinit var mUnitCnt: LinearLayout
    lateinit var mEtUnit: EditText
    lateinit var mBtnUnitClear: Button
    var unitBgRes: Int = R.drawable.input_bg

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefNameRemGroups = TP_PREFS_UNIV_GROUPS
    }

    override fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogsb_ui_univ, null, false)
        mUnitCnt = view.findViewById(R.id.sheet_unit_container)
        mUnitCnt.setBackgroundResource(unitBgRes)
        mEtUnit = view.findViewById(R.id.et_unit)
        mEtUnit.setText( if(savedInstanceState != null) savedInstanceState.getString(Sheet.KEY_UNIT) else oJsonSrc.getString(Sheet.KEY_UNIT) )
        mEtUnit.addTextChangedListener(UnitInputListener())
        mBtnUnitClear = view.findViewById(R.id.sheet_unit_clear)
        mBtnUnitClear.setOnClickListener { mEtUnit.setText("", TextView.BufferType.EDITABLE) }
        return view
    }

    override fun checkExtendedData(): Int? {
        var result: Int? = null
        val tmpUnit = mEtUnit.text.toString()
        var errorToastMsg: Int? = null
        unitBgRes = R.drawable.input_bg
        if(!tmpUnit.checkPattern(REGEX_PATTERN_FOR_UNIT)){
            errorToastMsg = R.string.error_incorrectData
            unitBgRes = R.drawable.input_bg_error
        }

        if(errorToastMsg == null){
            oJsonOut.put(Sheet.KEY_UNIT, tmpUnit)
        } else {
            result = R.string.error_incorrectData
        }
        mUnitCnt.setBackgroundResource(unitBgRes)
        return result
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putString(Sheet.KEY_UNIT, mEtUnit.text.toString())
        super.onSaveInstanceState(outState)
    }

    inner class UnitInputListener : EditTextWatcher() {
        override fun afterTextChanged(e: Editable?) {
            if(e != null){
                oJsonSrc.put(Sheet.KEY_UNIT, e.toString())
            }
        }
    }
}

abstract class SheetBuilder : DialogUI() {
    companion object {
        val SRC_TYPE = "src_type" // тип диалога (create, import, edit)
        val SRC_DATA = "src_data"
    }

    abstract fun getExtendedView(inflater: LayoutInflater, savedInstanceState: Bundle?): View
    abstract fun checkExtendedData(): Int? // проверка дочерних данных и возврат ID string-ресурса сообщения об ошибке

    lateinit var mSheetTitleContainer: LinearLayout
    lateinit var mEtTitle: EditText
    lateinit var mBtnTitleClear: Button
    lateinit var mEtComment: EditText
    lateinit var mRvGroupList: RecyclerView
    lateinit var mBtnAddGroup: Button // add group
    lateinit var mBtnPrice: Button
    lateinit var mBtnRemGroups: Button
    lateinit var mContentPager: WrappingViewPager
    lateinit var mPageIndicator0: View
    lateinit var mPageIndicator1: View
    lateinit var mBtnCancel: Button
    lateinit var mBtnPositive: Button
    lateinit var oJsonSrc: JSONObject // входные данные (для edit-режима и default-значений)
    lateinit var oJsonOut: JSONObject // json-донор для sheet
    val groupAdapter: GroupListAdapter by lazy { GroupListAdapter() }
    val pageListener: PageListener by lazy { PageListener() }
    var titleBgRes: Int = R.drawable.input_bg
    private var mRemGroupsState = false
    var mPrefNameRemGroups = TP_PREFS_UNIV_GROUPS

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putString(Sheet.KEY_TITLE, mEtTitle.text.toString())
        outState?.putString(Sheet.KEY_COMMENT, mEtComment.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oJsonSrc = getSrcJson()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: LinearLayout = inflater?.inflate(R.layout.dialogsb_ui, container, false) as LinearLayout
        val titleBar = view.findViewById<TextView>(R.id.dialog_title)
        titleBar.setText(when(arguments.getString(SRC_TYPE)){
            SheetOrderUI.BgType.SHEET_EDIT.name -> R.string.title_dialogSheetEdit
            SheetOrderUI.BgType.SHEET_IMPORT.name -> R.string.title_dialogSheetImport
            else -> R.string.title_dialogSheetCreate
        })

        mBtnCancel = view.findViewById(R.id.action_close_dialogui)
        mBtnCancel.setOnClickListener { onClickCancelAction() }
        mBtnPositive = view.findViewById(R.id.action_positive)
        mBtnPositive.setOnClickListener { onClickPositiveAction() }

        val pageSheetInfo: ScrollView = inflater.inflate(R.layout.dialogsb_vp_leftpage, null, false) as ScrollView
        (pageSheetInfo.findViewById<LinearLayout>(R.id.implement_container)).addView(getExtendedView(inflater, savedInstanceState))
        mSheetTitleContainer = pageSheetInfo.findViewById(R.id.sheet_title_container)
        mEtTitle = pageSheetInfo.findViewById(R.id.et_title)
        mBtnTitleClear = pageSheetInfo.findViewById(R.id.sheet_title_clear)
        mBtnTitleClear.setOnClickListener { mEtTitle.setText("", TextView.BufferType.EDITABLE) }

        var title = oJsonSrc.getString(Sheet.KEY_TITLE)
        title = title.replace("{dd.MM.yy HH:mm:ss}", "${System.currentTimeMillis().toDateTime(resources.getString(R.string.def_dateFormat0))}")
        oJsonSrc.put(Sheet.KEY_TITLE, title)
        mEtTitle.setText(oJsonSrc.getString(Sheet.KEY_TITLE), TextView.BufferType.EDITABLE)
        mEtComment = pageSheetInfo.findViewById(R.id.et_comment)
        mEtComment.setText(oJsonSrc.getString(Sheet.KEY_COMMENT), TextView.BufferType.EDITABLE)
        val pageGroupList: LinearLayout = inflater.inflate(R.layout.dialogsb_vp_rightpage, null, false) as LinearLayout
        mBtnAddGroup = pageGroupList.findViewById(R.id.btn_add_group)
        mBtnAddGroup.setOnClickListener { onClickAddGroup() }
        mBtnAddGroup.isClickable = (groupAdapter.findEditablePosition() == null)

        mRvGroupList = pageGroupList.findViewById(R.id.grouplist)
        mRvGroupList.layoutManager = LinearLayoutManager(activity)
        mRvGroupList.adapter = groupAdapter

        mBtnPrice = pageGroupList.findViewById(R.id.btn_price)
        mBtnPrice.setBackgroundResource(if(oJsonSrc.getBoolean(Sheet.KEY_PRICE_ENABLED)) R.drawable.sb_price_switcher_ch else R.drawable.sb_price_switcher)
        mBtnPrice.setOnClickListener { onClickPriceSwitcher() }

        mBtnRemGroups = pageGroupList.findViewById(R.id.btn_rem_groups)
        mBtnRemGroups.setBackgroundResource(if(mRemGroupsState) R.drawable.sb_rem_groups_ch else R.drawable.sb_rem_groups)
        mBtnRemGroups.setOnClickListener { onClickRemGroups() }

        mContentPager = view.findViewById(R.id.content_pager)
        mContentPager.adapter = ContentPagerAdapter(pageSheetInfo, pageGroupList)
        mContentPager.currentItem = 0
        mContentPager.addOnPageChangeListener(pageListener)
        mPageIndicator0 = view.findViewById(R.id.pi_0)
        mPageIndicator1 = view.findViewById(R.id.pi_1)
        setStatePageIndicator()

        if(savedInstanceState != null){
            mSheetTitleContainer.setBackgroundResource(titleBgRes)
            mEtTitle.setText(savedInstanceState.getString(Sheet.KEY_TITLE))
            mEtComment.setText(savedInstanceState.getString(Sheet.KEY_COMMENT))
        }
        return view
    }

    private fun onClickAddGroup(){ // добавление новой группы
        groupAdapter.addItemGroup("", true, true)
        groupAdapter.notifyDataSetChanged()
    }

    private fun onClickPriceSwitcher(){
        val priceEnabled = !oJsonSrc.getBoolean(Sheet.KEY_PRICE_ENABLED)
        oJsonSrc.put(Sheet.KEY_PRICE_ENABLED, priceEnabled)
        groupAdapter.notifyDataSetChanged()
        mBtnPrice.setBackgroundResource(if(priceEnabled) R.drawable.sb_price_switcher_ch else R.drawable.sb_price_switcher)
        if(priceEnabled){
            val msgId = resources.getString(R.string.tip_price_enb) + oJsonSrc.getString(Sheet.KEY_UNIT)
            toast(activity, msgId)
        }
    }

    private fun onClickRemGroups(){
        mRemGroupsState = !mRemGroupsState
        mBtnRemGroups.setBackgroundResource(if(mRemGroupsState) R.drawable.sb_rem_groups_ch else R.drawable.sb_rem_groups)
        if(mRemGroupsState){
            val msgId = if(mPrefNameRemGroups == TP_PREFS_UNIV_GROUPS) R.string.tip_remember_univ_groups else R.string.tip_remember_rw_groups
            toast(activity, msgId)
        }
    }

    private fun onClickCancelAction(){
        dismiss()
    }

    private fun onClickPositiveAction(){
        oJsonOut = createOutJson()

        val tmpTitle = mEtTitle.text.toString()
        var errorToastMsg: Int? = null
        titleBgRes = R.drawable.input_bg
        if(!tmpTitle.checkPattern(REGEX_PATTERN_FOR_TITLE)){
            errorToastMsg = R.string.error_incorrectData
            titleBgRes = R.drawable.input_bg_error
        } else {
            val nameList = arguments.getStringArrayList(SHEETNAMES).filter { it == tmpTitle }
            if(nameList.isNotEmpty()){
                errorToastMsg = R.string.error_sheetIsExists
                titleBgRes = R.drawable.input_bg_error
            }
        }

        val tmpComment = mEtComment.text.toString()
        val tmpGroups = groupAdapter.getGroups()
        if(tmpGroups.isEmpty()){
            errorToastMsg = R.string.error_groupListIsEmpty
        }

        val childError = checkExtendedData()
        if(childError != null){
            errorToastMsg = childError
        }

        if(errorToastMsg == null){
            val glob = (activity as MainUI).glob

            val bgType: SheetOrderUI.BgType? = when(arguments.getString(SRC_TYPE)){
                SheetOrderUI.BgType.SHEET_IMPORT.name -> SheetOrderUI.BgType.SHEET_IMPORT
                SheetOrderUI.BgType.SHEET_CREATE.name -> SheetOrderUI.BgType.SHEET_CREATE
                SheetOrderUI.BgType.SHEET_EDIT.name -> SheetOrderUI.BgType.SHEET_EDIT
                else -> null
            }

            val date = System.currentTimeMillis()
            if(bgType == SheetOrderUI.BgType.SHEET_CREATE
                    || bgType == SheetOrderUI.BgType.SHEET_IMPORT) {oJsonOut.put(Sheet.KEY_CREATE_DATE, date)}

            oJsonOut.put(Sheet.KEY_CHANGE_DATE, date)
            oJsonOut.put(Sheet.KEY_TITLE, tmpTitle)
            oJsonOut.put(Sheet.KEY_COMMENT, tmpComment)

            val giJsonArr = JSONArray()
            tmpGroups.forEach { giJsonArr.put(Group.GroupMeta.toJson(it)) }
            if(mRemGroupsState){
                val editor = glob.mPrefs.edit()
                editor.putString(mPrefNameRemGroups, giJsonArr.toString())
                editor.apply()
            }

            oJsonOut.put(Sheet.KEY_GROUPINFO_LIST, giJsonArr)
            val out = Sheet.fromJson(oJsonOut)

            if(bgType != null){
                if(!glob.mSheetOrderUI.isAdded){
                    glob.mSheetOrderUI.isBack = true
                    (activity as MainUI).onClickOpenUI(glob.mSheetOrderUI)
                }

                glob.mSheetOrderUI.startBg(TPNode.Task(bgType.name, {
                    when(bgType){
                        SheetOrderUI.BgType.SHEET_IMPORT -> onImportSheet(glob, out)
                        SheetOrderUI.BgType.SHEET_CREATE -> onCreateSheet(glob, out)
                        SheetOrderUI.BgType.SHEET_EDIT -> onEditSheet(glob, out)
                        else -> false
                    }
                }))
            }
            val fbBundle = Bundle()
            fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
            fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Новый расчёт")
            fbBundle.putString(FirebaseAnalytics.Param.CONTENT, "${out.title}, ${out.family}")
            glob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)

            dismiss()
        } else {
            toast(activity, errorToastMsg, Toast.LENGTH_SHORT)
            mSheetTitleContainer.setBackgroundResource(titleBgRes)
        }
    }

    open fun onImportSheet(glob: Glob, out: Sheet): Boolean {
        var result = false
        if(glob.mSheetOrderUI.sheetOperator.create(out)){
            val successList: MutableList<Boolean> = mutableListOf()
            val date = oJsonSrc.getLong(Sheet.KEY_CREATE_DATE)
            val tmplComponentList = glob.mNode.database.getTmplComponentList(Tmpl.PREFIX + date)

            tmplComponentList.forEach {
                val tmplComponent = it
                val tmpListGD: MutableList<Group.GroupData> = mutableListOf()

                tmplComponent.listGD.forEach{
                    val groupDataSid = it.sid
                    if(out.listGMI.find{it.sid == groupDataSid} != null){
                        tmpListGD.add(it)
                    }
                }

                out.listGMI.forEach {
                    val groupInfoSid = it.sid
                    if(tmpListGD.find { it.sid == groupInfoSid } == null){
                        tmpListGD.add(Group.GroupData(groupInfoSid, tmplComponent.listGD[0].capacity, 0))
                    }
                }

                val co = glob.mNode.ComponentOperator(out)
                successList.add(co.create(Component(it.title, tmpListGD)))
            }
            result = (successList.size == tmplComponentList.size)
        }
        return result
    }

    open fun onCreateSheet(glob: Glob, out: Sheet): Boolean {
        return glob.mSheetOrderUI.sheetOperator.create(out)
    }

    open fun onEditSheet(glob: Glob, out: Sheet): Boolean {
        var result = false
        if(glob.mSheetOrderUI.sheetOperator.edit(out)){
            (glob.mNode.database.getComponentList(out.sid)).forEach { // изменение данных в изделиях (добавление/удаление GroupData)
                // изменение ComponentList в редактируемой таблице
                val component = it
                val tmpListGD: MutableList<Group.GroupData> = mutableListOf()

                component.listGD.forEach{
                    val groupDataSid = it.sid
                    if(out.listGMI.find{it.sid == groupDataSid} != null){
                        tmpListGD.add(it)
                    }
                }

                out.listGMI.forEach {
                    val groupInfoSid = it.sid
                    if(tmpListGD.find { it.sid == groupInfoSid } == null){
                        tmpListGD.add(Group.GroupData(groupInfoSid, component.listGD[0].capacity, 0))
                    }
                }

                it.listGD.clear()
                it.listGD.addAll(tmpListGD)
                glob.mNode.database.editComponent(out.sid, it)
            }
            result = true
        }
        return result
    }

    fun setStatePageIndicator(focusedIndex: Int=0){
        mPageIndicator0.setBackgroundResource(if(focusedIndex == 0) R.color.viewpager_indicator_focused else R.color.viewpager_indicator_back)
        mPageIndicator1.setBackgroundResource(if(focusedIndex ==1) R.color.viewpager_indicator_focused else R.color.viewpager_indicator_back)
    }

    private fun getSrcJson(): JSONObject = JSONObject(arguments.getString(SRC_DATA))

    private fun createOutJson(): JSONObject {
        val json = JSONObject()
        json.put(Sheet.KEY_CREATE_DATE, if(arguments.getString(SRC_TYPE) == SheetOrderUI.BgType.SHEET_IMPORT.name) System.currentTimeMillis() else oJsonSrc.getLong(Sheet.KEY_CREATE_DATE))
        json.put(Sheet.KEY_FML_FILTER, oJsonSrc.getString(Sheet.KEY_FML_FILTER))
        json.put(Sheet.KEY_CHANGE_DATE, oJsonSrc.getLong(Sheet.KEY_CHANGE_DATE))
        json.put(Sheet.KEY_TITLE, oJsonSrc.getString(Sheet.KEY_TITLE))
        json.put(Sheet.KEY_UNIT, oJsonSrc.getString(Sheet.KEY_UNIT))
        json.put(Sheet.KEY_GROUPINFO_LIST, oJsonSrc.getJSONArray(Sheet.KEY_GROUPINFO_LIST))
        json.put(Sheet.KEY_EXT, oJsonSrc.getJSONObject(Sheet.KEY_EXT))
        json.put(Sheet.KEY_COMMENT, oJsonSrc.getString(Sheet.KEY_COMMENT))
        json.put(Sheet.KEY_PRICE_ENABLED, oJsonSrc.getBoolean(Sheet.KEY_PRICE_ENABLED))
        return json
    }

    inner class GroupListAdapter : RecyclerView.Adapter<GroupListAdapter.ItemHolder>(){
        private val itemList: MutableList<Item> = mutableListOf()

        init{
            val jsonGroupList = oJsonSrc.getJSONArray(Sheet.KEY_GROUPINFO_LIST)
            (0 until jsonGroupList.length())
                    .map { Group.GroupMeta.fromJson(jsonGroupList.getJSONObject(it)) }
                    .forEach { itemList.add(Item(it, false, false)) }
        }

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.dialogsb_grouplist_item, parent, false))
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.groupTitle.visibility = if(item.editable) View.GONE else View.VISIBLE
            holder.groupTitle.text = item.groupInfo.title
            holder.groupTitleEt.visibility = if(item.editable) View.VISIBLE else View.GONE
            holder.groupTitleEt.setText(item.groupInfo.title, TextView.BufferType.EDITABLE)
            val priceEnb: Boolean = oJsonSrc.optBoolean(Sheet.KEY_PRICE_ENABLED, false)
            holder.groupPrice.visibility = if(priceEnb && !item.editable) View.VISIBLE else View.GONE
            holder.groupPrice.text = (item.groupInfo.price).toString()
            holder.groupPriceEt.visibility = if(priceEnb && item.editable) View.VISIBLE else View.GONE
            holder.groupPriceEt.setText((item.groupInfo.price).toString(), TextView.BufferType.EDITABLE)
            holder.groupThemeBg.visibility = if(item.full) View.GONE else View.VISIBLE
            holder.groupThemeBg.setBackgroundColor(item.groupInfo.theme.bg)
            holder.groupThemeTxt.setBackgroundColor(item.groupInfo.theme.txt)
            holder.actionPanel.visibility = if(item.full) View.VISIBLE else View.GONE
            holder.actionRemove.setBackgroundResource(if(item.editable) R.drawable.sb_action_group_back else R.drawable.sb_action_group_remove)
            holder.actionEdit.setBackgroundResource(if(item.editable) R.drawable.sb_action_group_apply else R.drawable.sb_action_group_edit)
            holder.actionThemeBg.visibility = if(item.editable) View.GONE else View.VISIBLE
            holder.actionThemeBg.setBackgroundColor(item.groupInfo.theme.bg)
            holder.actionThemeTxt.setBackgroundColor(item.groupInfo.theme.txt)
            holder.rvGroupTheme.visibility = if(item.full && !item.editable && item.themeListOpen) View.VISIBLE else View.GONE
        }

        fun addItemGroup(groupTitle: String, full: Boolean, editable: Boolean){
            itemList.add(0, Item(Group.GroupMeta(groupTitle), full, editable))
        }

        fun removeItemGroup(position: Int){
            if(getGroups().size > 1){
                itemList.removeAt(position)
                notifyDataSetChanged()
                mBtnAddGroup.isClickable = (findEditablePosition() == null)
            } else{
                if(itemList.size > 1){
                    itemList.removeAt(position)
                    notifyDataSetChanged()
                    mBtnAddGroup.isClickable = (findEditablePosition() == null)
                } else{
                    toast(activity, R.string.error_groupRemoveLast)
                }
            }
        }

        fun getGroups(): List<Group.GroupMeta> {
            val list: MutableList<Group.GroupMeta> = mutableListOf()
            itemList.forEach { it -> if(!it.editable) list.add(it.groupInfo) }
            return list
        }

        fun getGroupNames(): List<String> {
            val list: MutableList<String> = mutableListOf()
            itemList.forEach { it -> if(!it.editable) list.add(it.groupInfo.title) }
            return list
        }

        fun findEditablePosition(): Int? {
            var editableIndex: Int? = null
            itemList.forEachIndexed { index, item ->
                if(item.editable){
                    editableIndex = index
                }
            }
            return editableIndex
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val infoPanel: FrameLayout = itemView.findViewById(R.id.infopanel)
            val groupTitle: TextView = itemView.findViewById(R.id.group_title)
            val groupPrice: TextView = itemView.findViewById(R.id.group_price)
            val groupThemeBg: LinearLayout = itemView.findViewById(R.id.group_theme_bg)
            val groupThemeTxt: View = itemView.findViewById(R.id.group_theme_txt)
            val groupTitleEt: EditText = itemView.findViewById(R.id.group_title_editable)
            val groupPriceEt: EditText = itemView.findViewById(R.id.group_price_editable)

            val actionPanel: FrameLayout = itemView.findViewById(R.id.itempanel)
            val actionRemove: Button = itemView.findViewById(R.id.action_remove)
            val actionEdit: Button = itemView.findViewById(R.id.action_edit)
            val actionThemeBg: LinearLayout = itemView.findViewById(R.id.action_group_theme_bg)
            val actionThemeTxt: View = itemView.findViewById(R.id.action_group_theme_txt)

            val rvGroupTheme: RecyclerView = itemView.findViewById(R.id.action_group_theme_list)
            private val adapterTheme: GroupThemeAdapter by lazy { GroupThemeAdapter() }

            init {
                infoPanel.setOnClickListener { changeItemState() }
                groupTitleEt.addTextChangedListener(GroupTitleWatcher())
                groupPriceEt.addTextChangedListener(GroupPriceWatcher())
                actionRemove.setOnClickListener { onClickRemove() }
                actionEdit.setOnClickListener { onClickEdit() }
                actionThemeBg.setOnClickListener { onClickTheme() }
                rvGroupTheme.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                rvGroupTheme.adapter = adapterTheme
            }

            private fun onClickRemove(){ // click remove/cancel button
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(item.editable){ // cancel mode
                        item.groupInfo.title = item.memTitle
                        item.groupInfo.price = item.memPrice
                        if(item.memTitle.isEmpty()){ removeItemGroup(adapterPosition) }
                        else{ notifyDataSetChanged() }
                    } else {
                        removeItemGroup(adapterPosition)
                    }
                }
            }

            private fun onClickEdit(){ // click edit/apply button
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(item.editable){ // apply mode
                        var errorToastMsg: Int? = null
                        var groupTitleBgRes: Int = R.drawable.input_bg
                        var groupPriceBgRes: Int = R.drawable.input_bg

                        val tmpGroupTitle = groupTitleEt.text.toString()
                        if(!(tmpGroupTitle.checkPattern(REGEX_PATTERN_FOR_TITLE))){
                            errorToastMsg = R.string.error_incorrectData
                            groupTitleBgRes = R.drawable.input_bg_error
                        } else {
                            if(getGroupNames().contains(tmpGroupTitle)){
                                errorToastMsg = R.string.error_sheetIsExists
                                groupTitleBgRes = R.drawable.input_bg_error
                            }
                        }

                        val tmpGroupPrice = (groupPriceEt.text.toString()).toDoubleOrNull()
                        if(tmpGroupPrice == null){
                            errorToastMsg = R.string.error_incorrectData
                            groupPriceBgRes = R.drawable.input_bg_error
                        }

                        if(errorToastMsg == null){ // apply successfull
                            item.groupInfo.title = tmpGroupTitle
                            item.groupInfo.price = tmpGroupPrice!!
                            groupTitleEt.setBackgroundResource(groupTitleBgRes)
                            groupPriceEt.setBackgroundResource(groupPriceBgRes)
                            itemList[adapterPosition].setItemEditable(false)
                            itemList.sortBy { it.groupInfo.title }
                            notifyDataSetChanged()

                        } else{ // apply error
                            toast(activity, errorToastMsg, Toast.LENGTH_SHORT)
                            groupTitleEt.setBackgroundResource(groupTitleBgRes)
                            groupPriceEt.setBackgroundResource(groupPriceBgRes)
                        }

                    } else{ // switch to edit mode
                        if(findEditablePosition() == null){
                            itemList[adapterPosition].setItemEditable(true)
                            notifyDataSetChanged()
                        }
                    }
                }
            }

            private fun onClickTheme(){ // click open/close GroupTheme list
                if(RecyclerView.NO_POSITION != adapterPosition){
                    itemList[adapterPosition].themeListOpen = !itemList[adapterPosition].themeListOpen
                    notifyItemChanged(adapterPosition)
                }
            }

            fun onSelectTheme(themePosition: Int){ // select theme
                if(RecyclerView.NO_POSITION != adapterPosition){
                    itemList[adapterPosition].groupInfo.theme = adapterTheme.getTheme(themePosition)
                    itemList[adapterPosition].themeListOpen = false
                    notifyItemChanged(adapterPosition)
                }
            }

            private fun changeItemState(){ // show/hide actionpanel
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(!item.editable){
                        item.full = !(item.full)
                        if(!item.full){ item.themeListOpen = false }
                        notifyItemChanged(adapterPosition)
                    }
                }
            }

            inner class GroupThemeAdapter : RecyclerView.Adapter<GroupThemeAdapter.GroupThemeItemHolder>(){
                private val themeList: MutableList<Group.GroupTheme> = mutableListOf(
                        Group.GroupTheme.C0,
                        Group.GroupTheme.C1,
                        Group.GroupTheme.C2,
                        Group.GroupTheme.C3,
                        Group.GroupTheme.C4,
                        Group.GroupTheme.C5,
                        Group.GroupTheme.C6,
                        Group.GroupTheme.C7,
                        Group.GroupTheme.C8
                )

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupThemeItemHolder = GroupThemeItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.dialogsb_themelist_item, parent, false))
                override fun getItemCount(): Int = themeList.size
                override fun onBindViewHolder(holder: GroupThemeItemHolder, position: Int) {
                    val groupThemeItem = themeList[position]
                    holder.bgColorView.setBackgroundColor(groupThemeItem.bg)
                    holder.txtColorView.setBackgroundColor(groupThemeItem.txt)
                }

                fun getTheme(position: Int) = themeList[position]

                inner class GroupThemeItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
                    val bgColorView: LinearLayout = itemView.findViewById(R.id.bgcolor)
                    val txtColorView: View = itemView.findViewById(R.id.txtcolor)
                    init { bgColorView.setOnClickListener { onClickThemeSelector() } }

                    private fun onClickThemeSelector(){ // click item theme
                        if(RecyclerView.NO_POSITION != adapterPosition){
                            onSelectTheme(adapterPosition)
                        }
                    }
                }
            }

            inner class GroupTitleWatcher : EditTextWatcher(){
                override fun afterTextChanged(e: Editable?) {
                    if(e != null){
                        itemList[adapterPosition].groupInfo.title = e.toString()
                    }
                }
            }

            inner class GroupPriceWatcher : EditTextWatcher(){
                override fun afterTextChanged(e: Editable?) {
                    if(e != null){
                        itemList[adapterPosition].groupInfo.price = (e.toString()).toDoubleOrNull() ?: 0.0
                    }
                }
            }

        }

        inner class Item(val groupInfo: Group.GroupMeta, var full: Boolean=false, var editable: Boolean=false, var themeListOpen: Boolean=false){
            var memTitle: String = ""
            var memPrice: Double = 0.0
            init{ setItemEditable(editable) }

            fun setItemEditable(editable: Boolean){
                mBtnAddGroup.isClickable = !editable
                if(editable){
                    memTitle = groupInfo.title
                    memPrice = groupInfo.price
                }
                this.editable = editable
            }
        }
    }

    inner class PageListener : ViewPager.OnPageChangeListener{
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

        override fun onPageSelected(position: Int) {
            setStatePageIndicator(position)
        }

    }

    class ContentPagerAdapter(page0: View, page1: View) : PagerAdapter() {
        val viewList: MutableList<View> = mutableListOf(page0, page1)
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = viewList[position]
            (container as ViewPager).addView(view)
            return view
        }
        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            super.destroyItem(container, position, `object`)
            (container as ViewPager).removeView((`object` as View))
        }
        override fun isViewFromObject(view: View, `object`: Any): Boolean = (view == `object`)
        override fun getCount(): Int = viewList.size
    }

}



class SortComponentList : DialogUI() {
    companion object {
        val TAGNAME = "sort_ui"
    }

    private lateinit var mOrderByTitle: RadioButton
    private lateinit var mOrderByCreateDate: RadioButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.dialogsort_componentlist, container, false)
        val order = (activity as MainUI).glob.mSheetUI.mOrder
        mOrderByTitle = view.findViewById(R.id.rb_order_by_title)
        mOrderByTitle.isChecked = (SheetUI.ORDER_BY_TITLE == order || SheetUI.ORDER_BY_TITLE_INT == order)
        mOrderByCreateDate = view.findViewById(R.id.rb_order_by_createdate)
        mOrderByCreateDate.isChecked = (SheetUI.ORDER_BY_CREATEDATE == order)
        val btnCancel: Button = view.findViewById(R.id.action_close_dialogui)
        btnCancel.setOnClickListener { onClickCancel() }
        val btnPositive: Button = view.findViewById(R.id.action_positive)
        btnPositive.setOnClickListener { onClickPositive() }
        return view
    }

    fun onClickCancel(){
        dismiss()
    }

    fun onClickPositive(){
        if((activity as MainUI).glob.mSheetUI.mParent.family == Glob.FML_ROUNDWOOD){
            val order: Int = if(mOrderByTitle.isChecked) SheetUI.ORDER_BY_TITLE_INT else SheetUI.ORDER_BY_CREATEDATE
            (activity as MainUI).glob.mSheetUI.setOrder(order)
        } else if((activity as MainUI).glob.mSheetUI.mParent.family == Glob.FML_UNIVERSAL){
            val order: Int = if(mOrderByTitle.isChecked) SheetUI.ORDER_BY_TITLE else SheetUI.ORDER_BY_CREATEDATE
            (activity as MainUI).glob.mSheetUI.setOrder(order)
        }
        dismiss()
    }

}

@TargetApi(Build.VERSION_CODES.KITKAT)
class PrintSheetDialog : DialogUI() {
    companion object {const val TAGNAME = "print_ui"}
    private lateinit var mPrintManager: PrintManager
    private lateinit var mJobName: String
    private lateinit var baseFont: BaseFont
    private lateinit var mRbGroupList: RadioButton
    private lateinit var mRbParticleList: RadioButton
    private lateinit var mChbFull: CheckBox
    private lateinit var mChbRemoveZeroItems: CheckBox

    private lateinit var mTotalCount: String
    private lateinit var mTotalVolume: String
    private lateinit var mTotalCost: String

    private lateinit var caption: Array<String>
    private lateinit var footerText: String
    private var mParticleListPdf: Boolean = false
    private var mFullPdf: Boolean = false
    private var mNonZeroPdf: Boolean = true
    private var mUnit: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mUnit = (activity as MainUI).glob.mSheetUI.mParent.unit
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.dialog_print, container, false)
        val glob = (activity as MainUI).glob
        val family = glob.mFamilyName
        mRbGroupList = view.findViewById(R.id.rb_grouplist)
        mRbParticleList = view.findViewById(R.id.rb_particlelist)
        if(family == Glob.FML_ROUNDWOOD){
            mRbGroupList.setText(R.string.tip_exportAsRwGroupList)
            mRbParticleList.setText(R.string.tip_exportAsRwComponentList)
        }
        mChbFull = view.findViewById(R.id.chb_fulldata)
        mChbRemoveZeroItems = view.findViewById(R.id.chb_remove_zero_items)

        val btnCancel: Button = view.findViewById(R.id.action_close_dialogui)
        btnCancel.setOnClickListener { onClickCancel() }
        val btnPositive: Button = view.findViewById(R.id.action_positive)
        btnPositive.setOnClickListener { onClickPositive() }

        baseFont = BaseFont.createFont("/assets/DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
        mTotalCount = resources.getString(R.string.count0)
        mTotalVolume = "${mUnit ?: resources.getString(R.string.volume1)}:"
        mTotalCost = resources.getString(R.string.cost0)
        caption = arrayOf(
                resources.getString(R.string.component_title),
                resources.getString(R.string.count1),
                (mUnit ?: resources.getString(R.string.volume1)),
                resources.getString(R.string.cost1)
        )
        footerText = resources.getString(R.string.sum0)
        return view
    }

    private fun onClickCancel(){
        dismiss()
    }

    private fun onClickPositive(){
        mParticleListPdf = mRbParticleList.isChecked
        mFullPdf = mChbFull.isChecked
        mNonZeroPdf = mChbRemoveZeroItems.isChecked
        val glob = (activity as MainUI).glob
        mPrintManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        mJobName = "${resources.getString(R.string.app_name)} printing"
        glob.mSheetUI.startBg(TPNode.Task(SheetUI.BgType.TO_PRINT.name, {
            prepareToPrint(glob)
        }))
        dismiss()
    }

    private fun prepareToPrint(glob: Glob): Boolean {
        var result = false
        val sheet = glob.mSheetUI.mParent
        val componentList: List<Component> = glob.mNode.database.getComponentList(sheet.sid)
        var isNotEmpty = false

        if(mParticleListPdf){
            componentList.forEach {
                if(!mNonZeroPdf || (mNonZeroPdf && it.calculateCountAt(sheet.listGMI) > 0)){
                    isNotEmpty = true
                    return@forEach
                }
            }
        } else {
            sheet.listGMI.forEach {
                val groupInfo = it
                var groupSumCount = 0
                componentList.forEach {
                    val groupData = it.findDataOf(groupInfo.sid)
                    groupSumCount += (groupData?.count ?: 0)
                }

                if (!mNonZeroPdf || (mNonZeroPdf && groupSumCount > 0)) {
                    isNotEmpty = true
                    return@forEach
                }
            }
        }

        if(isNotEmpty){
            val printAdapter = SheetPrintAdapter(glob, mJobName)
            mPrintManager.print(mJobName, printAdapter, null)
            result = true
        }
        return result
    }

    inner class SheetPrintAdapter(val glob: Glob, val jobName: String) : PrintDocumentAdapter() {

        override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: PrintDocumentAdapter.WriteResultCallback){
            var output: OutputStream? = null

            try {
                output = FileOutputStream(destination.fileDescriptor)
                generatePrintData(glob, output)
                callback.onWriteFinished(arrayOf<PageRange>(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                log("Unknown Exception FileOutputStream when write to print data")
            } finally {
                try {
                    output?.close()
                } catch (e: IOException) {
                    log("IOException FileOutputStream close()")
                }
            }
        }

        override fun onLayout(oldAttributes: PrintAttributes, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: PrintDocumentAdapter.LayoutResultCallback, extras: Bundle){
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }

            val pdi: PrintDocumentInfo = PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            callback.onLayoutFinished(pdi, true)
        }

        private fun generatePrintData(glob: Glob, fos: FileOutputStream) {
            val sheet = glob.mSheetUI.mParent
            val cellFontBold = Font(baseFont, 9f, Font.BOLD)

            val document = Document()
            PdfWriter.getInstance(document, fos)
            document.open()
            // add title to PDF
            val docTitleFont = Font(baseFont, 14f, Font.BOLD)
            val pDocTitle = Paragraph(sheet.title, docTitleFont)
            pDocTitle.font = docTitleFont
            pDocTitle.alignment = Paragraph.ALIGN_LEFT
            pDocTitle.spacingAfter = 30f
            document.add(pDocTitle)
            // add total data
            val pTotal = Paragraph()
            docTitleFont.size = 9f
            pTotal.font = docTitleFont
            pTotal.spacingAfter = 15f
            val componentList: List<Component> = glob.mNode.database.getComponentList(sheet.sid)
            var allCount = 0
            componentList.forEach {allCount += it.calculateCountAt(sheet.listGMI)}
            pTotal.add( "$mTotalCount  $allCount\n" )
            var allVolume = 0.0
            componentList.forEach {allVolume += it.calculateVolumeAt(sheet.listGMI)}
            pTotal.add( "$mTotalVolume  ${allVolume.round()}\n" )
            var allCost = 0.0
            if(sheet.priceEnabled){
                componentList.forEach {allCost += it.calculateCostAt(sheet.listGMI)}
                pTotal.add( "$mTotalCost  ${allCost.round()}\n" )
            }
            document.add(pTotal)
            // add details table
            val pdfTable = if(mParticleListPdf) fillDataAsParticleList(sheet, componentList) else fillDataAsGroupList(sheet, componentList)

            // add table footer
            val cellFooter = PdfPCell(Phrase(footerText, cellFontBold))
            cellFooter.verticalAlignment = PdfPCell.ALIGN_MIDDLE
            cellFooter.horizontalAlignment = PdfPCell.ALIGN_CENTER
            cellFooter.backgroundColor = BaseColor(190, 190, 190)
            pdfTable.addCell(cellFooter)

            cellFooter.phrase = Phrase(allCount.toString(), cellFontBold)
            pdfTable.addCell(cellFooter)

            cellFooter.phrase = Phrase(allVolume.round().toString(), cellFontBold)
            pdfTable.addCell(cellFooter)

            if(sheet.priceEnabled){
                cellFooter.phrase = Phrase(allCost.round(2).toString(), cellFontBold)
                pdfTable.addCell(cellFooter)
            }

            document.add(pdfTable)
            document.close()
        }

        private fun fillDataAsParticleList(sheet: Sheet, particleList: List<Component>): PdfPTable {
            val cellFontBold = Font(baseFont, 9f, Font.BOLD)
            val subCellFontBold = Font(baseFont, 8f, Font.BOLD)
            val cellFontNormal = Font(baseFont, 9f, Font.NORMAL)
            val subCellFontNormal = Font(baseFont, 8f, Font.NORMAL)

            val colWidthArray = if(sheet.priceEnabled) intArrayOf(3, 2, 2, 2) else intArrayOf(3, 2, 2)
            val table = PdfPTable(colWidthArray.size)
            table.setWidths(colWidthArray)
            table.horizontalAlignment = PdfPTable.ALIGN_LEFT
            table.headerRows = 1


            colWidthArray.forEachIndexed { index, _ ->
                val cellCaption = PdfPCell(Phrase(caption[index], cellFontBold))
                cellCaption.horizontalAlignment = PdfPCell.ALIGN_CENTER
                cellCaption.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                cellCaption.backgroundColor = BaseColor(190, 190, 190)
                table.addCell(cellCaption)
            }

            particleList.forEach {
                val component: Component = it
                if(!mNonZeroPdf || (mNonZeroPdf && component.calculateCountAt(sheet.listGMI) > 0)){
                    val cell = PdfPCell(Phrase(it.title, cellFontBold))
                    cell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                    cell.paddingLeft = 5f
                    cell.backgroundColor = BaseColor(230, 230, 230)
                    table.addCell(cell)

                    cell.phrase = Phrase(it.calculateCountAt(sheet.listGMI).toString(), cellFontNormal)
                    cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                    table.addCell(cell)

                    cell.phrase = Phrase(it.calculateVolumeAt(sheet.listGMI).round().toString(), cellFontNormal)
                    table.addCell(cell)

                    if(sheet.priceEnabled){
                        cell.phrase = Phrase(it.calculateCostAt(sheet.listGMI).round().toString(), cellFontNormal)
                        table.addCell(cell)
                    }

                    if(mFullPdf){
                        sheet.listGMI.forEach {
                            val groupData = component.findDataOf(it.sid)
                            if(groupData != null){
                                if(!mNonZeroPdf || (mNonZeroPdf && groupData.count > 0)){
                                    val subCell = PdfPCell(Phrase(it.title, subCellFontBold))
                                    subCell.paddingLeft = 5f
                                    table.addCell(subCell)

                                    subCell.phrase = Phrase(groupData.count.toString(), subCellFontNormal)
                                    subCell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                                    subCell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                                    table.addCell(subCell)

                                    subCell.phrase = Phrase(groupData.calculateVolume().round().toString(), subCellFontNormal)
                                    table.addCell(subCell)

                                    if(sheet.priceEnabled){
                                        subCell.phrase = Phrase(groupData.calculateCost(it.price).round().toString(), subCellFontNormal)
                                        table.addCell(subCell)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return table
        }

        private fun fillDataAsGroupList(sheet: Sheet, particleList: List<Component>): PdfPTable {
            val cellFontBold = Font(baseFont, 9f, Font.BOLD)
            val subCellFontBold = Font(baseFont, 8f, Font.BOLD)
            val cellFontNormal = Font(baseFont, 9f, Font.NORMAL)
            val subCellFontNormal = Font(baseFont, 8f, Font.NORMAL)

            val colWidthArray = if(sheet.priceEnabled) intArrayOf(3, 2, 2, 2) else intArrayOf(3, 2, 2)
            val table = PdfPTable(colWidthArray.size)
            table.setWidths(colWidthArray)
            table.horizontalAlignment = PdfPTable.ALIGN_LEFT
            table.headerRows = 1

            colWidthArray.forEachIndexed { index, _ ->
                val cellCaption = PdfPCell(Phrase(caption[index], cellFontBold))
                cellCaption.horizontalAlignment = PdfPCell.ALIGN_CENTER
                cellCaption.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                cellCaption.backgroundColor = BaseColor(190, 190, 190)
                table.addCell(cellCaption)
            }

            sheet.listGMI.forEach {
                val groupInfo = it

                var groupSumCount = 0
                particleList.forEach {
                    val groupData = it.findDataOf(groupInfo.sid)
                    groupSumCount += (groupData?.count ?: 0)
                }

                if(!mNonZeroPdf || (mNonZeroPdf && groupSumCount > 0)){
                    val cell = PdfPCell(Phrase(groupInfo.title, cellFontBold))
                    cell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                    cell.paddingLeft = 5f
                    cell.backgroundColor = BaseColor(230, 230, 230)
                    table.addCell(cell)

                    var groupCount = 0
                    var groupVolume = 0.0
                    var groupCost = 0.0
                    val particleCountList: MutableList<Int> = mutableListOf()
                    val particleVolumeList: MutableList<Double> = mutableListOf()
                    val particleCostList: MutableList<Double> = mutableListOf()
                    particleList.forEach {
                        val groupData = it.findDataOf(groupInfo.sid)
                        if(groupData != null){
                            val count = it.calculateCountAt(listOf(groupInfo))
                            val volume = it.calculateVolumeAt(listOf(groupInfo))
                            val cost = it.calculateCostAt(listOf(groupInfo))

                            particleCountList.add(count)
                            particleVolumeList.add(volume)
                            particleCostList.add(cost)

                            groupCount += count
                            groupVolume += volume
                            groupCost += cost
                        }
                    }

                    cell.phrase = Phrase(groupCount.toString(), cellFontNormal)
                    cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                    table.addCell(cell)

                    cell.phrase = Phrase(groupVolume.round().toString(), cellFontNormal)
                    table.addCell(cell)

                    if(sheet.priceEnabled){
                        cell.phrase = Phrase(groupCost.round().toString(), cellFontNormal)
                        table.addCell(cell)
                    }

                    if(mFullPdf){
                        particleList.forEachIndexed { index, particle ->
                            if(!mNonZeroPdf || (mNonZeroPdf && particleCountList[index] > 0)){
                                val subCell = PdfPCell(Phrase(particle.title, subCellFontBold))
                                subCell.paddingLeft = 5f
                                table.addCell(subCell)

                                subCell.phrase = Phrase(particleCountList[index].toString(), subCellFontNormal)
                                subCell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                                subCell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                                table.addCell(subCell)

                                subCell.phrase = Phrase(particleVolumeList[index].round().toString(), subCellFontNormal)
                                table.addCell(subCell)

                                if(sheet.priceEnabled){
                                    subCell.phrase = Phrase(particleCostList[index].round().toString(), subCellFontNormal)
                                    table.addCell(subCell)
                                }
                            }
                        }
                    }
                }
            }
            return table
        }

    }
}

class ExportSheetDialog : DialogUI(), MainUI.PermissionResultListener {
    companion object {
        const val TAGNAME = "export_ui"
        const val UNIT_KEY = "unit"
    }

    private lateinit var baseFont: BaseFont
    private lateinit var mRbGroupList: RadioButton
    private lateinit var mRbParticleList: RadioButton
    private lateinit var mChbFull: CheckBox
    private lateinit var mChbRemoveZeroItems: CheckBox

    private lateinit var mTotalCount: String
    private lateinit var mTotalVolume: String
    private lateinit var mTotalCost: String

    private lateinit var caption: Array<String>
    private lateinit var footerText: String
    private var mParticleListPdf: Boolean = false
    private var mFullPdf: Boolean = false
    private var mNonZeroPdf: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view: View = inflater.inflate(R.layout.dialog_export, container, false)
        val glob = (activity as MainUI).glob
        val family = glob.mFamilyName
        mRbGroupList = view.findViewById(R.id.rb_grouplist)
        mRbParticleList = view.findViewById(R.id.rb_particlelist)
        if(family == Glob.FML_ROUNDWOOD){
            mRbGroupList.setText(R.string.tip_exportAsRwGroupList)
            mRbParticleList.setText(R.string.tip_exportAsRwComponentList)
        }
        mChbFull = view.findViewById(R.id.chb_fulldata)
        mChbRemoveZeroItems = view.findViewById(R.id.chb_remove_zero_items)

        val warningReqPermission = view.findViewById<TextView>(R.id.warning_request_permission)
        warningReqPermission.visibility = if(ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) View.VISIBLE else View.GONE

        val btnCancel: Button = view.findViewById(R.id.action_close_dialogui)
        btnCancel.setOnClickListener { onClickCancel() }
        val btnPositive: Button = view.findViewById(R.id.action_positive)
        btnPositive.setOnClickListener { onClickPositive() }

        baseFont = BaseFont.createFont("/assets/DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
        mTotalCount = resources.getString(R.string.count0)
//        mTotalVolume = "${mUnit ?: resources.getString(R.string.volume1)}:"
        mTotalCost = resources.getString(R.string.cost0)
//        caption = arrayOf(
//                resources.getString(R.string.component_title),
//                resources.getString(R.string.count1),
//                (mUnit ?: resources.getString(R.string.volume1)),
//                resources.getString(R.string.cost1)
//        )
        footerText = resources.getString(R.string.sum0)
        return view
    }

    override fun onPermissionResult(mainIU: MainUI, requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if(requestCode == SettingsUI.REQUEST_WRITE_EXTERNAL_STORAGE){
            if(grantResults != null
                    && grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                mParticleListPdf = mRbParticleList.isChecked
                mFullPdf = mChbFull.isChecked
                val glob = mainIU.glob
                glob.mSheetOrderUI.startBg(TPNode.Task(SheetOrderUI.BgType.SHEETLIST_EXPORT.name, {
                    exportToSdCard(glob)
                }))
            }
        }
    }

    private fun onClickCancel(){
        dismiss()
    }

    private fun onClickPositive(){
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if(permission == PackageManager.PERMISSION_GRANTED){
                mParticleListPdf = mRbParticleList.isChecked
                mFullPdf = mChbFull.isChecked
                mNonZeroPdf = mChbRemoveZeroItems.isChecked
                val glob = (activity as MainUI).glob
                glob.mSheetOrderUI.startBg(TPNode.Task(SheetOrderUI.BgType.SHEETLIST_EXPORT.name, {
                    exportToSdCard(glob)
                }))
            } else{ // запросить разрешение
                (activity as MainUI).requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), SettingsUI.REQUEST_WRITE_EXTERNAL_STORAGE, this)
                dismiss()
            }
        } else {
            toast(activity, R.string.error_sdCardFailed)
            dismiss()
        }
    }

    private fun exportToSdCard(glob: Glob): Boolean {
        val cellFontBold = Font(baseFont, 9f, Font.BOLD)
        val successList: MutableList<Boolean> = mutableListOf()
        val selectedList = glob.mSheetOrderUI.mSheetListAdapter.getSelectedSheets()
        for(sheet in selectedList){

            mTotalVolume = "${sheet.unit}:"
            caption = arrayOf(
                    resources.getString(R.string.component_title),
                    resources.getString(R.string.count1),
                    sheet.unit,
                    resources.getString(R.string.cost1)
            )

            // prepare file
            val appDirectory = glob.mPrefs.getString(SettingsUI.KEY_EXPORT_FOLDER, SettingsUI.EXPORT_FOLDER)
            val dir = File(appDirectory)
            if (!dir.exists()) dir.mkdirs() // create rootPath, if not exists

            val fileTitle = sheet.title.replace(":", "_")
            val fos = FileOutputStream(File(dir, "$fileTitle.pdf"))

            val document = Document()
            PdfWriter.getInstance(document, fos)
            document.open()
            // add title to PDF
            val docTitleFont = Font(baseFont, 14f, Font.BOLD)
            val pDocTitle = Paragraph(sheet.title, docTitleFont)
            pDocTitle.font = docTitleFont
            pDocTitle.alignment = Paragraph.ALIGN_LEFT
            pDocTitle.spacingAfter = 30f
            document.add(pDocTitle)
            // add total data
            val pTotal = Paragraph()
            docTitleFont.size = 9f
            pTotal.font = docTitleFont
            pTotal.spacingAfter = 15f
            val componentList: List<Component> = glob.mNode.database.getComponentList(sheet.sid)
            var allCount = 0
            componentList.forEach {allCount += it.calculateCountAt(sheet.listGMI)}
            pTotal.add( "$mTotalCount  $allCount\n" )
            var allVolume = 0.0
            componentList.forEach {allVolume += it.calculateVolumeAt(sheet.listGMI)}
            pTotal.add( "$mTotalVolume  ${allVolume.round()}\n" )
            var allCost = 0.0
            if(sheet.priceEnabled){
                componentList.forEach {allCost += it.calculateCostAt(sheet.listGMI)}
                pTotal.add( "$mTotalCost  ${allCost.round()}\n" )
            }
            document.add(pTotal)
            // add details table
            val pdfTable = if(mParticleListPdf) fillDataAsParticleList(sheet, componentList) else fillDataAsGroupList(sheet, componentList)
            // add table footer
            val cellFooter = PdfPCell(Phrase(footerText, cellFontBold))
            cellFooter.verticalAlignment = PdfPCell.ALIGN_MIDDLE
            cellFooter.horizontalAlignment = PdfPCell.ALIGN_CENTER
            cellFooter.backgroundColor = BaseColor(190, 190, 190)
            pdfTable.addCell(cellFooter)

            cellFooter.phrase = Phrase(allCount.toString(), cellFontBold)
            pdfTable.addCell(cellFooter)

            cellFooter.phrase = Phrase(allVolume.round().toString(), cellFontBold)
            pdfTable.addCell(cellFooter)

            if(sheet.priceEnabled){
                cellFooter.phrase = Phrase(allCost.round(2).toString(), cellFontBold)
                pdfTable.addCell(cellFooter)
            }

            document.add(pdfTable)
            document.close()
            successList.add(true)
        }

        dismiss()
        return (successList.size == selectedList.size)
    }

    private fun fillDataAsParticleList(sheet: Sheet, particleList: List<Component>): PdfPTable {
        val cellFontBold = Font(baseFont, 9f, Font.BOLD)
        val subCellFontBold = Font(baseFont, 8f, Font.BOLD)
        val cellFontNormal = Font(baseFont, 9f, Font.NORMAL)
        val subCellFontNormal = Font(baseFont, 8f, Font.NORMAL)

        val colWidthArray = if(sheet.priceEnabled) intArrayOf(3, 2, 2, 2) else intArrayOf(3, 2, 2)
        val table = PdfPTable(colWidthArray.size)
        table.setWidths(colWidthArray)
        table.horizontalAlignment = PdfPTable.ALIGN_LEFT
        table.headerRows = 1


        colWidthArray.forEachIndexed { index, _ ->
            val cellCaption = PdfPCell(Phrase(caption[index], cellFontBold))
            cellCaption.horizontalAlignment = PdfPCell.ALIGN_CENTER
            cellCaption.verticalAlignment = PdfPCell.ALIGN_MIDDLE
            cellCaption.backgroundColor = BaseColor(190, 190, 190)
            table.addCell(cellCaption)
        }

        particleList.forEach {
            val component: Component = it
            if(!mNonZeroPdf || (mNonZeroPdf && component.calculateCountAt(sheet.listGMI) > 0)){
                val cell = PdfPCell(Phrase(it.title, cellFontBold))
                cell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                cell.paddingLeft = 5f
                cell.backgroundColor = BaseColor(230, 230, 230)
                table.addCell(cell)

                cell.phrase = Phrase(it.calculateCountAt(sheet.listGMI).toString(), cellFontNormal)
                cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                table.addCell(cell)

                cell.phrase = Phrase(it.calculateVolumeAt(sheet.listGMI).round().toString(), cellFontNormal)
                table.addCell(cell)

                if(sheet.priceEnabled){
                    cell.phrase = Phrase(it.calculateCostAt(sheet.listGMI).round().toString(), cellFontNormal)
                    table.addCell(cell)
                }

                if(mFullPdf){
                    sheet.listGMI.forEach {
                        val groupData = component.findDataOf(it.sid)
                        if(groupData != null){
                            if(!mNonZeroPdf || (mNonZeroPdf && groupData.count > 0)){
                                val subCell = PdfPCell(Phrase(it.title, subCellFontBold))
                                subCell.paddingLeft = 5f
                                table.addCell(subCell)

                                subCell.phrase = Phrase(groupData.count.toString(), subCellFontNormal)
                                subCell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                                subCell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                                table.addCell(subCell)

                                subCell.phrase = Phrase(groupData.calculateVolume().round().toString(), subCellFontNormal)
                                table.addCell(subCell)

                                if(sheet.priceEnabled){
                                    subCell.phrase = Phrase(groupData.calculateCost(it.price).round().toString(), subCellFontNormal)
                                    table.addCell(subCell)
                                }
                            }
                        }
                    }
                }
            }
        }

        return table
    }

    private fun fillDataAsGroupList(sheet: Sheet, particleList: List<Component>): PdfPTable {
        val cellFontBold = Font(baseFont, 9f, Font.BOLD)
        val subCellFontBold = Font(baseFont, 8f, Font.BOLD)
        val cellFontNormal = Font(baseFont, 9f, Font.NORMAL)
        val subCellFontNormal = Font(baseFont, 8f, Font.NORMAL)

        val colWidthArray = if(sheet.priceEnabled) intArrayOf(3, 2, 2, 2) else intArrayOf(3, 2, 2)
        val table = PdfPTable(colWidthArray.size)
        table.setWidths(colWidthArray)
        table.horizontalAlignment = PdfPTable.ALIGN_LEFT
        table.headerRows = 1

        colWidthArray.forEachIndexed { index, _ ->
            val cellCaption = PdfPCell(Phrase(caption[index], cellFontBold))
            cellCaption.horizontalAlignment = PdfPCell.ALIGN_CENTER
            cellCaption.verticalAlignment = PdfPCell.ALIGN_MIDDLE
            cellCaption.backgroundColor = BaseColor(190, 190, 190)
            table.addCell(cellCaption)
        }

        sheet.listGMI.forEach {
            val groupInfo = it

            var groupSumCount = 0
            particleList.forEach {
                val groupData = it.findDataOf(groupInfo.sid)
                groupSumCount += (groupData?.count ?: 0)
            }

            if(!mNonZeroPdf || (mNonZeroPdf && groupSumCount > 0)){
                val cell = PdfPCell(Phrase(groupInfo.title, cellFontBold))
                cell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                cell.paddingLeft = 5f
                cell.backgroundColor = BaseColor(230, 230, 230)
                table.addCell(cell)

                var groupCount = 0
                var groupVolume = 0.0
                var groupCost = 0.0
                val particleCountList: MutableList<Int> = mutableListOf()
                val particleVolumeList: MutableList<Double> = mutableListOf()
                val particleCostList: MutableList<Double> = mutableListOf()
                particleList.forEach {
                    val groupData = it.findDataOf(groupInfo.sid)
                    if(groupData != null){
                        val count = it.calculateCountAt(listOf(groupInfo))
                        val volume = it.calculateVolumeAt(listOf(groupInfo))
                        val cost = it.calculateCostAt(listOf(groupInfo))

                        particleCountList.add(count)
                        particleVolumeList.add(volume)
                        particleCostList.add(cost)

                        groupCount += count
                        groupVolume += volume
                        groupCost += cost
                    }
                }

                cell.phrase = Phrase(groupCount.toString(), cellFontNormal)
                cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                table.addCell(cell)

                cell.phrase = Phrase(groupVolume.round().toString(), cellFontNormal)
                table.addCell(cell)

                if(sheet.priceEnabled){
                    cell.phrase = Phrase(groupCost.round().toString(), cellFontNormal)
                    table.addCell(cell)
                }

                if(mFullPdf){
                    particleList.forEachIndexed { index, particle ->
                        if(!mNonZeroPdf || (mNonZeroPdf && particleCountList[index] > 0)){
                            val subCell = PdfPCell(Phrase(particle.title, subCellFontBold))
                            subCell.paddingLeft = 5f
                            table.addCell(subCell)

                            subCell.phrase = Phrase(particleCountList[index].toString(), subCellFontNormal)
                            subCell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                            subCell.verticalAlignment = PdfPCell.ALIGN_MIDDLE
                            table.addCell(subCell)

                            subCell.phrase = Phrase(particleVolumeList[index].round().toString(), subCellFontNormal)
                            table.addCell(subCell)

                            if(sheet.priceEnabled){
                                subCell.phrase = Phrase(particleCostList[index].round().toString(), subCellFontNormal)
                                table.addCell(subCell)
                            }
                        }
                    }
                }
            }
        }
        return table
    }

}

class FullHistoryUI : DialogUI(), TPNode.ComponentCallback {
    companion object {
        const val TAGNAME = "full_history_ui"
        const val COMPONENT = "component"
        const val GROUPINFO_LIST = "groupinfo_list"
    }

    override fun onComponentUpdate(component: Component) {
        activity.runOnUiThread {
            val sheetUI = (activity as MainUI).glob.mSheetUI
            sheetUI.onComponentUpdate(component)
            mComponent = component
            mAdapter.update(component)
        }
    }
    override fun preLoadFilter(component: Component): Boolean = true

    private lateinit var mFullHistoryRv: RecyclerView
    private lateinit var mAdapter: HistoryAdapter
    private lateinit var mComponent: Component
    val mGroupInfoList: MutableList<Group.GroupMeta> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mComponent = Component.fromJson(JSONObject(arguments.getString(COMPONENT)))
        mAdapter = HistoryAdapter(mComponent.compileHistory())
        val jarr = JSONArray(arguments.getString(GROUPINFO_LIST))
        (0 until jarr.length()).map { Group.GroupMeta.fromJson(jarr.getJSONObject(it)) }.forEach {
            mGroupInfoList.add(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_fullhistory, container, false)
        view.findViewById<TextView>(R.id.dialog_title).text = mComponent.title
        view.findViewById<Button>(R.id.action_close_dialogui).setOnClickListener { dismiss() }
        mFullHistoryRv = view.findViewById(R.id.full_history_rv)
        mFullHistoryRv.layoutManager = LinearLayoutManager(activity)
        mFullHistoryRv.adapter = mAdapter
        return view
    }

    inner class HistoryAdapter(historyList: List<Group.History>) : RecyclerView.Adapter<HistoryAdapter.ItemHolder>() {
        private val itemList: MutableList<Group.History> = mutableListOf()
        init {itemList.addAll(historyList)}

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.co_history_item, parent, false) as LinearLayout
            return ItemHolder(view)
        }

        override fun getItemCount(): Int = itemList.size

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            val groupInfo = mGroupInfoList.find { it.sid == item.sid }
            if(groupInfo != null){
                holder.themeView.setBackgroundColor(groupInfo.theme.bg)
                holder.createDateTv.text = item.createDate.toDateTime(resources.getString(R.string.def_dateFormat0))
                holder.itemView.tag = item
            }
        }

        fun update(component: Component){
            val historyList = component.compileHistory()
            val diffCallback = HistoryListDiffUtil(itemList, historyList)
            val diffResult: DiffUtil.DiffResult = DiffUtil.calculateDiff(diffCallback)

            itemList.clear()
            itemList.addAll(historyList)
            diffResult.dispatchUpdatesTo(this)
            if(itemList.isEmpty()) dismiss()
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val themeView: View = itemView.findViewById(R.id.groupinfo_theme)
            val createDateTv: TextView = itemView.findViewById(R.id.create_date)
            private val removeBtn: Button = itemView.findViewById(R.id.action_delete_history_item)
            init {removeBtn.setOnClickListener { onDeleteHistoryItem(itemView.tag as Group.History) }}

            private fun onDeleteHistoryItem(history: Group.History){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val sheetUI = (activity as MainUI).glob.mSheetUI
                    val co = sheetUI.mComponentOperator

                    val groupData = mComponent.findDataOf(history.sid)
                    if(groupData != null){
                        sheetUI.startBg(TPNode.Task(SheetUI.BgType.COMPONENT_DECREMENT.name, { co.decrementGD(mComponent, groupData, history.createDate, this@FullHistoryUI) }))
                    }
                }
            }
        }

        inner class HistoryListDiffUtil(private val oldList: List<Group.History>, private val newList: List<Group.History>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.createDate == newItem.createDate
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.sid == newItem.sid
            }
        }
    }

}



abstract class DialogUI : DialogFragment(){
    companion object {
        val DIALOG_WIDTH_PORT_FACTORY = 0.98F
        val DIALOG_WIDTH_LAND_FACTORY = 0.70F
        val DIALOG_HEIGHT_PORT_FACTORY = 0.70F
        val DIALOG_HEIGHT_LAND_FACTORY = 0.98F
        var dispWidth = 0
        var dispHeight = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dlg = super.onCreateDialog(savedInstanceState)
        dlg.window.requestFeature(Window.FEATURE_NO_TITLE)
        return dlg
    }

    override fun onStart() {
        super.onStart()
        setDialogSize()
    }

    override fun onDestroyView() {
        if (dialog != null && retainInstance) {
            dialog.setDismissMessage(null)
        }
        super.onDestroyView()
    }

    protected open fun setDialogSize(){
        if(dialog == null){return}

        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        dispWidth = metrics.widthPixels
        dispHeight = metrics.heightPixels

        val factoryDefault = if(isPortrait()) DIALOG_WIDTH_PORT_FACTORY else DIALOG_WIDTH_LAND_FACTORY
        var factory: Float? = getString(R.string.def_dialogWidthFactory).toFloatOrNull()
        factory = if(factory == null) factoryDefault else factory.toFloat()

        val dialogWidth = (dispWidth * factory).toInt()
        dialog.window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    fun isPortrait(): Boolean = resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
