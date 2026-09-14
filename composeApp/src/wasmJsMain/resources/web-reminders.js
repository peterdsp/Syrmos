'use strict';
// Syrmos 3.0 Phase N J09 user-enabled leave-by reminders (web reference).
//
// Mirrors Kotlin com.syrmos.core.common.LeaveByReminder / LeaveByReminders
// exactly. One pure engine computes the leave-by moment (departure minus the
// rider's walk+buffer lead), classifies the reminder state, and reconciles a
// desired reminder set against what is scheduled so duplicates collapse and a
// changed or removed departure cancels/replaces its pending notification.
//
// Pure and offline: arithmetic against an epoch-second clock the caller passes.
// Covered case-for-case by fixtures/reminders/leave-by.json.
//
// UMD: `require('./web-reminders.js')` in node, `window.SyrmosReminders` in browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosReminders = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  function idFor(lineId, stationId, departureEpochSeconds) {
    return lineId + '|' + stationId + '|' + departureEpochSeconds;
  }

  function reminderId(r) { return idFor(r.lineId, r.stationId, r.departureEpochSeconds); }

  function leaveByEpochSeconds(r) {
    return r.departureEpochSeconds - Math.max(0, r.leadSeconds);
  }

  function state(r, nowEpochSeconds) {
    if (nowEpochSeconds >= r.departureEpochSeconds) return 'departed';
    if (nowEpochSeconds >= leaveByEpochSeconds(r)) return 'leaveNow';
    return 'scheduled';
  }

  // Epoch second the notification should fire, or null once that moment passed.
  function fireAtEpochSeconds(r, nowEpochSeconds) {
    const lb = leaveByEpochSeconds(r);
    return lb > nowEpochSeconds ? lb : null;
  }

  // Whole minutes until the rider must leave, rounded up; 0 once leave-by passed.
  function minutesUntilLeave(r, nowEpochSeconds) {
    const secs = leaveByEpochSeconds(r) - nowEpochSeconds;
    if (secs <= 0) return 0;
    return Math.floor((secs + 59) / 60);
  }

  // Collapse duplicates by id, keeping the last occurrence (matches Kotlin's
  // associateBy last-wins).
  function dedupe(reminders) {
    const byId = new Map();
    for (const r of reminders) byId.set(reminderId(r), r);
    return Array.from(byId.values());
  }

  // Reminders still worth a pending notification: not yet departed.
  function active(reminders, nowEpochSeconds) {
    return dedupe(reminders).filter((r) => state(r, nowEpochSeconds) !== 'departed');
  }

  function reconcile(current, desired, nowEpochSeconds) {
    const desiredActive = active(desired, nowEpochSeconds);
    const currentById = new Map(dedupe(current).map((r) => [reminderId(r), r]));
    const desiredById = new Map(desiredActive.map((r) => [reminderId(r), r]));

    const toSchedule = [];
    const unchanged = [];
    for (const d of desiredActive) {
      const existing = currentById.get(reminderId(d));
      if (existing && leaveByEpochSeconds(existing) === leaveByEpochSeconds(d)) unchanged.push(d);
      else toSchedule.push(d);
    }

    const toCancel = [];
    for (const [id, cur] of currentById) {
      const d = desiredById.get(id);
      if (!d || leaveByEpochSeconds(d) !== leaveByEpochSeconds(cur)) toCancel.push(id);
    }

    return { toSchedule, toCancel, unchanged };
  }

  // ---- Saved-departure board store (Phase N J09) --------------------------
  // Pure list ops over saved departures plus the byte-parity persistence blob
  // that mirrors Kotlin ReminderContract / SavedDepartureStore exactly (validated
  // against fixtures/reminders/saved.json). A saved departure is
  // { lineId, stationId, stationName, destination, scheduledTime,
  //   departureEpochSeconds, leadSeconds, createdAt, schemaVersion }.
  const SCHEMA_VERSION = 1;

  function savedId(d) { return idFor(d.lineId, d.stationId, d.departureEpochSeconds); }

  // Prepend + dedupe by id; a re-save moves the id to the top and replaces it.
  function saveDeparture(list, entry) {
    return [entry].concat(list.filter((d) => savedId(d) !== savedId(entry)));
  }

  function removeDeparture(list, id) { return list.filter((d) => savedId(d) !== id); }

  function containsDeparture(list, id) { return list.some((d) => savedId(d) === id); }

  function pruneDeparted(list, nowEpochSeconds) {
    return list.filter((d) => d.departureEpochSeconds > nowEpochSeconds);
  }

  function reorderDepartures(list, order) {
    const byId = new Map(list.map((d) => [savedId(d), d]));
    return order.map((id) => byId.get(id)).filter((d) => d != null);
  }

  // A saved departure maps 1:1 onto the reminder shape the engine consumes.
  function toReminders(list) {
    return list.map((d) => ({
      lineId: d.lineId, stationId: d.stationId, stationName: d.stationName,
      destination: d.destination, scheduledTime: d.scheduledTime,
      departureEpochSeconds: d.departureEpochSeconds, leadSeconds: d.leadSeconds,
    }));
  }

  // Canonical wire form: fixed key order, compact (no spaces), matching the
  // Kotlin serializer so the persisted blob round-trips byte-for-byte.
  function encodeRoot(root) {
    const items = (root.savedDepartures || []).map((d) => ({
      lineId: d.lineId,
      stationId: d.stationId,
      stationName: d.stationName,
      destination: d.destination,
      scheduledTime: d.scheduledTime,
      departureEpochSeconds: d.departureEpochSeconds,
      leadSeconds: d.leadSeconds,
      createdAt: d.createdAt,
      schemaVersion: d.schemaVersion == null ? SCHEMA_VERSION : d.schemaVersion,
    }));
    return JSON.stringify({
      schemaVersion: root.schemaVersion == null ? SCHEMA_VERSION : root.schemaVersion,
      savedDepartures: items,
    });
  }

  // Returns { ok, value } | { unsupported, foundVersion } | { corrupt, reason }.
  // A blank blob is a fresh, valid empty list (not corrupt).
  function decodeRoot(raw) {
    if (raw == null || String(raw).trim() === '') {
      return { ok: true, value: { schemaVersion: SCHEMA_VERSION, savedDepartures: [] } };
    }
    let parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return { corrupt: true, reason: String(e) }; }
    const v = parsed && parsed.schemaVersion;
    if (typeof v === 'number' && v > SCHEMA_VERSION) return { unsupported: true, foundVersion: v };
    return {
      ok: true,
      value: {
        schemaVersion: v == null ? SCHEMA_VERSION : v,
        savedDepartures: Array.isArray(parsed.savedDepartures) ? parsed.savedDepartures : [],
      },
    };
  }

  return {
    idFor, reminderId, leaveByEpochSeconds, state, fireAtEpochSeconds,
    minutesUntilLeave, dedupe, active, reconcile,
    SCHEMA_VERSION, savedId, saveDeparture, removeDeparture, containsDeparture,
    pruneDeparted, reorderDepartures, toReminders, encodeRoot, decodeRoot,
  };
});
