'use strict';
// Renders the GO live-guidance panel for the web: given a planned GuidanceJourney,
// show the one instruction that matters now (board / stay on / get off next /
// change here / arrived) with the get-off cue emphasised, and Next/Back controls
// to step through. Pure view over web-go.js; advancing is manual for now (live
// position advance is a later phase), matching the iOS GO screen.
//
// UMD: `require('./web-go-panel.js')` in node (logic testable), `window.SyrmosGoPanel`
// in the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(root);
  else root.SyrmosGoPanel = factory(root);
})(typeof self !== 'undefined' ? self : this, function (root) {
  const GO = (typeof require === 'function') ? require('./web-go.js') : root.SyrmosGO;

  function t(lang, en, el, sq, it) {
    return lang === 'el' ? el : lang === 'sq' ? sq : lang === 'it' ? it : en;
  }

  // The rider-facing text for a guidance object (headline + detail + sub).
  function describe(g, lang) {
    switch (g.kind) {
      case 'board':
        return {
          icon: '🚶', headline: t(lang, `Board ${g.lineId}`, `Επιβίβαση ${g.lineId}`, `Hip në ${g.lineId}`, `Sali su ${g.lineId}`),
          detail: t(lang, `toward ${g.towards}`, `προς ${g.towards}`, `drejt ${g.towards}`, `verso ${g.towards}`),
          sub: t(lang, `${g.stopsRemaining} stops · next ${g.nextStation}`, `${g.stopsRemaining} στάσεις · επόμενη ${g.nextStation}`, `${g.stopsRemaining} ndalesa · tjetra ${g.nextStation}`, `${g.stopsRemaining} fermate · prossima ${g.nextStation}`),
        };
      case 'ride':
        return {
          icon: '🚈', headline: t(lang, `Stay on ${g.lineId}`, `Μείνε στη ${g.lineId}`, `Qëndro në ${g.lineId}`, `Resta su ${g.lineId}`),
          detail: t(lang, `toward ${g.towards}`, `προς ${g.towards}`, `drejt ${g.towards}`, `verso ${g.towards}`),
          sub: t(lang, `${g.stopsRemaining} stops · next ${g.nextStation}`, `${g.stopsRemaining} στάσεις · επόμενη ${g.nextStation}`, `${g.stopsRemaining} ndalesa · tjetra ${g.nextStation}`, `${g.stopsRemaining} fermate · prossima ${g.nextStation}`),
        };
      case 'getOffNext':
        return {
          icon: '🚪', headline: t(lang, 'Get off next', 'Αποβίβαση στην επόμενη', 'Zbrit në tjetrën', 'Scendi alla prossima'),
          detail: g.isDestination ? g.nextStation : (g.transferTo ? t(lang, `${g.nextStation} → change to ${g.transferTo}`, `${g.nextStation} → αλλαγή σε ${g.transferTo}`, `${g.nextStation} → ndërro në ${g.transferTo}`, `${g.nextStation} → cambia in ${g.transferTo}`) : g.nextStation),
          sub: g.isDestination ? t(lang, 'Your destination is next', 'Ο προορισμός σου είναι η επόμενη', 'Destinacioni yt është tjetra', 'La tua destinazione è la prossima') : t(lang, `Next stop: ${g.nextStation}`, `Επόμενη στάση: ${g.nextStation}`, `Ndalesa tjetër: ${g.nextStation}`, `Prossima fermata: ${g.nextStation}`),
        };
      case 'transfer':
        return {
          icon: '🔄', headline: t(lang, 'Change here', 'Αλλαγή εδώ', 'Ndërro këtu', 'Cambia qui'),
          detail: t(lang, `${g.atStation} → ${g.toLineId} toward ${g.towards}`, `${g.atStation} → ${g.toLineId} προς ${g.towards}`, `${g.atStation} → ${g.toLineId} drejt ${g.towards}`, `${g.atStation} → ${g.toLineId} verso ${g.towards}`),
          sub: '',
        };
      case 'arrived':
        return {
          icon: '✓', headline: t(lang, 'Arrived', 'Έφτασες', 'Mbërritët', 'Arrivato'), detail: g.station, sub: '',
        };
      default:
        return { icon: '', headline: '', detail: '', sub: '' };
    }
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  // Mount the panel into `container`. `opts`: { language, lineColor(lineId)->css }.
  function mount(container, journey, opts) {
    opts = opts || {};
    const lang = opts.language || 'en';
    const color = (id) => (opts.lineColor ? opts.lineColor(id) || '#0072CE' : '#0072CE');
    const coords = opts.coords || {}; // { [stopId]: {lat, lon} } for live advance
    const hasCoords = Object.keys(coords).length > 0;
    let pos = { legIndex: 0, stopIndex: 0 };
    let live = false;
    let watchId = null;
    const alertedLegs = new Set();

    const origin = journey.legs[0] && journey.legs[0].stops[0] ? journey.legs[0].stops[0].name : '';
    const destLeg = journey.legs[journey.legs.length - 1];
    const dest = destLeg ? destLeg.stops[destLeg.stops.length - 1].name : '';

    // Persistent structure so screen readers hear every instruction change: a
    // visually-hidden aria-live region whose text is updated on each render (a
    // re-created live region would not reliably announce), plus a content div the
    // card re-renders into.
    container.innerHTML = '';
    const srEl = document.createElement('div');
    srEl.className = 'go-sr';
    srEl.setAttribute('aria-live', 'assertive');
    srEl.setAttribute('role', 'status');
    srEl.style.cssText = 'position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;';
    const contentEl = document.createElement('div');
    container.appendChild(srEl);
    container.appendChild(contentEl);

    function progress() {
      const total = Math.max(1, journey.legs.reduce((n, l) => n + Math.max(0, l.stops.length - 1), 0));
      let done = 0;
      for (let i = 0; i < pos.legIndex; i++) done += Math.max(0, journey.legs[i].stops.length - 1);
      done += pos.stopIndex;
      return Math.min(1, done / total);
    }

    function render() {
      const g = GO.guidance(journey, pos);
      const alert = GO.shouldAlertGetOff(journey, pos);
      const arrived = GO.isArrived(journey, pos);
      const d = describe(g, lang);
      const tint = g.kind === 'arrived' ? '#2E7D32' : color(g.lineId || (journey.legs[pos.legIndex] && journey.legs[pos.legIndex].lineId));
      const canBack = pos.legIndex > 0 || pos.stopIndex > 0;

      // Announce the current instruction to screen readers (headline + detail).
      srEl.textContent = [d.headline, d.detail, d.sub].filter(Boolean).join('. ');

      contentEl.innerHTML = `
        <div class="go-panel" role="group" aria-label="GO journey guidance" style="max-width:420px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;">
          <div class="go-head" style="display:flex;gap:8px;align-items:center;color:#666;font-size:14px;font-weight:600;margin-bottom:12px;">
            <span>${esc(origin)}</span><span aria-hidden="true">→</span><span>${esc(dest)}</span>
          </div>
          <div class="go-card" style="border-radius:18px;padding:20px;background:${alert ? tint : tint + '1f'};color:${alert ? '#fff' : '#111'};">
            <div style="font-size:22px;font-weight:800;display:flex;gap:10px;align-items:center;">
              <span aria-hidden="true">${d.icon}</span><span>${esc(d.headline)}</span>
            </div>
            ${d.detail ? `<div style="font-size:17px;font-weight:700;margin-top:8px;">${esc(d.detail)}</div>` : ''}
            ${d.sub ? `<div style="font-size:14px;opacity:.85;margin-top:4px;">${esc(d.sub)}</div>` : ''}
          </div>
          <div class="go-progress" style="height:6px;border-radius:3px;background:#e5e5e5;margin:14px 0;overflow:hidden;">
            <div style="height:100%;width:${Math.round(progress() * 100)}%;background:${tint};"></div>
          </div>
          <div class="go-controls" style="display:flex;gap:10px;">
            <button class="go-back" ${canBack ? '' : 'disabled'} style="flex:1;padding:12px;border-radius:12px;border:1px solid #ccc;background:#fff;font-weight:600;">${esc(t(lang, 'Back', 'Πίσω', 'Prapa', 'Indietro'))}</button>
            <button class="go-next" style="flex:1;padding:12px;border-radius:12px;border:0;background:${tint};color:#fff;font-weight:700;">${arrived ? esc(t(lang, 'Restart', 'Επανεκκίνηση', 'Rifillo', 'Ricomincia')) : esc(t(lang, 'Next stop', 'Επόμενη στάση', 'Ndalesa tjetër', 'Fermata succ.'))}</button>
          </div>
          ${(hasCoords && typeof navigator !== 'undefined' && navigator.geolocation) ? `
          <button class="go-live" style="width:100%;box-sizing:border-box;margin-top:10px;padding:10px;border-radius:12px;border:1px solid ${live ? '#2E7D32' : '#ccc'};background:${live ? 'rgba(46,125,50,.08)' : '#fff'};font-weight:600;color:${live ? '#2E7D32' : '#333'};">
            ${live ? '● ' + esc(t(lang, 'Live guidance on', 'Ζωντανή καθοδήγηση ενεργή', 'Udhëzim i drejtpërdrejtë aktiv', 'Guida dal vivo attiva')) : esc(t(lang, 'Start live guidance', 'Έναρξη ζωντανής καθοδήγησης', 'Nis udhëzimin e drejtpërdrejtë', 'Avvia guida dal vivo'))}
          </button>` : ''}
        </div>`;

      const back = contentEl.querySelector('.go-back');
      const next = contentEl.querySelector('.go-next');
      const liveBtn = contentEl.querySelector('.go-live');
      if (back) back.onclick = () => { api.back(); };
      if (next) next.onclick = () => { arrived ? api.reset() : api.advance(); };
      if (liveBtn) liveBtn.onclick = () => { live ? api.stopLive() : api.startLive(); };
    }

    const api = {
      advance() { if (!GO.isArrived(journey, pos)) { pos = GO.advance(journey, pos); render(); } },
      back() {
        if (pos.stopIndex > 0) pos = { legIndex: pos.legIndex, stopIndex: pos.stopIndex - 1 };
        else if (pos.legIndex > 0) { const p = pos.legIndex - 1; pos = { legIndex: p, stopIndex: journey.legs[p].stops.length - 1 }; }
        render();
      },
      reset() { pos = { legIndex: 0, stopIndex: 0 }; alertedLegs.clear(); render(); },
      position() { return pos; },
      // Live GO: feed a GPS fix; auto-advance the position and fire onGetOff once
      // per leg when the rider is one stop from a leg's alight point.
      applyLocation(lat, lon) {
        if (!hasCoords) return;
        const np = GO.advancedPosition(journey, pos, coords, lat, lon);
        if (np.legIndex !== pos.legIndex || np.stopIndex !== pos.stopIndex) { pos = np; render(); }
        if (GO.shouldAlertGetOff(journey, pos) && !alertedLegs.has(pos.legIndex)) {
          alertedLegs.add(pos.legIndex);
          if (opts.onGetOff) { try { opts.onGetOff(GO.guidance(journey, pos)); } catch (_) {} }
        }
      },
      startLive() {
        if (live || typeof navigator === 'undefined' || !navigator.geolocation || !hasCoords) return;
        live = true; render();
        watchId = navigator.geolocation.watchPosition(
          (fx) => api.applyLocation(fx.coords.latitude, fx.coords.longitude),
          () => {},
          { enableHighAccuracy: true, maximumAge: 5000 }
        );
      },
      stopLive() {
        live = false;
        if (watchId != null && typeof navigator !== 'undefined' && navigator.geolocation) navigator.geolocation.clearWatch(watchId);
        watchId = null;
        render();
      },
      isLive() { return live; },
    };
    render();
    return api;
  }

  return { mount, describe };
});
