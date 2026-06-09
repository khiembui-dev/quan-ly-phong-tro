import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const failures = [];

function read(rel) {
  return fs.readFileSync(path.join(root, rel), 'utf8');
}

function exists(rel) {
  return fs.existsSync(path.join(root, rel));
}

function fail(message) {
  failures.push(message);
}

function assertFile(rel) {
  if (!exists(rel)) fail(`Missing file: ${rel}`);
}

function assertIncludes(rel, needle, label = needle) {
  if (!exists(rel)) {
    fail(`Missing file for include check: ${rel}`);
    return;
  }
  const text = read(rel);
  if (!text.includes(needle)) fail(`${rel} missing ${label}`);
}

function assertRegex(rel, regex, label) {
  if (!exists(rel)) {
    fail(`Missing file for regex check: ${rel}`);
    return;
  }
  const text = read(rel);
  if (!regex.test(text)) fail(`${rel} missing ${label}`);
}

function walk(dir) {
  const abs = path.join(root, dir);
  if (!fs.existsSync(abs)) return [];
  return fs.readdirSync(abs, { withFileTypes: true }).flatMap((entry) => {
    const rel = path.join(dir, entry.name).replaceAll('\\', '/');
    if (entry.isDirectory()) return walk(rel);
    return rel;
  });
}

const inputCss = 'src/main/resources/static/css/input.css';
const requiredCssClasses = [
  'app-shell',
  'page-shell',
  'page-header',
  'glass-card',
  'glass-panel',
  'glass-topbar',
  'glass-sidebar',
  'glass-modal',
  'glass-filter',
  'glass-search',
  'glass-badge',
  'soft-card',
  'stat-card',
  'room-card',
  'invoice-card',
  'status-badge',
  'btn',
  'btn-primary',
  'btn-secondary',
  'btn-danger',
  'btn-glass',
  'btn-icon',
  'form-input',
  'form-select',
  'form-textarea',
  'form-label',
  'form-error',
  'data-table',
  'table-action',
  'filter-bar',
  'search-box',
  'empty-state',
  'loading-state',
  'toast',
  'modal',
  'dropdown',
  'breadcrumb',
  'pagination',
  'section-title',
  'animate-fade-up',
  'animate-scale-in',
  'animate-slide-in-right',
  'animate-soft-pulse',
  'hover-lift',
  'hover-glow',
  'search-focus-ring',
  'skeleton',
];

assertFile(inputCss);
for (const className of requiredCssClasses) {
  assertRegex(inputCss, new RegExp(`\\.${className}(?:\\s|\\{|,|:)`), `CSS class .${className}`);
}

const tokenChecks = [
  ['#F8FAFC', 'light background token'],
  ['#2563EB', 'primary token'],
  ['#1D4ED8', 'primary hover token'],
  ['#DBEAFE', 'primary soft token'],
  ['#06B6D4', 'cyan accent token'],
  ['#7C3AED', 'purple accent token'],
  ['#0F172A', 'main text token'],
  ['#64748B', 'muted text token'],
  ['rgba(148, 163, 184, 0.28)', 'border token'],
  ['linear-gradient(135deg, #F8FAFC 0%, #EFF6FF 45%, #F5F3FF 100%)', 'page gradient token'],
];
for (const [needle, label] of tokenChecks) assertIncludes(inputCss, needle, label);

const uiModules = [
  'src/main/resources/static/js/ui/sidebar.js',
  'src/main/resources/static/js/ui/dropdown.js',
  'src/main/resources/static/js/ui/modal.js',
  'src/main/resources/static/js/ui/toast.js',
  'src/main/resources/static/js/ui/search.js',
  'src/main/resources/static/js/ui/filter.js',
  'src/main/resources/static/js/ui/theme.js',
  'src/main/resources/static/js/ui/animations.js',
];
for (const rel of uiModules) assertFile(rel);

const appJs = 'src/main/resources/static/js/app.js';
assertIncludes(appJs, 'window.toast', 'toast global');
assertIncludes(appJs, 'window.fmtVnd', 'VND formatter global');
assertIncludes(appJs, 'X-CSRF-TOKEN', 'CSRF fetch header');
assertIncludes(appJs, 'lucide.createIcons', 'Lucide bootstrap');

const layoutFiles = [
  'src/main/resources/templates/layouts/admin-layout.html',
  'src/main/resources/templates/layouts/customer-layout.html',
  'src/main/resources/templates/layouts/auth-layout.html',
];
for (const rel of layoutFiles) {
  assertIncludes(rel, '/js/app.js', `${rel} app.js load`);
  assertIncludes(rel, '/js/ui/', `${rel} UI modules load`);
}
assertIncludes('src/main/resources/templates/layouts/customer-layout.html', '/js/modules/favorite.js', 'favorite module load');
assertIncludes('src/main/resources/templates/layouts/customer-layout.html', '/js/modules/ai-assistant.js', 'customer AI module load');
assertIncludes('src/main/resources/templates/layouts/admin-layout.html', '/js/modules/ai-assistant.js', 'admin AI module load');

const brandFiles = [
  'src/main/resources/messages.properties',
  'src/main/resources/messages_vi.properties',
  'src/main/resources/messages_en.properties',
  'src/main/resources/templates/fragments/brand.html',
  'src/main/resources/templates/fragments/head.html',
  'src/main/resources/static/js/modules/ai-assistant.js',
];
for (const rel of brandFiles) assertIncludes(rel, 'SmartRent', `SmartRent brand in ${rel}`);

const brandScanFiles = [
  ...walk('src/main/resources/templates'),
  ...walk('src/main/resources/static/js'),
  inputCss,
  'src/main/resources/messages.properties',
  'src/main/resources/messages_vi.properties',
  'src/main/resources/messages_en.properties',
].filter((rel) => exists(rel));
for (const rel of brandScanFiles) {
  const text = read(rel);
  const legacyBrand = 'Glass' + ' Living';
  if (text.includes(legacyBrand)) fail(`${rel} still contains ${legacyBrand}`);
}

const preservedContracts = [
  ['src/main/resources/templates/auth/login.html', 'action="/login"', 'login form action'],
  ['src/main/resources/templates/auth/register.html', 'action="/register"', 'register form action'],
  ['src/main/resources/templates/layouts/admin-layout.html', 'action="/logout"', 'admin logout form action'],
  ['src/main/resources/templates/fragments/customer-navbar.html', 'action="/logout"', 'customer logout form action'],
  ['src/main/resources/templates/fragments/customer-navbar.html', 'data-lucide="ticket-check"', 'customer support ticket nav icon'],
  ['src/main/resources/templates/fragments/room-card.html', 'glassToggleFavorite(this)', 'favorite room card handler'],
  ['src/main/resources/templates/fragments/room-card.html', 'th:data-room-id="${room.id}"', 'favorite room id binding'],
  ['src/main/resources/templates/customer/room-detail.html', 'glassToggleFavorite(this)', 'favorite detail handler'],
  ['src/main/resources/templates/customer/room-detail.html', 'th:action="@{/booking/start}"', 'booking start form'],
  ['src/main/resources/templates/customer/room-detail.html', 'name="roomId"', 'booking roomId input'],
  ['src/main/resources/templates/customer/notifications.html', 'th:action="@{/me/tickets}"', 'customer ticket form'],
  ['src/main/resources/templates/customer/notifications.html', 'data-ticket-uploader', 'customer ticket image uploader'],
  ['src/main/resources/templates/customer/booking.html', 'data-booking-request', 'customer booking request modal'],
  ['src/main/resources/templates/admin/booking-requests.html', '/admin/booking-requests/', 'admin booking request actions'],
  ['src/main/resources/templates/customer/invoice-detail.html', '/me/invoices/', 'customer invoice payment route'],
  ['src/main/resources/templates/customer/payment-checkout.html', '/me/payments/', 'customer payment route'],
  ['src/main/resources/templates/admin/rooms.html', '@{/admin/rooms/create', 'admin room create route'],
  ['src/main/resources/templates/admin/rooms.html', '@{/admin/rooms/{id}/edit', 'admin room edit route'],
  ['src/main/resources/templates/admin/rooms.html', '@{/admin/rooms/{id}/delete', 'admin room delete route'],
  ['src/main/resources/templates/admin/properties.html', '/admin/properties/create', 'admin property create route'],
  ['src/main/resources/templates/admin/properties.html', '@{/admin/properties/{id}/edit', 'admin property edit route'],
  ['src/main/resources/templates/admin/invoices.html', '/admin/invoices/\' + ${i.id} + \'/status', 'admin invoice status route'],
  ['src/main/resources/templates/admin/maintenance.html', '@{/admin/tickets}', 'admin ticket create route'],
  ['src/main/resources/templates/admin/utilities.html', 'action="/admin/utilities"', 'admin utilities filter route'],
  ['src/main/resources/templates/admin/automations.html', 'th:action="@{/admin/system-settings}"', 'admin system settings save route'],
  ['src/main/resources/templates/admin/ai.html', 'x-data="aiAssistant()"', 'admin AI Alpine component'],
];

for (const [rel, needle, label] of preservedContracts) assertIncludes(rel, needle, label);

const dashboardController = 'src/main/java/vn/glassliving/admin/page/dashboard/DashboardPageController.java';
const dashboardTemplate = 'src/main/resources/templates/admin/dashboard.html';
assertIncludes(dashboardController, 'attentionItems', 'dashboard real attention model');
assertIncludes(dashboardController, 'pendingTasks', 'dashboard pending task model');
assertIncludes(dashboardTemplate, 'dashboardReport.months', 'dashboard real month series');
assertIncludes(dashboardTemplate, 'collectedHeight', 'dashboard collected chart scale');
assertIncludes(dashboardTemplate, 'outstandingHeight', 'dashboard outstanding chart scale');
assertIncludes(dashboardTemplate, 'attentionItems', 'dashboard attention render');
assertIncludes(dashboardTemplate, 'monthlyRevenue', 'dashboard revenue KPI');
assertIncludes(dashboardTemplate, 'totalRooms', 'dashboard total room KPI');
assertIncludes(dashboardTemplate, 'availableRooms', 'dashboard available room KPI');
{
  const controllerText = exists(dashboardController) ? read(dashboardController) : '';
  const templateText = exists(dashboardTemplate) ? read(dashboardTemplate) : '';
  for (const fakeNeedle of [
    'multiply(BigDecimal.valueOf(0.886))',
    'INV-202604-0099',
    'GT-A-301',
    'vừa xong',
    '30 phút trước',
    'Hoạt động hôm nay',
    'Cơ cấu vận hành',
    'Hóa đơn gần nhất',
    'Cơ sở thu tốt',
    'Ticket mới',
    'Dòng tiền',
  ]) {
    if (controllerText.includes(fakeNeedle) || templateText.includes(fakeNeedle)) {
      fail(`Dashboard still contains fake/static data marker: ${fakeNeedle}`);
    }
  }
}

if (failures.length > 0) {
  console.error(`UI redesign contract failed (${failures.length} issue${failures.length === 1 ? '' : 's'}):`);
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('UI redesign contract passed.');
