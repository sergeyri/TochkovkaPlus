package ru.sergeyri.tochkovkaplusv2

import android.annotation.TargetApi
import android.app.FragmentManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.view.doOnPreDraw
import androidx.view.postDelayed
import com.google.firebase.analytics.FirebaseAnalytics
import com.sergeyri.tpcore.*
import org.json.JSONArray
import org.json.JSONObject


/**
 * Created by sergeyri on 12/5/17.
 */

class RwSheetUI : SheetUI() {
    companion object {
        const val TAGNAME = "rw_sheet_ui" // uniq name this fragment in FragmentManager
        fun getInstance(fragmentManager: FragmentManager): RwSheetUI {
            var sheet = fragmentManager.findFragmentByTag(TAGNAME)
            if(sheet == null){
                sheet = RwSheetUI()
            }
            return sheet as RwSheetUI
        }
    }

    override fun preLoadFilter(component: Component): Boolean {
        var result = true
        if(mParent.ext.has(RWI.KEY_ODDEVEN_FILTER)){
            val intTitle = component.title.toIntOrNull()
            result = when(mParent.ext.getInt(RWI.KEY_ODDEVEN_FILTER)){
                RWI.ODD_COMPONENTS -> {if(intTitle != null) (intTitle % 2 > 0) else true}
                RWI.EVEN_COMPONENTS -> {if(intTitle != null) (intTitle % 2 <= 0) else true}
                else -> {true}
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mOrder = ORDER_BY_TITLE_INT
    }
}

open class SheetUI : FragmentUI(), TPNode.ComponentCallback {
    enum class Mode { SELECTION, DEFAULT}
    enum class CalculatorItemMode { INCREMENT, HISTORY, DEFAULT}
    enum class BgType{DETAILS_LOAD, COMPONENTLIST_LOAD, COMPONENT_CREATE, COMPONENT_EDIT, COMPONENT_DELETE, COMPONENT_INCREMENT, COMPONENT_DECREMENT, COMPONENT_CLEAR, TO_PRINT}
    companion object {
        const val TAGNAME = "sheet_ui" // uniq name this fragment in FragmentManager
        const val PARENT = "parent_obj"

        const val UP_DIRECTION = -1
        const val NO_DIRECTION = 0 //default direction
        const val DOWN_DIRECTION = 1
        const val ORDER_BY = "order_by"
        const val ORDER_BY_CREATEDATE = 0
        const val ORDER_BY_TITLE = 1
        const val ORDER_BY_TITLE_INT = 2

        fun getInstance(fragmentManager: FragmentManager): SheetUI {
            var sheet = fragmentManager.findFragmentByTag(TAGNAME)
            if(sheet == null){
                sheet = SheetUI()
            }
            return sheet as SheetUI
        }
    }

    override val xTagname = TAGNAME
    /* Функционал поиска */
    override val xFilterInputHint: String by lazy{ resources.getString(R.string.hint_filterComponentList) }
    override val xFilterVoiceDescription: String by lazy{ resources.getString(R.string.tip_voiceComponentTitle) }
    override fun filter(key: String) {
        mDetailedAdapter.update(mComponentOperator.filter(key))
        mCalculatorAdapter.update(mComponentOperator.filter(key))

    } /* Конец функционала поиска */

    /* Обработка фоновых процессов */
    override fun onBgStart(bgName: String) {
        when(bgName){
            BgType.COMPONENTLIST_LOAD.name -> {showPD(R.string.msg_progressLoadingData)}
            BgType.COMPONENT_CREATE.name -> {}
            BgType.COMPONENT_EDIT.name -> {}
            BgType.COMPONENT_DELETE.name -> {showPD(R.string.msg_progressDelete)}
            BgType.TO_PRINT.name -> showPD(R.string.msg_progressPrepareToPrint)
        }
    }
    override fun onBgResult(bgName: String, result: Boolean) {
        when(bgName){
            BgType.COMPONENTLIST_LOAD.name -> {
                when(mOrder){
                    ORDER_BY_TITLE_INT -> mComponentOperator.list.sortBy { it.title.toFloat() }
                    ORDER_BY_TITLE -> mComponentOperator.list.sortBy { it.title }
                    ORDER_BY_CREATEDATE -> mComponentOperator.list.sortBy { it.createDate }
                }

                mCalculatorAdapter.update(mComponentOperator.list)
                calculateTotalData()

                if(mComponentOperator.list.isEmpty()){
                    mResultCnt.postDelayed(0, {
                        onUpdateShiftOpenY(mResultCnt.bottom)
                        mResultCnt.post { mShift.moveToBottom(true) }
                    })
                }

                val fbBundle = Bundle()
                fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
                fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Открыт расчёт")
                fbBundle.putString(FirebaseAnalytics.Param.CONTENT, mParent.title)
                xGlob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)
            }
            BgType.COMPONENT_CREATE.name -> {
                val sid = mComponentOperator.list.last().sid
                when(mOrder){
                    ORDER_BY_TITLE_INT -> mComponentOperator.list.sortBy { it.title.toFloat() }
                    ORDER_BY_TITLE -> mComponentOperator.list.sortBy { it.title }
                    ORDER_BY_CREATEDATE -> mComponentOperator.list.sortBy { it.createDate }
                }

                setMode(Mode.DEFAULT, false)
                mCalculatorAdapter.update(mComponentOperator.list)
                calculateTotalData()
                val doScrollTo = mCalculatorAdapter.itemList.indexOfLast { it.component.sid == sid }
                mCalcRv.post {
                    mCalcRv.layoutManager.smoothScrollToPosition(mCalcRv, RecyclerView.State(), doScrollTo)
                }
            }
            BgType.COMPONENT_EDIT.name -> {
                setMode(Mode.DEFAULT, false)
                mCalculatorAdapter.update(mComponentOperator.list)
                calculateTotalData()
            }
            BgType.COMPONENT_DELETE.name -> {
                setMode(Mode.DEFAULT, false)
                mCalculatorAdapter.update(mComponentOperator.list)
                calculateTotalData()
            }
            BgType.COMPONENT_INCREMENT.name -> {
                if(prefSoundAction && soundLoaded){ sp.play(soundId, 1f, 1f, 0, 0, 1f) }
                if(prefVibrateAction){ vibrate(activity, 50) }
            }
            BgType.COMPONENT_DECREMENT.name -> {}
            BgType.COMPONENT_CLEAR.name -> {
                setMode(Mode.DEFAULT, false)
                mCalculatorAdapter.update(mComponentOperator.list)
                calculateTotalData()
            }
            BgType.TO_PRINT.name -> {
                if(!result){ toast(activity, R.string.error_sheetPrint) }
            }
            else -> log("error unknown result returned in SheetUI")
        }
        hidePD()
    }


    override fun onComponentUpdate(component: Component) {
        activity?.runOnUiThread {
            mCalculatorAdapter.updateItem(component)
            calculateTotalData(component)
        }
    }

    override fun preLoadFilter(component: Component): Boolean = true
    /* Конец обработки фоновых процессов */

    /* Обработка нажатия кнопки назад.
    Здесь проверяется и устанавливается Mode.DEFAULT, иначе выполняется возврат к SheetOrderUI*/
    override fun onBack(): Boolean {
        var result = false
        if(mMode == Mode.DEFAULT){
            result = true
        }
        else{ setMode(Mode.DEFAULT, true) }
        xGlob.mSheetOrderUI.isBack = true
        return result
    }
    /* Конец обработки нажатия кнопки назад */

    private lateinit var mTvSheetTitle: TextView
    private lateinit var mSearchCnt: LinearLayout
    private lateinit var mEtFilter: EditText
    private lateinit var mZeroFilterBtn: Button
    private lateinit var mPrintBtn: Button
    private lateinit var mResultCnt: LinearLayout

    private lateinit var mTotalSw0: LinearLayout
    private lateinit var mTotalAr0: ImageView
    private lateinit var mTotalList0: RecyclerView

    private lateinit var mTotalSw1: LinearLayout
    private lateinit var mTotalAr1: ImageView
    private lateinit var mTotalList1: RecyclerView
    private lateinit var mNoTotal1DataTv: TextView

    private lateinit var mTotalFtrCount: TextView
    private lateinit var mTotalFtrVolume: TextView
    private lateinit var mTotalFtrCost: TextView

    private lateinit var mDetailedRv: RecyclerView
    private lateinit var mNoDetailedDataTv: TextView
    private lateinit var mNearCnt: LinearLayout
    private lateinit var mEmptyStub: ViewStub
    private lateinit var mCalcRv: RecyclerView
    private lateinit var mRightPanel: LinearLayout
    private lateinit var mBtnSelectAll: Button

    private lateinit var mPsToolbar: LinearLayout
    private lateinit var mShift: ShiftHandler // обработчик сдвига передней панели с CalcRv и RightPanel

    private lateinit var mTotalAdapter0: TotalAdapter0
    private lateinit var mTotalAdapter1: TotalAdapter1
    lateinit var mDetailedAdapter: DetailedAdapter
    lateinit var mCalculatorAdapter: CalculatorAdapter

    private var mCurrentTotalInterface: Int = 0
    private var mIsVisibleTotalList: Boolean = true
    private var mMode: Mode = Mode.DEFAULT
    private lateinit var mComponentCallback: TPNode.ComponentCallback
    lateinit var mParent: Sheet
    lateinit var mComponentOperator: TPNode.ComponentOperator // класс для работы с Particle
    private lateinit var sp: SoundPool
    private var soundLoaded: Boolean = false
    private var soundId = -1
    private var prefSoundAction: Boolean = SettingsUI.SOUND_ACTION
    private var prefVibrateAction: Boolean = SettingsUI.VIBRATE_ACTION
    private var mUpdateTotalTask: UpdateTotalTask? = null
    private var mTotalUpdateIsRunning = false
    var mZeroVisible: Boolean = false
    open var mOrder: Int = ORDER_BY_CREATEDATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try{
            setupSoundPool()
            prefSoundAction = xGlob.mPrefs.getBoolean(SettingsUI.KEY_SOUND_ACTION, SettingsUI.SOUND_ACTION)
            prefVibrateAction = xGlob.mPrefs.getBoolean(SettingsUI.KEY_VIBRATE_ACTION, SettingsUI.VIBRATE_ACTION)
            mParent = Sheet.fromJson(JSONObject(arguments.getString(PARENT))) // parent json object
            mComponentOperator = xGlob.mNode.ComponentOperator(mParent)
            mTotalAdapter0 = TotalAdapter0()
            mTotalAdapter1 = TotalAdapter1()

            mCalculatorAdapter = CalculatorAdapter()
            mDetailedAdapter = DetailedAdapter()
            mShift = ShiftHandler()
            mComponentCallback = this

            startBg(TPNode.Task(BgType.COMPONENTLIST_LOAD.name, { mComponentOperator.load(mComponentCallback) }))
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.co_ui, container, false)
        try{
            (activity as MainUI).mMainTool.toolbar.visibility = View.GONE
            (activity as MainUI).lockDrawer()

            mTvSheetTitle = view.findViewById(R.id.toolbar_title)
            mTvSheetTitle.text = mParent.title
            mSearchCnt = view.findViewById(R.id.toolbar_searchpanel)
            mEtFilter = view.findViewById(R.id.search_input)
            mEtFilter.addTextChangedListener(FilterListener())
            view.findViewById<ImageButton>(R.id.search_btn).setOnClickListener { clickFilterButton() }

            view.findViewById<Button>(R.id.component_create).setOnClickListener { onClickComponentCreate() }
            view.findViewById<Button>(R.id.componentlist_order).setOnClickListener { onClickOrderChange() }
            mZeroFilterBtn = view.findViewById(R.id.componentlist_zero_filter)
            mZeroFilterBtn.setBackgroundResource(if(mZeroVisible) R.drawable.co_action_filter_ch else R.drawable.co_action_filter)
            mZeroFilterBtn.setOnClickListener { onClickComponentFilter() }
            mPrintBtn = view.findViewById(R.id.componentlist_print)
            if(Build.VERSION.SDK_INT >= 19){
                mPrintBtn.setOnClickListener { onClickPrint() }
            } else{mPrintBtn.visibility = View.GONE}

            mResultCnt = view.findViewById(R.id.result_container)
            mResultCnt.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ -> onUpdateShiftOpenY(bottom) }

            mTotalSw0 = mResultCnt.findViewById(R.id.total_sw_0)
            val total0BtnText = mTotalSw0.findViewById<TextView>(R.id.total_sw_0_btn_text)
            total0BtnText.setText(if(mParent.family == Sheet.FML_ROUNDWOOD) R.string.sum1 else R.string.sum2)
            mTotalSw0.setOnClickListener {showTotalData(0)}
            mTotalAr0 = mTotalSw0.findViewById(R.id.total_sw_ar0)

            mTotalSw1 = mResultCnt.findViewById(R.id.total_sw_1)
            val total1BtnText = mTotalSw1.findViewById<TextView>(R.id.total_sw_1_btn_text)
            total1BtnText.setText(if(mParent.family == Sheet.FML_ROUNDWOOD) R.string.groupsTotalRw else R.string.groupsTotalUniv)
            mTotalSw1.setOnClickListener { showTotalData(1) }
            mTotalAr1 = mTotalSw1.findViewById(R.id.total_sw_ar1)

            val mTotalHeader: LinearLayout = view.findViewById(R.id.caption_totalList)
            mTotalHeader.findViewById<TextView>(R.id.caption_unit).text = mParent.unit
            mTotalHeader.findViewById<TextView>(R.id.caption_cost).visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE

            mTotalList0 = view.findViewById(R.id.rv_totalList_0)
            mTotalList0.layoutManager = LinearLayoutManager(activity)
            mTotalList0.adapter = mTotalAdapter0

            mTotalList1 = view.findViewById(R.id.rv_totalList_1)
            mTotalList1.layoutManager = LinearLayoutManager(activity)
            mTotalList1.adapter = mTotalAdapter1
            mNoTotal1DataTv = view.findViewById(R.id.no_total_data)

            val mTotalFooter: LinearLayout = view.findViewById(R.id.footer_totalList)
            mTotalFooter.findViewById<TextView>(R.id.item_title).setText(R.string.sum0)
            mTotalFooter.findViewById<TextView>(R.id.item_price).visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
            //mTotalFooter.setOnClickListener { showTotal(!mIsVisibleTotalList) }
            mTotalFtrCount = mTotalFooter.findViewById(R.id.item_count)
            mTotalFtrVolume = mTotalFooter.findViewById(R.id.item_volume)
            mTotalFtrCost = mTotalFooter.findViewById(R.id.item_price)

            /* элементы таблицы результатов */
            val detailedHeader: LinearLayout = view.findViewById(R.id.caption_totallist)
            detailedHeader.findViewById<TextView>(R.id.caption_unit).text = mParent.unit
            detailedHeader.findViewById<TextView>(R.id.totallist_caption_cost).visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE

            mDetailedRv = mResultCnt.findViewById(R.id.detailed_rv)
            mDetailedRv.layoutManager = LinearLayoutManager(activity)
            mDetailedRv.adapter = mDetailedAdapter
            mNoDetailedDataTv = mResultCnt.findViewById(R.id.no_detailed_data)

            /* передняя панель */
            mNearCnt = view.findViewById(R.id.near)
            mPsToolbar = view.findViewById(R.id.pseudo_toolbar)
            mPsToolbar.setOnTouchListener(mShift)
            mPsToolbar.setOnClickListener { onClickToolbar() }

            mEmptyStub = view.findViewById(android.R.id.empty)
            mEmptyStub.visibility = if(mCalculatorAdapter.itemCount == 0) View.VISIBLE else View.GONE

            mCalcRv = mNearCnt.findViewById(R.id.calc_rv)
            mCalcRv.layoutManager = BlockLinearLayoutManager(activity)

            mRightPanel = view.findViewById(R.id.ll_right_actionpanel)
            mRightPanel.visibility = if(mMode == Mode.SELECTION) View.VISIBLE else View.GONE
            mBtnSelectAll  = view.findViewById(R.id.action_select_all)
            mBtnSelectAll.setOnClickListener { multiplySelector() }
            view.findViewById<Button>(R.id.action_edit).setOnClickListener { onClickComponentEdit() }
            view.findViewById<Button>(R.id.action_delete).setOnClickListener { onClickComponentDelete() }
            view.findViewById<Button>(R.id.action_clear_data).setOnClickListener { onClickClearData() }

            showTotalData(mCurrentTotalInterface, false)
            noDetailedData()
            if(savedInstanceState != null){
                calculateTotalData()
            }
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try{
            if(view != null){
                mCalcRv.post {
                    setMode(mMode, false)
                    val psToolbarOffset = resources.getDimensionPixelSize(R.dimen.toolbar_height)
                    mCalcRv.layoutParams.height = view.height - psToolbarOffset
                    mCalcRv.adapter = mCalculatorAdapter
                }
            }
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onDestroyView() {
        (activity as MainUI).mMainTool.toolbar.visibility = View.VISIBLE
        (activity as MainUI).unlockDrawer()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(mUpdateTotalTask != null){
            if(mTotalUpdateIsRunning){
                mUpdateTotalTask?.cancel(true)
                mTotalUpdateIsRunning = false
            }
            mUpdateTotalTask = null
        }

        mMode = Mode.DEFAULT
        mZeroVisible = false
    }

    private fun clickFilterButton(){
        val isActive: Boolean = filterIsActive
        if(isActive) mEtFilter.setText("")
        setupFilterPanelState(!isActive, true)
    }

    private fun setupFilterPanelState(newState: Boolean, animate: Boolean){
        if(newState){
            filterPanelOpen(animate)
        } else{
            filterPanelClose(animate)
        }
    }

    private fun filterPanelOpen(animate: Boolean = false){
        filterIsActive = true
        mTvSheetTitle.visibility = View.GONE
        mSearchCnt.visibility = View.VISIBLE

        mEtFilter.requestFocus()
        mEtFilter.hint = xFilterInputHint
        mEtFilter.setText(filterKey)

        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if(animate){
            val anim = AnimationUtils.loadAnimation(activity, R.anim.scale_open)
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {}
                override fun onAnimationStart(p0: Animation?) {}
                override fun onAnimationEnd(p0: Animation?) {
                    imm.showSoftInput(mEtFilter, 0)
                }
            })
            mSearchCnt.startAnimation(anim)
        }
    }

    private fun filterPanelClose(animate: Boolean = false){
        filterIsActive = false
        mSearchCnt.visibility = View.GONE

        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if(animate){
            val anim = AnimationUtils.loadAnimation(activity, R.anim.scale_close)
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(p0: Animation?) {}
                override fun onAnimationStart(p0: Animation?) {
                    mTvSheetTitle.visibility = View.INVISIBLE
                }
                override fun onAnimationEnd(p0: Animation?) {
                    mTvSheetTitle.visibility = View.VISIBLE
                    imm.hideSoftInputFromWindow(mEtFilter.windowToken, 0)
                }
            })

            mSearchCnt.startAnimation(anim)
        } else {
            mTvSheetTitle.visibility = View.VISIBLE
            imm.hideSoftInputFromWindow(mEtFilter.windowToken,0)
        }
    }

    private fun onClickToolbar(){
        if(mShift.mIsOpened){
            mShift.moveToTop()
        } else{
            mShift.moveToBottom()
        }
    }

    fun setOrder(order: Int){
        when(order){
            ORDER_BY_TITLE_INT -> mComponentOperator.list.sortBy { it.title.toFloat() }
            ORDER_BY_TITLE -> mComponentOperator.list.sortBy { it.title }
            ORDER_BY_CREATEDATE -> mComponentOperator.list.sortBy { it.createDate }
        }
        mDetailedAdapter.update(mComponentOperator.list)
        mCalculatorAdapter.update(mComponentOperator.list)
        val editor = xGlob.mPrefs.edit()
        editor.putInt(ORDER_BY, order)
        editor.apply()
        mOrder = order
    }

    fun setMode(mode: Mode, notify: Boolean){
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)

        if(mode == Mode.SELECTION){
            mRightPanel.visibility = View.VISIBLE
            val rightPanelOffset = resources.getDimensionPixelSize(R.dimen.co_rightPanelWidth)
            mCalcRv.layoutParams.width = metrics.widthPixels - rightPanelOffset
        } else {
            mRightPanel.visibility = View.GONE
            mCalcRv.layoutParams.width = metrics.widthPixels

            mCalculatorAdapter.selectAll(false, !notify)
        }
        mMode = mode
        if(notify){mCalculatorAdapter.notifyDataSetChanged()}
    }

    private fun setupSoundPool(){
        sp = if(Build.VERSION.SDK_INT >= 21){
            SoundPool.Builder().also { it.setMaxStreams(3) }.also { it.setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build()) }.build()
        } else{
            SoundPool(2, AudioManager.STREAM_MUSIC, 0)
        }

        sp.setOnLoadCompleteListener { _, sampleId, _ ->
            soundLoaded = true
            soundId = sampleId
        }
        sp.load(activity, R.raw.tp_sound, 1)
    }

    private fun onUpdateShiftOpenY(bottom: Int){
        if(bottom == 0){return}
        mResultCnt.post { mShift.updateOpenY(bottom.toFloat()) }
    }

    /* Обработчики кнопок верхней панели */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun onClickPrint(){
        val dialogUI: PrintSheetDialog? = PrintSheetDialog()
        dialogUI?.show(fragmentManager, PrintSheetDialog.TAGNAME)
    }

    private fun onClickComponentFilter(){
        mZeroVisible = !mZeroVisible
        mZeroFilterBtn.setBackgroundResource(if(mZeroVisible) R.drawable.co_action_filter_ch else R.drawable.co_action_filter)
        mDetailedAdapter.notifyDataSetChanged()
    }

    private fun onClickOrderChange(){
        val dialog = SortComponentList()
        dialog.show(fragmentManager, SortComponentList.TAGNAME)
    }

    private fun onClickComponentCreate(){
        data class DialogData(val componentBuilder: ComponentBuilder, val tag: String)

        val json = org.json.simple.JSONObject() // generate SRC_DATA
        json[Component.KEY_TITLE] = ""
        val gmiJsonArr = JSONArray()
        mParent.listGMI.forEach {
            gmiJsonArr.put(Group.GroupData.toJson(Group.GroupData(it.sid, 0.0)))
        }
        json[Component.KEY_GROUPDATA_LIST] = gmiJsonArr

        val bundle = Bundle()
        bundle.putString(ComponentBuilder.PARENT, Sheet.toJson(mParent).toString())
        bundle.putString(ComponentBuilder.SRC_TYPE, BgType.COMPONENT_CREATE.name)
        bundle.putString(ComponentBuilder.SRC_DATA, json.toString())
        val (componentBuilder, tagname) = when(mParent.family){
            Glob.FML_ROUNDWOOD -> DialogData(RwComponentBuilder(), RwComponentBuilder.TAGNAME)
            else -> DialogData(UnivComponentBuilder(), UnivComponentBuilder.TAGNAME)
        }

        componentBuilder.arguments = bundle
        componentBuilder.show(fragmentManager, tagname)
        mShift.moveToTop()
    }

    /* Обработчики кнопок боковой панели */
    private fun multiplySelector(){
        val isAllSlected = mCalculatorAdapter.isAllSelected()
        mBtnSelectAll.setBackgroundResource(if(!isAllSlected) R.drawable.co_action_select_all_ch else R.drawable.co_action_select_all)
        mCalculatorAdapter.selectAll(!isAllSlected, true)
    }

    private fun onClickComponentEdit(){
        data class DialogData(val componentBuilder: ComponentBuilder, val tag: String)
        val selectedList = mCalculatorAdapter.getSelectedComponentList()
        if(selectedList.size == 1){
            val json = Component.toJson(selectedList[0])
            val bundle = Bundle()
            bundle.putString(ComponentBuilder.PARENT, Sheet.toJson(mParent).toString())
            bundle.putString(ComponentBuilder.SRC_TYPE, BgType.COMPONENT_EDIT.name)
            bundle.putString(ComponentBuilder.SRC_DATA, json.toString())
            val (componentBuilder, tagname) = when(mParent.family){
                Glob.FML_ROUNDWOOD -> DialogData(RwComponentBuilder(), RwComponentBuilder.TAGNAME)
                else -> DialogData(UnivComponentBuilder(), UnivComponentBuilder.TAGNAME)
            }
            componentBuilder.arguments = bundle
            componentBuilder.show(fragmentManager, tagname)
        }
    }

    private fun onClickComponentDelete(){
        val selectedList = mCalculatorAdapter.getSelectedComponentList()
        if(selectedList.isNotEmpty()){
            DeleteComponentUI().show(fragmentManager, DeleteComponentUI.TAGNAME)
        }
    }

    private fun onClickClearData(){
        val selectedList = mCalculatorAdapter.getSelectedComponentList()
        if(selectedList.isNotEmpty()){
            ComponentClearDataUI().show(fragmentManager, ComponentClearDataUI.TAGNAME)
        }
    }

    /* Обработка общих результатов */
    private fun showTotalData(totalInterface: Int=0, notifyDetails: Boolean=true){
        if(totalInterface > 0){ // diameter groups
            mTotalSw0.setBackgroundResource(R.drawable.total_sw_bg)
            mTotalSw1.setBackgroundResource(R.drawable.total_sw_bg_active)
            if(mCurrentTotalInterface == 1){
                mIsVisibleTotalList = !mIsVisibleTotalList
            }

            mTotalList0.visibility = View.GONE
            mTotalList1.visibility = if(mIsVisibleTotalList) View.VISIBLE else View.GONE
            noTotal1Data()
            mCurrentTotalInterface = 1
        } else{ // sorts
            mTotalSw1.setBackgroundResource(R.drawable.total_sw_bg)
            mTotalSw0.setBackgroundResource(R.drawable.total_sw_bg_active)
            if(mCurrentTotalInterface == 0){
                mIsVisibleTotalList = !mIsVisibleTotalList
            }

            mTotalList1.visibility = View.GONE
            mTotalList0.visibility = if(mIsVisibleTotalList) View.VISIBLE else View.GONE
            mNoTotal1DataTv.visibility = View.GONE
            mCurrentTotalInterface = 0
        }

        mTotalAr1.rotation = if(mIsVisibleTotalList) 0F else -180F
        mTotalAr0.rotation = if(mIsVisibleTotalList) 0F else -180F
        if(notifyDetails){ mDetailedAdapter.notifyDataSetChanged() }
    }

    private fun calculateTotalData(component: Component?=null){
        if(!mTotalUpdateIsRunning){
            mUpdateTotalTask = UpdateTotalTask(component)
            mTotalUpdateIsRunning = true
        }
    }

    private fun noTotal1Data(){
        mNoTotal1DataTv.visibility = if(mTotalAdapter1.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun noDetailedData(){
        mNoDetailedDataTv.visibility = if(mDetailedAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    inner class DetailedAdapter : RecyclerView.Adapter<DetailedAdapter.ItemHolder>(){
        val itemList: MutableList<Item> = mutableListOf()
        val subList: MutableList<Group.GroupMeta> = mutableListOf()

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.co_detailedlist_item, parent, false))
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            val isHiddenItem = (mZeroVisible && item.component.calculateCountAt(mParent.listGMI) == 0)
            holder.container.visibility = if(isHiddenItem) View.GONE else View.VISIBLE
            holder.titleView.text = item.component.title
            holder.countView.text = item.sumCount.toString()
            holder.volumeView.text = item.sumVolume.toString()
            holder.costView.text = item.sumCost.toString()

            if(!isHiddenItem && item.isVisibleSubCnt){
                holder.subCnt.visibility = View.VISIBLE
                subList.forEachIndexed { index, groupInfo ->
                    val child: View = holder.subCnt.getChildAt(index)
                    val subTitleView = child.findViewById<TextView>(R.id.iteminfo_title)
                    subTitleView.text = groupInfo.title
                    val subCountView = child.findViewById<TextView>(R.id.iteminfo_count)
                    subCountView.text = (item.count[groupInfo.sid]).toString()
                    val subVolumeView = child.findViewById<TextView>(R.id.iteminfo_volume)
                    subVolumeView.text = item.volume[groupInfo.sid]?.round().toString()
                    val subCostView = child.findViewById<TextView>(R.id.iteminfo_cost)
                    subCostView.visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
                    subCostView.text  = item.cost[groupInfo.sid]?.round().toString()
                }
            } else{holder.subCnt.visibility = View.GONE}
        }

        fun updateItem(component: Component){
            val itemIndex = itemList.indexOfFirst { it.component.sid == component.sid }
            if(itemIndex != -1){
                itemList[itemIndex].component = component
                itemList[itemIndex].calculate()
                notifyItemChanged(itemIndex)
            }
        }

        fun update(componentList: List<Component>){
            itemList.clear()
            componentList.forEach { itemList.add(Item(it)) }

            subList.clear()
            subList.addAll(mParent.listGMI)
            noDetailedData()
            notifyDataSetChanged()
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val container: LinearLayout = itemView.findViewById(R.id.totallist_container)
            val titleView: TextView = itemView.findViewById(R.id.item_title)
            val countView: TextView = itemView.findViewById(R.id.item_count)
            val volumeView: TextView = itemView.findViewById(R.id.item_volume)
            val costView: TextView = itemView.findViewById(R.id.item_price)
            val subCnt: LinearLayout = itemView.findViewById(R.id.subitem)

            init {
                costView.visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
                subList.forEach {
                    subCnt.addView(LayoutInflater.from(activity).inflate(R.layout.co_detailedlist_subitem, null, false))
                }
                itemView.setOnClickListener{showSubItem(subCnt.visibility == View.GONE)}
            }

            private fun showSubItem(show: Boolean){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    itemList[adapterPosition].isVisibleSubCnt = show
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        inner class Item(var component: Component) {
            var isVisibleSubCnt: Boolean = false

            val count: MutableMap<String, Int> = mutableMapOf()
            val volume: MutableMap<String, Double> = mutableMapOf()
            val cost: MutableMap<String, Double> = mutableMapOf()
            var sumCount: Int = 0
            var sumVolume: Double = 0.0
            var sumCost: Double = 0.0

            init {calculate()}

            fun calculate() {
                sumCount = 0
                sumVolume = 0.0
                sumCost = 0.0
                mParent.listGMI.forEach {
                    val groupData: Group.GroupData? = component.findDataOf(it.sid)
                    if(groupData != null){
                        count[it.sid] = groupData.count
                        sumCount += count[it.sid]!!
                        volume[it.sid] = (groupData.count * groupData.capacity)
                        sumVolume += volume[it.sid]!!
                        cost[it.sid] = it.price * (groupData.count * groupData.capacity)
                        sumCost += cost[it.sid]!!
                    }
                }
                sumVolume = sumVolume.round()
                sumCost = sumCost.round(2)
            }
        }
    }

    inner class CalculatorAdapter : RecyclerView.Adapter<CalculatorAdapter.ItemHolder>() {
        val itemList: MutableList<Item> = mutableListOf()
        private val lpHistoryTitleFl: FrameLayout.LayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        private val lpHistorySwitcherFl: FrameLayout.LayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.co_item_heightHistoryMode))
        private val lpDefault: FrameLayout.LayoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.co_item_height))
        val maxHistoryItems = xGlob.mPrefs.getInt(SettingsUI.KEY_MAX_COUNT_HISTORY, SettingsUI.MAX_COUNT_HISTORY)
        val inflater: LayoutInflater = LayoutInflater.from(activity)
        var memPosX = 0
        var incrementModeIndex: Int = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            return ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.co_item, parent, false))
        }

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.floorTitle.layoutParams = if(item.mode == CalculatorItemMode.HISTORY) lpHistoryTitleFl else lpDefault
            holder.title.text = item.component.title
            holder.subCnt.visibility = if(item.mode == CalculatorItemMode.HISTORY) View.VISIBLE else View.GONE
            holder.floorIncrementPanel.visibility = if(item.mode == CalculatorItemMode.INCREMENT) View.VISIBLE else View.GONE
            holder.floorSw.layoutParams = if(item.mode == CalculatorItemMode.HISTORY) lpHistorySwitcherFl else lpDefault
            holder.incrementPanelOpenSw.visibility = if(item.mode == CalculatorItemMode.INCREMENT) View.GONE else View.VISIBLE
            holder.incrementPanelOpenSw.text = item.component.calculateCountAt(mParent.listGMI).toString()
            val isSeveralGroups = mParent.listGMI.size > 1
            holder.incrementPanelOpenSw.setBg(if(isSeveralGroups) resources.getColor(R.color.bg_actionBlue) else mParent.listGMI[0].theme.bg)
            holder.incrementPanelOpenSw.setTxt(if(isSeveralGroups) resources.getColor(R.color.txt_actionBlue) else mParent.listGMI[0].theme.txt)
            holder.incrementPanelCloseSw.setImageResource(if(item.mode == CalculatorItemMode.INCREMENT) R.drawable.arrow_horizontal else if(item.mode == CalculatorItemMode.HISTORY) R.drawable.arrow_vertical else R.drawable.ic_history_white_24dp)

            holder.floorIncrementPanel.visibility = if(item.mode == CalculatorItemMode.INCREMENT) View.VISIBLE else View.GONE

            holder.floorSw.visibility = if(mMode == Mode.SELECTION) View.GONE else View.VISIBLE
            holder.itemSelector.visibility = if(mMode == Mode.SELECTION) View.VISIBLE else View.GONE
            val selectorBg = if(item.selector) R.drawable.co_selector_en else R.drawable.co_selector_dis
            holder.itemSelector.setBackgroundResource(selectorBg)

            mParent.listGMI.forEachIndexed { index, groupInfo -> // заполнение списка GroupData для Particle
                val groupData = item.component.findDataOf(groupInfo.sid)
                val actionLayout = (holder.incrementPanelCnt.getChildAt(index) as LinearLayout)
                actionLayout.setBackgroundColor(groupInfo.theme.txt)

                val actionTitle = actionLayout.getChildAt(0) as TextView
                actionTitle.text = groupInfo.title
                actionTitle.setBackgroundColor(groupInfo.theme.bg)
                actionTitle.setTextColor(groupInfo.theme.txt)

                val actionValue = actionLayout.getChildAt(1) as TextView
                val actionCapacity = actionLayout.getChildAt(2) as TextView
                if(groupData != null){
                    actionValue.text = groupData.count.toString()
                    actionValue.setBackgroundColor(groupInfo.theme.bg)
                    actionValue.setTextColor(groupInfo.theme.txt)
                    actionCapacity.text = groupData.capacity.toString()
                    actionCapacity.setBackgroundColor(groupInfo.theme.bg)
                    actionCapacity.setTextColor(groupInfo.theme.txt)
                }
            }

            if(item.mode == CalculatorItemMode.HISTORY){
                val historyList = item.component.compileHistory()
                (0 until holder.historyCnt.childCount).map { holder.historyCnt.getChildAt(it)}.forEachIndexed { index, historyView ->
                    if(index < historyList.size){
                        val historyItem = historyList[index]
                        val groupInfo = mParent.listGMI.find { it.sid == historyItem.sid }
                        if(groupInfo != null){
                            val themeView: View = historyView.findViewById(R.id.groupinfo_theme)
                            themeView.setBackgroundColor(groupInfo.theme.bg)
                            val createDateTv: TextView = historyView.findViewById(R.id.create_date)
                            createDateTv.text = historyItem.createDate.toDateTime(resources.getString(R.string.def_dateFormat0))
                            historyView.tag = historyItem
                            historyView.visibility = View.VISIBLE
                        }
                    } else {historyView.visibility = View.GONE}
                }

                holder.btnShowAll.visibility = if(historyList.size > holder.historyCnt.childCount) View.VISIBLE else View.GONE
            }

            if(item.mode == CalculatorItemMode.INCREMENT){
                holder.incrementPanelSv.doOnPreDraw {
                    holder.incrementPanelSv.scrollX = memPosX
                }
                holder.incrementPanelSv.post {
                    holder.incrementPanelSv.scrollTo(memPosX, holder.incrementPanelSv.bottom)
                }
            }

            log("bind")
        }

        override fun getItemCount(): Int = itemList.size

        fun updateItem(component: Component){
            val itemIndex = itemList.indexOfFirst { it.component.sid == component.sid }
            if(itemIndex != -1){
                val historyList = component.compileHistory()
                if(CalculatorItemMode.HISTORY == itemList[itemIndex].mode && historyList.isEmpty()){
                    itemList[itemIndex].mode = CalculatorItemMode.DEFAULT
                }
                itemList[itemIndex].component = component
                notifyItemChanged(itemIndex)
            }
        }

        fun update(componentList: List<Component>){
            val diffCallback = ComponentDiffUtil(itemList, componentList)
            val diffResult: DiffUtil.DiffResult = DiffUtil.calculateDiff(diffCallback)
            itemList.clear()
            componentList.forEach { itemList.add(Item(it)) }
            diffResult.dispatchUpdatesTo(this)

            notifyDataSetChanged()

            mEmptyStub.visibility = if(componentList.isEmpty()) View.VISIBLE else View.GONE
        }

        fun getSelectedComponentList(): List<Component> {
            val componentList: MutableList<Component> = mutableListOf()
            itemList.filter { it.selector }.forEach {
                componentList.add(it.component)
            }
            return componentList
        }

        fun selectAll(select: Boolean, notify: Boolean){
            itemList.filter { it.selector == !select }.forEach { it.selector = select }
            if(notify){notifyDataSetChanged()}
        }

        fun isAllSelected(): Boolean {
            return itemList.filter { it.selector }.size == itemList.size
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val floorTitle: LinearLayout = itemView.findViewById(R.id.floor_title)
            val title: TextView = itemView.findViewById(R.id.title)
            val subCnt: LinearLayout = itemView.findViewById(R.id.sub_container)
            val historyCnt: LinearLayout = itemView.findViewById(R.id.history_container)
            val btnShowAll: TextView = itemView.findViewById(R.id.btn_show_all)
            val floorIncrementPanel: LinearLayout = itemView.findViewById(R.id.floor_increment_panel)
            val incrementPanelSv: BlockScrollView = itemView.findViewById(R.id.increment_panel_sv)
            val incrementPanelCnt: LinearLayout = itemView.findViewById(R.id.increment_panel_container)
            val floorSw: LinearLayout = itemView.findViewById(R.id.floor_switcher)
            val incrementPanelOpenSw: IncrementorView = itemView.findViewById(R.id.increment_panel_open_switcher)
            val incrementPanelCloseSw: ImageView = itemView.findViewById(R.id.increment_panel_close_switcher)
            val itemSelector: ImageView = itemView.findViewById(R.id.item_selector)
            val animOpen = AnimationUtils.loadAnimation(activity, R.anim.scale_open)

            init {
                log("item init")
                title.setOnClickListener { onItemClick() }
                title.setOnLongClickListener { onItemLongClick() }
                incrementPanelOpenSw.setOnClickListener { onClickIncrementPanelOpenSw() }
                incrementPanelCloseSw.setOnClickListener { onClickIncrementPanelCloseSw() }

                mParent.listGMI.forEach {
                    val groupInfo = it
                    val componentCalcLayout = inflater.inflate(R.layout.co_item_increment_action, null, false)
                    componentCalcLayout.setOnClickListener { onIncrement(groupInfo) }
                    incrementPanelCnt.addView(componentCalcLayout)
                }

                (0 until maxHistoryItems).forEachIndexed { index, _ ->
                    val historyView = inflater.inflate(R.layout.co_history_item, null, true) as LinearLayout
                    val removeBtn: Button = historyView.findViewById(R.id.action_delete_history_item)
                    removeBtn.setOnClickListener { onDeleteHistoryItem(historyView.tag as Group.History) }
                    historyCnt.addView(historyView, index)
                }

                btnShowAll.setOnClickListener {
                    if(RecyclerView.NO_POSITION != adapterPosition){
                        openFullHistory()
                    }
                }
            }

            private fun onItemClick() {
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(mMode == Mode.SELECTION){
                        item.selector = !item.selector
                        if(itemList.none { it.selector }){
                            setMode(Mode.DEFAULT, false)
                        }
                        notifyItemChanged(adapterPosition)
                        mBtnSelectAll.setBackgroundResource(if(isAllSelected()) R.drawable.co_action_select_all_ch else R.drawable.co_action_select_all)
                    }
                }
            }

            private fun onItemLongClick(): Boolean {
                if(RecyclerView.NO_POSITION != adapterPosition && mMode == Mode.DEFAULT){
                    itemList.filter { it.mode != CalculatorItemMode.DEFAULT }.forEach { it.mode = CalculatorItemMode.DEFAULT }
                    itemList[adapterPosition].selector = true
                    setMode(Mode.SELECTION, true)
                }
                return true
            }

            private fun onClickIncrementPanelOpenSw(){ // open calcpanel
                if(RecyclerView.NO_POSITION != adapterPosition){
                    if(mParent.listGMI.size > 1){
                        log("open incrementpanel, incrementModeIndex: $incrementModeIndex")
                        if(incrementModeIndex != adapterPosition){
                            itemList[adapterPosition].mode = CalculatorItemMode.INCREMENT
                            notifyItemChanged(adapterPosition)
                            if(incrementModeIndex > -1){
                                itemList[incrementModeIndex].mode = CalculatorItemMode.DEFAULT
                                notifyItemChanged(incrementModeIndex)
                            }
                            incrementModeIndex = adapterPosition
                        }
                    } else {
                        onIncrement(mParent.listGMI.first())
                    }
                }
            }

            private fun onClickIncrementPanelCloseSw(){ // close calcpanel
                if(RecyclerView.NO_POSITION != adapterPosition){
                    when(itemList[adapterPosition].mode){
                        CalculatorItemMode.INCREMENT -> kotlin.run {
                            incrementPanelOpenSw.animate().scaleX(1.0F).scaleY(1.0F).setDuration(150).start()
                            val anim = AnimationUtils.loadAnimation(activity, R.anim.scale_close)
                            anim.setAnimationListener(IncrementPanelAnimationListener(false, adapterPosition))
                            floorIncrementPanel.startAnimation(anim)
                            memPosX = 0

                            if(incrementModeIndex > -1){
                                notifyItemChanged(incrementModeIndex)
                                incrementModeIndex = -1
                            }
                        }
                        CalculatorItemMode.HISTORY -> kotlin.run {
                            itemList[adapterPosition].mode = CalculatorItemMode.DEFAULT
                            notifyItemChanged(adapterPosition)
                        }
                        else -> kotlin.run {
                            val history = itemList[adapterPosition].component.compileHistory()
                            if(history.isNotEmpty()){
                                itemList[adapterPosition].mode = CalculatorItemMode.HISTORY
                                notifyItemChanged(adapterPosition)
                            }
                        }
                    }
                }
            }

            private fun onIncrement(groupInfo: Group.GroupMeta){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val component: Component = itemList[adapterPosition].component
                    val groupData = component.findDataOf(groupInfo.sid)
                    if(groupData != null){
                        incrementPanelSv.postDelayed(0, {
                            incrementPanelSv.post {
                                memPosX = incrementPanelSv.scrollX
                            }
                        })

                        startBg(TPNode.Task(BgType.COMPONENT_INCREMENT.name, { mComponentOperator.incrementGD(component, groupData, mComponentCallback) }))
                    }
                }
            }

            private fun onDeleteHistoryItem(history: Group.History){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val component: Component = itemList[adapterPosition].component
                    val groupData = component.findDataOf(history.sid)
                    if(groupData != null){
                        startBg(TPNode.Task(BgType.COMPONENT_DECREMENT.name, { mComponentOperator.decrementGD(component, groupData, history.createDate, mComponentCallback) }))
                    }
                }
            }

            private fun openFullHistory(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val dialogUI: DialogUI = FullHistoryUI()
                    val bundle = Bundle()
                    val component: Component = itemList[adapterPosition].component
                    bundle.putString(FullHistoryUI.COMPONENT, Component.toJson(component).toString())
                    val jarr = JSONArray()
                    mParent.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
                    bundle.putString(FullHistoryUI.GROUPINFO_LIST, jarr.toString())
                    dialogUI.arguments = bundle
                    dialogUI.show(fragmentManager, FullHistoryUI.TAGNAME)
                }
            }

            inner class IncrementPanelAnimationListener(private val open: Boolean, val position: Int) : Animation.AnimationListener {
                override fun onAnimationRepeat(anim: Animation?) {}
                override fun onAnimationStart(anim: Animation?) {}
                override fun onAnimationEnd(anim: Animation?) {

                    itemList[position].mode = if(open) CalculatorItemMode.INCREMENT else CalculatorItemMode.DEFAULT
                    notifyItemChanged(position, open)
                }
            }
        }

        inner class ComponentDiffUtil(private val oldList: List<Item>, private val newList: List<Component>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.component.sid == newItem.sid
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.component.title == newItem.title
            }
        }

        inner class Item(var component: Component){
            var selector: Boolean = false
            var mode: CalculatorItemMode = CalculatorItemMode.DEFAULT
        }
    }

    inner class ShiftHandler : View.OnTouchListener {
        private var mTopY = 0F
        private var mBottomY = 0F
        private var mPointY: Float = 0F
        private var mDistancePointView: Float = -1F
        private var mDirection: Int = NO_DIRECTION
        var mIsOpened = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            var consume = false
            when(e.action){
                MotionEvent.ACTION_MOVE -> kotlin.run {
                    if(mDistancePointView == -1F){
                        mPointY = e.rawY
                        mDistancePointView = mPointY - mNearCnt.y
                    }

                    val curPointY = e.rawY
                    val differentPoint = curPointY - mPointY
                    mDirection = if(differentPoint < 0) UP_DIRECTION else if(differentPoint > 0) DOWN_DIRECTION else NO_DIRECTION
                    var moveTo = curPointY - mDistancePointView

                    if(moveTo < 0){
                        moveTo = 0F
                    }

                    mNearCnt.y = moveTo
                    mPointY = curPointY

                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> kotlin.run {
                    mPointY = 0F
                    mDistancePointView = -1F

                    consume = (mDirection != NO_DIRECTION)
                    if(consume){
                        smartMove()
                    }
                    mDirection = NO_DIRECTION
                }
            }
            return consume
        }

        fun updateOpenY(bottomY: Float){
            mBottomY = bottomY + 30
            if(mIsOpened) moveToBottom(false) else moveToTop(false)
        }

        private fun smartMove() {
            val partOfCancel = (mBottomY - mTopY) / 7
            if(mNearCnt.y < (mTopY + partOfCancel)){ // top cancel
                moveToTop()
            } else if(mNearCnt.y > (mBottomY - partOfCancel)){ // bottom cancel
                moveToBottom()
            } else{ // smart ending
                if(DOWN_DIRECTION == mDirection){
                    moveToBottom()
                } else {
                    moveToTop()
                }
            }
        }

        fun moveToTop(animate: Boolean=true){
            mIsOpened = false
            if(animate){
                mNearCnt.animate().translationY(mTopY).setInterpolator(DecelerateInterpolator()).setDuration(400).start()
            } else{mNearCnt.y = mTopY}
        }

        fun moveToBottom(animate: Boolean=true){
            val fbBundle = Bundle()
            fbBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, fb_user_name)
            fbBundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Просмотр результатов расчёта")
            fbBundle.putString(FirebaseAnalytics.Param.CONTENT, "sheet_size: ${mComponentOperator.list.size}")
            xGlob.mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, fbBundle)

            mIsOpened = true
            if(animate){
                mNearCnt.animate().translationY(mBottomY).setInterpolator(DecelerateInterpolator()).setDuration(400).start()
            } else{mNearCnt.y = mBottomY}
        }

    }

    inner class TotalAdapter0 : RecyclerView.Adapter<TotalAdapter0.ItemHolder>() {
        private val itemList: MutableList<Total> = mutableListOf()

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.co_totallist_item, parent, false))
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.totalTitle.text = item.title
            holder.totalCount.text = item.sumCount.toString()
            holder.totalVolume.text = item.sumVol.toString()
            holder.totalCost.text = item.sumCost.toString()
        }

        fun update(list: List<Total>){
            itemList.clear()
            itemList.addAll(list)
            notifyDataSetChanged()
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val totalTitle: TextView = itemView.findViewById(R.id.item_title)
            val totalCount: TextView = itemView.findViewById(R.id.item_count)
            val totalVolume: TextView = itemView.findViewById(R.id.item_volume)
            val totalCost: TextView = itemView.findViewById(R.id.item_price)

            init {
                itemView.setOnClickListener { showTotalData() }
                totalCost.visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
            }
        }
    }

    inner class TotalAdapter1 : RecyclerView.Adapter<TotalAdapter1.ItemHolder>(){
        private val itemList: MutableList<Item> = mutableListOf()
        val subList: MutableList<Group.GroupMeta> = mutableListOf()

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder = ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.co_detailedlist_item, parent, false))
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.titleView.text = item.component.title
            holder.countView.text = item.sumCount.toString()
            holder.volumeView.text = item.sumVolume.toString()
            holder.costView.text = item.sumCost.toString()

            if(item.isVisibleSubCnt){
                holder.subCnt.visibility = View.VISIBLE
                subList.forEachIndexed { index, groupInfo ->
                    val child: View = holder.subCnt.getChildAt(index)
                    val subTitleView = child.findViewById<TextView>(R.id.iteminfo_title)
                    subTitleView.text = groupInfo.title
                    val subCountView = child.findViewById<TextView>(R.id.iteminfo_count)
                    subCountView.text = (item.count[groupInfo.sid]).toString()
                    val subVolumeView = child.findViewById<TextView>(R.id.iteminfo_volume)
                    subVolumeView.text = item.volume[groupInfo.sid]?.round().toString()
                    val subCostView = child.findViewById<TextView>(R.id.iteminfo_cost)
                    subCostView.visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
                    subCostView.text  = item.cost[groupInfo.sid]?.round().toString()
                }
            } else{holder.subCnt.visibility = View.GONE}
        }

        fun update(gsArray: JSONArray){
            itemList.clear()

            (0 until gsArray.length())
                    .map{ gsArray.getJSONObject(it) }
                    .forEach { itemList.add(Item()) }



            subList.clear()
            subList.addAll(mParent.listGMI)
            noTotal1Data()
            notifyDataSetChanged()
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val container: LinearLayout = itemView.findViewById(R.id.totallist_container)
            val titleView: TextView = itemView.findViewById(R.id.item_title)
            val countView: TextView = itemView.findViewById(R.id.item_count)
            val volumeView: TextView = itemView.findViewById(R.id.item_volume)
            val costView: TextView = itemView.findViewById(R.id.item_price)
            val subCnt: LinearLayout = itemView.findViewById(R.id.subitem)

            init {
                costView.visibility = if(mParent.priceEnabled) View.VISIBLE else View.GONE
                subList.forEach {
                    subCnt.addView(LayoutInflater.from(activity).inflate(R.layout.co_detailedlist_subitem, null, false))
                }
                itemView.setOnClickListener{showSubItem(!subCnt.isVisible())}
            }

            private fun showSubItem(show: Boolean){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    itemList[adapterPosition].isVisibleSubCnt = show
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        inner class Item(gs: JSONObject) {
            var isVisibleSubCnt: Boolean = false
            var title: String
            var min: Int
            var max: Int


            val count: MutableMap<String, Int> = mutableMapOf()
            val volume: MutableMap<String, Double> = mutableMapOf()
            val cost: MutableMap<String, Double> = mutableMapOf()
            var sumCount: Int = 0
            var sumVolume: Double = 0.0
            var sumCost: Double = 0.0

            init {
                min = gs.getInt(Sheet.GS_MIN_IND)
                max = gs.getInt(Sheet.GS_MAX_IND)
                title = "${min} - ${max}"
                calculate()
            }

            fun calculate() {
                sumCount = 0
                sumVolume = 0.0
                sumCost = 0.0


                mParent.listGMI.forEach {
                    val groupData: Group.GroupData? = component.findDataOf(it.sid)
                    if(groupData != null){
                        count[it.sid] = groupData.count
                        sumCount += count[it.sid]!!
                        volume[it.sid] = (groupData.count * groupData.capacity)
                        sumVolume += volume[it.sid]!!
                        cost[it.sid] = it.price * (groupData.count * groupData.capacity)
                        sumCost += cost[it.sid]!!
                    }
                }
                sumVolume = sumVolume.round()
                sumCost = sumCost.round(2)
            }
        }
    }

    class Total(val title: String, var sumCount: Int, var sumVol: Double, var sumCost: Double)

    inner class UpdateTotalTask(val component: Component?=null) : AsyncTask<Void, Void, List<Total>>(){
        init {execute()}

        var count = 0
        var volume = 0.0
        var cost = 0.0

        override fun doInBackground(vararg params: Void?): List<Total> {
            return updateTotal()
        }

        override fun onPostExecute(result: List<Total>) {
            if(component != null){
                mDetailedAdapter.updateItem(component)
            } else{
                mDetailedAdapter.update(mComponentOperator.list)
            }

            mTotalAdapter0.update(result)
            mTotalFtrCount.text = count.toString()
            mTotalFtrVolume.text = volume.round().toString()
            mTotalFtrCost.text = cost.round().toString()
            mTotalUpdateIsRunning = false
        }

        private fun updateTotal(): List<Total> {
            val totalList: MutableList<Total> = mutableListOf()
            mParent.listGMI.forEach {
                val groupInfo = it

                var groupCount = 0
                var groupVolume = 0.0
                var groupCost = 0.0
                mComponentOperator.list.forEach {
                    val groupData = it.findDataOf(groupInfo.sid)
                    if(groupData != null){
                        groupCount += groupData.count
                        groupVolume += groupData.calculateVolume()
                        groupCost += groupData.calculateCost(groupInfo.price)
                    }
                }

                count += groupCount
                volume += groupVolume
                cost += groupCost

                totalList.add(Total(groupInfo.title, groupCount, groupVolume.round(), groupCost.round()))
            }
            return totalList
        }

    }

    private inner class FilterListener : EditTextWatcher() {
        override fun afterTextChanged(e: Editable?) {
            if(e != null){
                val s = e.toString()
                if(s != filterKey){
                    filter(s)
                    filterKey = s
                }
            }
        }
    }

}


