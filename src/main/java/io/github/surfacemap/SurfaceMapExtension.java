package io.github.surfacemap;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;

public class SurfaceMapExtension implements BurpExtension {

    private SurfaceMapHandler handler;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Surface Map");

        SurfaceMapModel  model = new SurfaceMapModel(api);
        SurfaceMapTab    tab   = new SurfaceMapTab(api, model);
        handler = new SurfaceMapHandler(api, model, tab);
        tab.setHandler(handler);

        api.http().registerHttpHandler(handler);
        api.userInterface().registerSuiteTab("Surface Map", tab.component());

        api.extension().registerUnloadingHandler(new ExtensionUnloadingHandler() {
            @Override
            public void extensionUnloaded() {
                handler.shutdown();
                model.shutdown();
                api.logging().logToOutput("Surface Map unloaded cleanly.");
            }
        });

        api.logging().logToOutput("Surface Map loaded. Define scope in Target > Scope, " +
                "tick Capture in the Surface Map tab, then browse the target.");
    }
}
