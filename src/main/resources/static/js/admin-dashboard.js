(function () {
    const moneyFormatter = new Intl.NumberFormat('vi-VN');

    function formatVnd(value) {
        const number = Number(value) || 0;
        return moneyFormatter.format(Math.round(number)) + 'đ';
    }

    function formatShortVnd(value) {
        const number = Number(value) || 0;
        if (number >= 1000000000) {
            return (number / 1000000000).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + 'tỷ';
        }
        if (number >= 1000000) {
            return (number / 1000000).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + 'tr';
        }
        if (number >= 1000) {
            return (number / 1000).toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + 'k';
        }
        return moneyFormatter.format(Math.round(number));
    }

    function readChartRows(root) {
        if (!root) return [];
        return Array.from(root.querySelectorAll('span')).map((item) => ({
            label: item.dataset.label || '',
            month: Number(item.dataset.month),
            year: Number(item.dataset.year),
            fullLabel: item.dataset.fullLabel || '',
            collected: Number(item.dataset.collected) || 0,
        }));
    }

    function renderFallback(shell, rows, focusMonth) {
        if (!shell || rows.length === 0) return;
        const max = Math.max(...rows.map((row) => row.collected), 1);
        const fallback = document.createElement('div');
        fallback.className = 'admin-chart-fallback';
        fallback.innerHTML = rows.map((row) => {
            const width = Math.max((row.collected / max) * 100, row.collected > 0 ? 4 : 0);
            const active = row.month === focusMonth ? 'font-weight:850;color:#2563EB' : '';
            return `
                <div class="admin-chart-fallback-row" style="${active}">
                    <span>${row.label}</span>
                    <span class="admin-chart-fallback-track">
                        <span class="admin-chart-fallback-bar" style="width:${width}%"></span>
                    </span>
                    <span>${formatVnd(row.collected)}</span>
                </div>
            `;
        }).join('');
        shell.innerHTML = '';
        shell.appendChild(fallback);
    }

    function initRevenueChart() {
        const canvas = document.getElementById('adminRevenueChart');
        const dataRoot = document.getElementById('adminRevenueChartData');
        if (!canvas || !dataRoot) return;

        const rows = readChartRows(dataRoot);
        if (rows.length === 0) return;

        const focusMonth = Number(dataRoot.dataset.focusMonth);
        const shell = canvas.closest('.admin-chart-shell');

        if (!window.Chart) {
            renderFallback(shell, rows, focusMonth);
            return;
        }

        if (canvas._adminRevenueChart) {
            canvas._adminRevenueChart.destroy();
        }

        const values = rows.map((row) => row.collected);
        const focusIndex = rows.findIndex((row) => row.month === focusMonth);
        const barColors = rows.map((row) =>
            row.month === focusMonth ? 'rgba(37, 99, 235, 0.92)' : 'rgba(6, 182, 212, 0.46)'
        );
        const barHoverColors = rows.map((row) =>
            row.month === focusMonth ? 'rgba(29, 78, 216, 1)' : 'rgba(6, 182, 212, 0.72)'
        );

        window.Chart.defaults.font.family = "'Plus Jakarta Sans', 'Inter', system-ui, sans-serif";
        window.Chart.defaults.color = '#64748B';

        canvas._adminRevenueChart = new window.Chart(canvas, {
            type: 'bar',
            data: {
                labels: rows.map((row) => row.label),
                datasets: [{
                    label: 'Doanh thu đã thu',
                    data: values,
                    backgroundColor: barColors,
                    hoverBackgroundColor: barHoverColors,
                    borderColor: rows.map((row) =>
                        row.month === focusMonth ? 'rgba(30, 64, 175, 1)' : 'rgba(6, 182, 212, 0.82)'
                    ),
                    borderWidth: 1,
                    borderRadius: 12,
                    borderSkipped: false,
                    maxBarThickness: 42,
                }],
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: 650,
                    easing: 'easeOutQuart',
                },
                interaction: {
                    mode: 'index',
                    intersect: false,
                },
                plugins: {
                    legend: {
                        display: false,
                    },
                    tooltip: {
                        displayColors: false,
                        backgroundColor: 'rgba(15, 23, 42, 0.94)',
                        titleColor: '#FFFFFF',
                        bodyColor: '#E2E8F0',
                        padding: 12,
                        cornerRadius: 12,
                        callbacks: {
                            title(context) {
                                const row = rows[context[0].dataIndex];
                                return (row.fullLabel || row.label) + ': ' + formatVnd(context[0].parsed.y);
                            },
                            label(context) {
                                return null;
                            },
                        },
                    },
                },
                scales: {
                    x: {
                        grid: {
                            display: false,
                        },
                        border: {
                            display: false,
                        },
                        ticks: {
                            color(context) {
                                return context.index === focusIndex ? '#1D4ED8' : '#64748B';
                            },
                            font(context) {
                                return {
                                    size: 12,
                                    weight: context.index === focusIndex ? 800 : 650,
                                };
                            },
                        },
                    },
                    y: {
                        beginAtZero: true,
                        grace: '8%',
                        border: {
                            display: false,
                        },
                        grid: {
                            color: 'rgba(148, 163, 184, 0.18)',
                            drawTicks: false,
                        },
                        ticks: {
                            padding: 10,
                            callback(value) {
                                return formatShortVnd(value);
                            },
                        },
                    },
                },
            },
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initRevenueChart, { once: true });
    } else {
        initRevenueChart();
    }
    document.addEventListener('htmx:afterSwap', initRevenueChart);
})();
