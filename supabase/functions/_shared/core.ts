import { createClient } from "npm:@supabase/supabase-js@2";

export const service = createClient(
  Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
export const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8" },
});
export function requireSecret(request: Request, header: string, env: string): boolean {
  const secret = Deno.env.get(env);
  return Boolean(secret && request.headers.get(header) === secret);
}
export async function telegram(method: string, body: Record<string, unknown>) {
  const token = Deno.env.get("TELEGRAM_BOT_TOKEN");
  if (!token) throw Error("Bot token is missing");
  const response = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
  });
  const value = await response.json();
  if (!response.ok || !value.ok) throw Error(`Telegram ${method}: ${value.description ?? response.status}`);
  return value.result;
}

export type Task = {
  title: string; note: string; firstAt: number; nextAt: number;
  unit: "NONE" | "DAYS" | "WEEKS" | "MONTHS" | "YEARS" | "AFTER_DONE_DAYS";
  every: number; weekdays: number; monthDay: number; leadMinutes: number;
  untilAt: number | null; zone: string; done: boolean; lastCompletedAt: number;
};
export const isUnit = (x: string): x is Task["unit"] =>
  ["NONE", "DAYS", "WEEKS", "MONTHS", "YEARS", "AFTER_DONE_DAYS"].includes(x);

export async function parseTask(text: string, zone: string): Promise<Task> {
  const key = Deno.env.get("OPENROUTER_API_KEY");
  if (!key) throw Error("کلید هوش مصنوعی سرور تنظیم نشده است");
  const now = new Date();
  const localNow = new Intl.DateTimeFormat("fa-IR-u-ca-persian", {
    timeZone: zone, dateStyle: "full", timeStyle: "short",
  }).format(now);
  const prompt = `Interpret the user's Persian reminder request. Return ONLY a JSON object.
Now UTC: ${now.toISOString()}; user's local Persian date/time: ${localNow}; timezone: ${zone}. Dates are Jalali unless explicitly Gregorian.
Fields: title, note, first_at (ISO timestamp WITH timezone offset), unit (NONE/DAYS/WEEKS/MONTHS/YEARS/AFTER_DONE_DAYS), every (positive integer), weekdays (ISO weekday array), month_day (Jalali day or 0), lead_minutes, assumption (Persian sentence).
Examples: 20th of every month => MONTHS month_day=20; every 20 days => DAYS every=20; every 3 months on the 5th => MONTHS every=3 month_day=5, first_at is next actual 5th; start 10 days later => first_at is 10 days later. Return first actual occurrence.
If day without time: 10:00; afternoon 16:00; evening 18:00. If no date: today if still future, else tomorrow. Preserve location conditions only in note.
User: ${text.slice(0, 2000)}`;
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST", headers: { authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify({ model: Deno.env.get("TEXT_MODEL") || "openai/gpt-4o-mini", temperature: 0,
      messages: [{ role: "user", content: prompt }] }),
  });
  const body = await response.json();
  if (!response.ok) throw Error(`OpenRouter: ${body.error?.message ?? response.status}`);
  const raw = body.choices?.[0]?.message?.content;
  if (typeof raw !== "string") throw Error("پاسخ مدل قابل خواندن نیست");
  const start = raw.indexOf("{"); const end = raw.lastIndexOf("}");
  if (start < 0 || end <= start) throw Error("مدل زمان یادآوری را درست تشخیص نداد");
  const value = JSON.parse(raw.slice(start, end + 1));
  const due = Date.parse(value.first_at);
  if (!Number.isFinite(due) || due < Date.now() - 60_000) throw Error("زمان برداشت‌شده گذشته است؛ زمان را روشن‌تر بگو");
  const unit = isUnit(value.unit) ? value.unit : "NONE";
  const days = Array.isArray(value.weekdays) ? value.weekdays : [];
  const weekdays = days.reduce((mask: number, day: number) =>
    Number.isInteger(day) && day >= 1 && day <= 7 ? mask | (1 << (day - 1)) : mask, 0);
  const note = [value.note, value.assumption ? `برداشت زمان: ${value.assumption}` : ""].filter(Boolean).join("\n");
  const every = Math.max(1, Math.min(3650, Number(value.every) || 1));
  return { title: String(value.title || "یادآوری").slice(0, 180), note: note.slice(0, 2000), firstAt: due,
    nextAt: due, unit, every, weekdays, monthDay: Math.max(0, Math.min(31, Number(value.month_day) || 0)),
    leadMinutes: Math.max(0, Math.min(525600, Number(value.lead_minutes) || 0)), untilAt: null,
    zone, done: false, lastCompletedAt: 0 };
}

export async function transcribe(fileId: string): Promise<string> {
  const file = await telegram("getFile", { file_id: fileId });
  if (file.file_size > 20_000_000) throw Error("ویس بیش از حد بزرگ است");
  const token = Deno.env.get("TELEGRAM_BOT_TOKEN")!;
  const download = await fetch(`https://api.telegram.org/file/bot${token}/${file.file_path}`);
  if (!download.ok) throw Error("دریافت ویس از تلگرام انجام نشد");
  const audio = new Uint8Array(await download.arrayBuffer());
  if (audio.length > 20_000_000) throw Error("ویس بیش از حد بزرگ است");
  let binary = "";
  for (let i = 0; i < audio.length; i += 8192) binary += String.fromCharCode(...audio.slice(i, i + 8192));
  const response = await fetch("https://openrouter.ai/api/v1/audio/transcriptions", {
    method: "POST", headers: { authorization: `Bearer ${Deno.env.get("OPENROUTER_API_KEY")}`, "content-type": "application/json" },
    body: JSON.stringify({ model: Deno.env.get("AUDIO_MODEL") || "openai/whisper-large-v3",
      input_audio: { data: btoa(binary), format: "ogg" } }),
  });
  const value = await response.json();
  if (!response.ok) throw Error(`OpenRouter STT: ${value.error?.message ?? response.status}`);
  if (!value.text?.trim()) throw Error("صدای قابل تبدیل به متن پیدا نشد");
  return value.text.trim();
}

export async function message(chatId: number, text: string, buttons?: unknown) {
  return telegram("sendMessage", { chat_id: chatId, text, ...(buttons ? { reply_markup: buttons } : {}) });
}
