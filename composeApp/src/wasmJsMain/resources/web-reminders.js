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

  return {
    idFor, reminderId, leaveByEpochSeconds, state, fireAtEpochSeconds,
    minutesUntilLeave, dedupe, active, reconcile,
  };
});
