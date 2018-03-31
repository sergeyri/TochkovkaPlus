package ru.sergeyri.tochkovkaplusv2

import android.app.FragmentManager
import android.os.Bundle
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.sergeyri.tpcore.Group
import com.sergeyri.tpcore.Sheet
import com.sergeyri.tpcore.TPNode
import com.sergeyri.tpcore.Tmpl

import org.json.JSONArray

/**
 * Created by sergeyri on 07.02.18.
 */
class TmplOrderUI : FragmentUI() {
    enum class Mode{DEFAULT, SELECTION}
    enum class BgType {TMPLLIST_LOAD, TMPL_EDIT, TMPL_DELETE}
    companion object {
        val TAGNAME = "tmplorder_ui"
        fun getInstance(fragmentManager: FragmentManager): TmplOrderUI {
            var fragment = fragmentManager.findFragmentByTag(TAGNAME)
            if(fragment == null){fragment = TmplOrderUI()
            }
            return fragment as TmplOrderUI
        }
    }

    override fun onBack(): Boolean {
        var result = false
        if(mMode == Mode.DEFAULT){ result = true }
        else{ setMode(Mode.DEFAULT, true) }
        xGlob.mSheetOrderUI.isBack = true
        return result
    }

    override fun filter(key: String) {
        mTmplAdapter.update(tmplOperator.filter(key))
    }

    override fun onBgStart(bgName: String) {

    }

    override fun onBgResult(bgName: String, result: Boolean) {
        when(bgName){
            BgType.TMPLLIST_LOAD.name -> {
                tmplOperator.list.sortBy { it.createDate }
                mTmplAdapter.update(tmplOperator.list)
                if(tmplOperator.list.isNotEmpty()){
                    mRvTmplList.post { mRvTmplList.layoutManager.smoothScrollToPosition(mRvTmplList, RecyclerView.State(), mTmplAdapter.itemCount-1) }
                }
            }
            BgType.TMPL_EDIT.name -> {
                setMode(Mode.DEFAULT)
                tmplOperator.list.sortBy { it.createDate }
                mTmplAdapter.update(tmplOperator.list)
                xGlob.mSheetOrderUI.mActionpanel.tmplButtonAdapter.update(tmplOperator.list)
            }
            BgType.TMPL_DELETE.name -> {
                setMode(Mode.DEFAULT)
                tmplOperator.list.sortBy { it.createDate }
                mTmplAdapter.update(tmplOperator.list)
                xGlob.mSheetOrderUI.mActionpanel.tmplButtonAdapter.update(tmplOperator.list)
            }
        }
    }

    override val xTagname: String = TAGNAME
    override val xFilterInputHint: String by lazy{ resources.getString(R.string.hint_filterTmplList) }
    override val xFilterVoiceDescription: String by lazy{ resources.getString(R.string.tip_voiceTmplTitle) }

    private lateinit var mRvTmplList: RecyclerView
    private lateinit var mRightPanelCnt: LinearLayout
    private lateinit var mBtnSelectAll: Button
    private lateinit var mBtnTmplImport: Button
    private lateinit var mBtnTmplEdit: Button
    private lateinit var mBtnTmplDelete: Button
    lateinit var tmplOperator: TPNode.TmplOperator
    lateinit var sheetOperator: TPNode.SheetOperator
    val mTmplAdapter: TmplListAdapter by lazy { TmplListAdapter() }
    var mMode: Mode = Mode.DEFAULT
    var isBack: Boolean = false // флаг возврата из TmplUI (для отмены перезагрузки данных)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try{
            if(!isBack){
                tmplOperator = xGlob.mSheetOrderUI.tmplOperator
                sheetOperator = xGlob.mSheetOrderUI.sheetOperator
                startBg(TPNode.Task(BgType.TMPLLIST_LOAD.name, {tmplOperator.load()}))
            }
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.to_ui, container, false)
        try {
            setToolbarTitle(resources.getString(R.string.title_tmplorderUI))
            mRvTmplList = view.findViewById(R.id.rv_tmpllist)
            val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, true)
            mRvTmplList.layoutManager = layoutManager
            mRvTmplList.adapter = mTmplAdapter
            mRightPanelCnt = view.findViewById(R.id.ll_right_actionpanel)
            mBtnSelectAll = view.findViewById(R.id.action_select_all)
            mBtnSelectAll.setOnClickListener { multiplySelector() }
            mBtnTmplImport = view.findViewById(R.id.action_import)
            mBtnTmplImport.setOnClickListener { onClickTmplImport() }
            mBtnTmplEdit = view.findViewById(R.id.action_edit)
            mBtnTmplEdit.setOnClickListener { onClickTmplEdit() }
            mBtnTmplDelete = view.findViewById(R.id.action_delete)
            mBtnTmplDelete.setOnClickListener { onClickTmplDelete() }
            setRightPanelState(mMode == Mode.SELECTION)
        } catch (e: UninitializedPropertyAccessException){
            (activity as MainUI).finishMainUI()
        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        setMode(Mode.DEFAULT)
    }

    fun setMode(mode: Mode, notify: Boolean=true){
        if(mode == Mode.SELECTION){
            mRightPanelCnt.visibility = View.VISIBLE
        } else {
            mRightPanelCnt.visibility = View.GONE
            mTmplAdapter.selectAll(false, !notify)
        }
        mMode = mode
        if(notify){mTmplAdapter.notifyDataSetChanged()}
    }

    private fun setRightPanelState(open: Boolean){
        mRightPanelCnt.visibility = if(open) View.VISIBLE else View.GONE
    }

    /* Обработчики кнопок боковой панели */
    private fun multiplySelector(){
        val isAllSlected = mTmplAdapter.isAllSelected()
        mBtnSelectAll.setBackgroundResource(if(!isAllSlected) R.drawable.co_action_select_all_ch else R.drawable.co_action_select_all)
        mTmplAdapter.selectAll(!isAllSlected, true)
    }

    private fun onClickTmplImport(){
        val selectedList = mTmplAdapter.getSelectedTmplList()
        if(selectedList.size == 1){
            val tmpl = selectedList[0]
            data class DialogData(val sheetBuilder: SheetBuilder, val tag: String)
            val json = org.json.simple.JSONObject()
            json.put(Sheet.KEY_CREATE_DATE, tmpl.createDate)
            json.put(Sheet.KEY_FML_FILTER, tmpl.family)
            json.put(Sheet.KEY_CHANGE_DATE, tmpl.createDate)
            json.put(Sheet.KEY_TITLE, tmpl.title)
            json.put(Sheet.KEY_UNIT, tmpl.unit)
            val gmiJsonArr = JSONArray()
            tmpl.listGMI.forEach {gmiJsonArr.put(Group.GroupMetaInfo.toJson(it))}
            json.put(Sheet.KEY_GROUPINFO_LIST, gmiJsonArr)
            json.put(Sheet.KEY_EXT, tmpl.ext)
            json.put(Sheet.KEY_COMMENT, "")
            json.put(Sheet.KEY_PRICE_ENABLED, tmpl.priceEnabled)

            val params = Bundle()
            val titles: ArrayList<String> = arrayListOf()
            sheetOperator.list.forEach {
                titles.add(it.title)
            }

            params.putStringArrayList(SHEETNAMES, titles)
            params.putString(SheetBuilder.SRC_TYPE, SheetOrderUI.BgType.SHEET_IMPORT.name)
            params.putString(SheetBuilder.SRC_DATA, json.toString())

            val (sheetBuilder, tagname) = when(tmpl.family){
                Glob.FML_ROUNDWOOD -> DialogData(RwSheetBuilder(), RwSheetBuilder.TAGNAME)
                else -> DialogData(UnivSheetBuilder(), UnivSheetBuilder.TAGNAME)
            }
            sheetBuilder.arguments = params
            sheetBuilder.show(fragmentManager, tagname)
        }
    }

    private fun onClickTmplEdit(){
        val selectedList = mTmplAdapter.getSelectedTmplList()
        if(selectedList.size == 1){
            val json = Tmpl.toJson(selectedList[0])
            val bundle = Bundle()
            bundle.putString(TmplBuilder.SRC_DATA, json.toString())
            val tmplBuilder = TmplBuilder()
            tmplBuilder.arguments = bundle
            tmplBuilder.show(fragmentManager, TmplBuilder.TAGNAME)
        }
    }

    private fun onClickTmplDelete(){
        val selectedList = mTmplAdapter.getSelectedTmplList()
        if(selectedList.isNotEmpty()){
            DeleteTmplUI().show(fragmentManager, DeleteTmplUI.TAGNAME)
        }
    }

    inner class TmplListAdapter : RecyclerView.Adapter<TmplListAdapter.ItemHolder>() {
        val itemList: MutableList<Item> = mutableListOf()

        override fun getItemCount(): Int = itemList.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            return ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.to_item, parent, false))
        }
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = itemList[position]
            holder.mTvTitle.text = item.tmpl.title
            val imgSelectorRes: Int = if(mMode == Mode.DEFAULT)
                R.drawable.ic_info_outline_black_24
            else if(item.selector) R.drawable.to_item_selector_en else R.drawable.to_item_selector_dis
            holder.mIvInfo.setImageResource(imgSelectorRes)
        }

        fun getSelectedTmplList(): List<Tmpl> {
            val tmplList: MutableList<Tmpl> = mutableListOf()
            itemList.filter { it.selector }.forEach {
                tmplList.add(it.tmpl)
            }
            return tmplList
        }

        fun selectAll(select: Boolean, notify: Boolean){
            itemList.filter { it.selector == !select }.forEach { it.selector = select }
            if(notify){notifyDataSetChanged()}
        }

        fun isAllSelected(): Boolean {
            return itemList.filter { it.selector }.size == itemList.size
        }

        fun update(list: List<Tmpl>){
            val diffCallback = TmplDiffUtil(itemList, list)
            val diffResult: DiffUtil.DiffResult = DiffUtil.calculateDiff(diffCallback)
            itemList.clear()
            list.forEach { itemList.add(Item(it)) }
            diffResult.dispatchUpdatesTo(this)
        }

        inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
            val mItemCnt: LinearLayout = itemView.findViewById(R.id.to_item)
            val mTvTitle: TextView = itemView.findViewById(R.id.item_title)
            val mIvInfo: ImageView = itemView.findViewById(R.id.item_info)

            init {
                mItemCnt.setOnClickListener { onClickItem() }
                mItemCnt.setOnLongClickListener { onLongClickItem() }
                mIvInfo.setOnClickListener { onClickSelector() }
            }

            private fun onClickItem(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(mMode == Mode.SELECTION){ // сброс режима выделения
                        item.selector = !item.selector
                        if(itemList.none { it.selector }){
                            setMode(Mode.DEFAULT, false)
                        }
                        notifyDataSetChanged()
                        mBtnSelectAll.setBackgroundResource(if(isAllSelected()) R.drawable.co_action_select_all_ch else R.drawable.co_action_select_all)
                    }
                }
            }

            private fun onLongClickItem(): Boolean {
                if(RecyclerView.NO_POSITION != adapterPosition){
                    if(mMode == Mode.DEFAULT){
                        itemList[adapterPosition].selector = true
                        setMode(Mode.SELECTION)
                    }
                }
                return true
            }

            private fun onClickSelector(){
                if(RecyclerView.NO_POSITION != adapterPosition){
                    val item = itemList[adapterPosition]
                    if(mMode == Mode.SELECTION){ // сброс режима выделения
                        item.selector = !item.selector
                        if(itemList.none { it.selector }){
                            setMode(Mode.DEFAULT)
                        } else{ notifyDataSetChanged() }
                    } else{ // open dialog info
                        val jsonTmpl = Tmpl.toJson(item.tmpl)
                        val bundle = Bundle()
                        bundle.putString(TmplInfo.SRC_DATA, jsonTmpl.toString())

                        var tmplInfoUI: TmplInfo? = null
                        var tmplInfoTag: String? = null
                        when(item.tmpl.family){
                            Glob.FML_ROUNDWOOD -> {
                                tmplInfoUI = RwTmplInfo()
                                tmplInfoTag = RwTmplInfo.TAGNAME
                            }
                            Glob.FML_UNIVERSAL -> {
                                tmplInfoUI = UnivTmplInfo()
                                tmplInfoTag = UnivTmplInfo.TAGNAME
                            }
                        }

                        if(tmplInfoUI != null){
                            tmplInfoUI.arguments = bundle
                            tmplInfoUI.show(fragmentManager, tmplInfoTag)
                        }
                    }
                }
            }
        }

        inner class Item(val tmpl: Tmpl){
            var selector: Boolean = false
        }

        inner class TmplDiffUtil(private val oldList: List<Item>, private val newList: List<Tmpl>) : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.tmpl.sid == newItem.sid
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.tmpl.title == newItem.title
            }
        }
    }

}