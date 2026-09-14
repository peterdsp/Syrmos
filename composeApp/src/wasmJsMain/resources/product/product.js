/*
 * Syrmos product showcase behaviour (/product/). Progressive enhancement only:
 * the page is complete without this file. It never registers the service
 * worker, never touches the transit app's storage, and loads no app code.
 */
(function () {
    "use strict";
    var root = document.documentElement;
    var motion = root.classList.contains("motion");
    window.syrmosProductReady = true;

    // --- Sticky header: slightly more opaque once the page scrolls. ----------
    var header = document.querySelector(".site-header");
    if (header) {
        var onScroll = function () { header.classList.toggle("is-scrolled", window.scrollY > 8); };
        window.addEventListener("scroll", onScroll, { passive: true });
        onScroll();
    }

    // --- Compact menu: close after choosing a link, on Escape, or outside. ---
    var menu = document.querySelector(".menu");
    if (menu) {
        menu.addEventListener("click", function (e) {
            if (e.target.closest && e.target.closest(".menu__panel a")) menu.removeAttribute("open");
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && menu.hasAttribute("open")) {
                menu.removeAttribute("open");
                menu.querySelector("summary").focus();
            }
        });
        document.addEventListener("click", function (e) {
            if (menu.hasAttribute("open") && !menu.contains(e.target)) menu.removeAttribute("open");
        });
    }

    var hasIO = "IntersectionObserver" in window;

    // --- Reveals + rail thread: once, as content enters the viewport. --------
    if (motion && hasIO) {
        var revealIO = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                entry.target.classList.add(entry.target.matches("[data-reveal]") ? "is-in" : "is-drawn");
                revealIO.unobserve(entry.target);
            });
        }, { rootMargin: "0px 0px -10% 0px", threshold: 0.08 });
        document.querySelectorAll("[data-reveal], .railed").forEach(function (el) { revealIO.observe(el); });
    } else {
        document.querySelectorAll("[data-reveal]").forEach(function (el) { el.classList.add("is-in"); });
        document.querySelectorAll(".railed").forEach(function (el) { el.classList.add("is-drawn"); });
        root.classList.remove("motion");
    }

    // --- Story: sticky frame crossfades to the step in view (wide + motion). -
    var story = document.querySelector("[data-story]");
    if (story && hasIO && motion && window.matchMedia) {
        var wide = window.matchMedia("(min-width: 1024px)");
        var steps = story.querySelectorAll(".story__step");
        var slides = story.querySelectorAll(".story__slide");
        var setActive = function (index) {
            steps.forEach(function (s, i) { s.classList.toggle("is-active", i === index); });
            slides.forEach(function (s, i) { s.classList.toggle("is-active", i === index); });
        };
        var storyIO = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) setActive(Number(entry.target.getAttribute("data-step")));
            });
        }, { rootMargin: "-45% 0px -45% 0px", threshold: 0 });
        var applyLayout = function () {
            var on = wide.matches;
            story.classList.toggle("is-sticky", on);
            if (on) {
                setActive(0);
                steps.forEach(function (s) { storyIO.observe(s); });
            } else {
                storyIO.disconnect();
            }
        };
        if (wide.addEventListener) wide.addEventListener("change", applyLayout);
        else if (wide.addListener) wide.addListener(applyLayout);
        applyLayout();
    }

    // --- Tabs (feature explorer + regions): WAI-ARIA tabs, manual rotation. --
    document.querySelectorAll("[data-tabs]").forEach(function (group) {
        var tabs = Array.prototype.slice.call(group.querySelectorAll('[role="tab"]'));
        var panels = tabs.map(function (t) { return document.getElementById(t.getAttribute("aria-controls")); });
        var select = function (index, focus) {
            tabs.forEach(function (tab, i) {
                var on = i === index;
                tab.setAttribute("aria-selected", on ? "true" : "false");
                tab.tabIndex = on ? 0 : -1;
                if (!panels[i]) return;
                panels[i].hidden = !on;
                panels[i].classList.remove("is-entering");
                if (on && motion) {
                    void panels[i].offsetWidth; // restart the short entrance animation
                    panels[i].classList.add("is-entering");
                }
            });
            if (focus) tabs[index].focus();
        };
        tabs.forEach(function (tab, i) {
            tab.addEventListener("click", function () { select(i, false); });
            tab.addEventListener("keydown", function (e) {
                var next = null;
                if (e.key === "ArrowRight") next = (i + 1) % tabs.length;
                else if (e.key === "ArrowLeft") next = (i - 1 + tabs.length) % tabs.length;
                else if (e.key === "Home") next = 0;
                else if (e.key === "End") next = tabs.length - 1;
                if (next === null) return;
                e.preventDefault();
                select(next, true);
            });
        });
        var initial = tabs.findIndex(function (t) { return t.getAttribute("aria-selected") === "true"; });
        select(initial < 0 ? 0 : initial, false);
    });

    // --- Service worker hygiene. This page never registers the app's worker
    // (a fresh visitor must not download the schedule dataset). If an older
    // worker already controls this visit, ask it to update right away so the
    // route-aware navigation caching in the current sw.js takes over.
    if ("serviceWorker" in navigator && navigator.serviceWorker.controller) {
        navigator.serviceWorker.getRegistration().then(function (reg) {
            if (reg) reg.update();
        }).catch(function () { /* best effort */ });
    }
})();
