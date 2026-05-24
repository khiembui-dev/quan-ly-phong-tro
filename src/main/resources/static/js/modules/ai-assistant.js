// Alpine component for the AI assistant floating button (customer + admin AI page).
// Loaded globally via app.js — Alpine reads window.aiAssistant().

window.aiAssistant = function () {
    return {
        open: false,
        loading: false,
        q: '',
        msgs: [
            { role: 'assistant', text: 'Xin chào! Tôi là SmartRent AI, tư vấn phòng trọ. Bạn cần tìm gì?' }
        ],

        quickAsk(text) {
            this.q = text;
            this.submit();
        },

        async submit() {
            const text = (this.q || '').trim();
            if (!text || this.loading) return;
            this.msgs.push({ role: 'user', text });
            this.q = '';
            this.loading = true;
            try {
                const res = await fetch('/api/v1/ai/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ prompt: text, context: window.__AI_CONTEXT__ || 'CUSTOMER' })
                });
                if (!res.ok) throw new Error('http_' + res.status);
                const data = await res.json();
                const reply = (data && data.data && data.data.reply) || 'Xin lỗi, hệ thống đang bận.';
                this.msgs.push({ role: 'assistant', text: reply });
            } catch (e) {
                console.warn(e);
                this.msgs.push({ role: 'assistant', text: 'Có lỗi kết nối. Vui lòng thử lại sau.' });
            } finally {
                this.loading = false;
                this.$nextTick(() => {
                    if (this.$refs.stream) this.$refs.stream.scrollTop = this.$refs.stream.scrollHeight;
                });
            }
        }
    };
};
