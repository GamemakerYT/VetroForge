import { PublicFactionView } from "@/components/PublicFactionView";

export default async function FactionPage({ params }: { params: Promise<{ name: string }> }) {
  const { name } = await params;
  return <PublicFactionView name={decodeURIComponent(name)} />;
}
