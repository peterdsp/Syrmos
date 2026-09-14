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
  const AJ = (typeof require === 'function') ? require('./web-active-journey.js') : root.SyrmosActiveJourney;

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
    // Persistent live session (S06): when a store is supplied the panel resumes from
    // (and writes every step to) the persisted ActiveJourney, so progress survives a
    // reload / navigation. Without a store it stays in-memory (used by unit tests).
    const store = (opts.store && AJ) ? opts.store : null;
    // Phase R S07: per-transfer risk (transferRisks[i] = leg i -> i+1) + a callback
    // that opens fresh results from the current station to the destination.
    const transferRisks = opts.transferRisks || [];
    const onFindAlternatives = opts.onFindAlternatives || null;
    const nowISO = () => new Date().toISOString();
    let active = null;
    if (store) {
      active = (opts.active && AJ.isResumable(opts.active)) ? opts.active : store.get();
      if (!active && opts.option) { active = AJ.start(AJ.newId(), opts.option, journey, nowISO()); store.set(active); }
    }
    let pos = active ? AJ.positionOf(active, journey) : { legIndex: 0, stopIndex: 0 };
    let live = false;
    let watchId = null;
    let locDenied = false; // Phase R S10: geolocation permission denied
    const alertedLegs = new Set();

    function persist(source) {
      if (store && active) { active = AJ.withPosition(active, journey, pos, nowISO(), source || 'manual'); store.set(active); }
    }

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

    // Journey completion from the one shared cross-client definition (web-go.js).
    function progress() { return GO.progress(journey, pos); }

    function render() {
      const g = GO.guidance(journey, pos);
      const alert = GO.shouldAlertGetOff(journey, pos);
      const arrived = GO.isArrived(journey, pos);
      const d = describe(g, lang);
      const tint = g.kind === 'arrived' ? '#2E7D32' : color(g.lineId || (journey.legs[pos.legIndex] && journey.legs[pos.legIndex].lineId));
      const canBack = pos.legIndex > 0 || pos.stopIndex > 0;

      // Phase R S07: inline connection-risk warning at a tight/missed transfer.
      const transferMoment = g.kind === 'transfer' || (g.kind === 'getOffNext' && g.transferTo);
      const rr = (transferMoment && transferRisks[pos.legIndex]) ? transferRisks[pos.legIndex] : null;
      const risk = (rr && (rr.status === 'tight' || rr.status === 'missed')) ? rr : null;
      let riskHtml = '';
      if (risk) {
        const missed = risk.status === 'missed';
        const rTitle = missed
          ? t(lang, 'This connection may be missed', 'Αυτή η ανταπόκριση μπορεί να χαθεί', 'Kjo lidhje mund të humbasë', 'Questa coincidenza potrebbe saltare')
          : t(lang, 'This connection is tight', 'Αυτή η ανταπόκριση είναι στενή', 'Kjo lidhje është e ngushtë', 'Questa coincidenza è stretta');
        const mn = (s) => (s == null ? null : Math.max(0, Math.round(s / 60)));
        const avail = mn(risk.availableSeconds), allow = mn(risk.minimumSeconds);
        const expl = (avail != null && allow != null)
          ? t(lang, `${avail} min available; allow ${allow} min to change.`, `${avail} λεπ διαθέσιμα, χρειάζονται ${allow} λεπ για αλλαγή.`, `${avail} min në dispozicion, duhen ${allow} min për ndërrim.`, `${avail} min disponibili, servono ${allow} min per cambiare.`)
          : '';
        riskHtml = `
          <div class="go-risk" style="border-radius:14px;padding:16px;margin-top:12px;background:rgba(230,126,34,.12);">
            <div style="font-weight:700;color:#c05a00;">⚠ ${esc(rTitle)}</div>
            ${expl ? `<div style="font-size:14px;opacity:.8;margin-top:4px;">${esc(expl)}</div>` : ''}
            ${onFindAlternatives ? `<button class="go-alt" style="margin-top:8px;padding:8px 12px;border-radius:10px;border:1px solid #e08a3c;background:transparent;color:#c05a00;font-weight:600;cursor:pointer;">${esc(t(lang, 'Find alternatives', 'Βρες εναλλακτικές', 'Gjej alternativa', 'Trova alternative'))}</button>` : ''}
          </div>`;
      }

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
          ${riskHtml}
          <div class="go-progress" style="height:6px;border-radius:3px;background:#e5e5e5;margin:14px 0;overflow:hidden;">
            <div style="height:100%;width:${Math.round(progress() * 100)}%;background:${tint};"></div>
          </div>
          <div class="go-controls" style="display:flex;gap:10px;">
            <button class="go-back" ${canBack ? '' : 'disabled'} style="flex:1;padding:12px;border-radius:12px;border:1px solid #ccc;background:#fff;font-weight:600;">${esc(t(lang, 'Back', 'Πίσω', 'Prapa', 'Indietro'))}</button>
            <button class="go-next" style="flex:1;padding:12px;border-radius:12px;border:0;background:${tint};color:#fff;font-weight:700;">${arrived ? esc(t(lang, 'Restart', 'Επανεκκίνηση', 'Rifillo', 'Ricomincia')) : esc(t(lang, 'Next stop', 'Επόμενη στάση', 'Ndalesa tjetër', 'Fermata succ.'))}</button>
          </div>
          ${locDenied ? `
          <div class="go-loc-denied" style="margin-top:10px;padding:12px;border-radius:12px;background:rgba(120,130,150,.14);">
            <div style="font-weight:600;">${esc(t(lang, 'Location is off', 'Η τοποθεσία είναι ανενεργή', 'Vendndodhja është joaktive', 'La posizione è disattivata'))}</div>
            <div style="font-size:13px;opacity:.75;margin-top:2px;">${esc(t(lang, 'Keep stepping through your journey manually.', 'Συνέχισε τη διαδρομή χειροκίνητα.', 'Vazhdo udhëtimin manualisht.', 'Continua il viaggio manualmente.'))}</div>
          </div>` :
          (hasCoords && typeof navigator !== 'undefined' && navigator.geolocation) ? `
          <button class="go-live" style="width:100%;box-sizing:border-box;margin-top:10px;padding:10px;border-radius:12px;border:1px solid ${live ? '#2E7D32' : '#ccc'};background:${live ? 'rgba(46,125,50,.08)' : '#fff'};font-weight:600;color:${live ? '#2E7D32' : '#333'};">
            ${live ? '● ' + esc(t(lang, 'Live guidance on', 'Ζωντανή καθοδήγηση ενεργή', 'Udhëzim i drejtpërdrejtë aktiv', 'Guida dal vivo attiva')) : esc(t(lang, 'Start live guidance', 'Έναρξη ζωντανής καθοδήγησης', 'Nis udhëzimin e drejtpërdrejtë', 'Avvia guida dal vivo'))}
          </button>` : ''}
          ${store ? `
          <button class="go-end" style="width:100%;box-sizing:border-box;margin-top:10px;padding:10px;border-radius:12px;border:1px solid #ccc;background:#fff;font-weight:600;color:#666;">
            ${esc(arrived ? t(lang, 'Finish journey', 'Ολοκλήρωση', 'Përfundo udhëtimin', 'Concludi viaggio') : t(lang, 'End journey', 'Τέλος διαδρομής', 'Përfundo udhëtimin', 'Termina viaggio'))}
          </button>` : ''}
        </div>`;

      const back = contentEl.querySelector('.go-back');
      const next = contentEl.querySelector('.go-next');
      const liveBtn = contentEl.querySelector('.go-live');
      const endBtn = contentEl.querySelector('.go-end');
      if (back) back.onclick = () => { api.back(); };
      if (next) next.onclick = () => { arrived ? api.reset() : api.advance(); };
      if (liveBtn) liveBtn.onclick = () => { live ? api.stopLive() : api.startLive(); };
      if (endBtn) endBtn.onclick = () => { api.end(); };
      const altBtn = contentEl.querySelector('.go-alt');
      if (altBtn && onFindAlternatives) altBtn.onclick = () => {
        const leg = journey.legs[pos.legIndex];
        const fromId = (leg && leg.stops[pos.stopIndex]) ? leg.stops[pos.stopIndex].id : null;
        const dLeg = journey.legs[journey.legs.length - 1];
        const toId = dLeg ? dLeg.stops[dLeg.stops.length - 1].id : null;
        onFindAlternatives(fromId, toId);
      };
    }

    const api = {
      advance() { if (!GO.isArrived(journey, pos)) { pos = GO.advance(journey, pos); persist('manual'); render(); } },
      back() {
        if (pos.stopIndex > 0) pos = { legIndex: pos.legIndex, stopIndex: pos.stopIndex - 1 };
        else if (pos.legIndex > 0) { const p = pos.legIndex - 1; pos = { legIndex: p, stopIndex: journey.legs[p].stops.length - 1 }; }
        persist('manual'); render();
      },
      reset() { pos = { legIndex: 0, stopIndex: 0 }; alertedLegs.clear(); persist('manual'); render(); },
      // End the live session: clear the persisted trip and hand control back so no
      // stale journey is offered again. In-memory panels just fire the callback.
      end() { if (store) store.clear(); if (opts.onEnd) { try { opts.onEnd(); } catch (_) {} } },
      // The persisted session (or null when the panel runs in-memory).
      session() { return active; },
      position() { return pos; },
      // Live GO: feed a GPS fix; auto-advance the position and fire onGetOff once
      // per leg when the rider is one stop from a leg's alight point.
      applyLocation(lat, lon) {
        if (!hasCoords) return;
        const np = GO.advancedPosition(journey, pos, coords, lat, lon);
        if (np.legIndex !== pos.legIndex || np.stopIndex !== pos.stopIndex) { pos = np; persist('gps'); render(); }
        if (GO.shouldAlertGetOff(journey, pos) && !alertedLegs.has(pos.legIndex)) {
          alertedLegs.add(pos.legIndex);
          if (opts.onGetOff) { try { opts.onGetOff(GO.guidance(journey, pos)); } catch (_) {} }
        }
      },
      startLive() {
        if (live || typeof navigator === 'undefined' || !navigator.geolocation || !hasCoords) return;
        live = true; render();
        watchId = navigator.geolocation.watchPosition(
          (fx) => { locDenied = false; api.applyLocation(fx.coords.latitude, fx.coords.longitude); },
          (err) => {
            // Permission denied changes capability, not availability: keep manual
            // stepping usable and disclose it honestly (S10).
            if (err && err.code === 1) { live = false; locDenied = true; render(); }
          },
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
