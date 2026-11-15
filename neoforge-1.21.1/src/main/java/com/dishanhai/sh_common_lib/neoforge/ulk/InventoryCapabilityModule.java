// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.ulk;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.automation.ServerCapabilityModule;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityResult;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilitySchema;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityValue;
import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Exposes ULK's existing self-inventory query to the server-side UUK directory. */
public final class InventoryCapabilityModule implements ServerCapabilityModule {
    private static final UiId MODULE = UiId.parse("sh_common_lib:inventory");
    private static final UiId COUNT = UiId.parse("sh_common_lib:inventory/count");
    private static final UiVersion VERSION = UiVersion.parse("1.0.0");
    private static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(COUNT, 1, MODULE, VERSION,
            new CapabilitySchema(Map.of("item", new CapabilitySchema.Field(CapabilitySchema.Type.TEXT, true))),
            new CapabilitySchema(Map.of("count", new CapabilitySchema.Field(CapabilitySchema.Type.INTEGER, true),
                    "sources", new CapabilitySchema.Field(CapabilitySchema.Type.FIELDS, true))),
            CapabilityDescriptor.Access.READ, COUNT, CapabilityDescriptor.Deduplication.NONE);

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(MODULE).version(VERSION).build();
    }

    @Override
    public void register(MinecraftServer server, Registrar registrar) {
        registrar.register(DESCRIPTOR, (caller, request) -> CompletableFuture.completedStage(
                count(server.getPlayerList().getPlayer(caller.playerId()), request)));
    }

    private static CapabilityResult count(ServerPlayer player, CapabilityRequest request) {
        if (player == null) return CapabilityResult.notExecuted(CapabilityResult.Code.SESSION_CLOSED);
        String itemName = ((CapabilityValue.Text) request.parameters().get("item")).value();
        var itemId = ResourceLocation.tryParse(itemName);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return CapabilityResult.notExecuted(CapabilityResult.Code.INVALID_PARAMETERS);
        }
        var item = BuiltInRegistries.ITEM.get(itemId);
        var snapshot = UnifiedLibKernel.inspect(player);
        var sources = new LinkedHashMap<String, CapabilityValue>();
        snapshot.sources().forEach((source, status) -> sources.put(source.name().toLowerCase(Locale.ROOT),
                new CapabilityValue.Text(status.name())));
        return CapabilityResult.success(Map.of("count", new CapabilityValue.IntegerValue(snapshot.count(stack -> stack.is(item))),
                "sources", new CapabilityValue.Fields(sources)));
    }
}
