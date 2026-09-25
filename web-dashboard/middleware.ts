import { NextRequest, NextResponse } from "next/server";

const rank = {
  public: 0,
  player: 1,
  staff: 2,
  admin: 3
} as const;

type Role = keyof typeof rank;

function roleOf(request: NextRequest): Role {
  const raw = request.cookies.get("vf_role")?.value ?? "public";
  return raw === "admin" || raw === "staff" || raw === "player" ? raw : "public";
}

function requireRole(request: NextRequest, required: Role) {
  const role = roleOf(request);
  if (rank[role] >= rank[required]) return NextResponse.next();
  const url = request.nextUrl.clone();
  url.pathname = "/";
  url.searchParams.set("locked", required);
  return NextResponse.redirect(url);
}

export function middleware(request: NextRequest) {
  if (request.nextUrl.pathname.startsWith("/admin")) {
    return requireRole(request, "admin");
  }
  if (request.nextUrl.pathname.startsWith("/auction/seller")) {
    return requireRole(request, "staff");
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/admin/:path*", "/auction/seller/:path*"]
};
