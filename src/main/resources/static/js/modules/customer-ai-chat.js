(function () {
    const panel = document.querySelector('[data-customer-chat-panel]');
    if (!panel) return;

    const messages = panel.querySelector('[data-customer-chat-messages]');
    const form = panel.querySelector('[data-customer-chat-form]');
    const input = panel.querySelector('[data-customer-chat-input]');
    const reply = 'Tính năng AI đang được phát triển, vui lòng gửi ticket hoặc liên hệ chủ trọ.';

    function setOpen(open) {
        panel.hidden = !open;
        panel.classList.toggle('is-open', open);
        if (open) window.setTimeout(() => input && input.focus(), 60);
    }

    function addMessage(text, role) {
        if (!messages || !text) return;
        const item = document.createElement('div');
        item.className = role === 'user' ? 'is-user' : 'is-assistant';
        item.textContent = text;
        messages.appendChild(item);
        messages.scrollTop = messages.scrollHeight;
    }

    function submit(text) {
        const clean = (text || '').trim();
        if (!clean) return;
        addMessage(clean, 'user');
        window.setTimeout(() => addMessage(reply, 'assistant'), 250);
    }

    document.querySelectorAll('[data-customer-chat-open]').forEach((button) => {
        button.addEventListener('click', () => setOpen(true));
    });
    panel.querySelector('[data-customer-chat-close]')?.addEventListener('click', () => setOpen(false));
    panel.querySelectorAll('[data-customer-chat-suggestion]').forEach((button) => {
        button.addEventListener('click', () => submit(button.dataset.customerChatSuggestion));
    });
    form?.addEventListener('submit', (event) => {
        event.preventDefault();
        submit(input?.value);
        if (input) input.value = '';
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') setOpen(false);
    });
})();
