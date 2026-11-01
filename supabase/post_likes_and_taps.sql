-- Run once in Supabase SQL editor (project nzolsmouyulzqeaocejm). Adds heart likes + tap counts for posts.
alter table public.homework
  add column if not exists likes bigint not null default 0,
  add column if not exists taps bigint not null default 0;

create table if not exists public.post_likes (
  post_id bigint not null references public.homework(id) on delete cascade,
  uid text not null,
  created_at timestamptz not null default now(),
  primary key (post_id, uid)
);
alter table public.post_likes enable row level security;

create policy "post_likes read" on public.post_likes for select to anon, authenticated using (true);
create policy "post_likes insert own" on public.post_likes for insert to authenticated with check (uid = auth.uid()::text);
create policy "post_likes delete own" on public.post_likes for delete to authenticated using (uid = auth.uid()::text);

create or replace function public.sync_post_likes() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    update public.homework set likes = likes + 1 where id = new.post_id;
  elsif tg_op = 'DELETE' then
    update public.homework set likes = greatest(likes - 1, 0) where id = old.post_id;
  end if;
  return null;
end $$;

drop trigger if exists post_likes_sync on public.post_likes;
create trigger post_likes_sync after insert or delete on public.post_likes
  for each row execute function public.sync_post_likes();

create or replace function public.bump_post_tap(p_id bigint) returns bigint
language sql security definer set search_path = public as $$
  update public.homework set taps = taps + 1 where id = p_id returning taps;
$$;
revoke all on function public.bump_post_tap(bigint) from public;
grant execute on function public.bump_post_tap(bigint) to anon, authenticated;
