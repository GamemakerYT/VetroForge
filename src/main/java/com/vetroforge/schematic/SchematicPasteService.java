// src/main/java/com/vetroforge/schematic/SchematicPasteService.java
package com.vetroforge.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;

public final class SchematicPasteService {

    public boolean paste(File schematicFile, Location pasteLocation, boolean ignoreAir) {
        if (schematicFile == null || !schematicFile.exists()) return false;
        if (pasteLocation == null || pasteLocation.getWorld() == null) return false;

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

        if (format == null) {
            return false;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();

            World bukkitWorld = pasteLocation.getWorld();

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(bukkitWorld))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(
                                pasteLocation.getBlockX(),
                                pasteLocation.getBlockY(),
                                pasteLocation.getBlockZ()
                        ))
                        .ignoreAirBlocks(ignoreAir)
                        .build();

                Operations.complete(operation);
                editSession.flushSession();
            }

            return true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
    }
}