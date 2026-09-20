package koharia.connection.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import koharia.connection.SharedConfigMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

class SharedConfigMigrationActivity : FragmentActivity() {
    private val migration by lazy { Injekt.get<SharedConfigMigration>() }
    private var authenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!migration.isPending()) {
            completeSelection()
            return
        }
        lifecycleScope.launch {
            migration.pending.first { !it }
            completeSelection()
        }
        setContent {
            if (authenticated) SharedConfigSelection(migration) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || authenticated || AuthenticatorUtil.isAuthenticating) return
        if (!migration.requiresAuthentication() || !isAuthenticationSupported()) {
            authenticated = true
            return
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        startAuthentication(
            contextStringResource(MR.strings.unlock_app_title, contextStringResource(MR.strings.app_name)),
            confirmationRequired = false,
            callback = object : AuthenticatorUtil.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    activity: FragmentActivity?,
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(activity, result)
                    (activity as? SharedConfigMigrationActivity)?.authenticated = true
                    SecureActivityDelegate.unlock()
                }

                override fun onAuthenticationError(
                    activity: FragmentActivity?,
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(activity, errorCode, errString)
                    activity?.finishAffinity()
                }
            },
        )
    }

    override fun onStop() {
        if (!AuthenticatorUtil.isAuthenticating) authenticated = false
        super.onStop()
    }

    private fun completeSelection() {
        (application as App).initializeSharedConfiguration()
        eu.kanade.tachiyomi.data.library.LibraryUpdateJob.setupTask(applicationContext)
        eu.kanade.tachiyomi.data.backup.create.BackupCreateJob.setupTask(
            applicationContext,
        )
        @Suppress("DEPRECATION")
        val destination = intent.getParcelableExtra<Intent>(EXTRA_DESTINATION)
            ?: Intent(this@SharedConfigMigrationActivity, MainActivity::class.java)
        startActivity(destination)
        finish()
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}

@androidx.compose.runtime.Composable
internal fun SharedConfigSelection(migration: SharedConfigMigration, onSelected: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(migration.defaultConnectionId()) }
    var busy by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    eu.kanade.presentation.theme.TachiyomiTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp),
            ) {
                Text(
                    stringResource(MR.strings.shared_config_choose_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(MR.strings.shared_config_choose_message),
                    Modifier.padding(vertical = 16.dp),
                )
                val choices = listOf(null to stringResource(MR.strings.shared_config_previous)) +
                    migration.candidates().map { it.id to "${it.name} · ${it.providerId}" }
                choices.forEach { (id, label) ->
                    Row(
                        Modifier.clickable(enabled = !busy) { selected = id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == id, onClick = { selected = id }, enabled = !busy)
                        Text(label)
                    }
                }
                if (failed) {
                    Text(
                        stringResource(MR.strings.shared_config_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val success = withContext(Dispatchers.IO) { migration.select(selected) }
                            if (success) {
                                onSelected()
                            } else {
                                failed = true
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(MR.strings.action_ok)) }
            }
        }
    }
}
