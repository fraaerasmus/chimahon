package chimahon.keybinding

import tachiyomi.core.common.preference.PreferenceStore

class KeyBindingPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun bindings(context: KeyContext) = preferenceStore.getObjectFromString(
        key = "pref_key_bindings_${context.prefKey}",
        defaultValue = defaultKeyBindings(context),
        serializer = ::encodeKeyBindings,
        deserializer = ::decodeKeyBindings,
    )
}
