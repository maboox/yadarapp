import { json, message, parseTask, requireSecret, service, telegram, transcribe, type Task } from "../_shared/core.ts";
import { addLocalDays } from "../_shared/recurrence.ts";

async function stableId(chat: number, id: number): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${chat}:${id}`)));
  digest[6] = (digest[6] & 15) | 64; digest[8] = (digest[8] & 63) | 128;
  const hex = Array.from(digest.slice(0, 16), (x) => x.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
const buttons = (id: string) => ({ inline_keyboard: [
  [{ text: "درسته ✅", callback_data: `ok:${id}` }, { text: "اصلاح ✏️", callback_data: `edit:${id}` }],
  [{ text: "حذف 🗑️", callback_data: `delete:${id}` }],
] });
const summary = (task: Task) => `ثبت شد: ${task.title}\n${new Date(task.nextAt).toLocaleString("fa-IR-u-ca-persian", { timeZone: task.zone, dateStyle: "medium", timeStyle: "short" })}\n${task.unit === "NONE" ? "یک‌بار" : `تکرار: ${task.unit} / ${task.every}`}`;

Deno.serve(async (request) => {
  if (!requireSecret(request, "x-telegram-bot-api-secret-token", "TELEGRAM_WEBHOOK_SECRET"))
    return json({ error: "Unauthorized" }, 401);
  if (request.method !== "POST") return json({ error: "POST required" }, 405);
  try {
    const update = await request.json();
    const callback = update.callback_query;
    const incoming = update.message;
    const chatId = Number(callback?.message?.chat?.id ?? incoming?.chat?.id);
    if (!Number.isSafeInteger(chatId) || chatId !== Number(callback?.from?.id ?? incoming?.from?.id))
      return json({ ok: true }); // Only a user's own private chat is supported.
    if (incoming?.text?.startsWith("/start ")) {
      const code = incoming.text.slice(7).trim().toUpperCase();
      const { data: entry } = await service.from("link_codes").select("zone").eq("code", code).maybeSingle();
      const { data: userId, error } = await service.rpc("yadar_claim_link", { p_code: code });
      if (error || !userId) { await message(chatId, "کد نامعتبر یا منقضی شده است. در تنظیمات یادار کد تازه بگیر."); return json({ ok: true }); }
      await service.from("telegram_links").delete().eq("chat_id", chatId);
      const { error: linkError } = await service.from("telegram_links").upsert({
        user_id: userId, chat_id: chatId, zone: entry?.zone ?? "Asia/Tehran", updated_at: new Date().toISOString(),
      });
      if (linkError) throw linkError;
      await message(chatId, "ربات به حساب یادار وصل شد. یک پیام یا ویس بفرست؛ یادآوری فوری ثبت می‌شود و اگر لازم بود می‌توانی اصلاحش کنی.");
      return json({ ok: true });
    }
    const { data: link } = await service.from("telegram_links").select("user_id,zone").eq("chat_id", chatId).maybeSingle();
    if (!link) { if (incoming) await message(chatId, "ابتدا از تنظیمات یادار به حساب وارد شو و کد اتصال ربات را بگیر."); return json({ ok: true }); }
    if (callback) {
      await telegram("answerCallbackQuery", { callback_query_id: callback.id });
      const [action, id] = String(callback.data || "").split(":");
      if (!/^[0-9a-f-]{36}$/.test(id ?? "")) return json({ ok: true });
      const { data: row } = await service.from("reminders").select("id,data,next_at").eq("id", id)
        .eq("user_id", link.user_id).eq("deleted", false).maybeSingle();
      if (!row) return json({ ok: true });
      if (action === "edit") {
        await service.from("telegram_pending").upsert({ chat_id: chatId, user_id: link.user_id, reminder_id: id,
          created_at: new Date().toISOString() });
        await message(chatId, `تسک «${row.data.title}» را با متن یا ویس درستش بگو؛ همان تسک را اصلاح می‌کنم.`);
      } else if (action === "delete") {
        await service.from("reminders").update({ deleted: true, next_at: 0 }).eq("id", id).eq("user_id", link.user_id);
        await message(chatId, "یادآوری حذف شد.");
      } else if (action === "done") {
        const task = row.data as Task;
        task.lastCompletedAt = Date.now();
        if (task.unit === "NONE") { task.done = true; task.nextAt = 0; }
        if (task.unit === "AFTER_DONE_DAYS") task.nextAt = addLocalDays(Date.now(), task.every, task.zone);
        await service.from("reminders").update({ data: task, next_at: task.nextAt }).eq("id", id).eq("user_id", link.user_id);
        await message(chatId, "انجام شد ✅");
      } else if (action === "ok") {
        await message(chatId, "باشه ✅");
      }
      return json({ ok: true });
    }
    if (!incoming) return json({ ok: true });
    if (incoming.text === "/start") {
      await message(chatId, "ربات آماده است؛ یادآوری را با متن یا ویس بفرست."); return json({ ok: true });
    }
    if (!incoming.text && !incoming.voice) {
      await message(chatId, "متن یا ویس معمولی بفرست."); return json({ ok: true });
    }
    const { data: pending } = await service.from("telegram_pending").select("reminder_id,created_at")
      .eq("chat_id", chatId).maybeSingle();
    const validPending = pending && Date.now() - Date.parse(pending.created_at) < 30 * 60_000;
    const { data: original } = validPending ? await service.from("reminders").select("data")
      .eq("id", pending.reminder_id).eq("user_id", link.user_id).maybeSingle() : { data: null };
    const text = incoming.voice ? await transcribe(incoming.voice.file_id) : String(incoming.text);
    const prompt = original ? `اصلاح یادآوری قبلی «${original.data.title}» با زمان و جزئیات «${JSON.stringify(original.data)}»: ${text}` : text;
    const task = await parseTask(prompt, link.zone);
    if (incoming.voice) task.note = [`ویس: ${text}`, task.note].filter(Boolean).join("\n").slice(0, 2000);
    const id = original ? pending.reminder_id : await stableId(chatId, incoming.message_id);
    const { error } = await service.from("reminders").upsert({
      id, user_id: link.user_id, data: task, next_at: task.nextAt, deleted: false,
    }, { onConflict: "id" });
    if (error) throw error;
    if (original) await service.from("telegram_pending").delete().eq("chat_id", chatId);
    await message(chatId, summary(task), buttons(id));
    return json({ ok: true });
  } catch (error) {
    console.error("telegram-webhook", error);
    return json({ error: "Processing failed; Telegram can retry" }, 500);
  }
});
