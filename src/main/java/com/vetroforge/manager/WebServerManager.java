/*package com.vetroforge.manager;

import com.google.gson.Gson;
import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.FactionShopManager.ShopItem;
import spark.Filter;
import spark.Request;
import spark.Response;

import java.util.Map;

import static spark.Spark.*;

public class WebServerManager {
    private final FactionsPlugin plugin;
    private final Gson gson = new Gson();

    public WebServerManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Startet den Spark-Webserver und registriert alle Endpoints.
     * Autorisierung erfolgt über statischen Bearer-Token aus config.yml.
     */
    /*public void start() {
        var cfg = plugin.getConfig().getConfigurationSection("web");
        if (cfg == null || !cfg.getBoolean("enabled", false)) return;

        int port = cfg.getInt("port", 8080);
        String token = cfg.getString("authToken", "");

        port(port);
        staticFiles.location("/web");        // src/main/resources/web
        staticFiles.expireTime(600L);        // Cache für 10 Minuten

        // Auth-Filter für alle /api/*-Routen
        before("/api/*", (req, res) -> {
            String auth = req.headers("Authorization");
            if (!("Bearer " + token).equals(auth)) {
                halt(401, "Unauthorized");
            }
        });

        // Health-Check
        get("/api/health", (req, res) -> Map.of(
                "status", "OK",
                "time", System.currentTimeMillis()
        ), gson::toJson);

        // Server-Status (TPS, players, memory)
        get("/api/server/status", this::handleServerStatus, gson::toJson);

        // Fraktions-Shop: list all items
        get("/api/factions/shop/items", (req, res) ->
                plugin.getFactionShopManager().listItems(), gson::toJson);

        // Wiki-Index (list of page IDs and titles)
        get("/api/wiki/pages", (req, res) ->
                        plugin.getWikiManager().pages().entrySet().stream()
                                .map(e -> Map.of("id", e.getKey(), "title", e.getValue().title()))
                                .toList(),
                gson::toJson
        );

        // Wiki-Page detail
        get("/api/wiki/page/:id", (req, res) -> {
            String id = req.params("id");
            var page = plugin.getWikiManager().pages().get(id);
            if (page == null) halt(404, "Page not found");
            return Map.of("id", id, "title", page.title(), "content", page.content());
        }, gson::toJson);

        // Ingame item wiki (HTML template rendering)
        get("/wiki/items", (req, res) -> {
            res.type("text/html");
            return new spark.template.handlebars.HandlebarsTemplateEngine()
                    .render(new spark.ModelAndView(
                            Map.of("items", plugin.getFactionShopManager().listItems()),
                            "items"  // src/main/resources/web/items.html
                    ));
        });

        // User profile lookup
        get("/api/player/:name", (req, res) -> {
            String name = req.params("name");
            var player = plugin.getServer().getPlayerExact(name);
            if (player == null) halt(404, "Player not online");
            return Map.of(
                    "name", player.getName(),
                    "uuid", player.getUniqueId().toString(),
                    "faction", plugin.getFactionManager().getFaction(player).name(),
                    "balance", plugin.getFactionCoinManager().getPlugin().getEconomyManager().getBalance(player)
            );
        }, gson::toJson);

        // News CRUD
        get("/api/news", (req, res) ->
                plugin.getNewsManager().listNews(), gson::toJson);

        post("/api/news", (req, res) -> {
            var data = gson.fromJson(req.body(), Map.class);
            plugin.getNewsManager().createNews(data);
            res.status(201);
            return Map.of("status", "created");
        }, gson::toJson);

        put("/api/news/:id", (req, res) -> {
            String id = req.params("id");
            var data = gson.fromJson(req.body(), Map.class);
            plugin.getNewsManager().updateNews(id, data);
            return Map.of("status", "updated");
        }, gson::toJson);

        delete("/api/news/:id", (req, res) -> {
            String id = req.params("id");
            plugin.getNewsManager().deleteNews(id);
            return Map.of("status", "deleted");
        }, gson::toJson);

        // Enable CORS for all origins on simple requests (optional)
        options("/*", (req, res) -> {
            String accessControlRequestHeaders = req.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                res.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }
            String accessControlRequestMethod = req.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                res.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });
        before((Filter) (req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
        });
    }

    private Map<String, Object> handleServerStatus(Request req, Response res) {
        var stats = plugin.getStatsManager();
        return Map.of(
                "tps", stats.getTps(),
                "players", stats.getPlayerCount(),
                "memoryMB", stats.getMemoryUsage(),
                "history", stats.getHistory()
        );
    }
/*
}
     */