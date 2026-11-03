package de.rhm176.example_mod.neoforge;

import de.rhm176.example_mod.ExampleMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if <1.20.4 {
/*import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
*///?}
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    //? if >=1.20.4 {
    public ExampleModNeoForge(IEventBus modBus) {
        register(modBus);
    }
    //?} else {
    /*public ExampleModNeoForge() {
        register(FMLJavaModLoadingContext.get().getModEventBus());
    }
    *///?}

    private static void register(IEventBus modBus) {
        modBus.addListener(ExampleModNeoForge::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(ExampleModNeoForge::onPlayerLoggedIn);
        ExampleMod.init();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {

    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

    }
}
