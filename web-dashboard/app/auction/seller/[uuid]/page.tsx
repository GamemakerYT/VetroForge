import { SellerProfileView } from "@/components/SellerProfileView";

export default async function SellerPage({ params }: { params: Promise<{ uuid: string }> }) {
  const { uuid } = await params;
  return <SellerProfileView uuid={decodeURIComponent(uuid)} />;
}
