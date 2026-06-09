(function () {
    function normalizeLang(value) {
        return String(value || '').slice(0, 2).toLowerCase() === 'en' ? 'en' : 'vi';
    }

    function initialLang() {
        try {
            const stored = localStorage.getItem('SMARTRENT_LANG');
            if (stored === 'vi' || stored === 'en') return stored;
        } catch (_) {}
        return normalizeLang(document.documentElement.lang || 'vi');
    }

    const state = {
        lang: initialLang(),
        messages: {},
        ready: false
    };

    function readPath(obj, path) {
        if (obj && Object.prototype.hasOwnProperty.call(obj, path)) return obj[path];
        return String(path || '').split('.').reduce(function (node, part) {
            return node && Object.prototype.hasOwnProperty.call(node, part) ? node[part] : undefined;
        }, obj);
    }

    function format(value, params) {
        if (value == null) return '';
        return String(value).replace(/\{(\w+)\}/g, function (_, key) {
            return params && params[key] != null ? params[key] : '';
        });
    }

    function t(key, params, fallback) {
        const value = readPath(state.messages, key);
        if (value == null) return fallback != null ? fallback : key;
        return format(value, params || {});
    }

    function applyKeyAttributes(root) {
        root.querySelectorAll('[data-i18n]').forEach(function (el) {
            el.textContent = t(el.dataset.i18n, {}, el.textContent);
        });
        root.querySelectorAll('[data-i18n-placeholder]').forEach(function (el) {
            el.setAttribute('placeholder', t(el.dataset.i18nPlaceholder, {}, el.getAttribute('placeholder') || ''));
        });
        root.querySelectorAll('[data-i18n-title]').forEach(function (el) {
            el.setAttribute('title', t(el.dataset.i18nTitle, {}, el.getAttribute('title') || ''));
        });
        root.querySelectorAll('[data-i18n-aria-label]').forEach(function (el) {
            el.setAttribute('aria-label', t(el.dataset.i18nAriaLabel, {}, el.getAttribute('aria-label') || ''));
        });
    }

    function applyLegacyText(root) {
        const legacy = state.messages.legacy || {};
        const textMap = legacy.text || {};
        const attrMap = legacy.attributes || {};
        const walker = document.createTreeWalker(root.body || root, NodeFilter.SHOW_TEXT, {
            acceptNode: function (node) {
                const parent = node.parentElement;
                if (!parent || ['SCRIPT', 'STYLE', 'TEXTAREA'].includes(parent.tagName)) return NodeFilter.FILTER_REJECT;
                return node.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }
        });
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        nodes.forEach(function (node) {
            const raw = node.nodeValue;
            const trimmed = raw.trim();
            if (!textMap[trimmed]) return;
            node.nodeValue = raw.replace(trimmed, textMap[trimmed]);
        });
        ['placeholder', 'title', 'aria-label', 'alt'].forEach(function (attr) {
            root.querySelectorAll('[' + attr + ']').forEach(function (el) {
                const current = el.getAttribute(attr);
                if (current && attrMap[current]) el.setAttribute(attr, attrMap[current]);
            });
        });
    }

    function apply(root) {
        const target = root || document;
        applyKeyAttributes(target);
        if (state.lang !== 'vi') applyLegacyText(target);
    }

    async function load() {
        try {
            const response = await fetch('/i18n/' + state.lang + '.json', { cache: 'no-store' });
            state.messages = response.ok ? await response.json() : {};
        } catch (_) {
            state.messages = {};
        }
        state.ready = true;
        try { localStorage.setItem('SMARTRENT_LANG', state.lang); } catch (_) {}
        document.documentElement.lang = state.lang;
        apply(document);
        window.dispatchEvent(new CustomEvent('smartrent:i18n-ready', { detail: { lang: state.lang } }));
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('a[href*="lang="]');
        if (!link) return;
        try {
            const url = new URL(link.href, window.location.origin);
            const lang = url.searchParams.get('lang');
            if (lang === 'vi' || lang === 'en') localStorage.setItem('SMARTRENT_LANG', lang);
        } catch (_) {}
    });

    document.addEventListener('htmx:afterSwap', function (event) {
        if (state.ready) apply(event.target);
    });

    window.SmartRentI18n = { t, apply, state };
    window.t = t;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', load, { once: true });
    } else {
        load();
    }
})();
