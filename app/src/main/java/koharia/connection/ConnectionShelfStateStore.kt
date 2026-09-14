package koharia.connection

import android.content.Context

/** Small derived shelf state; catalogue entries stay in provider repositories. */
class ConnectionShelfStateStore(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences("shelf_$namespace", Context.MODE_PRIVATE)

    fun read(key: String): String? = preferences.getString(key, null)

    fun write(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
    }
}
