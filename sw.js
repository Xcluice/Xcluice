/* Xcluice service worker: receives Web Push and shows notifications even when the site is closed. */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let d = {};
  try { d = event.data ? event.data.json() : {}; } catch { d = { title: 'Xcluice', body: event.data ? event.data.text() : '' }; }
  event.waitUntil((async () => {
    // Don't buzz if the person is already looking at the site
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if (wins.some((w) => w.visibilityState === 'visible' && w.focused)) return;
    await self.registration.showNotification(d.title || 'Xcluice', {
      body: d.body || '',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      tag: d.tag || 'xcluice-msg',
      renotify: true,
      vibrate: [100, 50, 100],
      data: { url: d.url || '/#people' },
    });
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/#people';
  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const w of wins) {
      if ('focus' in w) {
        await w.focus();
        try { w.postMessage({ type: 'open-url', url }); } catch {}
        return;
      }
    }
    await self.clients.openWindow(url);
  })());
});
