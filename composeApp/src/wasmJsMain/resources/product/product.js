/*
 * Syrmos product page behaviour (/product/). Progressive enhancement only:
 * the page is complete without this file. It never registers the service
 * worker, never touches the transit app's storage, and loads no app code.
 */
(function () {
    "use strict";
    var root = document.documentElement;
    var motion = root.classList.contains("motion");
    window.syrmosProductReady = true;

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

    // --- Reveals: once, as content enters the viewport. ----------------------
    var reveals = document.querySelectorAll("[data-reveal]");
    if (motion && "IntersectionObserver" in window) {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                entry.target.classList.add("is-in");
                io.unobserve(entry.target);
            });
        }, { rootMargin: "0px 0px -10% 0px", threshold: 0.08 });
        reveals.forEach(function (el) { io.observe(el); });
    } else {
        reveals.forEach(function (el) { el.classList.add("is-in"); });
    }

    // --- Showcase: three real screens, WAI-ARIA tabs with manual rotation. ---
    var tabs = Array.prototype.slice.call(document.querySelectorAll(".plate__tab"));
    var shots = document.querySelectorAll(".plate__picture [data-shot]");
    var selectShot = function (index, focus) {
        tabs.forEach(function (tab, i) {
            var on = i === index;
            tab.setAttribute("aria-selected", on ? "true" : "false");
            tab.tabIndex = on ? 0 : -1;
            var panel = document.getElementById(tab.getAttribute("aria-controls"));
            if (panel) panel.hidden = !on;
        });
        shots.forEach(function (shot, i) {
            var on = i === index;
            shot.hidden = !on;
            shot.classList.remove("is-entering");
            if (on && motion) { void shot.offsetWidth; shot.classList.add("is-entering"); }
        });
        if (focus) tabs[index].focus();
    };
    tabs.forEach(function (tab, i) {
        tab.addEventListener("click", function () { selectShot(i, false); });
        tab.addEventListener("keydown", function (e) {
            var next = null;
            if (e.key === "ArrowRight") next = (i + 1) % tabs.length;
            else if (e.key === "ArrowLeft") next = (i - 1 + tabs.length) % tabs.length;
            else if (e.key === "Home") next = 0;
            else if (e.key === "End") next = tabs.length - 1;
            if (next === null) return;
            e.preventDefault();
            selectShot(next, true);
        });
    });

    // --- Coverage: choose a region and see its network on the real map. -----
    var map = document.getElementById("regionMap");
    var regions = document.querySelectorAll(".region");
    var ALT = {
        athens: "The Athens network on the Syrmos map.",
        thessaloniki: "The Thessaloniki region on the Syrmos map, with suburban lines leaving the city.",
        patras: "The Patras suburban line along the coast on the Syrmos map.",
        national: "National rail corridors across mainland Greece on the Syrmos map, replacement buses dotted."
    };
    var showRegion = function (name) {
        regions.forEach(function (r) {
            r.querySelector(".region__name").setAttribute("aria-pressed", r.getAttribute("data-region") === name ? "true" : "false");
        });
        if (!map) return;
        var base = "/product/img/region-" + name;
        if (map.getAttribute("src") === base + "-480.webp") return;
        map.classList.add("is-swapping");
        var next = new Image();
        next.onload = next.onerror = function () {
            map.srcset = base + "-480.webp 480w, " + base + "-960.webp 960w";
            map.src = base + "-480.webp";
            map.alt = ALT[name] || "";
            map.classList.remove("is-swapping");
        };
        next.src = base + "-480.webp";
    };
    regions.forEach(function (r) {
        var name = r.getAttribute("data-region");
        var button = r.querySelector(".region__name");
        button.addEventListener("click", function () { showRegion(name); });
        if (window.matchMedia && window.matchMedia("(hover: hover) and (pointer: fine)").matches) {
            r.addEventListener("mouseenter", function () { showRegion(name); });
        }
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
