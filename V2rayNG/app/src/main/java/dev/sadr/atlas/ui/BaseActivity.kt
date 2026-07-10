package dev.sadr.atlas.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import dev.sadr.atlas.handler.SettingsManager
import dev.sadr.atlas.util.MyContextWrapper
import dev.sadr.atlas.util.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * BaseActivity provides common helpers and UI wiring used across the app's activities.
 */
@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity() {
    protected val isLoadingFlow = MutableStateFlow(false)

    /** App-styled modal dialogs; render with AtlasDialogHost inside setContent. */
    val atlasDialog = AtlasDialogState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Utils.getDarkModeStatus(this)) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    protected fun setContentViewWithToolbar(childView: View, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            fitsSystemWindows = true
        }

        val toolbar = MaterialToolbar(this)
        root.addView(toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
        supportActionBar?.title = title

        val progressBar = LinearProgressIndicator(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progressBar)

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        if (childView.parent != null) {
            (childView.parent as ViewGroup).removeView(childView)
        }
        container.addView(childView)
        root.addView(container)

        setContentView(root)

        lifecycleScope.launch {
            isLoadingFlow.collect {
                progressBar.visibility = if (it) View.VISIBLE else View.GONE
            }
        }
    }

    protected fun setContentViewWithToolbar(layoutResId: Int, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val view = LayoutInflater.from(this).inflate(layoutResId, null)
        setContentViewWithToolbar(view, showHomeAsUp, title)
    }

    // Member functions intentionally shadow the Context.toast* extensions so every
    // activity shows themed Material snackbars instead of legacy system toasts.
    fun toast(message: Int) = showSnackbar(getString(message), SnackType.NORMAL)
    fun toast(message: CharSequence) = showSnackbar(message.toString(), SnackType.NORMAL)
    fun toastSuccess(message: Int) = showSnackbar(getString(message), SnackType.SUCCESS)
    fun toastSuccess(message: CharSequence) = showSnackbar(message.toString(), SnackType.SUCCESS)
    fun toastError(message: Int) = showSnackbar(getString(message), SnackType.ERROR)
    fun toastError(message: CharSequence) = showSnackbar(message.toString(), SnackType.ERROR)

    protected enum class SnackType { NORMAL, SUCCESS, ERROR }

    protected fun showSnackbar(message: String, type: SnackType) {
        val root = findViewById<View>(android.R.id.content) ?: return
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
        val background = when (type) {
            SnackType.SUCCESS -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorTertiaryContainer)
            SnackType.ERROR -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorErrorContainer)
            SnackType.NORMAL -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        }
        val textColor = when (type) {
            SnackType.SUCCESS -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnTertiaryContainer)
            SnackType.ERROR -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnErrorContainer)
            SnackType.NORMAL -> MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        }
        snackbar.setBackgroundTint(background)
        snackbar.setTextColor(textColor)
        snackbar.view.let { view ->
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val margin = (16 * resources.displayMetrics.density).toInt()
                // Keep the snackbar above MainActivity's bottom navigation bar
                val bottomExtra = if (this is MainActivity) (76 * resources.displayMetrics.density).toInt() else 0
                params.setMargins(margin, margin, margin, margin + bottomExtra)
                view.layoutParams = params
            }
            (view.background as? MaterialShapeDrawable)?.setCornerSize(14 * resources.displayMetrics.density)
        }
        snackbar.show()
    }

    protected fun showLoading() {
        isLoadingFlow.value = true
    }

    protected fun hideLoading() {
        isLoadingFlow.value = false
    }

    protected fun isLoadingVisible(): Boolean {
        return isLoadingFlow.value
    }
}
