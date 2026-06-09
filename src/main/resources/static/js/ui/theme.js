(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};
    const key = 'smartrent-theme';

    function apply(theme) {
        const dark = theme === 'dark';
        document.documentElement.classList.toggle('dark', dark);
        document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
            button.setAttribute('aria-pressed', String(dark));
        });
    }

    ui.theme = {
        init(root = document) {
            apply(localStorage.getItem(key) || 'light');
            root.querySelectorAll('[data-theme-toggle]').forEach((button) => {
                if (button.dataset.themeBound) return;
                button.dataset.themeBound = 'true';
                button.addEventListener('click', () => {
                    const next = document.documentElement.classList.contains('dark') ? 'light' : 'dark';
                    localStorage.setItem(key, next);
                    apply(next);
                });
            });
        },
        apply
    };

    document.addEventListener('DOMContentLoaded', () => ui.theme.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.theme.init(event.target));
})();
