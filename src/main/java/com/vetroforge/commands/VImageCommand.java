package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VImageCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    private final File imageDir;
    private final File approvedFile;
    private final Set<String> approved = new HashSet<>();

    public VImageCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.imageDir = new File(plugin.getDataFolder(), "images");
        if (!imageDir.exists()) imageDir.mkdirs();
        this.approvedFile = new File(imageDir, "approved.txt");
        loadApproved();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.vimage")) return message(sender, UiDecor.noPermission());
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            String[] names = imageDir.list((dir, name) -> isImage(name) && approved.contains(name.toLowerCase(java.util.Locale.ROOT)));
            return message(sender, "§7Bilder: §f" + (names == null || names.length == 0 ? "-" : String.join(", ", names)));
        }
        if (args[0].equalsIgnoreCase("pending")) {
            if (!sender.hasPermission("vetroforge.vimage.moderate")) return message(sender, UiDecor.noPermission());
            String[] names = imageDir.list((dir, name) -> isImage(name) && !approved.contains(name.toLowerCase(java.util.Locale.ROOT)));
            return message(sender, "§7Offen: §f" + (names == null || names.length == 0 ? "-" : String.join(", ", names)));
        }
        if (args[0].equalsIgnoreCase("approve") && args.length >= 2) {
            if (!sender.hasPermission("vetroforge.vimage.moderate")) return message(sender, UiDecor.noPermission());
            try {
                File file = resolve(args[1]);
                approved.add(file.getName().toLowerCase(java.util.Locale.ROOT));
                saveApproved();
                return message(sender, "§aFreigegeben: §f" + file.getName());
            } catch (IOException exception) {
                return message(sender, "§cBild nicht gefunden.");
            }
        }
        if (!(sender instanceof Player player)) return message(sender, UiDecor.playersOnly());
        if (args[0].equalsIgnoreCase("padding") && args.length >= 4) {
            return createPaddingBanner(player, args[1], parse(args[2], 1), parse(args[3], 1));
        }
        if (!args[0].equalsIgnoreCase("create") || args.length < 2) {
            return message(sender, "§7/" + label + " <list|pending|approve <datei>|create <datei>|padding <datei> <breite> <höhe>>");
        }
        try {
            File file = resolve(args[1]);
            if (!sender.hasPermission("vetroforge.vimage.moderate")
                    && !approved.contains(file.getName().toLowerCase(java.util.Locale.ROOT))) {
                return message(sender, "§cDieses Bild ist noch nicht freigegeben.");
            }
            BufferedImage image = ImageIO.read(file);
            if (image == null) return message(sender, "§cBildformat nicht lesbar. Nutze PNG oder JPG.");
            player.getInventory().addItem(render(player.getWorld(), image));
            return message(sender, "§aMap-Bild erstellt: §f" + file.getName());
        } catch (IOException exception) {
            return message(sender, "§cBild nicht gefunden. Lege es in §fplugins/VetroForge/images§c ab.");
        }
    }

    private boolean createPaddingBanner(Player player, String imageName, int width, int height) {
        width = Math.max(1, Math.min(64, width));
        height = Math.max(1, Math.min(16, height));
        try {
            File file = resolve(imageName);
            if (!player.hasPermission("vetroforge.vimage.moderate")
                    && !approved.contains(file.getName().toLowerCase(Locale.ROOT))) {
                return message(player, "§cDieses Bild ist noch nicht freigegeben.");
            }
            BufferedImage image = ImageIO.read(file);
            if (image == null) return message(player, "§cBildformat nicht lesbar. Nutze PNG oder JPG.");

            List<ItemStack> maps = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    maps.add(renderTile(player.getWorld(), image, x, y, width, height, file.getName()));
                }
            }
            for (ItemStack map : maps) {
                var left = player.getInventory().addItem(map);
                left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            return message(player, "§aStretch-Banner erstellt: §f" + width + "x" + height
                    + " §7Maps. Reihenfolge: links nach rechts, oben nach unten.");
        } catch (IOException exception) {
            return message(player, "§cBild nicht gefunden. Lege es in §fplugins/VetroForge/images§c ab.");
        }
    }

    private File resolve(String name) throws IOException {
        File file = new File(imageDir, name).getCanonicalFile();
        if (!file.getPath().startsWith(imageDir.getCanonicalPath() + File.separator) || !file.isFile()) throw new IOException("invalid image");
        return file;
    }

    private boolean isImage(String name) {
        return name != null && name.matches("(?i).+\\.(png|jpg|jpeg)");
    }

    private int parse(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; }
    }

    private void loadApproved() {
        approved.clear();
        if (!approvedFile.isFile()) return;
        try {
            for (String line : java.nio.file.Files.readAllLines(approvedFile.toPath())) {
                String clean = line.trim().toLowerCase(java.util.Locale.ROOT);
                if (!clean.isBlank()) approved.add(clean);
            }
        } catch (IOException ignored) {}
    }

    private void saveApproved() {
        try {
            java.nio.file.Files.write(approvedFile.toPath(), approved.stream().sorted().toList());
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte VImage approvals nicht speichern: " + exception.getMessage());
        }
    }

    private ItemStack render(World world, BufferedImage source) {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, 128, 128, null);
        graphics.dispose();
        MapView view = Bukkit.createMap(world);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new MapRenderer() {
            private boolean rendered;
            @Override public void render(MapView map, MapCanvas canvas, Player player) {
                if (rendered) return;
                canvas.drawImage(0, 0, image);
                rendered = true;
            }
        });
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("§b§lVetro Image");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack renderTile(World world, BufferedImage source, int tileX, int tileY, int width, int height, String name) {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sx1 = (int) Math.floor(tileX * (source.getWidth() / (double) width));
        int sy1 = (int) Math.floor(tileY * (source.getHeight() / (double) height));
        int sx2 = (int) Math.ceil((tileX + 1) * (source.getWidth() / (double) width));
        int sy2 = (int) Math.ceil((tileY + 1) * (source.getHeight() / (double) height));
        graphics.drawImage(source, 0, 0, 128, 128, sx1, sy1, sx2, sy2, null);
        graphics.dispose();

        MapView view = Bukkit.createMap(world);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new MapRenderer() {
            private boolean rendered;
            @Override public void render(MapView map, MapCanvas canvas, Player player) {
                if (rendered) return;
                canvas.drawImage(0, 0, image);
                rendered = true;
            }
        });

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName("§b§lVImage §8| §f" + name + " §7[" + (tileX + 1) + "," + (tileY + 1) + "]");
        item.setItemMeta(meta);
        return item;
    }
    private boolean message(CommandSender sender, String text) { sender.sendMessage("§bVImage §8» " + text); return true; }
}
