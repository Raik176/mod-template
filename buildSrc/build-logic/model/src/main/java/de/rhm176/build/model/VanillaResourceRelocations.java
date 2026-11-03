package de.rhm176.build.model;

import java.util.ArrayList;
import java.util.List;

public final class VanillaResourceRelocations {
    private record Rename(McVersion since, String before, String after) {
        ResourceRelocation forward() {
            return ResourceRelocation.renaming(before, after);
        }

        ResourceRelocation backward() {
            return ResourceRelocation.renaming(after, before);
        }
    }

    private static final List<Rename> RENAMES = renames();

    private VanillaResourceRelocations() {}

    public static List<ResourceRelocation> between(McVersion from, McVersion to) {
        List<ResourceRelocation> relocations = new ArrayList<>();
        for (Rename rename : RENAMES) {
            boolean fromRenamed = from.isAtLeast(rename.since());
            boolean toRenamed = to.isAtLeast(rename.since());
            if (!fromRenamed && toRenamed) relocations.add(rename.forward());
            else if (fromRenamed && !toRenamed) relocations.add(rename.backward());
        }
        return relocations;
    }

    public static List<ResourceRelocation> between(String from, String to) {
        return between(McVersion.parse(from), McVersion.parse(to));
    }

    private static List<Rename> renames() {
        List<Rename> renames = new ArrayList<>();

        McVersion singularDirectories = McVersion.parse("1.21");
        String[][] directories = {
            {"advancements", "advancement"},
            {"functions", "function"},
            {"item_modifiers", "item_modifier"},
            {"loot_tables", "loot_table"},
            {"predicates", "predicate"},
            {"recipes", "recipe"},
            {"structures", "structure"},
            {"tags/blocks", "tags/block"},
            {"tags/entity_types", "tags/entity_type"},
            {"tags/fluids", "tags/fluid"},
            {"tags/functions", "tags/function"},
            {"tags/game_events", "tags/game_event"},
            {"tags/items", "tags/item"},
        };
        for (String[] directory : directories) {
            renames.add(new Rename(
                    singularDirectories, "data/(*)/" + directory[0] + "/(**)", "data/(*)/" + directory[1] + "/(**)"));
        }

        McVersion equipmentModels = McVersion.parse("1.21.2");
        String[][] equipment = {
            {
                "assets/minecraft/textures/models/armor/turtle_layer_1.png(*)",
                "assets/minecraft/textures/entity/equipment/humanoid/turtle_scute.png(*)"
            },
            {
                "assets/(*)/textures/models/armor/(*)_layer_1.png(*)",
                "assets/(*)/textures/entity/equipment/humanoid/(*).png(*)"
            },
            {
                "assets/(*)/textures/models/armor/(*)_layer_2.png(*)",
                "assets/(*)/textures/entity/equipment/humanoid_leggings/(*).png(*)"
            },
            {
                "assets/(*)/textures/trims/models/armor/(*)_leggings.png(*)",
                "assets/(*)/textures/trims/entity/humanoid_leggings/(*).png(*)"
            },
            {
                "assets/(*)/textures/trims/models/armor/(*).png(*)",
                "assets/(*)/textures/trims/entity/humanoid/(*).png(*)"
            },
            {
                "assets/minecraft/textures/entity/wolf/wolf_armor.png(*)",
                "assets/minecraft/textures/entity/equipment/wolf_body/armadillo_scute.png(*)"
            },
            {
                "assets/minecraft/textures/entity/wolf/wolf_armor_overlay.png(*)",
                "assets/minecraft/textures/entity/equipment/wolf_body/armadillo_scute_overlay.png(*)"
            },
            {
                "assets/minecraft/textures/entity/elytra.png(*)",
                "assets/minecraft/textures/entity/equipment/wings/elytra.png(*)"
            },
        };
        for (String[] rename : equipment) renames.add(new Rename(equipmentModels, rename[0], rename[1]));

        McVersion featureRegistries = McVersion.parse("26.3");
        renames.add(new Rename(
                featureRegistries, "data/(*)/worldgen/configured_feature/(**)", "data/(*)/worldgen/feature/(**)"));
        renames.add(new Rename(
                featureRegistries, "data/(*)/worldgen/configured_carver/(**)", "data/(*)/worldgen/carver/(**)"));

        McVersion palettes = McVersion.parse("26.3");
        renames.add(new Rename(
                palettes,
                "assets/minecraft/textures/trims/color_palettes/trim_palette.png(*)",
                "assets/minecraft/textures/palettes/trim_base.png(*)"));
        renames.add(new Rename(
                palettes, "assets/(*)/textures/trims/color_palettes/(**)", "assets/(*)/textures/palettes/trim/(**)"));

        return renames;
    }
}
