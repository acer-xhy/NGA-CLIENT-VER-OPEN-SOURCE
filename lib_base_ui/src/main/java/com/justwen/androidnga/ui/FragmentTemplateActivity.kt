package com.justwen.androidnga.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.justwen.androidnga.base.activity.ARouterConstants

class FragmentTemplateActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFragment()
    }

    private fun initFragment() {
        val fragmentStr: String? = intent.getStringExtra("fragment")
        fragmentStr?.let {
            val fragment = Class.forName(fragmentStr).getConstructor().newInstance()
            if (fragment is Fragment) {
                val bundle: Bundle? = intent.extras
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main_content, fragment).commit()
            }
        }

    }

}
