import type { Task } from "./core.ts";

const DAY = 86_400_000;
const gregorian = new Map<string, Intl.DateTimeFormat>();
const persian = new Map<string, Intl.DateTimeFormat>();
function formatter(zone: string, calendar: "gregory" | "persian") {
  const cache = calendar === "persian" ? persian : gregorian;
  if (!cache.has(zone)) cache.set(zone, new Intl.DateTimeFormat(`en-u-ca-${calendar}`, {
    timeZone: zone, year: "numeric", month: "numeric", day: "numeric",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }));
  return cache.get(zone)!;
}
function parts(ms: number, zone: string, calendar: "gregory" | "persian") {
  const fields = Object.fromEntries(formatter(zone, calendar).formatToParts(new Date(ms))
    .filter((x) => ["year", "month", "day", "hour", "minute"].includes(x.type))
    .map((x) => [x.type, Number(x.value)]));
  return fields as { year: number; month: number; day: number; hour: number; minute: number };
}
function utcDay(local: { year: number; month: number; day: number }) {
  return Date.UTC(local.year, local.month - 1, local.day);
}
// Convert wall-clock time to an instant, correcting the zone offset (including DST).
function atDay(day: number, hour: number, minute: number, zone: string) {
  const d = new Date(day);
  const intended = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), hour, minute);
  let instant = intended;
  for (let i = 0; i < 4; i++) {
    const actual = parts(instant, zone, "gregory");
    const wall = Date.UTC(actual.year, actual.month - 1, actual.day, actual.hour, actual.minute);
    const delta = intended - wall;
    instant += delta;
    if (delta === 0) break;
  }
  return instant;
}
export function addLocalDays(when: number, days: number, zone: string) {
  const local = parts(when, zone, "gregory");
  return atDay(utcDay(local) + days * DAY, local.hour, local.minute, zone);
}
function jalaliMonth(year: number, month: number, day: number, hour: number, minute: number, zone: string) {
  const offset = month <= 6 ? (month - 1) * 31 : 186 + (month - 7) * 30;
  const seed = Date.UTC(year + 621, 2, 16) + offset * DAY;
  let first: number | undefined;
  for (let i = 0; i < 14; i++) {
    const candidate = seed + i * DAY;
    const p = parts(atDay(candidate, 12, 0, zone), zone, "persian");
    if (p.year === year && p.month === month && p.day === 1) { first = candidate; break; }
  }
  if (first === undefined) throw Error("Persian calendar conversion failed");
  let length = 0;
  for (let i = 0; i < 32; i++) {
    const p = parts(atDay(first + i * DAY, 12, 0, zone), zone, "persian");
    if (p.year !== year || p.month !== month) break;
    length++;
  }
  return atDay(first + (Math.min(day, length) - 1) * DAY, hour, minute, zone);
}
export function nextAfter(task: Task, due: number, now = Date.now()): number {
  if (task.unit === "NONE" || task.unit === "AFTER_DONE_DAYS") return 0;
  const zone = task.zone;
  const first = parts(task.firstAt, zone, "gregory");
  const time = { hour: first.hour, minute: first.minute };
  const every = Math.max(1, Math.min(3650, task.every));
  const threshold = Math.max(due, now);
  let candidate = 0;
  if (task.unit === "DAYS") {
    const elapsed = Math.max(0, Math.floor((utcDay(parts(threshold, zone, "gregory")) - utcDay(first)) / DAY));
    for (let i = Math.floor(elapsed / every); i < Math.floor(elapsed / every) + 4; i++) {
      candidate = atDay(utcDay(first) + i * every * DAY, time.hour, time.minute, zone);
      if (candidate > threshold && candidate >= task.firstAt) break;
    }
  } else if (task.unit === "WEEKS") {
    const firstDay = utcDay(first);
    const monday = firstDay - ((new Date(firstDay).getUTCDay() + 6) % 7) * DAY;
    const current = utcDay(parts(threshold, zone, "gregory"));
    const weeks = Math.max(0, Math.floor((current - monday) / (7 * DAY)));
    const mask = task.weekdays || 1 << ((new Date(firstDay).getUTCDay() + 6) % 7);
    outer: for (let block = Math.max(0, Math.floor(weeks / every) - 1); block < Math.floor(weeks / every) + 4; block++) {
      for (let day = 0; day < 7; day++) {
        if (!(mask & (1 << day))) continue;
        candidate = atDay(monday + (block * every * 7 + day) * DAY, time.hour, time.minute, zone);
        if (candidate > threshold && candidate >= task.firstAt) break outer;
      }
    }
  } else {
    const base = parts(task.firstAt, zone, "persian");
    const today = parts(threshold, zone, "persian");
    const baseIndex = base.year * 12 + base.month - 1;
    const period = task.unit === "YEARS" ? every * 12 : every;
    const delta = Math.max(0, today.year * 12 + today.month - 1 - baseIndex);
    for (let index = Math.max(0, Math.floor(delta / period) - 1);
      index < Math.floor(delta / period) + 4; index++) {
      const monthIndex = baseIndex + index * period;
      candidate = jalaliMonth(Math.floor(monthIndex / 12), monthIndex % 12 + 1,
        task.monthDay || base.day, time.hour, time.minute, zone);
      if (candidate > threshold && candidate >= task.firstAt) break;
    }
  }
  return candidate > threshold && (!task.untilAt || candidate <= task.untilAt) ? candidate : 0;
}
