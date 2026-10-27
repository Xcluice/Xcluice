/* Profiles, stories and follow.
   Tap any avatar -> profile (posts grid, stories, follow). Stories last 24h.
   Relies on globals from index.html: SB_URL, BUCKET, authHeaders, currentUser, allPosts,
   chatUsers, esc, toast, timeAgo, getFileType, openViewer, closeViewer, openDM, setFilter,
   openFostHubAuth, triggerAvatarUpload, snapColor. */
(function () {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const A = (s) => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
  const lc = (s) => String(s || '').toLowerCase().trim();
  const reduce = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const color = (k) => (typeof snapColor === 'function' ? snapColor(k) : '#0FADFF');
  const ago = (iso) => (typeof timeAgo === 'function' ? timeAgo(iso) : '');

  const IC = {
    back: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>',
    x: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>',
    plus: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    trash: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/></svg>',
    grid: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><rect x="3" y="3" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1"/></svg>',
    pencil: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z"/><path d="m13.5 6.5 4 4"/></svg>',
    msg: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12Z"/></svg>',
    cam: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg>',
    img: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2.5"/><circle cx="9" cy="10" r="1.8"/><path d="m21 16-5-5-8 9"/></svg>',
    play: '<svg width="26" height="26" viewBox="0 0 24 24" fill="#fff"><path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5Z"/></svg>',
    file: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#7fe3ff" stroke-width="1.8" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>',
  };

  /* ───────── data ───────── */
  let profiles = [], follows = [], stories = [];
  const SEEN_KEY = 'xc_story_seen';
  const seenSet = () => { try { return new Set(JSON.parse(localStorage.getItem(SEEN_KEY) || '[]')); } catch { return new Set(); } };
  const markSeen = (id) => { try { const s = seenSet(); s.add(id); localStorage.setItem(SEEN_KEY, JSON.stringify([...s].slice(-500))); } catch {} };

  async function api(path) {
    const r = await fetch(`${SB_URL}/rest/v1/${path}`, { headers: authHeaders() });
    if (!r.ok) throw new Error(path);
    return r.json();
  }
  const loadProfiles = async () => { const d = await api('fosthub_users?select=id,username,display_name,' + (currentUser ? 'email,' : '') + 'avatar_url,role,created_at,verified,followers_override&limit=1000'); if (Array.isArray(d)) profiles = d; };
  const loadFollows = async () => { const d = await api('follows?select=follower,following&limit=5000'); if (Array.isArray(d)) follows = d; };
  const loadStories = async () => {
    const d = await api(`stories?select=id,uid,media_url,kind,caption,created_at,expires_at&expires_at=gt.${encodeURIComponent(new Date().toISOString())}&order=created_at.asc&limit=500`);
    if (Array.isArray(d)) stories = d;
  };
  const loadAll = () => Promise.allSettled([loadProfiles(), loadFollows(), loadStories()]);

  const storiesOf = (uid) => stories.filter((s) => s.uid === uid && new Date(s.expires_at) > Date.now());
  const hasUnseen = (uid) => { const seen = seenSet(); return storiesOf(uid).some((s) => !seen.has(s.id)); };
  const ringClass = (uid) => (!storiesOf(uid).length ? '' : hasUnseen(uid) ? 'unseen' : 'seen');
  const isFollowing = (id) => !!currentUser && follows.some((f) => f.follower === currentUser.id && f.following === id);
  const followers = (id) => { const o = (profiles.find((x) => x.id === id) || {}).followers_override; return o != null ? Number(o) : follows.filter((f) => f.following === id).length; };
  const fmtN = (n) => n >= 1e6 ? (n / 1e6).toFixed(n % 1e6 ? 1 : 0).replace(/\.0$/, '') + 'M' : n >= 1e4 ? (n / 1e3).toFixed(n % 1e3 ? 1 : 0).replace(/\.0$/, '') + 'K' : String(n);
  const TICK = '<svg class="pf-tick" width="18" height="18" viewBox="0 0 24 24" aria-label="Verified"><path fill="#0FADFF" d="M12 1.5l2.6 1.9 3.2-.1 1 3 2.6 1.9-1 3.1 1 3.1-2.6 1.9-1 3-3.2-.1L12 22.5l-2.6-1.9-3.2.1-1-3-2.6-1.9 1-3.1-1-3.1 2.6-1.9 1-3 3.2.1z"/><path d="m7.8 12.3 3 3 5.6-6" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const followingCount = (id) => follows.filter((f) => f.follower === id).length;

  /* ───────── profile resolution ───────── */
  function norm(p, extra) {
    return Object.assign({ id: p.id || null, username: p.username || '', name: p.display_name || p.name || 'User', email: p.email || '', photo: p.avatar_url || p.photo || '', role: p.role || '', joined: p.created_at || null, verified: !!p.verified, fo: p.followers_override }, extra || {});
  }
  function resolve(ref) {
    let p = null;
    if (ref.uid) p = profiles.find((x) => x.id === ref.uid);
    if (!p && ref.email) p = profiles.find((x) => lc(x.email) === lc(ref.email));
    if (!p && ref.name && !ref.uid && !ref.email) p = profiles.find((x) => lc(x.display_name) === lc(ref.name));
    if (p) return norm(p);
    if (ref.uid && typeof chatUsers !== 'undefined') { const c = chatUsers.find((u) => u.uid === ref.uid); if (c) return norm({ id: c.uid, display_name: c.name, email: c.email, photo: c.photo }); }
    return norm({ display_name: ref.name, email: ref.email, photo: ref.photo }, { legacy: true });
  }
  function postsOf(p) {
    const em = lc(p.email);
    return (typeof allPosts !== 'undefined' ? allPosts : []).filter((x) => (em && lc(x.author_email) === em) || (!em && p.name && x.author_name === p.name));
  }

  const avatarInner = (p, size) => p.photo ? `<img src="${A(p.photo)}" alt="" loading="lazy">` : `<span>${A((p.name || '?')[0].toUpperCase())}</span>`;
  const avatarBg = (p) => p.photo ? '#111' : color(p.id || p.email || p.name);

  /* ───────── profile page ───────── */
  let pfEl = null, pfCur = null, pfOpen = false, pfCb = null;
  function ensureProfileDom() {
    if (pfEl) return;
    pfEl = document.createElement('div');
    pfEl.id = 'pfView'; pfEl.className = 'pf-view hidden';
    pfEl.innerHTML = `<div class="pf-top"><button class="pf-iconbtn" data-a="back" aria-label="Back">${IC.back}</button><div class="pf-toptitle" id="pfTitle"></div><span></span></div><div class="pf-scroll" id="pfScroll"><div class="pf-body" id="pfBody"></div></div>`;
    document.body.appendChild(pfEl);
    pfEl.addEventListener('click', onProfileClick);
  }
  function skeleton() {
    return `<div class="pf-head"><div class="pf-sk" style="width:99px;height:99px;border-radius:50%"></div><div style="flex:1;display:flex;justify-content:space-around">${'<div class="pf-sk" style="width:44px;height:38px"></div>'.repeat(3)}</div></div><div class="pf-sk" style="width:46%;height:20px;margin-top:16px"></div><div class="pf-sk" style="width:30%;height:14px;margin-top:8px"></div><div class="pf-sk" style="height:38px;margin-top:16px"></div>`;
  }

  function tileHTML(post, i) {
    const t = getFileType(post.file_url);
    const isImg = post.file_url && /\.(png|jpe?g|gif|webp|svg)(\?|$)/i.test(post.file_url);
    const isVid = post.file_url && /\.(mp4|webm|mkv)(\?|$)/i.test(post.file_url);
    const kinds = { media: isVid ? 'VIDEO' : 'PREVIEW', docs: 'ASSET', html: 'DEMO', apk: 'APK', archive: 'ARCHIVE', link: 'LINK', other: 'FILE' };
    const label = post.file_url ? (kinds[t] || 'FILE') : (post.link_url ? 'LINK' : '');
    let inner;
    if (isImg) inner = `<img src="${A(post.file_url)}" alt="" loading="lazy">`;
    else if (post.thumb_url) inner = `<img src="${A(post.thumb_url)}" alt="" loading="lazy">`;
    else if (isVid) inner = `<div class="pf-txt">${IC.play}</div>`;
    else if (!post.file_url) inner = `<div class="pf-txt"><p>${esc(post.subject || post.title || '')}</p></div>`;
    else inner = `<div class="pf-txt">${IC.file}<p>${esc(post.subject || post.title || '')}</p></div>`;
    return `<div class="pf-tile" style="animation-delay:${Math.min(i, 14) * 30}ms" data-a="post" data-id="${A(post.id)}">${inner}${label ? `<span class="pf-kind">${label}</span>` : ''}</div>`;
  }

  function renderProfile() {
    const p = pfCur; if (!p) return;
    const me = !!currentUser && p.id && p.id === currentUser.id;
    const posts = postsOf(p);
    const ss = p.id ? storiesOf(p.id) : [];
    const ring = p.id ? ringClass(p.id) : '';
    const following = p.id && isFollowing(p.id);
    $('pfTitle').textContent = p.username ? '@' + p.username : p.name;
    const joined = p.joined ? 'Joined ' + new Date(p.joined).toLocaleDateString([], { month: 'long', year: 'numeric' }) : '';
    const sub = [p.username ? '@' + p.username : '', joined].filter(Boolean).join(' · ');
    const isAdmin = p.role === 'Admin';
    let actions = '';
    if (me) actions = `<button class="pf-btn primary" data-a="addstory">${IC.cam} Add story</button><button class="pf-btn" data-a="photo">${IC.img} Change photo</button><button class="pf-btn pf-edit" data-a="edit" aria-label="Edit profile">${IC.pencil}</button>`;
    else if (p.id) actions = `<button class="pf-btn ${following ? 'on' : 'primary'}" data-a="follow">${following ? 'Following' : 'Follow'}</button><button class="pf-btn" data-a="message">${IC.msg} Message</button>`;
    const strip = ss.length ? `<div class="pf-strip"><div class="pf-label">Stories · 24h</div><div class="pf-strip-row">${ss.map((s, i) => {
      const seen = seenSet().has(s.id);
      const m = s.kind === 'video' ? `<video src="${A(s.media_url)}#t=0.1" muted playsinline preload="metadata"></video>` : `<img src="${A(s.media_url)}" alt="" loading="lazy">`;
      return `<div class="pf-sthumb" data-a="story" data-i="${i}"><div class="pf-ring ${seen ? 'seen' : 'unseen'}"><div class="pf-sm">${m}</div></div><small>${ago(s.created_at)}</small></div>`;
    }).join('')}</div></div>` : '';
    $('pfBody').innerHTML = `
      <div class="pf-head">
        <div class="pf-avwrap"><div class="pf-ring ${ring}" data-a="avatar"><div class="pf-av" style="background:${avatarBg(p)}">${avatarInner(p)}</div></div>${me ? `<button class="pf-plus" data-a="addstory" aria-label="Add story">${IC.plus}</button>` : ''}</div>
        <div class="pf-stats"><div><b>${posts.length}</b><span>Posts</span></div><div><b>${p.id ? fmtN(followers(p.id)) : 0}</b><span>Followers</span></div><div><b>${p.id ? followingCount(p.id) : 0}</b><span>Following</span></div></div>
      </div>
      <div class="pf-name"><span>${esc(p.name)}</span>${p.verified ? TICK : ''}${isAdmin ? '<span class="pf-badge">ADMIN</span>' : (p.role && p.role !== 'Member' && p.role !== 'member' ? `<span class="pf-badge">${esc(String(p.role).toUpperCase())}</span>` : '')}</div>
      <div class="pf-sub">${esc(sub)}</div>
      ${actions ? `<div class="pf-actions">${actions}</div>` : ''}
      ${strip}
      <div class="pf-tabs"><div>${IC.grid}</div></div>
      <div class="pf-grid">${posts.length ? posts.map(tileHTML).join('') : `<div class="pf-empty">${me ? 'You haven’t posted anything yet.' : 'No posts yet.'}</div>`}</div>`;
  }

  async function openProfile(ref) {
    ref = ref || {};
    if (!currentUser) { toast('Sign in to view profiles', 'err'); if (typeof openFostHubAuth === 'function') openFostHubAuth(); return; }
    if (!ref.uid && !ref.email && !ref.name) return;
    if (pfOpen && pfCur && ((ref.uid && ref.uid === pfCur.id) || (ref.email && lc(ref.email) === lc(pfCur.email)))) return;
    const v = $('viewerOverlay'); if (v && !v.classList.contains('hidden') && typeof closeViewer === 'function') closeViewer();
    ensureProfileDom();
    const wasOpen = pfOpen;
    pfCur = null; $('pfBody').innerHTML = skeleton(); $('pfTitle').textContent = '';
    if (!wasOpen) {
      pfEl.classList.remove('hidden', 'out'); pfEl.classList.remove('in'); void pfEl.offsetWidth; pfEl.classList.add('in');
      history.pushState({ profile: true }, '', '#profile-' + (lc(ref.name || ref.uid || 'user').replace(/[^a-z0-9]+/g, '-').slice(0, 40)));
    }
    pfOpen = true; $('pfScroll').scrollTop = 0;
    const token = (openProfile.t = (openProfile.t || 0) + 1);
    await loadAll();
    if (token !== openProfile.t || !pfOpen) return;
    pfCur = resolve(ref); renderProfile(); refreshTray();
  }
  function doCloseProfile() {
    if (!pfOpen) return; pfOpen = false; pfCur = null; openProfile.t = (openProfile.t || 0) + 1;
    pfEl.classList.remove('in'); pfEl.classList.add('out');
    setTimeout(() => { if (!pfOpen) { pfEl.classList.add('hidden'); pfEl.classList.remove('out'); } }, reduce() ? 0 : 220);
    const cb = pfCb; pfCb = null; if (cb) cb();
  }
  function closeProfile(cb) {
    if (!pfOpen) { if (cb) cb(); return; }
    pfCb = cb || null;
    if (history.state && history.state.profile) history.back(); else doCloseProfile();
  }

  async function toggleFollow() {
    const p = pfCur; if (!p || !p.id || !currentUser) return;
    const was = isFollowing(p.id);
    if (was) follows = follows.filter((f) => !(f.follower === currentUser.id && f.following === p.id));
    else follows.push({ follower: currentUser.id, following: p.id });
    renderProfile(); refreshTray();
    try {
      const r = was
        ? await fetch(`${SB_URL}/rest/v1/follows?follower=eq.${encodeURIComponent(currentUser.id)}&following=eq.${encodeURIComponent(p.id)}`, { method: 'DELETE', headers: authHeaders() })
        : await fetch(`${SB_URL}/rest/v1/follows`, { method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json', Prefer: 'resolution=ignore-duplicates,return=minimal' }), body: JSON.stringify({ follower: currentUser.id, following: p.id }) });
      if (!r.ok) throw new Error();
    } catch {
      if (was) follows.push({ follower: currentUser.id, following: p.id });
      else follows = follows.filter((f) => !(f.follower === currentUser.id && f.following === p.id));
      renderProfile(); toast('Couldn’t update follow', 'err');
    }
  }

  /* ───────── edit profile (username + display name) ───────── */
  function openEdit() {
    const p = pfCur; if (!p || !currentUser || p.id !== currentUser.id) return;
    let m = $('pfEdit'); if (m) m.remove();
    m = document.createElement('div'); m.id = 'pfEdit'; m.className = 'pf-editwrap';
    m.innerHTML = `<div class="pf-editbox"><div class="pf-edittitle">Edit profile</div>
      <label>Display name<input id="pfeName" maxlength="40" autocomplete="off" value="${A(p.name)}"></label>
      <label>Username<input id="pfeUser" maxlength="20" autocomplete="off" autocapitalize="none" spellcheck="false" value="${A(p.username)}"></label>
      <div class="pf-editerr" id="pfeErr"></div>
      <div class="pf-editbtns"><button class="pf-btn" data-e="cancel">Cancel</button><button class="pf-btn primary" data-e="save">Save</button></div></div>`;
    m.addEventListener('click', (e) => {
      if (e.target === m || e.target.closest('[data-e="cancel"]')) m.remove();
      else if (e.target.closest('[data-e="save"]')) saveEdit(m);
    });
    pfEl.appendChild(m);
    setTimeout(() => { const i = $('pfeName'); if (i) i.focus(); }, 50);
  }
  async function saveEdit(m) {
    const err = $('pfeErr'), btn = m.querySelector('[data-e="save"]');
    const name = $('pfeName').value.trim().replace(/\s+/g, ' ');
    const user = $('pfeUser').value.trim().toLowerCase().replace(/^@/, '');
    if (!name) { err.textContent = 'Display name can’t be empty.'; return; }
    if (!/^[a-z0-9_]{3,20}$/.test(user)) { err.textContent = 'Username: 3–20 letters, numbers or _ only.'; return; }
    const old = { name: currentUser.display_name, user: currentUser.username };
    if (name === old.name && user === (pfCur.username || old.user)) { m.remove(); return; }
    btn.disabled = true; err.textContent = '';
    try {
      const r = await fetch(`${SB_URL}/rest/v1/fosthub_users?id=eq.${currentUser.id}`, { method: 'PATCH', headers: authHeaders({ 'Content-Type': 'application/json', Prefer: 'return=representation' }), body: JSON.stringify({ display_name: name, username: user }) });
      if (r.status === 409) { err.textContent = 'That username is taken.'; btn.disabled = false; return; }
      const rows = r.ok ? await r.json() : null;
      if (!rows || !rows.length) throw new Error('save');
      const h = authHeaders({ 'Content-Type': 'application/json' });
      const uid = encodeURIComponent(currentUser.id), em = encodeURIComponent(currentUser.email || '');
      await Promise.allSettled([
        fetch(`${SB_URL}/rest/v1/chat_users?uid=eq.${uid}`, { method: 'PATCH', headers: h, body: JSON.stringify({ name }) }),
        em ? fetch(`${SB_URL}/rest/v1/homework?author_email=eq.${em}`, { method: 'PATCH', headers: h, body: JSON.stringify({ author_name: name }) }) : null,
      ].filter(Boolean));
      currentUser.display_name = name; currentUser.username = user;
      try { chatUserName = name; } catch (e) {}
      const pr = profiles.find((x) => x.id === currentUser.id); if (pr) { pr.display_name = name; pr.username = user; }
      pfCur = norm(Object.assign({}, pr || { id: currentUser.id, email: currentUser.email, avatar_url: currentUser.avatar_url, role: currentUser.role }, { display_name: name, username: user }));
      m.remove(); renderProfile();
      try { if (typeof updateUserUI === 'function') updateUserUI(); } catch (e) {}
      try { if (typeof loadPosts === 'function') loadPosts(false); } catch (e) {}
      if (typeof toast === 'function') toast('Profile updated', 'ok');
    } catch (e) { err.textContent = 'Couldn’t save. Try again.'; btn.disabled = false; }
  }

  function onProfileClick(e) {
    const t = e.target.closest('[data-a]'); if (!t) return;
    const a = t.dataset.a, p = pfCur;
    if (a === 'back') closeProfile();
    else if (a === 'follow') toggleFollow();
    else if (a === 'addstory') pickStory();
    else if (a === 'edit') openEdit();
    else if (a === 'photo') { if (typeof triggerAvatarUpload === 'function') triggerAvatarUpload(); }
    else if (a === 'avatar') { if (p && p.id && storiesOf(p.id).length) openStories([p.id], 0, 0); else if (p && currentUser && p.id === currentUser.id) pickStory(); }
    else if (a === 'story') { if (p && p.id) openStories([p.id], 0, +t.dataset.i || 0); }
    else if (a === 'post') { if (typeof openViewer === 'function') openViewer(t.dataset.id); }
    else if (a === 'message') {
      if (!p || !p.id) return;
      const person = { uid: p.id, name: p.name, email: p.email, photo: p.photo };
      closeProfile(() => { setFilter('people'); setTimeout(() => openDM(person), 60); });
    }
  }

  /* ───────── stories: viewer ───────── */
  let svEl = null;
  const st = { open: false, queue: [], u: 0, i: 0, list: [], raf: 0, paused: false, ready: false, dur: 5000, acc: 0, t0: 0, media: null, tok: 0, cb: null };
  function ensureStoryDom() {
    if (svEl) return;
    svEl = document.createElement('div'); svEl.id = 'stView'; svEl.className = 'st-view hidden';
    svEl.innerHTML = `<div class="st-stage" id="stStage"><div id="stMediaBox"></div><div class="st-spin" id="stSpin"></div><div class="st-shade"></div><div class="st-shade b"></div>
      <div class="st-bars" id="stBars"></div>
      <div class="st-head"><div class="pf-av" id="stAv" data-a="who"></div><div class="st-who" data-a="who"><b id="stName"></b><small id="stTime"></small></div><button class="st-hbtn hidden" id="stDel" data-a="del" aria-label="Delete story">${IC.trash}</button><button class="st-hbtn" data-a="close" aria-label="Close">${IC.x}</button></div>
      <div class="st-zone l" data-z="l"></div><div class="st-zone r" data-z="r"></div><div class="st-cap" id="stCap"></div></div>`;
    document.body.appendChild(svEl);
    const stage = $('stStage');
    let down = null, hold = 0, holding = false;
    stage.addEventListener('pointerdown', (e) => {
      if (e.target.closest('[data-a]')) return;
      down = { x: e.clientX, y: e.clientY, t: Date.now() }; holding = false;
      clearTimeout(hold); hold = setTimeout(() => { holding = true; stPause(true); }, 220);
    });
    const end = (e, cancelled) => {
      clearTimeout(hold); if (!down) return;
      const d = down; down = null;
      if (holding) { holding = false; stPause(false); }
      if (cancelled) return;
      const dx = e.clientX - d.x, dy = e.clientY - d.y;
      if (dy > 90 && Math.abs(dx) < 80) { closeStories(); return; }
      if (Math.hypot(dx, dy) < 12 && Date.now() - d.t < 260) {
        const r = stage.getBoundingClientRect();
        if (e.clientX - r.left < r.width * 0.34) stPrev(); else stNext();
      }
    };
    stage.addEventListener('pointerup', (e) => end(e, false));
    stage.addEventListener('pointercancel', (e) => end(e, true));
    stage.addEventListener('click', (e) => {
      const t = e.target.closest('[data-a]'); if (!t) return;
      if (t.dataset.a === 'close') closeStories();
      else if (t.dataset.a === 'del') stDelete();
      else if (t.dataset.a === 'who') { const uid = st.queue[st.u]; closeStories(() => openProfile({ uid })); }
    });
    svEl.addEventListener('click', (e) => { if (e.target === svEl) closeStories(); });
    document.addEventListener('keydown', (e) => {
      if (!st.open) return;
      if (e.key === 'Escape') closeStories(); else if (e.key === 'ArrowRight') stNext(); else if (e.key === 'ArrowLeft') stPrev();
    });
    document.addEventListener('visibilitychange', () => { if (st.open) stPause(document.hidden); });
  }

  function openStories(uids, ui, si) {
    const queue = uids.filter((u) => storiesOf(u).length); if (!queue.length) return;
    ensureStoryDom();
    st.queue = queue; st.u = Math.min(ui || 0, queue.length - 1); st.i = si || 0;
    if (!st.open) {
      st.open = true; svEl.classList.remove('hidden', 'out'); svEl.classList.remove('in'); void svEl.offsetWidth; svEl.classList.add('in');
      history.pushState({ story: true }, '', location.hash || '#');
    }
    stLoad();
  }
  function stLoad() {
    cancelAnimationFrame(st.raf); st.tok++; const tok = st.tok;
    const uid = st.queue[st.u]; st.list = storiesOf(uid);
    if (!st.list.length) { stNextUser(); return; }
    if (st.i >= st.list.length) st.i = st.list.length - 1;
    const s = st.list[st.i]; markSeen(s.id);
    const prof = resolve({ uid }), me = currentUser && uid === currentUser.id;
    $('stName').textContent = me ? 'Your story' : prof.name;
    $('stTime').textContent = ago(s.created_at);
    $('stAv').style.background = avatarBg(prof); $('stAv').innerHTML = avatarInner(prof);
    $('stDel').classList.toggle('hidden', !me);
    $('stCap').textContent = s.caption || '';
    $('stBars').innerHTML = st.list.map((_, k) => `<div class="st-bar ${k < st.i ? 'done' : ''}"><i></i></div>`).join('');
    st.ready = false; st.acc = 0; st.paused = false; st.dur = 5000; $('stSpin').style.display = '';
    const box = $('stMediaBox'); box.innerHTML = '';
    let m;
    const ready = () => { if (tok !== st.tok) return; st.ready = true; st.t0 = performance.now(); $('stSpin').style.display = 'none'; st.raf = requestAnimationFrame(stTick); };
    if (s.kind === 'video') {
      m = document.createElement('video'); m.className = 'st-media'; m.src = s.media_url; m.playsInline = true; m.autoplay = true; m.preload = 'auto';
      m.addEventListener('loadedmetadata', () => { st.dur = Math.min(60000, Math.max(1500, (m.duration || 5) * 1000)); });
      m.addEventListener('playing', ready, { once: true });
      m.addEventListener('ended', () => { if (tok === st.tok) stNext(); });
      m.addEventListener('error', () => { if (tok === st.tok) { toast('Couldn’t play this story', 'err'); stNext(); } });
      box.appendChild(m); st.media = m;
      m.play().catch(() => { m.muted = true; m.play().catch(() => {}); });
    } else {
      m = document.createElement('img'); m.className = 'st-media'; m.alt = '';
      m.addEventListener('load', ready, { once: true });
      m.addEventListener('error', () => { if (tok === st.tok) { toast('Couldn’t load this story', 'err'); stNext(); } }, { once: true });
      m.src = s.media_url; box.appendChild(m); st.media = m;
    }
    // preload the next image
    const nx = st.list[st.i + 1]; if (nx && nx.kind !== 'video') { const im = new Image(); im.src = nx.media_url; }
  }
  function stTick(now) {
    if (!st.open || !st.ready) return;
    const s = st.list[st.i]; if (!s) return;
    let frac;
    if (s.kind === 'video' && st.media && st.media.duration) frac = st.media.currentTime / st.media.duration;
    else { if (!st.paused) { st.acc += now - st.t0; } st.t0 = now; frac = st.acc / st.dur; }
    const bar = $('stBars').children[st.i]; if (bar) bar.firstChild.style.width = Math.min(100, frac * 100) + '%';
    if (frac >= 1 && s.kind !== 'video') { stNext(); return; }
    st.raf = requestAnimationFrame(stTick);
  }
  function stPause(p) {
    st.paused = p; if (svEl) svEl.classList.toggle('paused', p);
    const s = st.list[st.i];
    if (st.media && s && s.kind === 'video') { if (p) st.media.pause(); else st.media.play().catch(() => {}); }
    if (!p && st.ready) st.t0 = performance.now();
  }
  function stNext() {
    if (st.i < st.list.length - 1) { st.i++; stLoad(); } else stNextUser();
  }
  function stNextUser() { if (st.u < st.queue.length - 1) { st.u++; st.i = 0; stLoad(); } else closeStories(); }
  function stPrev() {
    if (st.i > 0) { st.i--; stLoad(); } else if (st.u > 0) { st.u--; st.i = Math.max(0, storiesOf(st.queue[st.u]).length - 1); stLoad(); } else stLoad();
  }
  async function stDelete() {
    const s = st.list[st.i]; if (!s || !currentUser || s.uid !== currentUser.id) return;
    stPause(true);
    if (!confirm('Delete this story?')) { stPause(false); return; }
    try {
      const r = await fetch(`${SB_URL}/rest/v1/stories?id=eq.${s.id}`, { method: 'DELETE', headers: authHeaders() });
      if (!r.ok) throw new Error();
      stories = stories.filter((x) => x.id !== s.id); toast('Story deleted', 'ok');
      refreshTray(); if (pfOpen) renderProfile();
      if (!storiesOf(st.queue[st.u]).length) stNextUser(); else stLoad();
    } catch { stPause(false); toast('Couldn’t delete story', 'err'); }
  }
  function doCloseStories() {
    if (!st.open) return; st.open = false; st.tok++; cancelAnimationFrame(st.raf);
    if (st.media && st.media.pause) { try { st.media.pause(); } catch {} }
    svEl.classList.remove('in'); svEl.classList.add('out');
    setTimeout(() => { if (!st.open) { svEl.classList.add('hidden'); svEl.classList.remove('out'); $('stMediaBox').innerHTML = ''; } }, reduce() ? 0 : 200);
    refreshTray(); if (pfOpen && pfCur) renderProfile();
    const cb = st.cb; st.cb = null; if (cb) cb();
  }
  function closeStories(cb) {
    if (!st.open) { if (cb) cb(); return; }
    st.cb = cb || null;
    if (history.state && history.state.story) history.back(); else doCloseStories();
  }

  /* ───────── stories: upload ───────── */
  let fileIn = null, compEl = null, compFile = null, compUrl = null;
  function pickStory() {
    if (!currentUser) { toast('Sign in to add a story', 'err'); return; }
    if (!fileIn) {
      fileIn = document.createElement('input'); fileIn.type = 'file'; fileIn.accept = 'image/*,video/*'; fileIn.className = 'hidden'; fileIn.style.display = 'none';
      fileIn.addEventListener('change', () => { const f = fileIn.files && fileIn.files[0]; fileIn.value = ''; if (f) openComposer(f); });
      document.body.appendChild(fileIn);
    }
    fileIn.click();
  }
  function openComposer(file) {
    const isVid = file.type.startsWith('video/'), isImg = file.type.startsWith('image/');
    if (!isVid && !isImg) { toast('Choose a photo or video', 'err'); return; }
    if (isImg && file.size > 10 * 1024 * 1024) { toast('Photo is too big (max 10 MB)', 'err'); return; }
    if (isVid && file.size > 40 * 1024 * 1024) { toast('Video is too big (max 40 MB)', 'err'); return; }
    if (!compEl) {
      compEl = document.createElement('div'); compEl.className = 'st-comp hidden';
      compEl.innerHTML = `<div class="st-card"><div class="st-prev" id="stPrev"></div><input class="st-cap-in" id="stCapIn" maxlength="200" placeholder="Add a caption…" autocomplete="off" /><div class="st-row"><button class="pf-btn" data-c="cancel">Cancel</button><button class="pf-btn primary" data-c="share" id="stShare">Share story</button></div></div>`;
      document.body.appendChild(compEl);
      compEl.addEventListener('click', (e) => { const t = e.target.closest('[data-c]'); if (t) { if (t.dataset.c === 'cancel') closeComposer(); else shareStory(); } else if (e.target === compEl) closeComposer(); });
    }
    compFile = file; if (compUrl) URL.revokeObjectURL(compUrl); compUrl = URL.createObjectURL(file);
    const prev = $('stPrev');
    prev.innerHTML = isVid ? `<video src="${compUrl}" muted loop autoplay playsinline></video>` : `<img src="${compUrl}" alt="">`;
    if (isVid) { const v = prev.firstChild; v.addEventListener('loadedmetadata', () => { if (v.duration > 60) { toast('Videos can be up to 60 seconds', 'err'); closeComposer(); } }); }
    $('stCapIn').value = ''; $('stShare').disabled = false; $('stShare').textContent = 'Share story';
    compEl.classList.remove('hidden', 'in'); void compEl.offsetWidth; compEl.classList.add('in');
  }
  function closeComposer() { if (!compEl) return; compEl.classList.add('hidden'); $('stPrev').innerHTML = ''; if (compUrl) { URL.revokeObjectURL(compUrl); compUrl = null; } compFile = null; }
  async function shareStory() {
    if (!compFile || !currentUser) return;
    const btn = $('stShare'); btn.disabled = true; btn.textContent = 'Sharing…';
    const f = compFile, kind = f.type.startsWith('video/') ? 'video' : 'image';
    const ext = (f.name.split('.').pop() || (kind === 'video' ? 'mp4' : 'jpg')).toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 5) || 'bin';
    const path = `story_${String(currentUser.id).slice(0, 8)}_${Date.now()}.${ext}`;
    try {
      const up = await fetch(`${SB_URL}/storage/v1/object/${BUCKET}/${path}`, { method: 'POST', headers: authHeaders({ 'Content-Type': f.type || 'application/octet-stream' }), body: f });
      if (!up.ok) throw new Error('upload');
      const url = `${SB_URL}/storage/v1/object/public/${BUCKET}/${path}`;
      const ins = await fetch(`${SB_URL}/rest/v1/stories`, { method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json', Prefer: 'return=minimal' }), body: JSON.stringify({ uid: currentUser.id, media_url: url, kind, caption: $('stCapIn').value.trim() || null }) });
      if (!ins.ok) throw new Error('insert');
      closeComposer(); toast('Story shared ✓', 'ok');
      await loadStories(); refreshTray(); if (pfOpen && pfCur) renderProfile();
    } catch { btn.disabled = false; btn.textContent = 'Share story'; toast('Couldn’t share story', 'err'); }
  }

  /* ───────── stories tray (top of Chat) ───────── */
  let trayEl = null;
  function ensureTray() {
    if (trayEl && trayEl.isConnected) return trayEl;
    const anchor = $('snapSearch'); if (!anchor) return null;
    trayEl = document.createElement('div'); trayEl.id = 'storyTray'; trayEl.className = 'st-tray hidden';
    anchor.after(trayEl);
    trayEl.addEventListener('click', (e) => {
      const plus = e.target.closest('[data-a="addstory"]'); if (plus) { e.stopPropagation(); pickStory(); return; }
      const it = e.target.closest('[data-uid]'); if (!it) return;
      const uid = it.dataset.uid, order = trayOrder();
      if (storiesOf(uid).length) openStories(order, Math.max(0, order.indexOf(uid)), 0);
      else if (currentUser && uid === currentUser.id) pickStory();
    });
    return trayEl;
  }
  function trayOrder() {
    const seen = seenSet(), ids = [...new Set(stories.map((s) => s.uid))].filter((u) => storiesOf(u).length && (!currentUser || u !== currentUser.id));
    const score = (u) => (hasUnseen(u) ? 0 : 2) + (isFollowing(u) ? 0 : 1);
    ids.sort((a, b) => score(a) - score(b));
    return (currentUser && storiesOf(currentUser.id).length ? [currentUser.id] : []).concat(ids);
  }
  function refreshTray() {
    const t = ensureTray(); if (!t) return;
    if (!currentUser) { t.classList.add('hidden'); return; }
    const order = trayOrder(), me = currentUser, myHas = storiesOf(me.id).length > 0;
    const cell = (p, label, extra) => `<div class="st-item" data-uid="${A(p.id)}"><div class="pf-ring ${ringClass(p.id)}"><div class="pf-av" style="background:${avatarBg(p)}">${avatarInner(p)}</div>${extra || ''}</div><span>${esc(label)}</span></div>`;
    const mine = norm({ id: me.id, display_name: me.display_name, email: me.email, avatar_url: me.avatar_url });
    let html = cell(mine, 'Your story', `<button class="pf-plus" data-a="addstory" aria-label="Add story">${IC.plus}</button>`);
    order.filter((u) => u !== me.id).forEach((u) => { const p = resolve({ uid: u }); html += cell(p, (p.name || '').split(' ')[0]); });
    t.innerHTML = html; t.classList.remove('hidden');
  }

  /* ───────── wiring ───────── */
  document.addEventListener('click', (e) => {
    const t = e.target.closest('.pf-tap'); if (!t) return;
    e.preventDefault(); e.stopPropagation();
    openProfile({ uid: t.dataset.pfUid || undefined, email: t.dataset.pfEmail || undefined, name: t.dataset.pfName || undefined, photo: t.dataset.pfPhoto || undefined });
  }, true);

  // Called from index.html's popstate handler. Returns true when it handled the event.
  function onPop(e) {
    if (st.open) { doCloseStories(); return true; }
    if (pfOpen && !(e.state && e.state.profile)) { doCloseProfile(); return true; }
    if (e.state && (e.state.profile || e.state.story)) return true;
    return false;
  }

  // Keep profile + tray in sync after the avatar changes or someone signs in
  if (typeof window.handleAvatarUpload === 'function') {
    const _h = window.handleAvatarUpload;
    window.handleAvatarUpload = async function (e) {
      await _h.apply(this, arguments);
      try { await loadProfiles(); } catch {}
      if (pfOpen && currentUser && pfCur && pfCur.id === currentUser.id) { pfCur = resolve({ uid: currentUser.id }); renderProfile(); }
      refreshTray();
    };
  }
  if (typeof window.setSignedInUser === 'function') {
    const _s = window.setSignedInUser;
    window.setSignedInUser = function () { const r = _s.apply(this, arguments); setTimeout(() => loadAll().then(refreshTray), 0); return r; };
  }
  setInterval(() => { if (currentUser && !document.hidden) loadStories().then(() => { refreshTray(); }).catch(() => {}); }, 60000);
  window.addEventListener('DOMContentLoaded', () => setTimeout(() => { if (currentUser) loadAll().then(refreshTray); else refreshTray(); }, 1500));

  window.openProfile = openProfile;
  window.openMyProfile = function () {
    ['userDropdown', 'userDropdownMobile'].forEach((id) => { const d = $(id); if (d) d.classList.add('hidden'); });
    if (typeof closeUserDropdown === 'function') { try { closeUserDropdown(); } catch {} }
    if (currentUser) openProfile({ uid: currentUser.id });
  };
  window.xcProfiles = { onPop, refreshTray, openStories, isOpen: () => pfOpen || st.open };
})();
