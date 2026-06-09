(function () {
  const maxFiles = 12;

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-ticket-page]').forEach(initTicketPage);
    document.querySelectorAll('[data-ticket-form]').forEach(initTicketForm);
  });

  function initTicketPage(page) {
    const search = page.querySelector('[data-ticket-search]');
    const rows = Array.from(page.querySelectorAll('.ticket-row'));
    if (!search || rows.length === 0) return;

    search.addEventListener('input', function () {
      const needle = normalize(search.value);
      rows.forEach(function (row) {
        row.hidden = !!needle && !normalize(row.dataset.search || '').includes(needle);
      });
    });
  }

  function initTicketForm(form) {
    initTicketUploader(form);
  }

  function initTicketUploader(form) {
    const uploader = form.querySelector('[data-ticket-uploader]');
    if (!uploader || uploader.dataset.ready === 'true') return;
    uploader.dataset.ready = 'true';

    const input = uploader.querySelector('[data-ticket-file-input]');
    const button = uploader.querySelector('[data-ticket-upload-button]');
    const list = uploader.querySelector('[data-ticket-file-list]');
    const counter = uploader.querySelector('[data-ticket-file-count]');
    if (!input || !button || !list || !counter) return;

    let selected = [];

    button.addEventListener('click', function () {
      input.click();
    });

    input.addEventListener('change', function () {
      addFiles(input.files);
      syncInputFiles();
      render();
    });

    ['dragenter', 'dragover'].forEach(function (eventName) {
      uploader.addEventListener(eventName, function (event) {
        event.preventDefault();
        uploader.classList.add('is-dragging');
      });
    });

    ['dragleave', 'drop'].forEach(function (eventName) {
      uploader.addEventListener(eventName, function (event) {
        event.preventDefault();
        uploader.classList.remove('is-dragging');
      });
    });

    uploader.addEventListener('drop', function (event) {
      addFiles(event.dataTransfer ? event.dataTransfer.files : []);
      syncInputFiles();
      render();
    });

    form.addEventListener('reset', function () {
      selected.forEach(function (item) { URL.revokeObjectURL(item.url); });
      selected = [];
      syncInputFiles();
      render();
    });

    function addFiles(fileList) {
      Array.from(fileList || []).forEach(function (file) {
        if (!file || !file.type || !file.type.startsWith('image/')) return;
        if (selected.length >= maxFiles) return;
        const key = [file.name, file.size, file.lastModified].join(':');
        const exists = selected.some(function (item) { return item.key === key; });
        if (exists) return;
        selected.push({
          key: key,
          file: file,
          url: URL.createObjectURL(file),
        });
      });
    }

    function removeFile(key) {
      const next = [];
      selected.forEach(function (item) {
        if (item.key === key) {
          URL.revokeObjectURL(item.url);
        } else {
          next.push(item);
        }
      });
      selected = next;
      syncInputFiles();
      render();
    }

    function syncInputFiles() {
      try {
        const dataTransfer = new DataTransfer();
        selected.forEach(function (item) {
          dataTransfer.items.add(item.file);
        });
        input.files = dataTransfer.files;
      } catch (ignored) {
        // Browsers without DataTransfer assignment still keep the native selected files.
      }
    }

    function render() {
      list.innerHTML = '';
      list.hidden = selected.length === 0;
      counter.textContent = selected.length
        ? selected.length + ' ảnh đã chọn'
        : 'Chưa chọn ảnh';

      selected.forEach(function (item) {
        const thumb = document.createElement('div');
        thumb.className = 'ticket-uploader-thumb';
        thumb.innerHTML =
          '<img src="' + escapeAttr(item.url) + '" alt="Ảnh đã chọn">' +
          '<button type="button" aria-label="Xóa ảnh">×</button>' +
          '<span>' + escapeHtml(shortName(item.file.name)) + '</span>';
        thumb.querySelector('button').addEventListener('click', function () {
          removeFile(item.key);
        });
        list.appendChild(thumb);
      });
    }
  }

  function shortName(name) {
    const value = String(name || 'Ảnh');
    return value.length > 18 ? value.slice(0, 8) + '…' + value.slice(-7) : value;
  }

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }

  function normalize(value) {
    return String(value || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/đ/g, 'd')
      .replace(/\s+/g, ' ')
      .trim();
  }
})();
