package eu.kanade.tachiyomi.ui.base.activity

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.kanade.domain.ui.EInkPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    protected open val usePersistedEInkPreferences = true
    protected var configRedirected = false
        private set
    protected var configStartupDeferred = false

    protected fun needsSharedConfigSelection(): Boolean =
        Injekt.get<koharia.connection.SharedConfigMigration>().isPending()

    protected fun redirectToSharedConfigSelection() {
        if (configRedirected) return
        configRedirected = true
        startActivity(
            android.content.Intent(this, koharia.connection.ui.SharedConfigMigrationActivity::class.java)
                .putExtra(
                    koharia.connection.ui.SharedConfigMigrationActivity.EXTRA_DESTINATION,
                    android.content.Intent(intent),
                ),
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!configRedirected && needsSharedConfigSelection()) redirectToSharedConfigSelection()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Injekt.get<koharia.connection.SharedConfigMigration>().pending.collect { pending ->
                    if (pending && !configRedirected) redirectToSharedConfigSelection()
                }
            }
        }
        if (usePersistedEInkPreferences && Injekt.get<EInkPreferences>().enabled.get()) {
            disableActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)
        }
    }

    override fun finish() {
        super.finish()
        if (usePersistedEInkPreferences && Injekt.get<EInkPreferences>().enabled.get()) {
            disableActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
        }
    }

    @Suppress("DEPRECATION")
    private fun disableActivityTransition(transitionType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(transitionType, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}
