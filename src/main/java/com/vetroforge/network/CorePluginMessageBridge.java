package com.vetroforge.network;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.ReasonsCommand;
import com.vetroforge.commands.ReportCommand;
import com.vetroforge.commands.StaffGuiCommand;
import com.vetroforge.punish.PunishmentManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CorePluginMessageBridge implements PluginMessageListener {

    private final FactionsPlugin plugin;
    private final ReportCommand reportCommand;
    private final ReasonsCommand reasonsCommand;
    private final StaffGuiCommand staffGuiCommand;

    public CorePluginMessageBridge(FactionsPlugin plugin, ReportCommand reportCommand, ReasonsCommand reasonsCommand, StaffGuiCommand staffGuiCommand) {
        this.plugin = plugin;
        this.reportCommand = reportCommand;
        this.reasonsCommand = reasonsCommand;
        this.staffGuiCommand = staffGuiCommand;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"vetro:main".equalsIgnoreCase(channel) || player == null) return;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String action = in.readUTF();
            if ("OPEN_REPORT_GUI".equalsIgnoreCase(action)) {
                String first = readUtf(in);
                Player targetPlayer = playerFromMaybeUuid(first, player);
                String targetName = isUuid(first) ? readUtf(in) : first;
                // NOTE (adapted from factions-repair): the original called
                // reportCommand.openReportGui(targetPlayer, targetName) directly. The current
                // codebase's ReportCommand keeps that method private (its GUI is only opened via
                // its own /report command handling), so this goes through the same public
                // command-dispatch path StaffGuiService already uses for the equivalent action.
                targetPlayer.performCommand("report " + targetName);
                return;
            }
            if ("OPEN_STAFF_GUI".equalsIgnoreCase(action)) {
                String maybeUuid = readOptionalUtf(in);
                staffGuiCommand.openPlayers(playerFromMaybeUuid(maybeUuid, player));
                return;
            }
            if ("OPEN_REPORT_DETAIL".equalsIgnoreCase(action)) {
                String maybeUuid = readUtf(in);
                Player targetPlayer = playerFromMaybeUuid(maybeUuid, player);
                staffGuiCommand.openCoreReportDetailById(targetPlayer, in.readLong());
                return;
            }
            if ("REPORT_LIST".equalsIgnoreCase(action)) {
                String first = readUtf(in);
                Player targetPlayer = playerFromMaybeUuid(first, player);
                String filter = isUuid(first) ? in.readUTF() : first;
                int count = in.readInt();
                List<StaffGuiCommand.CoreReportEntry> reports = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    reports.add(readCoreReportEntry(in));
                }
                staffGuiCommand.openCoreReports(targetPlayer, filter, reports);
                return;
            }
            if ("REPORT_DETAIL".equalsIgnoreCase(action)) {
                String maybeUuid = readUtf(in);
                Player targetPlayer = playerFromMaybeUuid(maybeUuid, player);
                boolean found = in.readBoolean();
                if (!found) {
                    staffGuiCommand.openMissingCoreReport(targetPlayer);
                    return;
                }
                staffGuiCommand.openCoreReportDetail(targetPlayer, readCoreReportEntry(in));
                return;
            }
            if ("REPORT_STATS".equalsIgnoreCase(action)) {
                String maybeUuid = readOptionalUtf(in);
                Player targetPlayer = playerFromMaybeUuid(maybeUuid, player);
                staffGuiCommand.updateCoreReportStats(targetPlayer, in.readInt(), in.readInt());
                return;
            }
            if ("OPEN_REASONS_GUI".equalsIgnoreCase(action)) {
                String first = readUtf(in);
                Player targetPlayer = playerFromMaybeUuid(first, player);
                String rawType = isUuid(first) ? readUtf(in) : first;
                PunishmentManager.Type type = parseType(rawType);
                String lang = readOptionalUtf(in);
                // NOTE (adapted from factions-repair): the original called
                // reasonsCommand.openReasonsGui(targetPlayer, type, lang) - an inventory GUI.
                // The current codebase's ReasonsCommand has no such method; it instead delegates
                // straight to PunishmentManager.sendReasonsBanner(sender, type, lang) (a chat
                // banner, not a GUI), and that PunishmentManager is only constructed at all when
                // Factions handles its own moderation locally (!externalModerationActive()) -
                // normally VetroCore does, so it's null by default. This calls the real current
                // mechanism when available and no-ops otherwise, rather than inventing a GUI
                // method on a class outside this integration's scope.
                if (plugin.getPunishmentManager() != null) {
                    plugin.getPunishmentManager().sendReasonsBanner(targetPlayer, type, lang == null || lang.isBlank() ? "de" : lang);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[CoreBridge] Plugin-Message konnte nicht verarbeitet werden: " + t.getMessage());
        }
    }

    private PunishmentManager.Type parseType(String raw) {
        try {
            return PunishmentManager.Type.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return PunishmentManager.Type.REPORT;
        }
    }

    private StaffGuiCommand.CoreReportEntry readCoreReportEntry(ByteArrayDataInput in) {
        return new StaffGuiCommand.CoreReportEntry(
                in.readLong(),
                in.readUTF(),
                in.readUTF(),
                in.readUTF(),
                in.readUTF(),
                in.readUTF(),
                in.readLong(),
                in.readUTF(),
                in.readLong()
        );
    }

    private Player playerFromMaybeUuid(String raw, Player fallback) {
        if (!isUuid(raw)) return fallback;
        Player found = plugin.getServer().getPlayer(UUID.fromString(raw));
        return found == null ? fallback : found;
    }

    private boolean isUuid(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            UUID.fromString(raw);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String readUtf(ByteArrayDataInput in) {
        return in.readUTF();
    }

    private String readOptionalUtf(ByteArrayDataInput in) {
        try {
            return in.readUTF();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
