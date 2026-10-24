/* Xcluice: "Add link" on posts + clickable links in chat.
   Loaded AFTER the main script in index.html (just before </body>). */
(function () {
  const isLinkUrl = (u) => !!u && /^https?:\/\//i.test(u) && !u.startsWith(SB_URL + '/storage/');
  const host = (u) => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch (e) { return u; } };

  function normalizeLink(raw) {
    let s = (raw || '').trim();
    if (!s) return null;
    if (!/^https?:\/\//i.test(s)) s = 'https://' + s;
    try {
      const u = new URL(s);
      if (!/^https?:$/.test(u.protocol) || !u.hostname.includes('.')) return null;
      return u.href;
    } catch (e) { return null; }
  }

  // Escapes the text first, then turns URLs into safe, clickable links.
  function linkify(text) {
    return esc(text || '').replace(/(https?:\/\/[^\s<>"']+|www\.[^\s<>"']+)/gi, (m) => {
      const trail = (m.match(/[.,!?;:)\]]+$/) || [''])[0];
      const url = trail ? m.slice(0, -trail.length) : m;
      const href = /^www\./i.test(url) ? 'https://' + url : url;
      return '<a href="' + href + '" target="_blank" rel="noopener noreferrer" class="underline text-cyan-300 break-all">' + url + '</a>' + trail;
    });
  }
  window.linkify = linkify;

  /* ── Link posts: file type, badge, card thumbnail ── */
  const _getFileType = getFileType;
  window.getFileType = (u) => (isLinkUrl(u) ? 'link' : _getFileType(u));
  badgeColors.link = 'from-cyan-600/20 to-cyan-500/10 text-cyan-300 border-cyan-500/25';
  badgeLabel.link = 'Link';

  const _cardHTML = cardHTML;
  window.cardHTML = (p) => {
    let h = _cardHTML(p);
    if (isLinkUrl(p.file_url)) {
      const thumb = '<div class="w-full h-full flex flex-col items-center justify-center gap-2 px-4" style="background:linear-gradient(135deg,rgba(34,211,238,0.12),rgba(14,8,28,0.85))">' +
        '<svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="rgba(103,232,249,0.8)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>' +
        '<span class="text-xs text-cyan-300/80 font-semibold max-w-full truncate">' + esc(host(p.file_url)) + '</span></div>';
      h = h.replace(/(<div class="w-full aspect-video[^>]*>)[\s\S]*?(<span class="absolute bottom-2)/, (m, a, b) => a + thumb + b);
    }
    return h;
  };

  // Tapping a link post opens the link straight away.
  const _openViewer = openViewer;
  window.openViewer = (id) => {
    const p = allPosts.find((x) => String(x.id) === String(id));
    if (p && isLinkUrl(p.file_url)) { window.open(p.file_url, '_blank', 'noopener,noreferrer'); return; }
    _openViewer(id);
    if (p && !p.file_url) {
      const box = document.querySelector('#viewerContent .whitespace-pre-wrap');
      if (box) box.innerHTML = linkify(p.subject || '');
    }
  };

  // Three-dot menu: "Open link" instead of "Download" for link posts.
  const _openPostMenu = openPostMenu;
  window.openPostMenu = (e, id) => {
    _openPostMenu(e, id);
    const p = allPosts.find((x) => String(x.id) === String(id));
    if (p && isLinkUrl(p.file_url)) {
      const a = document.querySelector('#postMenu a[download]');
      if (a) {
        a.removeAttribute('download');
        a.rel = 'noopener noreferrer';
        a.innerHTML = '<i data-lucide="external-link" class="w-4 h-4"></i> Open link';
        refreshIcons();
      }
    }
  };

  /* ── Clickable links in chat messages ── */
  const _renderChatMessages = renderChatMessages;
  window.renderChatMessages = function () {
    _renderChatMessages();
    ['chatMessages', 'chatMessagesMobile'].forEach((id) => {
      const box = document.getElementById(id);
      if (!box) return;
      box.querySelectorAll('p.leading-relaxed').forEach((p) => { p.innerHTML = linkify(p.textContent); });
      // tag each bubble with its message id (same order/filter as the original render)
      const vis = chatMessages.filter((m) => {
        if (!currentThread) return !m.to_uid;
        return (m.uid === chatUserId && m.to_uid === currentThread.uid) || (m.uid === currentThread.uid && m.to_uid === chatUserId);
      });
      box.querySelectorAll('.chat-bubble').forEach((b, i) => { if (vis[i]) b.dataset.mid = vis[i].id; });
      box.scrollTop = box.scrollHeight;
    });
  };

  /* ── "Add link" button next to "Attach File" in the New Post modal ── */
  const label = Array.from(document.querySelectorAll('#uploadOverlay label')).find((l) => /Attach File/i.test(l.textContent));
  const dz = document.getElementById('dropzone');
  if (label && dz) {
    label.classList.remove('mb-2');
    const row = document.createElement('div');
    row.className = 'flex items-center justify-between mb-2';
    label.parentNode.insertBefore(row, label);
    row.appendChild(label);
    row.insertAdjacentHTML('beforeend',
      '<button type="button" id="addLinkBtn" class="flex items-center gap-1.5 text-xs font-semibold text-violet-300 hover:text-violet-100 rounded-lg px-2.5 py-1.5 hover:bg-violet-900/30 transition-colors" style="border:1px solid rgba(139,92,246,0.25)">' +
      '<i data-lucide="link" class="w-3.5 h-3.5"></i><span>Add link</span></button>');
    const input = document.createElement('input');
    input.id = 'inputLink';
    input.type = 'text';
    input.setAttribute('inputmode', 'url');
    input.autocomplete = 'off';
    input.placeholder = 'Paste a link (https://…)';
    input.className = 'hidden w-full mt-3 px-3.5 py-2.5 rounded-xl text-sm x-input';
    dz.insertAdjacentElement('afterend', input);
    document.getElementById('addLinkBtn').addEventListener('click', () => {
      const show = input.classList.contains('hidden');
      input.classList.toggle('hidden', !show);
      if (show) input.focus(); else input.value = '';
      document.querySelector('#addLinkBtn span').textContent = show ? 'Remove link' : 'Add link';
    });
    refreshIcons();
  }

  const _resetUploadForm = resetUploadForm;
  window.resetUploadForm = function () {
    _resetUploadForm();
    const i = document.getElementById('inputLink');
    if (i) { i.value = ''; i.classList.add('hidden'); }
    const s = document.querySelector('#addLinkBtn span');
    if (s) s.textContent = 'Add link';
  };

  window.publishPost = async function () {
    const title = el('inputTitle').value.trim(), desc = el('inputDesc').value.trim();
    const rawLink = (el('inputLink')?.value || '').trim();
    const link = rawLink ? normalizeLink(rawLink) : null;
    if (rawLink && !link) { toast('That link doesn’t look right', 'err'); return; }
    if (link && selectedFile) { toast('Add a file or a link, not both', 'err'); return; }
    if (!title && !desc && !selectedFile && !link) return;
    const btn = el('publishBtn'); btn.disabled = true; btn.textContent = 'Publishing…';
    try {
      let fileUrl = link;
      if (selectedFile) {
        el('dzProgress').classList.remove('hidden');
        const safeName = `${Date.now()}_${selectedFile.name.replace(/[^a-zA-Z0-9.]/g, '')}`;
        const r = await fetch(`${SB_URL}/storage/v1/object/${BUCKET}/${safeName}`, {
          method: 'POST', headers: authHeaders({ 'Content-Type': selectedFile.type }), body: selectedFile
        });
        if (!r.ok) throw await sbError(r, 'Upload failed');
        fileUrl = `${SB_URL}/storage/v1/object/public/${BUCKET}/${safeName}`;
      }
      const fallbackTitle = link ? host(link) : '';
      const ins = await fetch(`${SB_URL}/rest/v1/${TABLE}`, {
        method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          title: title || selectedFile?.name || fallbackTitle || 'Post',
          subject: desc || title || fallbackTitle,
          file_url: fileUrl || '',
          author_name: currentUser.display_name, author_email: currentUser.email, author_photo: currentUser.avatar_url
        })
      });
      if (!ins.ok) throw await sbError(ins, 'Save failed');
      toast('Published successfully', 'ok'); closeUpload(); loadPosts();
    } catch (e) { console.log(e); toast('Publish failed: ' + (e?.message || 'unknown error'), 'err'); }
    finally { btn.disabled = false; btn.textContent = 'Publish'; }
  };

  /* ── Long-press (or right-click) a message: Copy / Delete ── */
  const msgCss = document.createElement('style');
  msgCss.textContent = '#chatMessages .chat-bubble,#chatMessagesMobile .chat-bubble,#chatMessages .chat-bubble *,#chatMessagesMobile .chat-bubble *{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}';
  document.head.appendChild(msgCss);

  let msgMenu = null, pressTimer = null, pressStart = null, suppressClick = false;
  const closeMsgMenu = () => { if (msgMenu) { msgMenu.remove(); msgMenu = null; } };

  async function copyText(t) {
    try { await navigator.clipboard.writeText(t); }
    catch (e) {
      const ta = document.createElement('textarea');
      ta.value = t; ta.style.cssText = 'position:fixed;opacity:0';
      document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch (e2) {}
      ta.remove();
    }
    toast('Copied', 'ok');
  }

  async function deleteMessage(m) {
    if (!confirm('Delete this message?')) return;
    try {
      const r = await fetch(`${SB_URL}/rest/v1/chat_messages?id=eq.${m.id}`, {
        method: 'DELETE', headers: authHeaders({ Prefer: 'return=representation' })
      });
      const rows = r.ok ? await r.json() : [];
      if (!rows.length) throw new Error('not deleted');
      const i = chatMessages.findIndex((v) => String(v.id) === String(m.id));
      if (i > -1) chatMessages.splice(i, 1);
      renderChatMessages();
      toast('Message deleted', 'ok');
    } catch (e) { toast('Could not delete message', 'err'); }
  }

  function showMsgMenu(bubble, x, y) {
    const m = chatMessages.find((v) => String(v.id) === String(bubble.dataset.mid));
    if (!m) return;
    closeMsgMenu();
    const isAudio = (m.text || '').startsWith('[AUDIO::');
    const mine = m.uid === chatUserId && !String(m.id).startsWith('temp');
    msgMenu = document.createElement('div');
    msgMenu.className = 'x-dropdown rounded-xl overflow-hidden';
    msgMenu.style.cssText = 'position:fixed;z-index:10000;min-width:160px;left:0;top:0;visibility:hidden';
    msgMenu.innerHTML =
      '<button data-act="copy" class="flex items-center gap-2.5 w-full px-4 py-3 text-sm font-medium text-violet-300 hover:bg-violet-900/30 transition-colors"><i data-lucide="copy" class="w-4 h-4"></i> Copy</button>' +
      (mine ? '<button data-act="delete" class="flex items-center gap-2.5 w-full px-4 py-3 text-sm font-medium text-red-400 hover:bg-red-900/20 transition-colors"><i data-lucide="trash-2" class="w-4 h-4"></i> Delete</button>' : '');
    document.body.appendChild(msgMenu);
    refreshIcons();
    const r = msgMenu.getBoundingClientRect();
    const left = Math.max(8, Math.min(x - r.width / 2, window.innerWidth - r.width - 8));
    let top = y - r.height - 12;
    if (top < 8) top = Math.min(y + 12, window.innerHeight - r.height - 8);
    msgMenu.style.left = left + 'px';
    msgMenu.style.top = top + 'px';
    msgMenu.style.visibility = 'visible';
    msgMenu.addEventListener('click', (ev) => {
      const act = ev.target.closest('button')?.dataset.act;
      if (!act) return;
      closeMsgMenu();
      if (act === 'copy') copyText(isAudio ? m.text.replace('[AUDIO::', '').replace(']', '') : m.text);
      if (act === 'delete') deleteMessage(m);
    });
  }

  ['chatMessages', 'chatMessagesMobile'].forEach((id) => {
    const box = document.getElementById(id);
    if (!box) return;
    box.addEventListener('pointerdown', (e) => {
      const b = e.target.closest('.chat-bubble');
      if (!b || e.button > 0) return;
      pressStart = { x: e.clientX, y: e.clientY };
      clearTimeout(pressTimer);
      pressTimer = setTimeout(() => {
        suppressClick = true;
        showMsgMenu(b, pressStart.x, pressStart.y);
        setTimeout(() => { suppressClick = false; }, 600);
      }, 500);
    });
    ['pointerup', 'pointercancel', 'pointerleave', 'scroll'].forEach((t) => box.addEventListener(t, () => clearTimeout(pressTimer), { passive: true }));
    box.addEventListener('pointermove', (e) => {
      if (pressStart && Math.hypot(e.clientX - pressStart.x, e.clientY - pressStart.y) > 10) clearTimeout(pressTimer);
    }, { passive: true });
    box.addEventListener('contextmenu', (e) => {
      const b = e.target.closest('.chat-bubble');
      if (!b) return;
      e.preventDefault();
      showMsgMenu(b, e.clientX, e.clientY);
    });
    box.addEventListener('click', (e) => { if (suppressClick) { e.preventDefault(); e.stopPropagation(); } }, true);
  });
  document.addEventListener('pointerdown', (e) => { if (msgMenu && !msgMenu.contains(e.target)) closeMsgMenu(); }, true);
  window.addEventListener('resize', closeMsgMenu);
})();
