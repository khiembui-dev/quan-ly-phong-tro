// Optimistic favorite toggle. Wired from data-room-id attribute on the heart button.
window.glassToggleFavorite = async function (btn) {
    const roomId = btn.getAttribute('data-room-id');
    if (!roomId) return;
    const icon = btn.querySelector('i,svg');
    const wasActive = btn.classList.contains('is-active');
    btn.classList.toggle('is-active', !wasActive);
    if (icon) {
        icon.style.fill = wasActive ? 'none' : 'currentColor';
        icon.style.color = wasActive ? '' : '#dc2626';
    }
    try {
        const res = await fetch(`/api/v1/favorites/${roomId}`, {
            method: 'POST',
            credentials: 'include'
        });
        if (!res.ok) throw new Error('http_' + res.status);
    } catch (e) {
        // revert
        btn.classList.toggle('is-active', wasActive);
        if (icon) {
            icon.style.fill = wasActive ? 'currentColor' : 'none';
            icon.style.color = wasActive ? '#dc2626' : '';
        }
        if (window.toast) window.toast('Không thể lưu yêu thích. Đăng nhập?', 'err');
    }
};
