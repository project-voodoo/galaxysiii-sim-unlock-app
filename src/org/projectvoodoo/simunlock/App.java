
package org.projectvoodoo.simunlock;

import android.app.Application;
import android.content.Context;

public class App extends Application {

    static Context context;

    static final String VALID_BUILD_MODEL[] = {
            "GT-I9300.*"
    };

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
    }
}
