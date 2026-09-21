package koharia.tts.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import koharia.tts.TtsPreferences
import koharia.tts.TtsSecurePreferences
import koharia.tts.TtsVendor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TtsSettingsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.tts_engine_settings_title

    @Composable
    override fun getPreferences(): List<Preference> = getPreferences(readerControlsOnly = false)

    @Composable
    fun getPreferences(readerControlsOnly: Boolean): List<Preference> {
        val preferences = remember { Injekt.get<TtsPreferences>() }
        val securePreferences = remember { Injekt.get<TtsSecurePreferences>() }
        val uriHandler = LocalUriHandler.current
        val vendorId by preferences.vendorId.changes().collectAsState(preferences.vendorId.get())
        val vendor = TtsVendor.fromId(vendorId)
        val speedTenths by preferences.speedTenths.changes().collectAsState(preferences.speedTenths.get())
        val displaySpeed = speedTenths.coerceIn(TtsPreferences.MIN_SPEED_TENTHS, TtsPreferences.MAX_SPEED_TENTHS)
        var editingKey by remember(vendor.id) { mutableStateOf(false) }
        var keyConfigured by remember(vendor.id) {
            mutableStateOf(!securePreferences.getApiKey(vendor.id).isNullOrBlank())
        }
        var needsKeyReentry by remember(vendor.id) {
            mutableStateOf(securePreferences.needsLegacyKeyReentry(vendor.id))
        }

        if (editingKey) {
            ApiKeyDialog(
                vendor = vendor,
                securePreferences = securePreferences,
                onDismiss = { editingKey = false },
                onSaved = {
                    keyConfigured = !securePreferences.getApiKey(vendor.id).isNullOrBlank()
                    needsKeyReentry = securePreferences.needsLegacyKeyReentry(vendor.id)
                    editingKey = false
                },
            )
        }

        if (readerControlsOnly) {
            return listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.vendorId,
                    title = stringResource(MR.strings.tts_engine_section),
                    entries = TtsVendor.ALL.associate { it.id to it.displayName }.toImmutableMap(),
                    subtitleProvider = { _, _ -> vendor.displayName },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = displaySpeed,
                    title = stringResource(MR.strings.tts_speed),
                    valueString = "${displaySpeed / 10f}×",
                    valueRange = TtsPreferences.MIN_SPEED_TENTHS..TtsPreferences.MAX_SPEED_TENTHS,
                    onValueChanged = { preferences.speedTenths.set(it) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.voiceIdFor(vendor.id),
                    title = stringResource(MR.strings.tts_engine_voice_section),
                    entries = vendor.presetVoices().associate { it.id to it.name }.toImmutableMap(),
                    subtitleProvider = { value, entries -> entries[value] ?: entries[vendor.defaultVoiceId()] },
                ),
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.tts_engine_settings_title),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.vendorId,
                        title = stringResource(MR.strings.tts_engine_section),
                        entries = TtsVendor.ALL.associate { it.id to it.displayName }.toImmutableMap(),
                        subtitleProvider = { _, _ -> vendor.displayName },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = displaySpeed,
                        title = stringResource(MR.strings.tts_speed),
                        valueString = "${displaySpeed / 10f}×",
                        valueRange = TtsPreferences.MIN_SPEED_TENTHS..TtsPreferences.MAX_SPEED_TENTHS,
                        onValueChanged = { preferences.speedTenths.set(it) },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.voiceIdFor(vendor.id),
                        title = stringResource(MR.strings.tts_engine_voice_section),
                        entries = vendor.presetVoices().associate { it.id to it.name }.toImmutableMap(),
                        subtitleProvider = { value, entries -> entries[value] ?: entries[vendor.defaultVoiceId()] },
                    ),
                ).toImmutableList(),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.tts_engine_api_key_label),
                preferenceItems = buildList {
                    if (vendor.needsApiKey) {
                        if (needsKeyReentry) {
                            add(
                                Preference.PreferenceItem.TextPreference(
                                    title = stringResource(MR.strings.tts_legacy_key_notice_title),
                                    subtitle = stringResource(MR.strings.tts_legacy_key_notice_body),
                                ),
                            )
                        }
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.tts_engine_api_key_label),
                                subtitle = stringResource(
                                    if (keyConfigured) {
                                        MR.strings.tts_engine_status_ok
                                    } else {
                                        MR.strings.tts_engine_status_missing
                                    },
                                ),
                                onClick = { editingKey = true },
                            ),
                        )
                        vendor.apiKeyHintUrl?.let { url ->
                            add(
                                Preference.PreferenceItem.CustomPreference(
                                    title = stringResource(MR.strings.tts_engine_get_api_key),
                                    content = {
                                        TextPreferenceWidget(
                                            title = stringResource(MR.strings.tts_engine_get_api_key),
                                            onPreferenceClick = { uriHandler.openUri(url) },
                                            widget = {
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(end = 24.dp).size(18.dp),
                                                )
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                    } else {
                        add(
                            Preference.PreferenceItem.InfoPreference(
                                title = stringResource(MR.strings.tts_engine_vendor_free),
                            ),
                        )
                    }
                }.toImmutableList(),
            ),
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(MR.strings.tts_data_disclosure_settings_card_title),
                content = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(MR.strings.tts_data_disclosure_settings_card_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(MR.strings.tts_data_disclosure_settings_card_body, vendor.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ),
        )
    }

    @Composable
    private fun ApiKeyDialog(
        vendor: TtsVendor,
        securePreferences: TtsSecurePreferences,
        onDismiss: () -> Unit,
        onSaved: () -> Unit,
    ) {
        var input by remember(vendor.id) { mutableStateOf(securePreferences.getApiKey(vendor.id).orEmpty()) }
        var showKey by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(MR.strings.tts_engine_api_key_label)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(vendor.displayName) },
                        supportingText = { Text(stringResource(MR.strings.tts_engine_api_key_hint)) },
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (showKey) {
                                            MR.strings.tts_engine_hide_api_key
                                        } else {
                                            MR.strings.tts_engine_show_api_key
                                        },
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        securePreferences.setApiKey(vendor.id, input)
                        onSaved()
                    },
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
