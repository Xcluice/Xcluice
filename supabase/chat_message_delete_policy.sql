-- Lets a user delete their own chat messages (delete for everyone). Run once in the Supabase SQL editor if deleting fails.
drop policy if exists "chat_messages delete own" on public.chat_messages;
create policy "chat_messages delete own" on public.chat_messages for delete to authenticated using (uid = auth.uid()::text);
