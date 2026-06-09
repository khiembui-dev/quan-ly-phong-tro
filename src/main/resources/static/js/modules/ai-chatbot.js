(function () {
  const maxLength = 1000;
  const sourceLabel = 'Dữ liệu từ hệ thống SmartRent';
  const guideLabel = 'Hướng dẫn SmartRent';

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-ai-chatbot]').forEach(initChatbot);
  });

  function initChatbot(root) {
    const role = window.location.pathname.startsWith('/admin') ? 'ADMIN' : 'CUSTOMER';
    const storageKey = 'smartrent-ai-chat-' + role.toLowerCase();
    const conversationKey = 'smartrent-ai-conversation-' + role.toLowerCase();
    const panel = root.querySelector('[data-ai-panel]');
    const openButton = root.querySelector('[data-ai-open]');
    const closeButton = root.querySelector('[data-ai-close]');
    const newButton = root.querySelector('[data-ai-new]');
    const messagesNode = root.querySelector('[data-ai-messages]');
    const form = root.querySelector('[data-ai-form]');
    const input = root.querySelector('[data-ai-input]');
    const sendButton = root.querySelector('[data-ai-send]');
    const limit = root.querySelector('[data-ai-limit]');
    const customerSuggestions = root.querySelector('[data-ai-suggestions]');
    const adminSuggestions = root.querySelector('[data-ai-admin-suggestions]');
    let conversationId = window.localStorage.getItem(conversationKey) || null;
    let messages = readStoredMessages(storageKey);
    let loading = false;

    root.dataset.aiRole = role;
    if (adminSuggestions && customerSuggestions) {
      adminSuggestions.hidden = role !== 'ADMIN';
      customerSuggestions.hidden = role === 'ADMIN';
    }

    if (!messages.length) messages = [welcomeMessage(role)];
    render();
    loadConversation();

    openButton?.addEventListener('click', function () { setOpen(true); });
    closeButton?.addEventListener('click', function () { setOpen(false); });
    newButton?.addEventListener('click', function () {
      conversationId = null;
      window.localStorage.removeItem(conversationKey);
      messages = [welcomeMessage(role)];
      persist();
      render();
      input?.focus();
    });
    root.querySelectorAll('[data-ai-suggestion]').forEach(function (button) {
      button.addEventListener('click', function () {
        sendMessage(button.dataset.aiSuggestion || button.textContent || '');
      });
    });
    input?.addEventListener('input', function () {
      resizeInput();
      updateLimit();
    });
    input?.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        form?.requestSubmit();
      }
    });
    form?.addEventListener('submit', function (event) {
      event.preventDefault();
      sendMessage(input?.value || '');
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setOpen(false);
    });

    function setOpen(open) {
      if (!panel) return;
      panel.hidden = !open;
      panel.classList.toggle('is-open', open);
      openButton?.classList.toggle('is-hidden', open);
      if (open) window.setTimeout(function () { input?.focus(); scrollBottom(); }, 60);
    }

    async function loadConversation() {
      if (!conversationId) return;
      try {
        const response = await fetch('/api/ai/conversations/' + encodeURIComponent(conversationId), {
          method: 'GET',
          credentials: 'same-origin',
          headers: { Accept: 'application/json' }
        });
        if (!response.ok) throw new Error('conversation_unavailable');
        const data = await response.json();
        if (Array.isArray(data.messages) && data.messages.length) {
          messages = data.messages.map(function (item) {
            return {
              role: item.sender === 'USER' ? 'user' : 'assistant',
              text: item.content || ''
            };
          });
          persist();
          render();
        }
      } catch (error) {
        window.localStorage.removeItem(conversationKey);
        conversationId = null;
      }
    }

    async function sendMessage(raw) {
      const text = String(raw || '').trim();
      if (!text || loading) return;
      if (text.length > maxLength) {
        addMessage('assistant', 'Tin nhắn tối đa 1000 ký tự.');
        return;
      }
      addMessage('user', text);
      if (input) input.value = '';
      resizeInput();
      updateLimit();
      setLoading(true);
      try {
        const response = await fetch('/api/ai/chat', {
          method: 'POST',
          credentials: 'same-origin',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ message: text, conversationId: conversationId })
        });
        if (!response.ok) throw new Error('http_' + response.status);
        const data = await response.json();
        if (!data || data.success === false) throw new Error('ai_failed');
        conversationId = data.conversationId || conversationId;
        if (conversationId) window.localStorage.setItem(conversationKey, conversationId);
        const responseSource = data.sourceSummary || data.source || (data.dataFound ? sourceLabel : guideLabel);
        addMessage('assistant', data.message || 'AI đang bận, vui lòng thử lại.', responseSource);
        renderSuggestions(data.suggestions || []);
      } catch (error) {
        addMessage('assistant', error.message && error.message.startsWith('http_')
          ? 'Không thể kết nối máy chủ.'
          : 'AI đang bận, vui lòng thử lại.');
      } finally {
        setLoading(false);
      }
    }

    function addMessage(roleName, text, sourceSummary) {
      messages.push({
        role: roleName,
        text: text,
        sourceSummary: roleName === 'assistant' ? sourceSummary || null : null
      });
      messages = messages.slice(-40);
      persist();
      render();
    }

    function render() {
      if (!messagesNode) return;
      messagesNode.innerHTML = '';
      messages.forEach(function (message) {
        const item = document.createElement('div');
        item.className = 'sr-ai-message is-' + (message.role === 'user' ? 'user' : 'assistant');
        const bubble = document.createElement('div');
        bubble.className = 'sr-ai-bubble';
        bubble.textContent = message.text;
        if (message.role !== 'user' && message.sourceSummary) {
          const source = document.createElement('small');
          source.className = 'sr-ai-source';
          source.textContent = message.sourceSummary;
          bubble.appendChild(source);
        }
        item.appendChild(bubble);
        messagesNode.appendChild(item);
      });
      if (loading) {
        const typing = document.createElement('div');
        typing.className = 'sr-ai-message is-assistant';
        typing.innerHTML = '<div class="sr-ai-bubble sr-ai-typing"><small>Đang kiểm tra dữ liệu hệ thống...</small><span></span><span></span><span></span></div>';
        messagesNode.appendChild(typing);
      }
      scrollBottom();
      window.lucide?.createIcons?.();
    }

    function renderSuggestions(suggestions) {
      const target = role === 'ADMIN' ? adminSuggestions : customerSuggestions;
      if (!target || !Array.isArray(suggestions) || !suggestions.length) return;
      target.innerHTML = '';
      suggestions.slice(0, role === 'ADMIN' ? 6 : 8).forEach(function (suggestion) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = suggestion;
        button.dataset.aiSuggestion = suggestion;
        button.addEventListener('click', function () { sendMessage(suggestion); });
        target.appendChild(button);
      });
    }

    function setLoading(value) {
      loading = value;
      if (sendButton) sendButton.disabled = value;
      if (input) input.disabled = value;
      render();
    }

    function resizeInput() {
      if (!input) return;
      input.style.height = 'auto';
      input.style.height = Math.min(input.scrollHeight, 120) + 'px';
    }

    function updateLimit() {
      if (limit && input) limit.textContent = input.value.length + '/1000';
    }

    function scrollBottom() {
      if (messagesNode) messagesNode.scrollTop = messagesNode.scrollHeight;
    }

    function persist() {
      window.localStorage.setItem(storageKey, JSON.stringify(messages));
    }
  }

  function welcomeMessage(role) {
    return {
      role: 'assistant',
      text: role === 'ADMIN'
        ? 'Xin chào, tôi có thể hỗ trợ quản lý phòng, khách thuê, hóa đơn, doanh thu, điện nước, ticket và hướng dẫn thao tác trên SmartRent.'
        : 'Xin chào, tôi có thể hỗ trợ tài khoản, phòng đang thuê, hóa đơn, thanh toán QR, điện nước, ticket, thông báo và hướng dẫn sử dụng SmartRent.'
    };
  }

  function readStoredMessages(key) {
    try {
      const parsed = JSON.parse(window.localStorage.getItem(key) || '[]');
      return Array.isArray(parsed) ? parsed.filter(function (item) {
        return item && typeof item.text === 'string';
      }).map(function (item) {
        return {
          role: item.role === 'user' ? 'user' : 'assistant',
          text: item.text,
          sourceSummary: typeof item.sourceSummary === 'string' ? item.sourceSummary : null
        };
      }) : [];
    } catch (error) {
      return [];
    }
  }
})();
