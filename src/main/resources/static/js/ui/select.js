(function () {
    const ENHANCED = 'prettySelectReady';
    const SELECTOR = 'select:not([multiple]):not([data-native-select])';
    let openSelect = null;

    document.addEventListener('DOMContentLoaded', function () {
        enhanceSelects(document);
        document.addEventListener('click', handleDocumentClick);
        document.addEventListener('keydown', handleDocumentKeydown);
    });

    window.SmartRentSelect = {
        refresh: function (root) {
            enhanceSelects(root || document);
            const target = root && root.matches && root.matches(SELECTOR) ? root : null;
            if (target) syncSelect(target);
        }
    };

    function enhanceSelects(root) {
        const scope = root && root.querySelectorAll ? root : document;
        const selects = root && root.matches && root.matches(SELECTOR)
            ? [root]
            : Array.from(scope.querySelectorAll(SELECTOR));

        selects.forEach(function (select) {
            if (select.dataset[ENHANCED] === 'true') {
                syncSelect(select);
                return;
            }
            enhanceSelect(select);
        });
    }

    function enhanceSelect(select) {
        const wrapper = document.createElement('span');
        const originalClass = select.getAttribute('class') || '';
        wrapper.className = originalClass ? `pretty-select ${originalClass}` : 'pretty-select';

        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'pretty-select-trigger';
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        const label = document.createElement('span');
        label.className = 'pretty-select-label';
        trigger.appendChild(label);

        const arrow = document.createElement('i');
        arrow.setAttribute('data-lucide', 'chevron-down');
        arrow.className = 'pretty-select-arrow';
        trigger.appendChild(arrow);

        const menu = document.createElement('div');
        menu.className = 'pretty-select-menu';
        menu.setAttribute('role', 'listbox');

        select.parentNode.insertBefore(wrapper, select);
        wrapper.appendChild(select);
        wrapper.appendChild(trigger);
        wrapper.appendChild(menu);

        select.classList.add('pretty-select-source');
        select.dataset[ENHANCED] = 'true';

        trigger.addEventListener('click', function (event) {
            event.preventDefault();
            if (select.disabled) return;
            toggleSelect(wrapper);
        });

        select.addEventListener('change', function () {
            syncSelect(select);
        });

        observeSelect(select);
        syncSelect(select);
        refreshIcons(wrapper);
    }

    function observeSelect(select) {
        const observer = new MutationObserver(function () {
            syncSelect(select);
        });
        observer.observe(select, {
            attributes: true,
            attributeFilter: ['disabled'],
            childList: true,
            subtree: true
        });
    }

    function syncSelect(select) {
        const wrapper = select.closest('.pretty-select');
        if (!wrapper) return;

        const trigger = wrapper.querySelector('.pretty-select-trigger');
        const label = wrapper.querySelector('.pretty-select-label');
        const menu = wrapper.querySelector('.pretty-select-menu');
        const selected = select.options[select.selectedIndex];
        const selectedText = selected ? selected.textContent : '';

        wrapper.classList.toggle('is-disabled', select.disabled);
        if (trigger) {
            trigger.disabled = select.disabled;
            trigger.setAttribute('aria-expanded', wrapper.classList.contains('is-open') ? 'true' : 'false');
        }
        if (label) {
            label.textContent = selectedText || '';
            label.classList.toggle('is-placeholder', !select.value);
        }
        if (!menu) return;

        menu.innerHTML = '';
        Array.from(select.options).forEach(function (option) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'pretty-select-option';
            item.setAttribute('role', 'option');
            item.setAttribute('aria-selected', option.selected ? 'true' : 'false');
            item.dataset.value = option.value;
            item.textContent = option.textContent;
            item.disabled = option.disabled;
            if (option.selected) item.classList.add('is-selected');
            if (!option.value) item.classList.add('is-placeholder');
            item.addEventListener('click', function () {
                if (option.disabled) return;
                select.value = option.value;
                select.dispatchEvent(new Event('input', { bubbles: true }));
                select.dispatchEvent(new Event('change', { bubbles: true }));
                closeSelect(wrapper);
            });
            menu.appendChild(item);
        });
    }

    function toggleSelect(wrapper) {
        if (wrapper.classList.contains('is-open')) {
            closeSelect(wrapper);
            return;
        }
        if (openSelect && openSelect !== wrapper) closeSelect(openSelect);
        wrapper.classList.add('is-open');
        const trigger = wrapper.querySelector('.pretty-select-trigger');
        if (trigger) trigger.setAttribute('aria-expanded', 'true');
        openSelect = wrapper;
    }

    function closeSelect(wrapper) {
        if (!wrapper) return;
        wrapper.classList.remove('is-open');
        const trigger = wrapper.querySelector('.pretty-select-trigger');
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
        if (openSelect === wrapper) openSelect = null;
    }

    function handleDocumentClick(event) {
        if (!openSelect || openSelect.contains(event.target)) return;
        closeSelect(openSelect);
    }

    function handleDocumentKeydown(event) {
        if (event.key !== 'Escape') return;
        closeSelect(openSelect);
    }

    function refreshIcons(scope) {
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }
})();
