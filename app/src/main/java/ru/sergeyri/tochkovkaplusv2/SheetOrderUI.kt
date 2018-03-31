package ru.sergeyri.tochkovkaplusv2

import android.accounts.Account
import android.accounts.AccountManager
import android.app.FragmentManager
import android.content.Intent
import android.os.Bundle
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.net.toUri
import com.google.firebase.analytics.FirebaseAnalytics
import com.sergeyri.tpcore.Group
import com.sergeyri.tpcore.Sheet
import com.sergeyri.tpcore.TPNode
import com.sergeyri.tpcore.Tmpl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by sergeyri on 8/27/17.
 */
class SheetOrderUI : FragmentUI() {
    enum class Mode{DEFAULT, SELECTION}
    enum class BgType{RELOAD_FAMILY, TMPLLIST_LOAD, SHEETLIST_LOAD, TMPL_SAVE, SHEETLIST_EXPORT, SHEET_IMPORT, SHEET_CREATE, SHEET_EDIT, SHEETLIST_DELETE}
    companion object {
        val TAGNAME = "sheetorder_ui"
        fun getInstance(fragmentManager: FragmentManager): SheetOrderUI {
            var fragment = fragmentManager.findFragmentByTag(TAGNAME)
            if(fragment == null){fragment = SheetOrderUI()}

            return fragment as SheetOrderUI
        }
    }
    
    override val xTagname = TAGNAME
    /* Функционал поиска */
    override val xFilterInputHint: String by lazy{ resources.getString(R.string.hint_filterSheetList) }
    override val xFilterVoiceDescription: String by lazy{ resources.getString(R.string.tip_voiceSheetTitle) }
    override fun filter(key: String) {
        mSheetListAdapter.update(sheetOperator.filter(key))
    }
    /* Конец функционала поиска */
    
    /* Обработка фоновых процессов */
    override fun onBgStart(bgName: String) {
        when(bgName) {
            BgType.TMPLLIST_LOAD.name,
            BgType.SHEETLIST_LOAD.name -> showPD(R.string.app_name)
            BgType.RELOAD_FAMILY.name -> {
                showPD(R.string.msg_progressLoadingData)
            }
            BgType.TMPL_SAVE.name -> {showPD(R.string.msg_progressTmplSave)}
            BgType.SHEETLIST_EXPORT.name -> {showPD(R.string.msg_progressSheetExport)}
            BgType.SHEET_IMPORT.name -> {showPD(R.string.msg_progressSheetImport)}
            BgType.SHEET_CREATE.name -> {showPD(R.string.msg_progressSheetCreate)}
            BgType.SHEET_EDIT.name -> {showPD(R.string.msg_progressSheetEdit)}
            BgType.SHEETLIST_DELETE.name -> {showPD(R.string.msg_progressDelete)}
        }

        if(bgName == BgType.RELOAD_FAMILY.name) setMode(Mode.DEFAULT)
    }

    override fun onBgResult(bgName: String, result: Boolean) {
        when(bgName) {
            BgType.RELOAD_FAMILY.name -> {
                mActionpanelY = 0
                mActionpanel.y = mActionpanelY.toFloat()
                mActionpanel.tmplButtonAdapter.update(tmplOperator.list)
                mActionpanel.updateBuildersAtFamily(xGlob.mFamilyName)
                sheetOperator.list.sortByDescending { it.createDate }
                mSheetListAdapter.update(sheetOperator.list)
            }
            BgType.TMPL_SAVE.name -> {
                setMode(Mode.DEFAULT)
                mActionpanel.tmplButtonAdapter.update(tmplOperator.list)
            }
            BgType.TMPLLIST_LOAD.name -> {
                mActionpanel.tmplButtonAdapter.update(tmplOperator.list)
            }
            BgType.SHEETLIST_LOAD.name -> {
                sheetOperator.list.sortByDescending { it.createDate }
                mSheetListAdapter.update(sheetOperator.list)
            }
            BgType.SHEETLIST_EXPORT.name -> {
                setMode(Mode.DEFAULT)
                toast(activity, R.string.desc_successExportSheet)
            }
            BgType.SHEET_IMPORT.name, BgType.SHEET_CREATE.name -> {
                sheetOperator.list.sortByDescending { it.createDate }
                mSheetListAdapter.update(sheetOperator.list)
                mSheetList.post { mSheetList.layoutManager.smoothScrollToPosition(mSheetList, RecyclerView.State(), 0) }
            }
            BgType.SHEET_EDIT.name -> {
                setMode(Mode.DEFAULT)
                mSheetListAdapter.update(sheetOperator.list)
            }
            BgType.SHEETLIST_DELETE.name -> {
                setMode(Mode.DEFAULT)
                sheetOperator.list.sortByDescending { it.createDate }
                mSheetListAdapter.update(sheetOperator.list)
            }
            else -> log("error unknown result returned in SheetOrderUI")
        }

        hidePD()
    }
    /* Конец обработки фоновых процессов */
    
    /* Обработка нажатия кнопки назад.
    Здесь проверяется и устанавливается Mode.DEFAULT, иначе выполняется MainUI.onBackPressed() */
    override fun onBack(): Boolean {
        var result = false
        if(mMode == Mode.DEFAULT){ result = true }
        else{ setMode(Mode.DEFAULT) }
        return result
    }
    /* Конец обработки нажатия кнопки назад */

    var mGlob: Glob? = null
    lateinit var sheetOperator: TPNode.SheetOperator // оператор для работы со списком Sheet
    lateinit var tmplOperator: TPNode.TmplOperator // оператор для работы со списком Tmpl (для ActionPanel)
    private lateinit var mEmptyStub: ViewStub
    lateinit var mSheetList: RecyclerView // список оболочек Item(Selector, Sheet)
    val mActionpanel: ActionPanel by lazy { ActionPanel() } // панель кнопок операций над списком Sheet
    val mSheetListAdapter: SheetListAdapter by lazy { SheetListAdapter() } // адаптер оболочек Item(Selector, Sheet)

    var mActionpanelY: Int = 0 // y-координата подвижной панели действий
    var mScrollOffset: Int = 0
    var mScrollActivate: Boolean = false
    var mMode: Mode = Mode.DEFAULT
    var isBack: Boolean = false // флаг возврата из SheeUI (для отмены перезагрузки данных)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try{
            if(!isBack){
                xGlob.mFamilyName = xGlob.mPrefs.getString(Glob.FML, Glob.FML_UNIVERSAL)
                tmplOperator = xGlob.mNode.TmplOperator(xGlob.mFamilyName)
                sheetOperator = xGlob.mNode.SheetOperator(xGlob.mFamilyName)
                fmlReload(xGlob.mFamilyName)

                val gmailAccounts: Array<Account> = AccountManager.get(activity).getAccountsByType("com.google")
                fb_user_name = if(gmailAccounts.isNotEmpty()) gmailAccounts.last().name else "unknown"
            }
            isBack = false
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setToolbarTitle(getString(R.string.app_name))
        val view = inflater.inflate(R.layout.so_ui, container, false)
        try {
            mActionpanel.refreshView(view)
            mSheetList = view.findViewById(R.id.sheetlist)
            mSheetList.addOnScrollListener(ScrollListener())
            mSheetList.layoutManager = GridLayoutManager(activity, resources.getInteger(R.integer.sheetlist_columncount))
            mSheetList.adapter = mSheetListAdapter

            mEmptyStub = view.findViewById(R.id.empty)
            val stub = mEmptyStub.inflate()
            stub.findViewById<TextView>(R.id.howto_1).setOnClickListener { openDemo1() }
            stub.findViewById<TextView>(R.id.howto_2).setOnClickListener { openDemo2() }
            mEmptyStub.visibility = if(mSheetListAdapter.itemCount == 0) View.VISIBLE else View.GONE
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    fun fmlReload(familyName: String){
        xGlob.mFamilyName = familyName
        tmplOperator.fml = familyName
        sheetOperator.fml = familyName
        mActionpanel.updateBuildersAtFamily(familyName)
        xGlob.mSheetUI = if(familyName == Glob.FML_ROUNDWOOD)
            RwSheetUI.getInstance(fragmentManager)
        else SheetUI.getInstance(fragmentManager)

        startBg(TPNode.Task(BgType.RELOAD_FAMILY.name, { xGlob.mNode.fmlFilterReload(tmplOperator, sheetOperator) }))
    }

    fun setMode(mode: Mode, notify: Boolean=true): Boolean {
        var result = false
        if(mode != mMode){
            mMode = mode
            if(mode == Mode.DEFAULT){
                mSheetListAdapter.itemList.filter { it.selector }.forEach { it.selector = false }
                mActionpanel.selectionAdapter.notifyDataSetChanged()
            }
            mActionpanel.updateActionPanelMode()
            if(notify){ mSheetListAdapter.notifyDataSetChanged() }
            result = true
        }
        return result
    }

    fun openSheetUI(sheet: Sheet){
        val bundle = Bundle()
        bundle.putString(SheetUI.PARENT, Sheet.toJson(sheet).toString())
        xGlob.mSheetUI.arguments = bundle
        (activity as MainUI).onClickOpenUI(xGlob.mSheetUI)
    }

    fun openBuilderUniv(){
        val json = org.json.simple.JSONObject()
        val date = System.currentTimeMillis()
        json[Sheet.KEY_CREATE_DATE] = date
        json[Sheet.KEY_FML_FILTER] = xGlob.mFamilyName
        json[Sheet.KEY_CHANGE_DATE] = date
        json[Sheet.KEY_TITLE] = xGlob.mPrefs.getString(SettingsUI.KEY_SHEET_TITLE_DEFAULT, SettingsUI.getSheetTitleDefault(activity))
        json[Sheet.KEY_UNIT] = xGlob.mPrefs.getString(SettingsUI.KEY_SHEET_UNIT_DEFAULT, resources.getString(R.string.def_sheetUnit))

        val groupsDefault = JSONArray()
        groupsDefault.put(Group.GroupMetaInfo.toJson(Group.GroupMetaInfo(Group.GroupMetaInfo.getDefaultTitle(activity))))
        val groupsFromMem = xGlob.mPrefs.getString(TP_PREFS_UNIV_GROUPS, groupsDefault.toString())
        json[Sheet.KEY_GROUPINFO_LIST] = JSONArray(groupsFromMem)

        val ext = JSONObject()
        json[Sheet.KEY_EXT] = ext
        json[Sheet.KEY_COMMENT] = ""
        json[Sheet.KEY_PRICE_ENABLED] = false

        val params = Bundle()
        params.putStringArrayList(SHEETNAMES, mSheetListAdapter.getItemTitles())
        params.putString(SheetBuilder.SRC_TYPE, BgType.SHEET_CREATE.name)
        params.putString(SheetBuilder.SRC_DATA, json.toString())

        val builder = UnivSheetBuilder()
        builder.arguments = params
        builder.show(fragmentManager, UnivSheetBuilder.TAGNAME)
    }

    private fun openBuilderGOST270875(){
        val json = org.json.simple.JSONObject()
        val date = System.currentTimeMillis()
        json[Sheet.KEY_CREATE_DATE] = date
        json[Sheet.KEY_FML_FILTER] = xGlob.mFamilyName
        json[Sheet.KEY_CHANGE_DATE] = date

        json[Sheet.KEY_TITLE] = xGlob.mPrefs.getString(SettingsUI.KEY_SHEET_TITLE_DEFAULT, SettingsUI.getSheetTitleDefault(activity))
        json[Sheet.KEY_UNIT] = resources.getString(R.string.def_sheetUnit)

        val groupsDefault = JSONArray()
        val sort1 = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort1))
        sort1.theme = Group.GroupTheme.C1
        groupsDefault.put(Group.GroupMetaInfo.toJson(sort1))
        val sort3 = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort3))
        sort3.theme = Group.GroupTheme.C2
        groupsDefault.put(Group.GroupMetaInfo.toJson(sort3))
        val balance = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort4))
        groupsDefault.put(Group.GroupMetaInfo.toJson(balance))
        val groupsFromMem = xGlob.mPrefs.getString(TP_PREFS_RW_GOST270875_GROUPS, groupsDefault.toString())
        json[Sheet.KEY_GROUPINFO_LIST] = JSONArray(groupsFromMem)

        val ext = JSONObject()
        if(xGlob.mFamilyName == Glob.FML_ROUNDWOOD){
            ext.also { it.put(RWI.KEY_STANDART, RWI.St.GOST_2708_75.name) }
                    .also { it.put(RWI.KEY_LENGTH, 4.0F) }
                    .also { it.put(RWI.KEY_ODDEVEN_FILTER, RWI.ALL_COMPONENTS) }
        }
        json[Sheet.KEY_EXT] = ext
        json[Sheet.KEY_COMMENT] = ""
        json[Sheet.KEY_PRICE_ENABLED] = false

        val params = Bundle()
        params.putStringArrayList(SHEETNAMES, mSheetListAdapter.getItemTitles())
        params.putString(SheetBuilder.SRC_TYPE, BgType.SHEET_CREATE.name)
        params.putString(SheetBuilder.SRC_DATA, json.toString())

        val builder = RwSheetBuilder()
        builder.arguments = params
        builder.show(fragmentManager, RwSheetBuilder.TAGNAME)
    }

    private fun openBuilderISO448083(){
        val json = org.json.simple.JSONObject()
        val date = System.currentTimeMillis()
        json[Sheet.KEY_CREATE_DATE] = date
        json[Sheet.KEY_FML_FILTER] = xGlob.mFamilyName
        json[Sheet.KEY_CHANGE_DATE] = date

        json[Sheet.KEY_TITLE] = xGlob.mPrefs.getString(SettingsUI.KEY_SHEET_TITLE_DEFAULT, SettingsUI.getSheetTitleDefault(activity))
        json[Sheet.KEY_UNIT] = resources.getString(R.string.def_sheetUnit)

        val groupsDefault = JSONArray()
        val sort1 = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort1))
        sort1.theme = Group.GroupTheme.C1
        groupsDefault.put(Group.GroupMetaInfo.toJson(sort1))
        val sort3 = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort3))
        sort3.theme = Group.GroupTheme.C2
        groupsDefault.put(Group.GroupMetaInfo.toJson(sort3))
        val balance = Group.GroupMetaInfo(resources.getString(R.string.def_rwGroupSort4))
        groupsDefault.put(Group.GroupMetaInfo.toJson(balance))
        val groupsFromMem = xGlob.mPrefs.getString(TP_PREFS_RW_ISO448083_GROUPS, groupsDefault.toString())
        json[Sheet.KEY_GROUPINFO_LIST] = JSONArray(groupsFromMem)

        val ext = JSONObject()
        if(xGlob.mFamilyName == Glob.FML_ROUNDWOOD){
            ext.also { it.put(RWI.KEY_STANDART, RWI.St.ISO_4480_83.name) }
                    .also { it.put(RWI.KEY_LENGTH, 4.0F) }
                    .also { it.put(RWI.KEY_ODDEVEN_FILTER, RWI.ALL_COMPONENTS) }
        }
        json[Sheet.KEY_EXT] = ext
        json[Sheet.KEY_COMMENT] = ""
        json[Sheet.KEY_PRICE_ENABLED] = false

        val params = Bundle()
        params.putStringArrayList(SHEETNAMES, mSheetListAdapter.getItemTitles())
        params.putString(SheetBuilder.SRC_TYPE, BgType.SHEET_CREATE.name)
        params.putString(SheetBuilder.SRC_DATA, json.toString())

        val builder = RwSheetBuilder()
        builder.arguments = params
        builder.show(fragmentManager, RwSheetBuilder.TAGNAME)
    }

    fun openTmplBuilder(tmpl: Tmpl){
        data class DialogData(val sheetBuilder: SheetBuilder, val tag: String)
        val json = org.json.simple.JSONObject()
        json[Sheet.KEY_CREATE_DATE] = tmpl.createDate
        json[Sheet.KEY_FML_FILTER] = tmpl.family
        json[Sheet.KEY_CHANGE_DATE] = tmpl.createDate
        json[Sheet.KEY_TITLE] = tmpl.title
        json[Sheet.KEY_UNIT] = tmpl.unit
        val gmiJsonArr = JSONArray()
        tmpl.listGMI.forEach {gmiJsonArr.put(Group.GroupMetaInfo.toJson(it))}
        json[Sheet.KEY_GROUPINFO_LIST] = gmiJsonArr
        json[Sheet.KEY_EXT] = tmpl.ext
        json[Sheet.KEY_COMMENT] = ""
        json[Sheet.KEY_PRICE_ENABLED] = tmpl.priceEnabled

        val params = Bundle()
        params.putStringArrayList(SHEETNAMES, mSheetListAdapter.getItemTitles())
        params.putString(SheetBuilder.SRC_TYPE, BgType.SHEET_IMPORT.name)
        params.putString(SheetBuilder.SRC_DATA, json.toString())

        val (sheetBuilder, tagname) = when(tmpl.family){
            Glob.FML_ROUNDWOOD -> DialogData(RwSheetBuilder(), RwSheetBuilder.TAGNAME)
            else -> DialogData(UnivSheetBuilder(), UnivSheetBuilder.TAGNAME)
        }
        sheetBuilder.arguments = params
        sheetBuilder.show(fragmentManager, tagname)
    }

    fun editSheet(){
        data class DialogData(var dialogUI: DialogUI, var tag: String)
        val selectedSheets = mSheetListAdapter.getSelectedSheets()
        if(selectedSheets.size == 1){
            val sheet = selectedSheets[0]

            val json = org.json.simple.JSONObject()
            json[Sheet.KEY_CREATE_DATE] = sheet.createDate
            json[Sheet.KEY_FML_FILTER] = sheet.family
            json[Sheet.KEY_CHANGE_DATE] = sheet.changeDate
            json[Sheet.KEY_TITLE] = sheet.title
            json[Sheet.KEY_UNIT] = sheet.unit
            val gmiJsonArr = JSONArray()
            sheet.listGMI.forEach {gmiJsonArr.put(Group.GroupMetaInfo.toJson(it))}
            json[Sheet.KEY_GROUPINFO_LIST] = gmiJsonArr
            json[Sheet.KEY_EXT] = sheet.ext
            json[Sheet.KEY_COMMENT] = sheet.comment
            json[Sheet.KEY_PRICE_ENABLED] = sheet.priceEnabled

            val titles: ArrayList<String> = mSheetListAdapter.getItemTitles()
            titles.remove(titles.find { it == sheet.title })

            val (dialogUI, tag) = when(xGlob.mFamilyName){
                Glob.FML_ROUNDWOOD -> DialogData(RwSheetBuilder(), RwSheetBuilder.TAGNAME)
                else -> DialogData(UnivSheetBuilder(), UnivSheetBuilder.TAGNAME)
            }

            val params = Bundle()
            params.putStringArrayList(SHEETNAMES, titles)
            params.putString(SheetBuilder.SRC_TYPE, BgType.SHEET_EDIT.name)
            params.putString(SheetBuilder.SRC_DATA, json.toString())
            dialogUI.arguments = params
            dialogUI.show(fragmentManager, tag)
        }
    }

    fun deleteSheet(){
        if(mSheetListAdapter.getSelectedCount() > 0){
            DeleteSheetUI().show(fragmentManager, DeleteSheetUI.TAGNAME)
        }
    }

    fun exportSheet(){
        if(mSheetListAdapter.getSelectedCount() > 0){
            ExportSheetDialog().show(fragmentManager, ExportSheetDialog.TAGNAME)
        }
    }

    fun saveTmpl(){
        if(mSheetListAdapter.getSelectedCount() > 0){
            SaveTmplUI().show(fragmentManager, SaveTmplUI.TAGNAME)
        }
    }

    private fun openDemo1(){
        val fbBundle = Bundle()
        fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "open demo 1")
        xGlob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)

        val uri = HOWTO_1.toUri()
        val playDemo = Intent(Intent.ACTION_VIEW, uri)
        startActivity(playDemo)
    }

    private fun openDemo2(){
        val fbBundle = Bundle()
        fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
        fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "open demo 2")
        xGlob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)

        val uri = HOWTO_2.toUri()
        val playDemo = Intent(Intent.ACTION_VIEW, uri)
        startActivity(playDemo)
    }

    inner class ActionPanel {
    	private lateinit var acpCnt: FrameLayout
        private var rwBuilderCnt: LinearLayout? = null
        private var rwGOST270875Builder: ImageView? = null
        private var rwISO448083Builder: ImageView? = null
        private var univBuilder: ImageView? = null

        private lateinit var creatorPanelContainer: LinearLayout
        private lateinit var tmplButtonList: RecyclerView
        private lateinit var tmplButtonListLayoutManager: LinearLayoutManager
    	val tmplButtonAdapter: TmplButtonAdapter by lazy { TmplButtonAdapter() }
        private lateinit var selectorPanelContainer: RecyclerView
        private lateinit var selectionLayoutManager: LinearLayoutManager
        val selectionAdapter: SelectionAdapter by lazy { SelectionAdapter() }
        var height: Int = 0

        var y: Float
            get() = acpCnt.y
            set(value) { acpCnt.y = value }

        @Throws(UninitializedPropertyAccessException::class)
    	fun refreshView(rootView: View){
    		acpCnt = rootView.findViewById(R.id.actionpanel)
    		univBuilder = rootView.findViewById(R.id.univ_builder)
            univBuilder?.setOnClickListener { openBuilderUniv() }
            rwBuilderCnt = rootView.findViewById(R.id.rw_builder_container)
            rwGOST270875Builder = rwBuilderCnt?.findViewById(R.id.gost_2708_75_builder)
            rwGOST270875Builder?.setOnClickListener { openBuilderGOST270875() }
            rwISO448083Builder = rwBuilderCnt?.findViewById(R.id.iso_4480_83_builder)
            rwISO448083Builder?.setOnClickListener { openBuilderISO448083() }

    		creatorPanelContainer = rootView.findViewById(R.id.builderpanel)
    		tmplButtonList = rootView.findViewById(R.id.builderpanel_tmpllist)
    		tmplButtonListLayoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
    		tmplButtonListLayoutManager.stackFromEnd = true
            tmplButtonList.layoutManager = tmplButtonListLayoutManager
    		tmplButtonList.adapter = tmplButtonAdapter
    		selectorPanelContainer = rootView.findViewById(R.id.selectorpanel)
    		selectorPanelContainer.setHasFixedSize(true)
            selectionLayoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            selectionLayoutManager.stackFromEnd = true
            selectorPanelContainer.layoutManager = selectionLayoutManager
            selectorPanelContainer.adapter = selectionAdapter

            rootView.post {
                updateBuildersAtFamily(xGlob.mFamilyName)
                updateActionPanelMode()

                tmplButtonListLayoutManager.scrollToPosition(tmplButtonAdapter.itemCount - 1)
                y = mActionpanelY.toFloat()
                height = acpCnt.height
            }
    	}

        fun updateActionPanelMode(){
            creatorPanelContainer.visibility = if(mMode == Mode.SELECTION) View.GONE else View.VISIBLE
            selectorPanelContainer.visibility = if(mMode == Mode.SELECTION) View.VISIBLE else View.GONE
        }

        fun updateBuildersAtFamily(familyName: String){
            if(familyName == Glob.FML_ROUNDWOOD){
                univBuilder?.visibility = View.GONE
                rwBuilderCnt?.visibility = View.VISIBLE
            } else{
                univBuilder?.visibility = View.VISIBLE
                rwBuilderCnt?.visibility = View.GONE
            }
        }

        fun multiSelector() {
            if(mSheetListAdapter.getAllSelectedState()){
                mSheetListAdapter.itemList.forEach { it.selector = false }
            } else{
                mSheetListAdapter.itemList.filter { !it.selector }.forEach { it.selector = true }
            }
            mSheetListAdapter.notifyDataSetChanged()
            selectionAdapter.notifyDataSetChanged()
        }



        inner class ItemTask(var title: String, val func: ()->Unit, val bgRes: Int= R.drawable.so_action_sl)

        inner class TmplButtonAdapter : RecyclerView.Adapter<TmplButtonAdapter.ItemHolder>() {
            val itemList: MutableList<ItemTask> = mutableListOf()

            override fun getItemCount(): Int = itemList.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.so_actionpanel_item, parent, false) as TextView)
            override fun onBindViewHolder(holder: ItemHolder, position: Int) {
                holder.acpTv.text = itemList[position].title
            }

            fun update(list: List<Tmpl>){
                val diffCallback = TmplDiffUtil(itemList, list)
                val diffResult: DiffUtil.DiffResult = DiffUtil.calculateDiff(diffCallback)
                itemList.clear()
                list.forEach {itemList.add(ItemTask(it.title, { openTmplBuilder(it) }) )}
                diffResult.dispatchUpdatesTo(this)
                tmplButtonListLayoutManager.scrollToPosition(itemCount-1)
            }

            inner class ItemHolder(val acpTv: TextView) : RecyclerView.ViewHolder(acpTv) {
                init{acpTv.setOnClickListener { onClickAction() }}
                private fun onClickAction(){
                    if(RecyclerView.NO_POSITION != adapterPosition){
                        itemList[adapterPosition].func()
                    }
                }
            }

            inner class TmplDiffUtil(private val oldList: List<ItemTask>, private val newList: List<Tmpl>) : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldList.size
                override fun getNewListSize(): Int = newList.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldList[oldItemPosition]
                    val newItem = newList[newItemPosition]
                    return oldItem.title != newItem.title
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldList[oldItemPosition]
                    val newItem = newList[newItemPosition]
                    return oldItem.title == newItem.title
                }
            }
        }

        inner class SelectionAdapter : RecyclerView.Adapter<SelectionAdapter.ItemHolder>() {
            val itemList: MutableList<ItemTask> = mutableListOf()

            init{
                itemList.add(ItemTask("", {editSheet()}, R.drawable.so_action_edit_sl))
                itemList.add(ItemTask("", {deleteSheet()}, R.drawable.so_action_delete_sl))
                itemList.add(ItemTask("", {exportSheet()}, R.drawable.so_action_topdf_sl))
                itemList.add(ItemTask("", {saveTmpl()}, R.drawable.so_action_totmpl_sl))
                itemList.add(ItemTask("", {multiSelector()}, R.drawable.so_action_selector))
            }

            override fun getItemCount(): Int = itemList.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.so_actionpanel_item, parent, false) as TextView)
            override fun onBindViewHolder(holder: ItemHolder, position: Int) {
                if(position == itemList.size - 1){
                    holder.acpTv.setBackgroundResource(if(mSheetListAdapter.getAllSelectedState()) R.drawable.so_action_selector_ch else R.drawable.so_action_selector)
                } else{
                    holder.acpTv.setBackgroundResource(itemList[position].bgRes)
                }
            }

            inner class ItemHolder(val acpTv: TextView) : RecyclerView.ViewHolder(acpTv) {
                init{acpTv.setOnClickListener { onClickAction() }}
                private fun onClickAction(){
                    if(RecyclerView.NO_POSITION != adapterPosition){
                        itemList[adapterPosition].func()
                    }
                }
            }
        }

    }

    inner class SheetListAdapter : RecyclerView.Adapter<SheetListAdapter.ItemHolder>(){
        val itemList: MutableList<Item> = mutableListOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.so_item, parent, false)
            return ItemHolder(view)
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            if(mMode == Mode.SELECTION){
                holder.itemSelector.visibility = View.VISIBLE
                holder.itemMenu.visibility = View.GONE
                val selectorImageRes = if(item.selector) R.drawable.so_item_selector_en else R.drawable.so_item_selector_dis
                holder.itemSelector.setImageResource(selectorImageRes)
            } else{
                holder.itemSelector.visibility = View.GONE
                holder.itemMenu.visibility = View.VISIBLE
            }

            holder.itemTitle.text = item.sheet.title
        }

        override fun getItemCount(): Int  = itemList.size

        fun getItemTitles(): ArrayList<String> {
            val titleList: ArrayList<String> = ArrayList()
            itemList.forEach { titleList.add(it.sheet.title) }
            return titleList
        }

        fun getSelectedCount(): Int = itemList.filter { it.selector }.size

        fun getSelectedSheets(): List<Sheet> {
            val selectedSheetList = mutableListOf<Sheet>()
            itemList.filter { it.selector }.forEach {
                val selectedSheet = it.sheet
                if(sheetOperator.list.find { selectedSheet.sid == it.sid } != null){
                    selectedSheetList.add(it.sheet)
                }
            }
            return selectedSheetList
        }

        fun getAllSelectedState(): Boolean {
            return (mSheetListAdapter.getSelectedCount() == mSheetListAdapter.itemCount)
        }

        fun update(list: List<Sheet>){
            val diffCallback = SheetDiffUtil(itemList, list)
            val diffResult: DiffUtil.DiffResult = DiffUtil.calculateDiff(diffCallback)
            itemList.clear()
            list.forEach { itemList.add(Item(it)) }
            diffResult.dispatchUpdatesTo(this)

            mEmptyStub.visibility = if(itemCount == 0) View.VISIBLE else View.GONE
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val itemContainer: LinearLayout by lazy { itemView.findViewById<LinearLayout>(R.id.so_item) }
            val itemSelector: ImageView by lazy { itemView.findViewById<ImageView>(R.id.itempanel_selector) }
            val itemMenu: LinearLayout by lazy { itemView.findViewById<LinearLayout>(R.id.itempanel_menu) }
            val itemTitle: TextView by lazy { itemView.findViewById<TextView>(R.id.title) }

            init {
                itemMenu.setOnClickListener { onClickMenu() }
                itemContainer.setOnClickListener { onClickContainer() }
                itemContainer.setOnLongClickListener { onLongClickContainer() }
            }

            private fun onLongClickContainer(): Boolean {
                if(RecyclerView.NO_POSITION != adapterPosition){
                    if(mMode == Mode.DEFAULT){
                        itemList[adapterPosition].selector = true
                        setMode(Mode.SELECTION)
                    }
                }
                return true
            }

            private fun onClickMenu(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    val jsonSheet = Sheet.toJson(item.sheet)
                    val bundle = Bundle()
                    bundle.putString(SheetInfo.SRC_DATA, jsonSheet.toString())

                    var sheetInfoUI: SheetInfo? = null
                    var sheetInfoTag: String? = null
                    when(item.sheet.family){
                        Glob.FML_ROUNDWOOD -> {
                            sheetInfoUI = RwSheetInfo()
                            sheetInfoTag = RwSheetInfo.TAGNAME
                        }
                        Glob.FML_UNIVERSAL -> {
                            sheetInfoUI = UnivSheetInfo()
                            sheetInfoTag = UnivSheetInfo.TAGNAME
                        }
                    }

                    if(sheetInfoUI != null){
                        sheetInfoUI.arguments = bundle
                        sheetInfoUI.show(fragmentManager, sheetInfoTag)
                    }
                }
            }

            private fun onClickContainer() {
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(mMode == Mode.SELECTION){ // сброс режима выделения
                        item.selector = !item.selector
                        mActionpanel.selectionAdapter.notifyDataSetChanged()

                        if(itemList.none { it.selector }){
                            setMode(Mode.DEFAULT)
                        } else{ notifyDataSetChanged() }
                    } else{
                        openSheetUI(item.sheet)
                    }
                }
            }

        }

        inner class Item(var sheet: Sheet){
            var selector: Boolean = false
        }

        inner class SheetDiffUtil(private val oldList: List<Item>, private val newList: List<Sheet>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.sheet.sid == newItem.sid
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.sheet.title == newItem.title
            }
        }
    }

    inner class ScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if(newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING){
                mScrollOffset = recyclerView.computeVerticalScrollOffset()
                mScrollActivate = true
            } else{
                mScrollActivate = false
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val curOffset = recyclerView.computeVerticalScrollOffset()

            if(mScrollActivate) {
                val different = (curOffset - mScrollOffset)

                if(different != 0){
                    mActionpanelY -= different

                    if(different > 0){ // up
                        if(mActionpanelY < -mActionpanel.height + 1){
                            mActionpanelY = -mActionpanel.height + 1
                        }
                    } else if(different < 0){ // down
                        if(mActionpanelY > 0){
                            mActionpanelY = 0
                        }
                    }
                }

                mScrollOffset = curOffset
                mActionpanel.y = mActionpanelY.toFloat()
            } else{
                if(curOffset == 0){
                    mActionpanelY = 0
                    mActionpanel.y = mActionpanelY.toFloat()
                }
            }
        }
    }
}
