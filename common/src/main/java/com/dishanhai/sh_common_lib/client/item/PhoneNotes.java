// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.mojang.logging.LogUtils;
import com.dishanhai.sh_better_ui.kernel.api.ConfigCodec;
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIConfigCenter;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIPriority;
import com.dishanhai.sh_better_ui.kernel.api.UITask;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

final class PhoneNotes {
    static final int LIMIT = 2048;
    private static final ConfigKey<String> NOTE = new ConfigKey<>(new UiId(ShCommonLib.MOD_ID, "phone_note"),
            String.class, "", Set.of(ConfigLayer.USER_GLOBAL), ConfigKey.Mutability.RUNTIME,
            ConfigKey.Persistence.USER_GLOBAL, 1, "sh_common_lib:phone_note", value -> value.length() <= LIMIT,
            UnaryOperator.identity(), Map.of());

    private UIConfigCenter configs;
    private ModuleContext context;
    private String stored = "";
    private String draft;
    private Component status;
    private boolean saving;
    private boolean failed;

    void bind(ModuleContext module) {
        if (context == module) return;
        configs = null;
        if (module != null) {
            var settings = module.engine().configs();
            settings.define(NOTE);
            module.scope().track(settings.persist(NOTE, ConfigCodec.strings()));
            configs = settings;
        }
        context = module;
        draft = null;
        status = null;
        saving = false;
        failed = false;
    }

    boolean available() { return configs != null; }

    String draft() {
        if (draft == null) {
            stored = available() ? configs.get(NOTE) : "";
            draft = stored;
        }
        return draft;
    }

    void edit(String value) {
        draft = value;
        if (!saving) status = null;
    }

    Component status() { return status; }

    boolean canSave() {
        return available() && !saving && draft != null && (failed || !draft.equals(stored));
    }

    void save() {
        if (!canSave()) return;
        var settings = configs;
        var value = draft;
        saving = true;
        status = PhoneScreen.text("notes.saving");
        try {
            context.engine().scheduler().submit(UITask.ui(context.scope(), UIPhase.STATE_MUTATION,
                    UIPriority.USER_INTERACTION, () -> write(settings, value)));
        } catch (RuntimeException failure) {
            saved(value, failure);
        }
    }

    private void write(UIConfigCenter settings, String value) {
        try {
            settings.beginTransaction().set(settings.userGlobal(), NOTE, value).commit();
            settings.flush().whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
                if (configs == settings) saved(value, failure);
            }));
        } catch (RuntimeException failure) {
            saved(value, failure);
        }
    }

    private void saved(String value, Throwable failure) {
        saving = false;
        failed = failure != null;
        if (failed) LogUtils.getLogger().warn("Could not save the phone note", failure);
        if (!failed) stored = value;
        status = failed ? PhoneScreen.text("notes.failed")
                : draft.equals(stored) ? PhoneScreen.text("notes.saved") : null;
    }
}
