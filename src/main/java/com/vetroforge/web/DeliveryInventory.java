package com.vetroforge.web;

import com.vetroforge.items.ItemSafety;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.*;
import java.util.*;

/** Plans a complete delivery without mutating live inventory or special-item stacks. */
public final class DeliveryInventory {
    public static ItemStack[] plan(ItemStack[] before, ItemStack template, int amount) {
        if (amount < 1 || empty(template)) throw new IllegalArgumentException("Invalid delivery");
        ItemStack[] after = Arrays.stream(before).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        int remaining = amount;
        for (ItemStack stack : after) {
            if (stack == null || ItemSafety.locked(stack) || !stack.isSimilar(template)) continue;
            int added = Math.min(remaining, Math.max(0, Math.min(stack.getMaxStackSize(), template.getMaxStackSize()) - stack.getAmount()));
            stack.setAmount(stack.getAmount() + added); remaining -= added;
        }
        for (int i = 0; i < after.length && remaining > 0; i++) {
            if (!empty(after[i])) continue;
            after[i] = template.clone();
            int added = Math.min(remaining, template.getMaxStackSize());
            after[i].setAmount(added); remaining -= added;
        }
        return remaining == 0 ? after : null;
    }
    public static String encode(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack item : items) out.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getType() == Material.CAVE_AIR || item.getType() == Material.VOID_AIR;
    }
    private DeliveryInventory() {}
}
