/* Xcluice: "Add link" on posts + clickable links in chat.
   Loaded AFTER the main script in index.html (just before </body>). */
(function () {
  let selectedThumb = null, existingThumb = null, editingPost = null;
  const isLinkUrl = (u) => !!u && /^https?:\/\//i.test(u) && !u.startsWith(SB_URL + '/storage/');
  const postLink = (p) => (p && (p.link_url || (isLinkUrl(p.file_url) ? p.file_url : null))) || null;
  const isShotPost = (p) => !!p.link_url && /\/link_\d+_/.test(p.file_url || '');
  const linkOnly = (p) => (p.link_url ? isShotPost(p) : isLinkUrl(p.file_url));
  const isImageName = (n) => /\.(png|jpe?g|gif|webp|svg)$/i.test(n || '');
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
  const THUMB_RE = /(<div class="w-full aspect-video[^>]*>)[\s\S]*?(<span class="absolute bottom-2)/;
  window.cardHTML = (p) => {
    let h = _cardHTML(p);
    if (p.thumb_url) {
      const img = '<img src="' + esc(p.thumb_url) + '" alt="" loading="lazy" class="w-full h-full object-cover">';
      h = h.replace(THUMB_RE, (m, a, b) => a + img + b);
    }
    if (p.link_url) {
      const chip = '<a href="' + safeUrl(p.link_url) + '" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()" class="absolute bottom-2 left-2 max-w-[60%] truncate text-[10px] font-semibold rounded-full px-2 py-0.5 text-cyan-200 border border-cyan-500/30" style="background:rgba(4,2,12,0.7);backdrop-filter:blur(8px)">🔗 ' + esc(host(p.link_url)) + '</a>';
      h = h.replace('<span class="absolute bottom-2 right-2', chip + '<span class="absolute bottom-2 right-2');
      if (isShotPost(p)) h = h.replace('>Preview</span>', '>Link</span>');
    } else if (isLinkUrl(p.file_url)) {
      const thumb = '<div class="w-full h-full flex flex-col items-center justify-center gap-2 px-4" style="background:linear-gradient(135deg,rgba(34,211,238,0.12),rgba(14,8,28,0.85))">' +
        '<svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="rgba(103,232,249,0.8)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>' +
        '<span class="text-xs text-cyan-300/80 font-semibold max-w-full truncate">' + esc(host(p.file_url)) + '</span></div>';
      h = h.replace(THUMB_RE, (m, a, b) => a + thumb + b);
    }
    return h;
  };

  // Link-only posts open the link straight away. File posts open normally,
  // with a link button in the header and their thumbnail on the download screen.
  const _openViewer = openViewer;
  window.openViewer = (id) => {
    const p = allPosts.find((x) => String(x.id) === String(id));
    document.getElementById('viewerLinkBtn')?.remove();
    if (p && linkOnly(p)) { window.open(postLink(p), '_blank', 'noopener,noreferrer'); return; }
    _openViewer(id);
    if (!p) return;
    if (!p.file_url) {
      const box = document.querySelector('#viewerContent .whitespace-pre-wrap');
      if (box) box.innerHTML = linkify(p.subject || '');
    } else if (p.thumb_url) {
      const dl = document.querySelector('#viewerContent .p-12');
      if (dl) dl.insertAdjacentHTML('afterbegin', '<img src="' + esc(p.thumb_url) + '" alt="" class="mx-auto mb-5 rounded-xl max-h-64 w-auto object-contain">');
    }
    if (p.link_url) {
      document.getElementById('viewerDate').insertAdjacentHTML('afterend',
        '<a id="viewerLinkBtn" href="' + esc(p.link_url) + '" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1 mt-1 text-xs font-semibold text-cyan-300 underline">🔗 Open link · ' + esc(host(p.link_url)) + '</a>');
    }
  };

  // Three-dot menu: "Open link" instead of "Download" for link posts.
  const _openPostMenu = openPostMenu;
  window.openPostMenu = (e, id) => {
    _openPostMenu(e, id);
    const p = allPosts.find((x) => String(x.id) === String(id));
    if (p && canEdit(p)) {
      document.getElementById('postMenu').insertAdjacentHTML('afterbegin',
        '<button class="flex items-center gap-2.5 w-full px-4 py-3 text-sm font-medium text-violet-300 hover:bg-violet-900/30 transition-colors" onclick="openEdit(\'' + p.id + '\')"><i data-lucide="pencil" class="w-4 h-4"></i> Edit</button>');
      refreshIcons();
    }
    if (p && linkOnly(p)) {
      const a = document.querySelector('#postMenu a[download]');
      if (a) {
        a.href = postLink(p);
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
    // Optional thumbnail picker, shown when the chosen file isn't an image
    const row2 = document.createElement('div');
    row2.id = 'thumbRow';
    row2.className = 'hidden mt-3 flex items-center gap-3 rounded-xl p-3';
    row2.style.cssText = 'border:1px solid rgba(139,92,246,0.25);background:rgba(139,92,246,0.04)';
    row2.innerHTML =
      '<div id="thumbPreview" class="w-14 h-14 rounded-lg flex items-center justify-center overflow-hidden shrink-0" style="background:rgba(139,92,246,0.12)"><i data-lucide="image" class="w-5 h-5 text-violet-400/60"></i></div>' +
      '<div class="flex-1 min-w-0"><p class="text-xs font-semibold text-violet-300/80">Thumbnail <span class="font-normal text-violet-400/40">(optional)</span></p>' +
      '<p id="thumbName" class="text-[11px] text-violet-400/40 truncate">Add a cover image for this file</p></div>' +
      '<button type="button" id="thumbBtn" class="text-xs font-semibold text-violet-300 rounded-lg px-2.5 py-1.5 hover:bg-violet-900/30" style="border:1px solid rgba(139,92,246,0.25)">Choose</button>' +
      '<input type="file" id="thumbInput" accept="image/png,image/jpeg,image/webp,image/gif" class="hidden">';
    input.insertAdjacentElement('afterend', row2);
    document.getElementById('thumbBtn').addEventListener('click', () => {
      if (selectedThumb || existingThumb) { existingThumb = null; setThumb(null); } else document.getElementById('thumbInput').click();
    });
    document.getElementById('thumbInput').addEventListener('change', (e) => {
      const f = e.target.files[0];
      e.target.value = '';
      if (!f) return;
      if (!/^image\/(png|jpe?g|webp|gif)$/.test(f.type)) { toast('Thumbnail must be an image', 'err'); return; }
      if (f.size > 5 * 1024 * 1024) { toast('Thumbnail max size is 5MB', 'err'); return; }
      existingThumb = null;
      setThumb(f);
    });
    refreshIcons();
  }

  function setThumb(f) {
    selectedThumb = f;
    const prev = document.getElementById('thumbPreview'), name = document.getElementById('thumbName'), btn = document.getElementById('thumbBtn');
    if (!prev) return;
    if (f) {
      prev.innerHTML = '';
      const im = document.createElement('img');
      im.src = URL.createObjectURL(f);
      im.className = 'w-full h-full object-cover';
      prev.appendChild(im);
      name.textContent = f.name; btn.textContent = 'Remove';
    } else {
      prev.innerHTML = '<i data-lucide="image" class="w-5 h-5 text-violet-400/60"></i>';
      name.textContent = 'Add a cover image for this file'; btn.textContent = 'Choose';
      refreshIcons();
    }
  }

  function updateThumbRow() {
    const row = document.getElementById('thumbRow');
    if (!row) return;
    const show = !!selectedFile && !isImageName(selectedFile.name);
    row.classList.toggle('hidden', !show);
    if (!show && selectedThumb) setThumb(null);
  }
  const _handleFile = handleFile;
  window.handleFile = function (file) { _handleFile(file); updateThumbRow(); };

  const _resetUploadForm = resetUploadForm;
  window.resetUploadForm = function () {
    _resetUploadForm();
    setThumb(null);
    exitEditMode();
    document.getElementById('thumbRow')?.classList.add('hidden');
    const i = document.getElementById('inputLink');
    if (i) { i.value = ''; i.classList.add('hidden'); }
    const s = document.querySelector('#addLinkBtn span');
    if (s) s.textContent = 'Add link';
  };

  // Asks the link-screenshot function to capture the site and store it in Supabase.
  async function captureScreenshot(link) {
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 30000);
      const r = await fetch(`${SB_URL}/functions/v1/link-screenshot`, {
        method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ url: link }), signal: ctrl.signal
      });
      clearTimeout(t);
      if (!r.ok) return null;
      const j = await r.json();
      return j.file_url || null;
    } catch (e) { return null; }
  }

  async function uploadToBucket(file, prefix) {
    const safeName = `${prefix}${Date.now()}_${file.name.replace(/[^a-zA-Z0-9.]/g, '')}`;
    const r = await fetch(`${SB_URL}/storage/v1/object/${BUCKET}/${safeName}`, {
      method: 'POST', headers: authHeaders({ 'Content-Type': file.type }), body: file
    });
    if (!r.ok) throw await sbError(r, 'Upload failed');
    return `${SB_URL}/storage/v1/object/public/${BUCKET}/${safeName}`;
  }

  window.publishPost = async function () {
    if (editingPost) return saveEdit();
    const title = el('inputTitle').value.trim(), desc = el('inputDesc').value.trim();
    const rawLink = (el('inputLink')?.value || '').trim();
    const link = rawLink ? normalizeLink(rawLink) : null;
    if (rawLink && !link) { toast('That link doesn’t look right', 'err'); return; }
    if (!title && !desc && !selectedFile && !link) return;
    const btn = el('publishBtn'); btn.disabled = true; btn.textContent = 'Publishing…';
    try {
      let fileUrl = null, thumbUrl = null;
      if (selectedFile) {
        el('dzProgress').classList.remove('hidden');
        fileUrl = await uploadToBucket(selectedFile, '');
        if (selectedThumb) thumbUrl = await uploadToBucket(selectedThumb, 'thumb_');
      }
      if (link && (!selectedFile || (!thumbUrl && !isImageName(selectedFile.name)))) {
        // Link alone: the screenshot is the post's picture. File + link, no thumbnail chosen: the screenshot becomes the thumbnail.
        btn.textContent = 'Capturing preview…';
        const shot = await captureScreenshot(link);
        if (shot) { if (selectedFile) thumbUrl = shot; else fileUrl = shot; }
        else {
          if (!selectedFile) fileUrl = link;
          toast('Couldn’t capture a preview of the link', 'err');
        }
      }
      const fallbackTitle = link ? host(link) : '';
      const ins = await fetch(`${SB_URL}/rest/v1/${TABLE}`, {
        method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          title: title || selectedFile?.name || fallbackTitle || 'Post',
          subject: desc || title || fallbackTitle,
          file_url: fileUrl || '',
          link_url: link,
          thumb_url: thumbUrl,
          author_name: currentUser.display_name, author_email: currentUser.email, author_photo: currentUser.avatar_url
        })
      });
      if (!ins.ok) throw await sbError(ins, 'Save failed');
      toast('Published successfully', 'ok'); closeUpload(); loadPosts();
    } catch (e) { console.log(e); toast('Publish failed: ' + (e?.message || 'unknown error'), 'err'); }
    finally { btn.disabled = false; btn.textContent = editingPost ? 'Save changes' : 'Publish'; }
  };

  /* ── Edit post (owner or admin) ── */
  const canEdit = (p) => !!currentUser && (currentUser.email === ADMIN_EMAIL || currentUser.role === 'Admin' || (p.author_email && currentUser.email === p.author_email));
  const oldLinkOf = (p) => p.link_url || (isLinkUrl(p.file_url) ? p.file_url : null);
  const hasRealFile = (p) => !!p.file_url && !isShotPost(p) && !isLinkUrl(p.file_url);

  function attachLabel() { const b = document.getElementById('addLinkBtn'); return b ? b.parentNode.firstElementChild : null; }

  function exitEditMode() {
    editingPost = null; existingThumb = null;
    document.getElementById('dropzone')?.classList.remove('hidden');
    const lab = attachLabel();
    if (lab && lab.dataset.orig) { lab.innerHTML = lab.dataset.orig; delete lab.dataset.orig; }
    document.getElementById('addLinkBtn')?.classList.remove('hidden');
    const h2 = document.querySelector('#uploadOverlay h2'); if (h2) h2.textContent = 'New Post';
    const pb = document.getElementById('publishBtn'); if (pb) pb.textContent = 'Publish';
  }

  function openEdit(id) {
    closePostMenu();
    const p = allPosts.find((x) => String(x.id) === String(id));
    if (!p || !canEdit(p)) return;
    resetUploadForm();
    editingPost = p;
    el('inputTitle').value = p.title || '';
    el('inputDesc').value = p.subject && p.subject !== p.title ? p.subject : '';
    const li = document.getElementById('inputLink');
    li.classList.remove('hidden');
    li.value = oldLinkOf(p) || '';
    document.getElementById('dropzone').classList.add('hidden');
    const lab = attachLabel();
    if (lab) { lab.dataset.orig = lab.innerHTML; lab.textContent = 'Link'; }
    document.getElementById('addLinkBtn').classList.add('hidden');
    if (hasRealFile(p) && !isImageName(p.file_url)) {
      document.getElementById('thumbRow').classList.remove('hidden');
      if (p.thumb_url) {
        existingThumb = p.thumb_url;
        const prev = document.getElementById('thumbPreview');
        prev.innerHTML = '';
        const im = document.createElement('img');
        im.src = p.thumb_url; im.className = 'w-full h-full object-cover';
        prev.appendChild(im);
        document.getElementById('thumbName').textContent = 'Current thumbnail';
        document.getElementById('thumbBtn').textContent = 'Remove';
      }
    }
    document.querySelector('#uploadOverlay h2').textContent = 'Edit Post';
    document.getElementById('publishBtn').textContent = 'Save changes';
    showOverlay('uploadOverlay');
  }
  window.openEdit = openEdit;

  async function saveEdit() {
    const p = editingPost;
    const title = el('inputTitle').value.trim(), desc = el('inputDesc').value.trim();
    const rawLink = (el('inputLink').value || '').trim();
    const link = rawLink ? normalizeLink(rawLink) : null;
    if (rawLink && !link) { toast('That link doesn’t look right', 'err'); return; }
    if (!title && !desc && !p.file_url && !link) { toast('Add a title or description', 'err'); return; }
    const btn = el('publishBtn'); btn.disabled = true; btn.textContent = 'Saving…';
    try {
      const patch = {
        title: title || p.title || 'Post',
        subject: desc || title || (hasRealFile(p) ? '' : (p.subject || ''))
      };
      const fileLike = hasRealFile(p), canThumb = fileLike && !isImageName(p.file_url);
      const linkChanged = (link || null) !== (oldLinkOf(p) || null);

      let thumbFinal = null;
      if (canThumb) {
        if (selectedThumb) thumbFinal = await uploadToBucket(selectedThumb, 'thumb_');
        else if (existingThumb && !(linkChanged && /\/link_\d+_/.test(existingThumb))) thumbFinal = existingThumb;
      }

      if (linkChanged) {
        if (link) {
          const needShot = !fileLike || (canThumb && !thumbFinal);
          let shot = null;
          if (needShot) { btn.textContent = 'Capturing preview…'; shot = await captureScreenshot(link); }
          patch.link_url = link;
          if (!fileLike) {
            if (shot) patch.file_url = shot;
            else { patch.file_url = link; patch.link_url = null; toast('Couldn’t capture a preview of the link', 'err'); }
          } else if (canThumb && !thumbFinal && shot) thumbFinal = shot;
        } else {
          patch.link_url = null;
          if (!fileLike && isLinkUrl(p.file_url)) patch.file_url = '';
        }
      }
      if (canThumb) patch.thumb_url = thumbFinal;

      const r = await fetch(`${SB_URL}/rest/v1/${TABLE}?id=eq.${p.id}`, {
        method: 'PATCH', headers: authHeaders({ 'Content-Type': 'application/json', Prefer: 'return=representation' }),
        body: JSON.stringify(patch)
      });
      const rows = r.ok ? await r.json() : [];
      if (!rows.length) throw new Error('not allowed');
      toast('Post updated', 'ok'); closeUpload(); loadPosts();
    } catch (e) { console.log(e); toast('Save failed: ' + (e?.message || 'unknown error'), 'err'); }
    finally { btn.disabled = false; btn.textContent = editingPost ? 'Save changes' : 'Publish'; }
  }

  /* ── Delete post: only say "deleted" if a row was really removed ── */
  window.deletePost = async function (id) {
    closePostMenu();
    try {
      const r = await fetch(`${SB_URL}/rest/v1/${TABLE}?id=eq.${id}`, {
        method: 'DELETE', headers: authHeaders({ Prefer: 'return=representation' })
      });
      const rows = r.ok ? await r.json() : [];
      if (!rows.length) throw new Error('not deleted');
      toast('Post deleted', 'ok'); loadPosts();
    } catch (e) { toast('Delete failed: not allowed or already removed', 'err'); }
  };

  /* ── Motion: tap feedback + animated close for panels ── */
  document.addEventListener('click', (e) => {
    const t = e.target.closest('button, a.nav-item, .cat-pill, .bnav-item, [role="button"]');
    if (!t || t.disabled || t.closest('.snap-row')) return;
    t.classList.remove('x-tap');
    void t.offsetWidth;
    t.classList.add('x-tap');
    setTimeout(() => t.classList.remove('x-tap'), 450);
  }, true);

  const closeTimers = {};
  const _showOverlay = showOverlay, _hideOverlay = hideOverlay;
  window.showOverlay = (id) => {
    const o = document.getElementById(id);
    clearTimeout(closeTimers[id]); delete closeTimers[id];
    if (o) o.classList.remove('x-closing');
    _showOverlay(id);
  };
  window.hideOverlay = (id) => {
    const o = document.getElementById(id);
    if (!o || o.classList.contains('hidden') || closeTimers[id]) return;
    o.classList.add('x-closing');
    closeTimers[id] = setTimeout(() => {
      delete closeTimers[id];
      o.classList.remove('x-closing');
      _hideOverlay(id);
    }, 180);
  };

  /* ── Back-button guard: the first Back never leaves the site; a second Back within 2s does ── */
  (function backGuard() {
    if (history.state && history.state.guard) return;
    const base = history.state || { filter: 'all' };
    history.replaceState({ filter: '__guard', guard: true }, '', location.href);
    history.pushState(base, '', location.href);
    const _setFilter = setFilter;
    window.setFilter = function (f) { if (f === '__guard') return; return _setFilter.apply(this, arguments); };
    let last = 0;
    window.addEventListener('popstate', (e) => {
      if (!(e.state && e.state.guard)) return;
      const now = Date.now();
      if (now - last < 2000) { history.back(); return; }
      last = now;
      toast('Press back again to exit', 'ok');
      history.pushState({ filter: activeFilter || 'all' }, '', '#' + (activeFilter || 'all'));
    });
  })();

  /* ── Chat thread: animations + shareable link  #people/thread-<name> ── */
  const slug = (n) => String(n || '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  const THREAD_RE = /^#people\/thread-([a-z0-9-]+)$/;
  const _openDM = openDM, _closeThread = closeThread;

  window.openDM = function (person) {
    const v = document.getElementById('chatThreadView');
    const wasOpen = v && !v.classList.contains('hidden');
    _openDM(person);
    if (v && wasOpen) { v.classList.remove('x-switch'); void v.offsetWidth; v.classList.add('x-switch'); }
    const target = '#people/thread-' + slug(person.name);
    if (slug(person.name) && location.hash !== target) history.pushState({ filter: 'people', thread: true }, '', target);
  };

  let leaving = false;
  window.closeThread = function () {
    if (leaving) return;
    // In-app back arrow: step back in history; the browser's popstate then closes the thread
    if (history.state && history.state.thread) { history.back(); return; }
    const m = document.getElementById('chatThreadMobile');
    const finish = () => {
      _closeThread();
      if (THREAD_RE.test(location.hash)) history.replaceState({ filter: 'people' }, '', '#people');
    };
    if (!m || m.classList.contains('hidden') || getComputedStyle(m).display === 'none') return finish();
    leaving = true;
    m.classList.add('x-leaving');
    setTimeout(() => { m.classList.remove('x-leaving'); leaving = false; finish(); }, 240);
  };

  // Opening a shared link: wait for login + contact list, then open that thread
  let linkTimer = null;
  function openThreadFromHash() {
    const m = THREAD_RE.exec(location.hash);
    if (!m) return;
    clearInterval(linkTimer);
    let tries = 0;
    linkTimer = setInterval(() => {
      if (++tries > 60 || !THREAD_RE.test(location.hash)) { clearInterval(linkTimer); return; }
      if (!currentUser || !chatUsers || !chatUsers.length) return;
      clearInterval(linkTimer);
      const u = chatUsers.find((x) => slug(x.name) === m[1] && x.uid !== chatUserId);
      if (!u) { toast('Couldn’t find that person', 'err'); history.replaceState({ filter: 'people' }, '', '#people'); return; }
      if (currentThread && currentThread.uid === u.uid) return;
      history.replaceState({ filter: 'people' }, '', '#people');   // keep a clean base entry for the back button
      openDM({ uid: u.uid, name: u.name, email: u.email, photo: u.photo });
    }, 500);
  }
  window.addEventListener('hashchange', () => { if (!currentThread) openThreadFromHash(); });
  openThreadFromHash();
})();
