import { StatusPageClient } from "@/components/StatusPageClient";

export default function StatusPage() {
  const apiBase = process.env.NEXT_PUBLIC_VETROFORGE_API
    ?? process.env.VF_FACTIONS_API_URL
    ?? "http://45.146.252.240:8500";

  return <StatusPageClient initialApiBase={apiBase} />;
}
