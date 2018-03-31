package ru.sergeyri.tochkovkaplusv2

import android.app.FragmentManager


/**
 * Created by sergeyri on 07.02.18.
 */
class TmplUI : FragmentUI() {
    companion object {
        val TAGNAME = "tmpl_ui"
        fun getInstance(fragmentManager: FragmentManager): TmplUI {
            var fragment = fragmentManager.findFragmentByTag(TAGNAME)
            if(fragment == null){fragment = TmplUI()
            }
            return fragment as TmplUI
        }
    }

    override fun onBack(): Boolean {
        return false
    }

    override fun filter(key: String) {

    }

    override fun onBgStart(bgName: String) {

    }

    override fun onBgResult(bgName: String, result: Boolean) {

    }

    override val xTagname: String = TAGNAME
    override val xFilterInputHint: String by lazy{ resources.getString(R.string.hint_filterComponentList) }
    override val xFilterVoiceDescription: String by lazy{ resources.getString(R.string.tip_voiceComponentTitle) }



}