-- User-owned reminder data. The bot and scheduler use the server key; Android uses RLS.
create table if not exists public.reminders (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  data jsonb not null,
  next_at bigint not null default 0,
  deleted boolean not null default false,
  last_sent_at bigint not null default 0,
  updated_at timestamptz not null default now()
);
create index if not exists yadar_due on public.reminders(next_at) where next_at > 0 and deleted = false;
create index if not exists yadar_owner on public.reminders(user_id, updated_at);
create or replace function public.yadar_touch() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
create trigger yadar_touch before insert or update on public.reminders
  for each row execute function public.yadar_touch();
alter table public.reminders enable row level security;
create policy "owner read" on public.reminders for select to authenticated using (user_id = (select auth.uid()));
create policy "owner insert" on public.reminders for insert to authenticated with check (user_id = (select auth.uid()));
create policy "owner update" on public.reminders for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create table if not exists public.telegram_links (
  user_id uuid primary key references auth.users(id) on delete cascade,
  chat_id bigint unique not null,
  zone text not null default 'Asia/Tehran',
  updated_at timestamptz not null default now()
);
alter table public.telegram_links enable row level security;

create table if not exists public.link_codes (
  code text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  zone text not null default 'Asia/Tehran',
  expires_at timestamptz not null,
  claimed boolean not null default false
);
create index if not exists yadar_link_expiry on public.link_codes(expires_at);
alter table public.link_codes enable row level security;

-- Atomically claim a code. The function is callable only with the server key.
create or replace function public.yadar_claim_link(p_code text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_user uuid;
begin
  update public.link_codes set claimed = true
    where code = p_code and claimed = false and expires_at > now()
    returning user_id into v_user;
  return v_user;
end $$;
revoke all on function public.yadar_claim_link(text) from public, anon, authenticated;
grant execute on function public.yadar_claim_link(text) to service_role;

create table if not exists public.telegram_pending (
  chat_id bigint primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  reminder_id uuid not null,
  created_at timestamptz not null default now()
);
alter table public.telegram_pending enable row level security;

create or replace function public.yadar_claim_due(p_id uuid, p_due bigint)
returns boolean language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  update public.reminders set last_sent_at = p_due
    where id = p_id and next_at = p_due and deleted = false and last_sent_at is distinct from p_due
    returning id into v_id;
  return v_id is not null;
end $$;
revoke all on function public.yadar_claim_due(uuid,bigint) from public, anon, authenticated;
grant execute on function public.yadar_claim_due(uuid,bigint) to service_role;

create or replace function public.yadar_due_candidates(p_now bigint, p_limit integer)
returns setof public.reminders language sql security definer set search_path = public as $$
  select * from public.reminders where deleted = false and next_at > 0 and next_at <= p_now
    and last_sent_at is distinct from next_at order by next_at limit least(p_limit, 100);
$$;
revoke all on function public.yadar_due_candidates(bigint,integer) from public, anon, authenticated;
grant execute on function public.yadar_due_candidates(bigint,integer) to service_role;
