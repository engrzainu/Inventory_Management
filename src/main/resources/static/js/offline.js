/* Offline support: keeps a copy of the product catalogue and a queue of sales made while offline,
   in the browser's IndexedDB. Nothing here talks to the server; app.js does the uploading. */
const Offline = (() => {
  let dbPromise = null;
  let pending = [];            // in-memory copy of the queue (also stored in IndexedDB)

  function open() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      if (!window.indexedDB) { reject(new Error('This browser cannot store data offline.')); return; }
      const req = indexedDB.open('inventory-offline', 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        db.createObjectStore('catalog', { keyPath: 'id' });
        db.createObjectStore('pending', { keyPath: 'clientId' });
        db.createObjectStore('meta', { keyPath: 'key' });
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error || new Error('Could not open offline storage.'));
    });
    dbPromise.catch(() => { dbPromise = null; });
    return dbPromise;
  }

  // Runs fn(store) inside one transaction; resolves with the IDBRequest result (if fn returns a request).
  async function run(storeName, mode, fn) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(storeName, mode);
      let req;
      try { req = fn(tx.objectStore(storeName)); } catch (e) { reject(e); return; }
      tx.oncomplete = () => resolve(req && 'result' in req ? req.result : undefined);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error || new Error('Offline storage was interrupted.'));
    });
  }

  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    const b = new Uint8Array(16); (window.crypto || {}).getRandomValues ? crypto.getRandomValues(b) : b.forEach((_, i) => b[i] = Math.random() * 256);
    b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map(x => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
  }

  // ---- meta (signed-in user, business profile, counters)
  const getMeta = async key => { const r = await run('meta', 'readonly', s => s.get(key)); return r ? r.value : null; };
  const setMeta = (key, value) => run('meta', 'readwrite', s => { s.put({ key, value }); });
  const deleteMeta = key => run('meta', 'readwrite', s => { s.delete(key); });

  // ---- product catalogue
  const putCatalog = list => run('catalog', 'readwrite', s => { s.clear(); list.forEach(p => s.put(p)); });
  const clearCatalog = () => run('catalog', 'readwrite', s => { s.clear(); });
  const catalogCount = () => run('catalog', 'readonly', s => s.count());
  async function searchCatalog(q, limit = 8) {
    const all = await run('catalog', 'readonly', s => s.getAll());
    const term = (q || '').trim().toLowerCase();
    return (all || [])
      .filter(p => !term || p.name.toLowerCase().includes(term) || p.sku.toLowerCase().includes(term))
      .sort((a, b) => a.name.localeCompare(b.name))
      .slice(0, limit);
  }

  // ---- queue of sales made while offline
  async function reloadPending() {
    const all = await run('pending', 'readonly', s => s.getAll());
    pending = (all || []).sort((a, b) => a.soldAt.localeCompare(b.soldAt));
    return pending;
  }
  const listPending = () => pending;
  async function putPending(rec) { await run('pending', 'readwrite', s => { s.put(rec); }); await reloadPending(); }
  async function removePending(clientId) { await run('pending', 'readwrite', s => { s.delete(clientId); }); await reloadPending(); }
  // Units already sold on this device but not yet uploaded. Subtracted from catalogue stock so the same
  // last unit cannot be sold twice while offline.
  function pendingQty(productId) {
    let n = 0;
    for (const p of pending) for (const i of p.items) if (i.productId === productId) n += i.quantity;
    return n;
  }
  // After a sale, lower the saved stock so offline searches stay accurate.
  async function decrementCatalog(items) {
    for (const i of items) {
      const p = await run('catalog', 'readonly', s => s.get(i.productId));
      if (p) { p.quantity = Math.max(0, p.quantity - i.quantity); await run('catalog', 'readwrite', s => { s.put(p); }); }
    }
  }
  async function nextLocalNo() {
    const n = ((await getMeta('localNo')) || 0) + 1;
    await setMeta('localNo', n);
    return 'OFF-' + String(n).padStart(4, '0');
  }

  return { uuid, decrementCatalog, getMeta, setMeta, deleteMeta, putCatalog, clearCatalog, catalogCount, searchCatalog,
           reloadPending, listPending, putPending, removePending, pendingQty, nextLocalNo };
})();
