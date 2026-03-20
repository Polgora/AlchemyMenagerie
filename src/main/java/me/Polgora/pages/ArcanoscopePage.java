package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ArcanoscopePage extends InteractiveCustomUIPage<ArcanoscopePage.ElementAction> {

    public static class ElementAction {
        public static final BuilderCodec<ElementAction> CODEC = BuilderCodec.builder(ElementAction.class, ElementAction::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("Element", Codec.STRING), (d, v) -> d.element = v, d -> d.element)
                .build();
        public String action;
        public String element;
    }

    private final String displayName;
    private final List<String> elements;

    public ArcanoscopePage(PlayerRef playerRef, String displayName, List<String> elements) {
        super(playerRef, CustomPageLifetime.CanDismiss, ElementAction.CODEC);
        this.displayName = displayName;
        this.elements = elements;
    }

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("ArcanoscopeUI.ui");
        commandBuilder.set("#BlockNameLabel.Text", displayName.toUpperCase());
        populateElementList(commandBuilder, eventBuilder);
    }

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ElementAction data) {
        if (!"select".equals(data.action) || data.element == null) return;
        // TODO: handle element selection — behavior to be defined
    }

    private void populateElementList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            String entry = "#ElementList[" + i + "]";
            commandBuilder.append("#ElementList", "ElementSlot.ui");
            commandBuilder.set(entry + " #ElementLabel.Text", element);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, entry + " #ElementButton",
                    EventData.of("Action", "select").append("Element", element));
        }
    }

    /** Parses element lines from the _Known variant's description in server.lang. */
    public static List<String> parseElements(String blockId) {
        String key = "server.items." + blockId + "_Known.description";
        String desc = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        if (desc == null || desc.isBlank()) return List.of();

        // Description format: "Elements:\nCarbon (72%)\nOxygen (46%)\nMagnesium (4%)"
        // server.lang stores \n as a literal backslash-n
        String[] lines = desc.split("\\\\n|\n");
        List<String> elements = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("Elements:")) continue;
            elements.add(line);
        }
        return elements;
    }

    /** Resolves the display name for a block ID from server.lang. */
    public static String resolveDisplayName(String blockId) {
        String key = "server.items." + blockId + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : blockId;
    }
}