package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.api.client.ItemContentPreview;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.function.Consumer;

final class ParcelClient {
    private ParcelClient() {}

    static void register(IEventBus modBus, Consumer<OpenParcelRequest> send) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ParcelClientHooks::registerModel));
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(ParcelStorage.menuType(), ParcelScreen::new);
            ParcelScreen.registerBlur();
        });
        ParcelClientHooks.registerPreview();
        ParcelClientHooks input = new ParcelClientHooks();
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((ItemTooltipEvent event) ->
                ItemContentPreview.append(event.getItemStack(), event.getToolTip()));
        gameBus.addListener((ScreenEvent.Opening event) -> input.reset());
        gameBus.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (input.mousePressed(event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY(), send)) {
                event.setCanceled(true);
            }
        });
    }
}
