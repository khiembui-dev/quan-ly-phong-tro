// Optimistic favorite toggle. Wired from data-room-id attribute on the heart button.
window.glassToggleFavorite = async function (btn) {
    const roomId = btn.getAttribute('data-room-id');
    if (!roomId) return;
    const icon = btn.querySelector('i,svg');
    const wasActive = btn.classList.contains('is-active');
    btn.classList.toggle('is-active', !wasActive);
    btn.setAttribute('aria-pressed', String(!wasActive));
    if (icon) {
        icon.style.fill = wasActive ? 'none' : 'currentColor';
        icon.style.color = wasActive ? '' : '#dc2626';
    }
    try {
        const csrf = document.querySelector('meta[name="csrf-token"]')?.content || '';
        const res = await fetch(`/me/favorites/${roomId}`, {
            method: 'POST',
            credentials: 'include',
            headers: csrf ? {
                'X-CSRF-TOKEN': csrf,
                'X-XSRF-TOKEN': csrf,
                'Accept': 'application/json'
            } : {
                'Accept': 'application/json'
            },
            keepalive: true
        });
        if (!res.ok) throw new Error('http_' + res.status);
        const payload = await res.json().catch(function () { return null; });
        const isFavorite = !!(payload && payload.data && payload.data.favorite);
        btn.classList.toggle('is-active', isFavorite);
        btn.setAttribute('aria-pressed', String(isFavorite));
        if (icon) {
            icon.style.fill = isFavorite ? 'currentColor' : 'none';
            icon.style.color = isFavorite ? '#dc2626' : '';
        }
    } catch (e) {
        // revert
        btn.classList.toggle('is-active', wasActive);
        btn.setAttribute('aria-pressed', String(wasActive));
        if (icon) {
            icon.style.fill = wasActive ? 'currentColor' : 'none';
            icon.style.color = wasActive ? '#dc2626' : '';
        }
        if (window.toast) window.toast('Không thể lưu yêu thích. Đăng nhập?', 'err');
    }
};
