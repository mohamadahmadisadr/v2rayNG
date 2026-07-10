package dev.sadr.atlas.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import dev.sadr.atlas.R
import dev.sadr.atlas.core.CoreServiceManager

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContent { }

        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopVService(this)
        } else {
            CoreServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }
}
