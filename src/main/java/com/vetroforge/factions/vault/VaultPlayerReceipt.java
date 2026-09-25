package com.vetroforge.factions.vault;

import net.kyori.adventure.nbt.BinaryTagIO;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** A saveData() call alone cannot prove that Paper successfully wrote player data. */
public final class VaultPlayerReceipt {
    public static void verify(Path playerData, String key, String expected) throws IOException {
        String actual = BinaryTagIO.reader(16 * 1024 * 1024)
                .read(playerData, BinaryTagIO.Compression.GZIP).getCompound("BukkitValues").getString(key);
        if (!expected.equals(actual)) throw new IOException("Player inventory receipt was not saved");
        try (FileChannel channel = FileChannel.open(playerData, StandardOpenOption.WRITE)) { channel.force(true); }
        try (FileChannel directory = FileChannel.open(playerData.getParent(), StandardOpenOption.READ)) { directory.force(true); }
    }
    private VaultPlayerReceipt() {}
}
