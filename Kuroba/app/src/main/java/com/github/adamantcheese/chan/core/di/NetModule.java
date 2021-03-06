/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.di;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.net.ProxiedHurlStack;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;

import org.codejargon.feather.Provides;

import java.io.File;

import javax.inject.Singleton;

import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NetModule {
    public static final String USER_AGENT = getApplicationLabel() + "/" + BuildConfig.VERSION_NAME;

    @Provides
    @Singleton
    public RequestQueue provideRequestQueue() {
        Logger.d(AppModule.DI_TAG, "Request queue");
        return Volley.newRequestQueue(getAppContext(), new ProxiedHurlStack());
    }

    @Provides
    @Singleton
    public FileCache provideFileCache(FileManager fileManager) {
        Logger.d(AppModule.DI_TAG, "File cache");
        return new FileCache(getCacheDir(), fileManager);
    }

    private File getCacheDir() {
        // See also res/xml/filepaths.xml for the fileprovider.
        if (getAppContext().getExternalCacheDir() != null) {
            return getAppContext().getExternalCacheDir();
        } else {
            return getAppContext().getCacheDir();
        }
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager() {
        Logger.d(AppModule.DI_TAG, "Http call manager");
        return new HttpCallManager();
    }

    @Provides
    @Singleton
    public OkHttpClient provideBasicOkHttpClient() {
        Logger.d(AppModule.DI_TAG, "OkHTTP client");
        return new ProxiedOkHttpClient();
    }

    //this is basically the same as OkHttpClient, but with a singleton for a proxy instance
    public class ProxiedOkHttpClient
            extends OkHttpClient {
        private OkHttpClient proxiedClient;

        public OkHttpClient getProxiedClient() {
            if (proxiedClient == null) {
                proxiedClient = newBuilder().proxy(ChanSettings.getProxy())
                        .connectTimeout(20, SECONDS)
                        .readTimeout(20, SECONDS)
                        .writeTimeout(20, SECONDS)
                        .callTimeout(2, MINUTES)
                        .build();
            }
            return proxiedClient;
        }
    }
}
