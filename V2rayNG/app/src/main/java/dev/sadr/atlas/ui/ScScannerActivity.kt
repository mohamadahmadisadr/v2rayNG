package dev.sadr.atlas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import dev.sadr.atlas.R
import dev.sadr.atlas.extension.toastError
import dev.sadr.atlas.extension.toastSuccess
import dev.sadr.atlas.handler.AngConfigManager

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { }
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)

                if (count + countSub > 0) {
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }

                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}