-- Run this ONCE in Supabase Dashboard > SQL Editor, after backend deployment.
-- Replace PROJECT_REF and CRON_SECRET_VALUE with your own values.
-- Do not commit a completed copy with your secret to GitHub.
create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;
select vault.create_secret('https://PROJECT_REF.supabase.co', 'yadar_project_url');
select vault.create_secret('CRON_SECRET_VALUE', 'yadar_cron_secret');

select cron.schedule('yadar-send-due', '* * * * *', $$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'yadar_project_url') || '/functions/v1/send-due',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-yadar-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'yadar_cron_secret')),
    body := '{}'::jsonb
  );
$$);
