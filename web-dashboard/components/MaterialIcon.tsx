"use client";

import type { CSSProperties } from "react";
import { useMemo, useState } from "react";

const materialColors: Record<string, string> = {
  DIAMOND: "#69d7ff",
  DIAMOND_SWORD: "#69d7ff",
  DIAMOND_PICKAXE: "#69d7ff",
  EMERALD: "#35d07f",
  GOLD_INGOT: "#ffbe45",
  GOLDEN_APPLE: "#ffbe45",
  NETHERITE_SWORD: "#514a5c",
  NETHERITE_CHESTPLATE: "#514a5c",
  REDSTONE: "#ff304f",
  TNT: "#d51f38",
  DIRT: "#8b5a35",
  STONE: "#8a92a3",
  COBBLESTONE: "#747b89",
  OAK_LOG: "#9b6a3b",
  BREAD: "#d59a45",
  APPLE: "#ff304f",
  CACTUS: "#2e9f54",
  CHEST: "#b77935",
  NETHER_STAR: "#f5f7fb",
  END_CRYSTAL: "#d889ff",
  TRIPWIRE_HOOK: "#d8dde8"
};

function colorOf(material: string) {
  const upper = material.toUpperCase();
  if (materialColors[upper]) return materialColors[upper];
  if (upper.includes("DIAMOND")) return materialColors.DIAMOND;
  if (upper.includes("GOLD")) return materialColors.GOLD_INGOT;
  if (upper.includes("NETHERITE")) return materialColors.NETHERITE_SWORD;
  if (upper.includes("WOOD") || upper.includes("LOG")) return materialColors.OAK_LOG;
  if (upper.includes("STONE")) return materialColors.STONE;
  if (upper.includes("KEY")) return materialColors.TRIPWIRE_HOOK;
  return "#ff304f";
}

export function MaterialIcon({ material, size = "md" }: { material: string; size?: "sm" | "md" | "lg" }) {
  const color = colorOf(material || "CHEST");
  const [fallback, setFallback] = useState<"item" | "block" | "css">("item");
  const texture = useMemo(() => textureName(material || "CHEST"), [material]);
  const label = (material || "?")
    .split("_")
    .map((part) => part[0])
    .join("")
    .slice(0, 3);

  return (
    <span className={`material-icon ${size}`} style={{ "--mat": color } as CSSProperties} title={material}>
      {fallback !== "css" ? (
        <img
          alt={material}
          src={textureUrl(texture, fallback)}
          onError={() => setFallback(fallback === "item" ? "block" : "css")}
        />
      ) : (
        <span>{label}</span>
      )}
    </span>
  );
}

function textureName(material: string) {
  return material
    .toLowerCase()
    .replaceAll(" ", "_")
    .replace(/[^a-z0-9_]/g, "");
}

function textureUrl(texture: string, kind: "item" | "block") {
  return `https://assets.mcasset.cloud/1.21.4/assets/minecraft/textures/${kind}/${texture}.png`;
}
