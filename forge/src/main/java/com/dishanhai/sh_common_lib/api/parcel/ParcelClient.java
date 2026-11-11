package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.api.client.ItemContentPreview;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.function.Consumer;

final class ParcelClient {
    private ParcelClient() {}

    static void register(IEventBus modBus, Consumer<OpenParcelRequest> send) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ParcelClientHooks.registerModel();
            MenuScreens.register(ParcelStorage.menuType(), ParcelScreen::new);
            ParcelScreen.registerBlur();
        }));
        ParcelClientHooks.registerPreview();
        ParcelClientHooks input = new ParcelClientHooks();
        IEventBus gameBus = MinecraftForge.EVENT_BUS;
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
