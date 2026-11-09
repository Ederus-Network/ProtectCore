/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.logging;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum ActionType {
    BLOCK_PLACE(true, Category.PLAYER),
    ENTITY_PLACE(true, Category.PLAYER),
    BLOCK_BREAK(true, Category.PLAYER, Category.WORLD),
    ITEM_PICKUP(true, Category.PLAYER),
    ITEM_DROP(true, Category.PLAYER),
    BUCKET_EMPTY(true, Category.PLAYER),
    INTERACT(true, Category.PLAYER),
    CONTAINER_ADD(true, Category.PLAYER),
    CONTAINER_TAKE(true, Category.PLAYER),
    PLAYER_COMMAND(true, Category.PLAYER),
    PLAYER_MESSAGE(true, Category.PLAYER),
    BLOCK_EXPLODE(true, Category.WORLD),
    ENTITY_EXPLODE(true, Category.WORLD),
    BLOCK_BURN(true, Category.WORLD),
    STRUCTURE_GROW(true, Category.WORLD),
    LIGHTNING_STRIKE(true, Category.WORLD),
    PORTAL_CREATE(true, Category.WORLD),
    LEAVES_DECAY(true, Category.WORLD),
    BLOCK_FORM(true, Category.WORLD),
    PISTON_EXTEND(true, Category.WORLD),
    PISTON_RETRACT(true, Category.WORLD),
    ENTITY_DEATH(true, Category.WORLD),
    LIQUID_FLOW(true, Category.WORLD),
    HOPPER_TRANSFER(true, Category.WORLD),
    CONTAINER_SNAPSHOT(false, Category.INTERNAL);

    private final boolean userVisible;
    private final Set<Category> categories;

    private ActionType(boolean bl, Category ... categoryArray) {
        this.userVisible = bl;
        this.categories = EnumSet.copyOf(Arrays.asList(categoryArray));
    }

    public boolean isUserVisible() {
        return this.userVisible;
    }

    public boolean belongsTo(Category category) {
        return this.categories.contains((Object)category);
    }

    public String lowerName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static List<String> visibleNames() {
        return Arrays.stream(ActionType.values()).filter(ActionType::isUserVisible).map(Enum::name).toList();
    }

    public static List<String> editorNames() {
        return ActionType.visibleNames();
    }

    public static enum Category {
        PLAYER,
        WORLD,
        INTERNAL;

    }
}

