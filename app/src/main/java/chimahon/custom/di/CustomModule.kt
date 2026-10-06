package chimahon.custom.di

import android.app.Application
import chimahon.keybinding.KeyBindingPreferences
import chimahon.novel.kosync.KosyncManager
import chimahon.novel.kosync.KosyncSettingsRepository
import chimahon.novel.kosync.NovelDbPositionStore
import chimahon.novel.opds.OpdsCatalogRepository
import chimahon.custom.kosync.MangaKosyncManager
import eu.kanade.tachiyomi.data.upload.ServerUploadManager
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Everything the fork registers with Injekt, in one place. `AppModule` calls [register] once, so a
 * new fork service is added here and never touches an upstream module.
 */
object CustomModule {
    fun register(registrar: InjektRegistrar, app: Application) = with(registrar) {
        addSingletonFactory { KosyncSettingsRepository(app) }
        addSingletonFactory { KosyncManager(app, get(), positionStore = NovelDbPositionStore(get(), get(), get())) }
        addSingletonFactory { MangaKosyncManager(app, get(), get(), get(), get()) }
        addSingletonFactory { ServerUploadManager(app, get(), get()) }
        addSingletonFactory { OpdsCatalogRepository(app) }
        addSingletonFactory { KeyBindingPreferences(get()) }
    }
}
