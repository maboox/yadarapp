import { json, message, requireSecret, service, type Task } from "../_shared/core.ts";
import { nextAfter } from "../_shared/recurrence.ts";

Deno.serve(async (request) => {
  if (!requireSecret(request, "x-yadar-cron-secret", "CRON_SECRET")) return json({ error: "Unauthorized" }, 401);
  if (request.method !== "POST") return json({ error: "POST required" }, 405);
  const now = Date.now();
  const { data: reminders, error } = await service.rpc("yadar_due_candidates", { p_now: now, p_limit: 100 });
  if (error) return json({ error: "Database unavailable" }, 500);
  let sent = 0; let failed = 0;
  for (const row of reminders ?? []) {
    const due = Number(row.next_at);
    const { data: claimed, error: claimError } = await service.rpc("yadar_claim_due", { p_id: row.id, p_due: due });
    if (claimError || !claimed) continue;
    let delivered = false;
    try {
      const { data: current } = await service.from("reminders").select("next_at,deleted")
        .eq("id", row.id).single();
      if (!current || current.deleted || Number(current.next_at) !== due) continue;
      const task = row.data as Task;
      const { data: link } = await service.from("telegram_links").select("chat_id").eq("user_id", row.user_id).maybeSingle();
      if (link) {
        await message(Number(link.chat_id), `یادار: ${task.title}${task.note ? `\n${task.note.slice(0, 200)}` : ""}`, {
          inline_keyboard: [[{ text: "انجام شد ✅", callback_data: `done:${row.id}` }]],
        });
        sent++;
      }
      delivered = true; // With no linked chat, still advance the remote schedule.
      const next = nextAfter(task, due);
      task.nextAt = next;
      const { error: updateError } = await service.from("reminders").update({ data: task, next_at: next })
        .eq("id", row.id).eq("next_at", due);
      if (updateError) throw updateError;
    } catch (e) {
      if (!delivered) await service.from("reminders").update({ last_sent_at: 0 })
        .eq("id", row.id).eq("next_at", due).eq("last_sent_at", due);
      // After Telegram accepts a message, keep the claim to avoid duplicate delivery.
      console.error("send-due", row.id, e);
      failed++;
    }
  }
  return json({ sent, failed });
});
