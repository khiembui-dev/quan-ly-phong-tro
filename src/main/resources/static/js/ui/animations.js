(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    ui.animations = {
        init(root = document) {
            const items = Array.from(root.querySelectorAll('[data-animate]'));
            if (!items.length) return;
            if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                items.forEach((el) => el.classList.add('animate-fade-up'));
                return;
            }
            const observer = new IntersectionObserver((entries) => {
                entries.forEach((entry) => {
                    if (!entry.isIntersecting) return;
                    entry.target.classList.add(entry.target.getAttribute('data-animate') || 'animate-fade-up');
                    observer.unobserve(entry.target);
                });
            }, { threshold: 0.08 });
            items.forEach((el) => observer.observe(el));
        }
    };

    document.addEventListener('DOMContentLoaded', () => ui.animations.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.animations.init(event.target));
})();
