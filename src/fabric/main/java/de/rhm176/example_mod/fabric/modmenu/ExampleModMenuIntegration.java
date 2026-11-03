package de.rhm176.example_mod.fabric.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.rhm176.build.fabric.FabricEntrypoint;

@FabricEntrypoint
public final class ExampleModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ExampleModConfigScreen::new;
    }
}
