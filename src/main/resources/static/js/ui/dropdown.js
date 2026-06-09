(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    function closeAll(except) {
        document.querySelectorAll('[data-dropdown].is-open').forEach((dropdown) => {
            if (dropdown !== except) {
                dropdown.classList.remove('is-open');
                const trigger = dropdown.querySelector('[data-dropdown-trigger]');
                if (trigger) trigger.setAttribute('aria-expanded', 'false');
            }
        });
    }

    ui.dropdown = {
        init(root = document) {
            root.querySelectorAll('[data-dropdown]').forEach((dropdown) => {
                if (dropdown.dataset.dropdownBound) return;
                dropdown.dataset.dropdownBound = 'true';
                const trigger = dropdown.querySelector('[data-dropdown-trigger]');
                if (!trigger) return;
                trigger.setAttribute('aria-haspopup', 'menu');
                trigger.setAttribute('aria-expanded', 'false');
                trigger.addEventListener('click', (event) => {
                    event.stopPropagation();
                    const open = !dropdown.classList.contains('is-open');
                    closeAll(dropdown);
                    dropdown.classList.toggle('is-open', open);
                    trigger.setAttribute('aria-expanded', String(open));
                });
            });
        },
        closeAll
    };

    document.addEventListener('click', () => closeAll());
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeAll();
    });
    document.addEventListener('DOMContentLoaded', () => ui.dropdown.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.dropdown.init(event.target));
})();
