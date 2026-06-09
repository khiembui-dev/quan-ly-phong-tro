(function () {
  const pollIntervalMs = 5000;
  const fallbackSessionMs = 30 * 60 * 1000;

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-payment-checkout]').forEach(initPaymentCheckout);
  });

  function initPaymentCheckout(page) {
    const checkUrl = page.dataset.checkUrl || '';
    const copyButtons = page.querySelectorAll('[data-copy-target]');
    const startButton = page.querySelector('[data-payment-start]');
    const checkButtons = page.querySelectorAll('[data-payment-check]');
    const state = page.querySelector('#payment-check-state');
    const actions = page.querySelector('[data-payment-actions]');
    const successBox = page.querySelector('[data-payment-success]');
    const countdown = page.querySelector('[data-payment-countdown]');
    const expiryCard = page.querySelector('[data-payment-expiry]');
    const expiresAtMs = parseExpiry(page.dataset.paymentExpiresAt);
    const expiredUrl = page.dataset.paymentExpiredUrl || '/me/invoices';
    let pollingTimer = null;
    let expiryTimer = null;
    let checking = false;
    let expired = false;

    startExpiryCountdown();

    copyButtons.forEach(function (button) {
      button.addEventListener('click', function () {
        const target = document.getElementById(button.dataset.copyTarget);
        const text = target ? target.textContent.trim() : '';
        copyText(text);
      });
    });

    startButton?.addEventListener('click', function () {
      startPolling();
      checkPayment();
    });

    checkButtons.forEach(function (button) {
      button.addEventListener('click', function () {
        startPolling();
        checkPayment();
      });
    });

    if (page.dataset.paymentPaid === 'true') {
      setPaid({
        message: 'Thanh toán thành công.',
        amount: textOf(page.querySelector('[data-paid-amount]')),
        paidAt: textOf(page.querySelector('[data-paid-at]'))
      });
    } else if (checkUrl && !isExpired()) {
      startPolling();
      checkPayment();
    }

    async function checkPayment() {
      if (!checkUrl || checking || expired) return;
      checking = true;
      setChecking();
      try {
        const response = await fetch(checkUrl, {
          method: 'GET',
          headers: { 'Accept': 'application/json' },
          credentials: 'same-origin'
        });
        if (!response.ok) {
          setBankError();
          return;
        }
        const payload = await response.json();
        if (payload.status === 'PAID') {
          stopPolling();
          setPaid(payload);
          return;
        }
        if (payload.status === 'BANK_API_ERROR') {
          setBankError(payload.message);
          return;
        }
        setPending(payload.message);
      } catch (error) {
        setBankError();
      } finally {
        checking = false;
        setButtonsLoading(false);
      }
    }

    function startPolling() {
      if (pollingTimer || !checkUrl || expired) return;
      pollingTimer = window.setInterval(function () {
        if (isExpired()) {
          stopPolling();
          expireCheckout();
          return;
        }
        checkPayment();
      }, pollIntervalMs);
    }

    function stopPolling() {
      if (!pollingTimer) return;
      window.clearInterval(pollingTimer);
      pollingTimer = null;
    }

    function startExpiryCountdown() {
      updateCountdown();
      if (page.dataset.paymentPaid === 'true') return;
      expiryTimer = window.setInterval(updateCountdown, 1000);
    }

    function updateCountdown() {
      const remainingMs = expiresAtMs - Date.now();
      if (remainingMs <= 0) {
        if (countdown) countdown.textContent = '00:00';
        expireCheckout();
        return;
      }
      if (countdown) countdown.textContent = formatCountdown(remainingMs);
      if (expiryCard) {
        expiryCard.classList.toggle('is-warning', remainingMs <= 5 * 60 * 1000);
      }
    }

    function expireCheckout() {
      if (expired || page.dataset.paymentPaid === 'true') return;
      expired = true;
      stopPolling();
      if (expiryTimer) {
        window.clearInterval(expiryTimer);
        expiryTimer = null;
      }
      setButtonsLoading(false);
      disableActions();
      setState('is-error', 'timer-off', 'Phiên thanh toán đã hết hạn', 'Trang thanh toán sẽ đóng. Nếu đã chuyển khoản, mở lại hóa đơn để hệ thống kiểm tra tiếp.');
      updateStatusBadges('PENDING', 'is-pending');
      window.setTimeout(function () {
        try {
          window.close();
        } catch (error) {
          // Browsers only allow script-opened tabs to close themselves.
        }
        if (!window.closed) {
          window.location.assign(expiredUrl);
        }
      }, 5000);
    }

    function setChecking() {
      setButtonsLoading(true);
      setState('is-checking', 'loader-circle', 'Đang kiểm tra giao dịch...', 'Hệ thống đang đối soát lịch sử giao dịch ngân hàng.');
    }

    function setPending(message) {
      setState('is-pending', 'clock-3', 'Chưa tìm thấy giao dịch', message || 'Chưa tìm thấy giao dịch, vui lòng chờ 1-3 phút.');
    }

    function setBankError(message) {
      setState('is-error', 'triangle-alert', 'API ngân hàng đang lỗi', message || 'Chưa thể kiểm tra ngân hàng, vui lòng thử lại.');
    }

    function setPaid(payload) {
      const fallbackAmount = textOf(page.querySelector('[data-paid-amount]'));
      const amount = payload.amount != null ? formatVnd(payload.amount) : fallbackAmount;
      const paidAt = payload.paidAt ? formatDateTime(payload.paidAt) : textOf(page.querySelector('[data-paid-at]'));
      setState('is-paid', 'badge-check', 'Thanh toán thành công', payload.message || 'Thanh toán thành công.');
      updateStatusBadges('PAID', 'is-paid');
      page.dataset.paymentPaid = 'true';
      if (successBox) {
        successBox.classList.add('is-visible');
        const amountNode = successBox.querySelector('[data-paid-amount]');
        const paidAtNode = successBox.querySelector('[data-paid-at]');
        if (amountNode && amount) amountNode.textContent = amount;
        if (paidAtNode && paidAt) paidAtNode.textContent = paidAt;
      }
      if (actions) actions.hidden = true;
      if (expiryTimer) window.clearInterval(expiryTimer);
      window.lucide?.createIcons?.();
    }

    function updateStatusBadges(text, statusClass) {
      page.querySelectorAll('[data-payment-status]').forEach(function (badge) {
        badge.classList.remove('is-pending', 'is-paid', 'is-overdue', 'is-failed');
        badge.classList.add(statusClass);
        badge.textContent = text;
      });
    }

    function setState(statusClass, iconName, title, message) {
      if (!state) return;
      state.classList.remove('is-pending', 'is-checking', 'is-paid', 'is-error');
      state.classList.add(statusClass);
      const icon = state.querySelector('[data-payment-state-icon]');
      const titleNode = state.querySelector('[data-payment-state-title]');
      const messageNode = state.querySelector('[data-payment-state-message]');
      if (icon) icon.setAttribute('data-lucide', iconName);
      if (titleNode) titleNode.textContent = title;
      if (messageNode) messageNode.textContent = message;
      window.lucide?.createIcons?.();
    }

    function setButtonsLoading(loading) {
      page.querySelectorAll('[data-payment-check], [data-payment-start]').forEach(function (button) {
        button.classList.toggle('is-loading', loading);
        button.disabled = loading || expired;
      });
    }

    function disableActions() {
      page.querySelectorAll('[data-payment-check], [data-payment-start]').forEach(function (button) {
        button.disabled = true;
        button.classList.remove('is-loading');
      });
    }

    function isExpired() {
      return Date.now() >= expiresAtMs;
    }
  }

  async function copyText(text) {
    if (!text) return;
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        fallbackCopy(text);
      }
      window.toast?.('Đã sao chép.', 'ok');
    } catch (error) {
      fallbackCopy(text);
      window.toast?.('Đã sao chép.', 'ok');
    }
  }

  function fallbackCopy(text) {
    const input = document.createElement('textarea');
    input.value = text;
    input.setAttribute('readonly', '');
    input.style.position = 'fixed';
    input.style.opacity = '0';
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    input.remove();
  }

  function formatVnd(value) {
    const number = parseMoney(value);
    return number.toLocaleString('vi-VN') + 'đ';
  }

  function parseMoney(value) {
    if (typeof value === 'number') return Number.isFinite(value) ? value : 0;
    if (value == null) return 0;
    const digits = String(value).replace(/[^\d-]/g, '');
    if (!digits || digits === '-') return 0;
    const parsed = Number(digits);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function formatDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value || '');
    return date.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    });
  }

  function textOf(node) {
    return node ? node.textContent.trim() : '';
  }

  function parseExpiry(value) {
    const parsed = Date.parse(value || '');
    if (!Number.isNaN(parsed)) return parsed;
    return Date.now() + fallbackSessionMs;
  }

  function formatCountdown(ms) {
    const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
  }
})();
