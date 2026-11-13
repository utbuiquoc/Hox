/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.translations

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mozilla.components.concept.engine.translate.Language
import mozilla.components.concept.engine.translate.TranslationError
import mozilla.components.concept.engine.translate.TranslationPageSettings
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.compose.DESKTOP_DROPDOWN_CORNER_DP

@Composable
internal fun TranslationDialogBottomSheet(
    onRequestDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DESKTOP_DROPDOWN_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        modifier = modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(rememberScrollState()),
    ) {
        Column {
            content()
        }
    }
}

@Suppress("LongParameterList")
@Composable
internal fun TranslationsDialog(
    translationsDialogState: TranslationsDialogState,
    learnMoreUrl: String,
    showPageSettings: Boolean,
    showFirstTime: Boolean = false,
    onSettingClicked: () -> Unit,
    onLearnMoreClicked: () -> Unit,
    onPositiveButtonClicked: () -> Unit,
    onNegativeButtonClicked: () -> Unit,
    onFromSelected: (Language) -> Unit,
    onToSelected: (Language) -> Unit,
) {
    TranslationsDialogBottomSheet(
        translationsDialogState = translationsDialogState,
        learnMoreUrl = learnMoreUrl,
        showPageSettings = showPageSettings,
        showFirstTimeFlow = showFirstTime,
        onSettingClicked = onSettingClicked,
        onLearnMoreClicked = onLearnMoreClicked,
        onPositiveButtonClicked = onPositiveButtonClicked,
        onNegativeButtonClicked = onNegativeButtonClicked,
        onFromDropdownSelected = onFromSelected,
        onToDropdownSelected = onToSelected,
    )
}

@Composable
internal fun TranslationsOptionsDialog(
    context: Context,
    showGlobalSettings: Boolean,
    translationPageSettings: TranslationPageSettings? = null,
    translationPageSettingsError: TranslationError? = null,
    offerTranslation: Boolean? = null,
    initialFrom: Language? = null,
    onStateChange: (TranslationSettingsOption, Boolean) -> Unit,
    onBackClicked: () -> Unit,
    onTranslationSettingsClicked: () -> Unit,
    aboutTranslationClicked: () -> Unit,
) {
    TranslationOptionsDialog(
        showGlobalSettings = showGlobalSettings,
        translationOptionsList =
            getTranslationSwitchItemList(
                translationPageSettings = translationPageSettings,
                offerTranslation = offerTranslation,
                initialFrom = initialFrom,
                context = context,
                onStateChange = onStateChange,
            ),
        pageSettingsError = translationPageSettingsError,
        onBackClicked = onBackClicked,
        onTranslationSettingsClicked = onTranslationSettingsClicked,
        aboutTranslationClicked = aboutTranslationClicked,
    )
}

@Composable
private fun getTranslationSwitchItemList(
    translationPageSettings: TranslationPageSettings? = null,
    offerTranslation: Boolean? = null,
    initialFrom: Language? = null,
    context: Context,
    onStateChange: (TranslationSettingsOption, Boolean) -> Unit,
): List<TranslationSwitchItem> {
    val translationSwitchItemList = mutableListOf<TranslationSwitchItem>()

    val alwaysTranslateLanguage = translationPageSettings?.alwaysTranslateLanguage
    val neverTranslateLanguage = translationPageSettings?.neverTranslateLanguage
    val neverTranslateSite = translationPageSettings?.neverTranslateSite

    offerTranslation?.let {
        translationSwitchItemList.add(
            TranslationSwitchItem(
                type = TranslationPageSettingsOption.AlwaysOfferPopup(),
                textLabel = context.getString(R.string.translation_option_bottom_sheet_always_translate),
                isChecked = it,
                isEnabled =
                    !(alwaysTranslateLanguage == true || neverTranslateLanguage == true || neverTranslateSite == true),
                onStateChange = onStateChange,
            )
        )
    }

    translationPageSettings?.let {
        if (initialFrom != null) {
            alwaysTranslateLanguage?.let {
                translationSwitchItemList.add(
                    TranslationSwitchItem(
                        type = TranslationPageSettingsOption.AlwaysTranslateLanguage(),
                        textLabel =
                            context.getString(
                                R.string.translation_option_bottom_sheet_always_translate_in_language,
                                initialFrom.localizedDisplayName,
                            ),
                        isChecked = it,
                        isEnabled = neverTranslateSite != true,
                        onStateChange = onStateChange,
                    )
                )
            }

            neverTranslateLanguage?.let {
                translationSwitchItemList.add(
                    TranslationSwitchItem(
                        type = TranslationPageSettingsOption.NeverTranslateLanguage(),
                        textLabel =
                            context.getString(
                                R.string.translation_option_bottom_sheet_never_translate_in_language,
                                initialFrom.localizedDisplayName,
                            ),
                        isChecked = it,
                        isEnabled = neverTranslateSite != true,
                        onStateChange = onStateChange,
                    )
                )
            }
        }

        translationPageSettings.neverTranslateSite?.let {
            translationSwitchItemList.add(
                TranslationSwitchItem(
                    type = TranslationPageSettingsOption.NeverTranslateSite(),
                    textLabel = stringResource(R.string.translation_option_bottom_sheet_never_translate_site),
                    isChecked = it,
                    isEnabled = true,
                    onStateChange = onStateChange,
                )
            )
        }
    }
    return translationSwitchItemList
}
