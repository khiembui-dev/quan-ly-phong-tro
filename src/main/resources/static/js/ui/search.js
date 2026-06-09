(function () {
    const ui = window.SmartRentUI = window.SmartRentUI || {};

    ui.debounce = ui.debounce || function (fn, delay = 250) {
        let timer;
        return function (...args) {
            window.clearTimeout(timer);
            timer = window.setTimeout(() => fn.apply(this, args), delay);
        };
    };

    ui.search = {
        init(root = document) {
            root.querySelectorAll('[data-search-input]').forEach((input) => {
                if (input.dataset.searchBound) return;
                input.dataset.searchBound = 'true';
                const container = input.closest('.search-box, .glass-search, .input-glass') || input.parentElement;
                const clear = container ? container.querySelector('[data-search-clear]') : null;
                const update = () => {
                    const hasValue = Boolean(input.value && input.value.trim());
                    if (container) container.classList.toggle('has-value', hasValue);
                    if (clear) clear.hidden = !hasValue;
                };
                input.addEventListener('input', update);
                input.addEventListener('focus', () => container && container.classList.add('is-focused'));
                input.addEventListener('blur', () => container && container.classList.remove('is-focused'));
                if (clear) {
                    clear.addEventListener('click', () => {
                        input.value = '';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.focus();
                    });
                }
                update();
            });
            root.querySelectorAll('[data-auto-search-form]').forEach((form) => {
                if (form.dataset.autoSearchBound) return;
                form.dataset.autoSearchBound = 'true';
                const submit = ui.debounce(() => {
                    if (!form.isConnected) return;
                    if (typeof form.requestSubmit === 'function') form.requestSubmit();
                    else form.submit();
                }, Number(form.dataset.autoSearchDelay || 450));

                form.querySelectorAll('[data-auto-search-input], input[name="q"]').forEach((input) => {
                    input.addEventListener('input', submit);
                });

                form.querySelectorAll('[data-auto-search-change], select').forEach((control) => {
                    control.addEventListener('change', () => {
                        if (typeof form.requestSubmit === 'function') form.requestSubmit();
                        else form.submit();
                    });
                });
            });
        }
    };

    document.addEventListener('DOMContentLoaded', () => ui.search.init());
    document.addEventListener('htmx:afterSwap', (event) => ui.search.init(event.target));
})();
