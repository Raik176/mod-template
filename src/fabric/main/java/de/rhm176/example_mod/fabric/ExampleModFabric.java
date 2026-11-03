package de.rhm176.example_mod.fabric;

import de.rhm176.build.fabric.FabricEntrypoint;
import de.rhm176.example_mod.ExampleMod;
import net.fabricmc.api.ModInitializer;

@FabricEntrypoint
public final class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ExampleMod.init();
    }
}
