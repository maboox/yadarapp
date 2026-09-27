import { json, service } from "../_shared/core.ts";

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "POST required" }, 405);
  const jwt = request.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (!jwt) return json({ error: "Sign in first" }, 401);
  const { data: { user }, error } = await service.auth.getUser(jwt);
  if (error || !user) return json({ error: "Invalid session" }, 401);
  try {
    const input = await request.json();
    const zone = String(input.zone || "Asia/Tehran");
    new Intl.DateTimeFormat("en", { timeZone: zone });
    await service.from("link_codes").delete().eq("user_id", user.id);
    const code = crypto.randomUUID().replaceAll("-", "").slice(0, 20).toUpperCase();
    const { error: insertError } = await service.from("link_codes").insert({
      code, user_id: user.id, zone, expires_at: new Date(Date.now() + 10 * 60_000).toISOString(),
    });
    if (insertError) throw insertError;
    return json({ code, expires_in_seconds: 600 });
  } catch (error) {
    console.error("link-code", error);
    return json({ error: "Could not create a linking code" }, 500);
  }
});
