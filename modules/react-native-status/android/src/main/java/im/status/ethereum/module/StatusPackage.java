package im.status.ethereum.module;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.Collections;
import java.util.List;

import im.status.ethereum.function.Function;
import statusgo.Statusgo;

public class StatusPackage implements ReactPackage {

    private boolean debug;
    private boolean devCluster;

    public StatusPackage (boolean debug, boolean devCluster) {
        this.debug = debug;
        this.devCluster = devCluster;
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        NativeModule statusModule = new StatusModule(reactContext, this.debug, this.devCluster);
        return Collections.singletonList(statusModule);
    }

    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return Collections.emptyList();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }

    public Function<String, String> getCallRPC() {
        return new Function<String, String>() {
            @Override
            public String apply(String payload) {
                return Statusgo.callRPC(payload);
            }
        };
    }
}
