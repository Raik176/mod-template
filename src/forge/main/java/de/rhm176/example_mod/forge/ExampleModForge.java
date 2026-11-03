package de.rhm176.example_mod.forge;

import de.rhm176.example_mod.ExampleMod;
//? if <1.21.6 {
import net.minecraftforge.common.MinecraftForge;
//?}
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModForge {
    //? if >=1.21.6 {
    /*public ExampleModForge(FMLJavaModLoadingContext context) {
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(ExampleModForge::onCommonSetup);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(ExampleModForge::onPlayerLoggedIn);
        ExampleMod.init();
    }
    *///?} else if >=1.21.1 {
    public ExampleModForge(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(ExampleModForge::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(ExampleModForge::onPlayerLoggedIn);
        ExampleMod.init();
    }
    //?} else {
    /*public ExampleModForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ExampleModForge::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(ExampleModForge::onPlayerLoggedIn);
        ExampleMod.init();
    }
    *///?}

    private static void onCommonSetup(FMLCommonSetupEvent event) {

    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {

    }
}
