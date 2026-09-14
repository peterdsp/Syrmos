'use strict';
// Syrmos 3.0 Phase N J09 leave-by reminder runtime (web).
//
// The web peer of the Android AlarmManager scheduler and the iOS
// UNUserNotificationCenter scheduler. It persists the saved-departure board in
// localStorage (the same byte-parity blob as the other clients, via the pure
// web-reminders.js store) and, while the tab is open, schedules a foreground
// setTimeout for each active reminder's leave-by moment. When one fires it shows
// a browser Notification if the user has granted permission, and always emits a
// `syrmos:leave-by` event so the app can show an in-page cue too.
//
// Web has no background execution: reminders only fire while a Syrmos tab is
// open. That is the honest limit of the platform; the board and the opt-in are
// shared, and scheduling is driven by the same LeaveByReminders.reconcile engine
// as the native clients, so what to schedule/cancel never diverges.
//
// UMD: require in node (tests inject a fake localStorage/Notification), or
// window.SyrmosRemindersRuntime in the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./web-reminders.js'));
  } else {
    root.SyrmosRemindersRuntime = factory(root.SyrmosReminders);
  }
})(typeof self !== 'undefined' ? self : this, function (R) {
  const STORE_KEY = 'syrmos.saved-departures.v1';
  const OPTIN_KEY = 'syrmos.notif.leaveBy';

  // Injectable seams so node tests can drive the runtime without a browser.
  const env = {
    storage: (typeof localStorage !== 'undefined') ? localStorage : null,
    now: () => Math.floor(Date.now() / 1000),
    setTimeout: (fn, ms) => setTimeout(fn, ms),
    clearTimeout: (h) => clearTimeout(h),
    notify: defaultNotify,
  };

  const timers = new Map(); // reminder id -> timeout handle
  const listeners = [];     // change listeners (board/opt-in changed)

  function defaultNotify(reminder) {
    const title = 'Leave now: ' + reminder.lineId;
    const bodyParts = [reminder.stationName];
    if (reminder.destination) bodyParts.push('to ' + reminder.destination);
    if (reminder.scheduledTime) bodyParts.push(reminder.scheduledTime);
    const body = bodyParts.join(' · ');
    try {
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        // eslint-disable-next-line no-new
        new Notification(title, { body: body, tag: 'syrmos.leaveby.' + R.reminderId(reminder) });
      }
    } catch (_) { /* Notification unavailable: the event below still fires. */ }
  }

  function emitEvent(name, detail) {
    try {
      if (typeof window !== 'undefined' && window.dispatchEvent) {
        window.dispatchEvent(new CustomEvent(name, { detail: detail }));
      }
    } catch (_) {}
  }

  function readRaw(key, dflt) {
    try { const v = env.storage && env.storage.getItem(key); return v == null ? dflt : v; } catch (_) { return dflt; }
  }
  function writeRaw(key, value) {
    try { env.storage && env.storage.setItem(key, value); } catch (_) {}
  }

  function loadList() {
    const res = R.decodeRoot(readRaw(STORE_KEY, ''));
    return res && res.ok ? res.value.savedDepartures : [];
  }
  function persistList(list) {
    writeRaw(STORE_KEY, R.encodeRoot({ schemaVersion: R.SCHEMA_VERSION, savedDepartures: list }));
  }

  function isEnabled() { return readRaw(OPTIN_KEY, '0') === '1'; }
  function setEnabled(on) { writeRaw(OPTIN_KEY, on ? '1' : '0'); sync(); notifyListeners(); }

  function list() { return loadList(); }
  function contains(id) { return R.containsDeparture(loadList(), id); }

  // Adding a departure is the opt-in on the native clients too: turn the master
  // switch on so the very first reminder actually fires.
  function add(departure) {
    persistList(R.saveDeparture(loadList(), departure));
    writeRaw(OPTIN_KEY, '1');
    requestPermission();
    sync();
    notifyListeners();
  }
  function remove(id) {
    persistList(R.removeDeparture(loadList(), id));
    sync();
    notifyListeners();
  }

  function requestPermission() {
    try {
      if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
        Notification.requestPermission();
      }
    } catch (_) {}
  }

  function onChange(fn) { if (typeof fn === 'function') listeners.push(fn); }
  function notifyListeners() { for (const fn of listeners) { try { fn(); } catch (_) {} } }

  function clearTimers() {
    for (const h of timers.values()) env.clearTimeout(h);
    timers.clear();
  }

  // Reconcile the desired reminders against the live timers and (re)arm only
  // what is needed. Prunes departed entries from the board first so a stale tab
  // self-heals. Returns the number of armed timers (handy for tests).
  function sync() {
    clearTimers();
    const nowS = env.now();

    // Self-heal: drop trains that already left.
    const stored = loadList();
    const pruned = R.pruneDeparted(stored, nowS);
    if (pruned.length !== stored.length) persistList(pruned);

    if (!isEnabled()) return 0;
    const active = R.active(R.toReminders(pruned), nowS);
    for (const r of active) {
      const fireAt = R.fireAtEpochSeconds(r, nowS);
      if (fireAt == null) continue; // leave-by already passed: never arm into the past
      const ms = Math.max(0, (fireAt - nowS) * 1000);
      const id = R.reminderId(r);
      timers.set(id, env.setTimeout(function () { fire(r); }, ms));
    }
    return timers.size;
  }

  function fire(reminder) {
    timers.delete(R.reminderId(reminder));
    env.notify(reminder);
    emitEvent('syrmos:leave-by', { reminder: reminder });
  }

  function init() {
    requestPermission();
    sync();
    // Re-arm on focus in case the board changed in another tab, or the machine
    // slept through a timer.
    try {
      if (typeof window !== 'undefined' && window.addEventListener) {
        window.addEventListener('focus', sync);
        window.addEventListener('storage', function (e) {
          if (!e || e.key === STORE_KEY || e.key === OPTIN_KEY) { sync(); notifyListeners(); }
        });
      }
    } catch (_) {}
  }

  return {
    init, list, add, remove, contains, isEnabled, setEnabled, onChange,
    syncNow: sync, _env: env, _timerCount: () => timers.size,
  };
});
