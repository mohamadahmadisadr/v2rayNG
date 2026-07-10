package dev.sadr.atlas.ui

import android.os.Bundle
import androidx.fragment.app.FragmentContainerView
import dev.sadr.atlas.R

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FragmentContainerView(this).apply {
            id = R.id.fragment_settings
        }
        setContentViewWithToolbar(container, title = getString(R.string.title_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_settings, SettingsFragment())
                .commit()
        }
    }
}
