/**
 * gCallback - Distributed Callback & Webhook Scheduler Dashboard
 * Pure Vanilla JavaScript Client Application
 */

const API_BASE = '/api';

class CallbackApp {
  constructor() {
    this.currentTab = 'overview';
    this.refreshInterval = 10000;
    this.refreshTimer = null;
    this.clockTimer = null;

    // Jobs state
    this.jobsPage = 0;
    this.jobsPageSize = 15;
    this.jobsTotal = 0;
    this.jobStatusFilter = '';
    this.jobSearchQuery = '';
    this.cachedJobs = [];

    // Occurrences state
    this.occPage = 0;
    this.occPageSize = 15;
    this.occTotal = 0;
    this.occJobIdFilter = '';
    this.occStateFilter = '';

    // Active inspection / deletion
    this.selectedOccurrenceId = null;
    this.jobToDelete = null;

    this.init();
  }

  async init() {
    this.bindEvents();
    this.startUtcClock();
    this.setupAutoRefresh();
    this.initHeaderRows();
    this.initQueryParamRows();

    // Initial load
    await this.refreshAll();
  }

  // =========================================================================
  // Event Bindings
  // =========================================================================
  bindEvents() {
    // Navigation Tabs
    document.querySelectorAll('.nav-tab-link').forEach(link => {
      link.addEventListener('click', (e) => {
        const tab = e.currentTarget.getAttribute('data-tab');
        this.switchTab(tab);
      });
    });

    // Auto-refresh select & manual refresh
    const autoRefreshSelect = document.getElementById('autoRefreshInterval');
    if (autoRefreshSelect) {
      autoRefreshSelect.addEventListener('change', (e) => {
        this.refreshInterval = parseInt(e.target.value, 10);
        this.setupAutoRefresh();
      });
    }

    const manualRefreshBtn = document.getElementById('manualRefreshBtn');
    if (manualRefreshBtn) {
      manualRefreshBtn.addEventListener('click', () => {
        this.refreshAll(true);
      });
    }

    // Seed Demo Jobs
    const seedDemoBtn = document.getElementById('seedDemoBtn');
    if (seedDemoBtn) {
      seedDemoBtn.addEventListener('click', () => this.seedDemoJobs());
    }

    // Job Creation Modal
    const openCreateBtn = document.getElementById('openCreateJobBtn');
    if (openCreateBtn) {
      openCreateBtn.addEventListener('click', () => this.openJobModal());
    }

    const closeJobModalBtn = document.getElementById('closeJobModalBtn');
    if (closeJobModalBtn) {
      closeJobModalBtn.addEventListener('click', () => this.closeJobModal());
    }

    const cancelJobModalBtn = document.getElementById('cancelJobModalBtn');
    if (cancelJobModalBtn) {
      cancelJobModalBtn.addEventListener('click', () => this.closeJobModal());
    }

    // Job Form Submission
    const jobForm = document.getElementById('jobForm');
    if (jobForm) {
      jobForm.addEventListener('submit', (e) => this.handleJobFormSubmit(e));
    }

    // Modal Nav Tabs (Basic, HTTP, Retry)
    document.querySelectorAll('.modal-nav-tab').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const tabId = e.currentTarget.getAttribute('data-modaltab');
        this.switchModalTab(tabId);
      });
    });

    // Radio Schedule Type Toggle
    document.querySelectorAll('input[name="scheduleType"]').forEach(radio => {
      radio.addEventListener('change', (e) => {
        this.toggleScheduleTypeFields(e.target.value);
      });
    });

    // Job Search Input
    const jobSearchInput = document.getElementById('jobSearchInput');
    if (jobSearchInput) {
      let debounceTimeout;
      jobSearchInput.addEventListener('input', (e) => {
        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => {
          this.jobSearchQuery = e.target.value;
          this.jobsPage = 0;
          this.fetchJobs();
        }, 350);
      });
    }

    // Job Status Filter Pills
    document.querySelectorAll('#jobStatusFilterPills .filter-pill').forEach(pill => {
      pill.addEventListener('click', (e) => {
        document.querySelectorAll('#jobStatusFilterPills .filter-pill').forEach(p => p.classList.remove('active'));
        e.currentTarget.classList.add('active');
        this.jobStatusFilter = e.currentTarget.getAttribute('data-status');
        this.jobsPage = 0;
        this.fetchJobs();
      });
    });

    // Job Pagination Controls
    const jobsPrevBtn = document.getElementById('jobsPrevBtn');
    if (jobsPrevBtn) {
      jobsPrevBtn.addEventListener('click', () => {
        if (this.jobsPage > 0) {
          this.jobsPage--;
          this.fetchJobs();
        }
      });
    }

    const jobsNextBtn = document.getElementById('jobsNextBtn');
    if (jobsNextBtn) {
      jobsNextBtn.addEventListener('click', () => {
        this.jobsPage++;
        this.fetchJobs();
      });
    }

    // Occurrences Filters
    const occurrenceJobFilter = document.getElementById('occurrenceJobFilter');
    if (occurrenceJobFilter) {
      occurrenceJobFilter.addEventListener('change', (e) => {
        this.occJobIdFilter = e.target.value;
        this.occPage = 0;
        this.fetchOccurrences();
      });
    }

    document.querySelectorAll('#occurrenceStateFilterPills .filter-pill').forEach(pill => {
      pill.addEventListener('click', (e) => {
        document.querySelectorAll('#occurrenceStateFilterPills .filter-pill').forEach(p => p.classList.remove('active'));
        e.currentTarget.classList.add('active');
        this.occStateFilter = e.currentTarget.getAttribute('data-state');
        this.occPage = 0;
        this.fetchOccurrences();
      });
    });

    const clearOccFilterBtn = document.getElementById('clearOccFilterBtn');
    if (clearOccFilterBtn) {
      clearOccFilterBtn.addEventListener('click', () => {
        this.occJobIdFilter = '';
        this.occStateFilter = '';
        if (occurrenceJobFilter) occurrenceJobFilter.value = '';
        document.querySelectorAll('#occurrenceStateFilterPills .filter-pill').forEach(p => p.classList.remove('active'));
        const allPill = document.querySelector('#occurrenceStateFilterPills .filter-pill[data-state=""]');
        if (allPill) allPill.classList.add('active');
        this.occPage = 0;
        this.fetchOccurrences();
      });
    }

    // Occurrence Pagination
    const occPrevBtn = document.getElementById('occurrencesPrevBtn');
    if (occPrevBtn) {
      occPrevBtn.addEventListener('click', () => {
        if (this.occPage > 0) {
          this.occPage--;
          this.fetchOccurrences();
        }
      });
    }

    const occNextBtn = document.getElementById('occurrencesNextBtn');
    if (occNextBtn) {
      occNextBtn.addEventListener('click', () => {
        this.occPage++;
        this.fetchOccurrences();
      });
    }

    // Occurrence Details Modal
    const closeOccBtn1 = document.getElementById('closeOccurrenceModalBtn');
    if (closeOccBtn1) closeOccBtn1.addEventListener('click', () => this.closeOccurrenceModal());
    const closeOccBtn2 = document.getElementById('closeOccurrenceModalBtn2');
    if (closeOccBtn2) closeOccBtn2.addEventListener('click', () => this.closeOccurrenceModal());

    const retryOccBtn = document.getElementById('retryOccurrenceModalBtn');
    if (retryOccBtn) {
      retryOccBtn.addEventListener('click', () => {
        if (this.selectedOccurrenceId) {
          this.retryOccurrence(this.selectedOccurrenceId);
        }
      });
    }

    // Delete Job Modal
    const closeDelBtn = document.getElementById('closeDeleteJobModalBtn');
    if (closeDelBtn) closeDelBtn.addEventListener('click', () => this.closeDeleteModal());
    const cancelDelBtn = document.getElementById('cancelDeleteJobBtn');
    if (cancelDelBtn) cancelDelBtn.addEventListener('click', () => this.closeDeleteModal());

    const confirmDelBtn = document.getElementById('confirmDeleteJobBtn');
    if (confirmDelBtn) {
      confirmDelBtn.addEventListener('click', () => this.confirmDeleteJob());
    }

    // Close Modals on Backdrop Click & ESC key
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        this.closeJobModal();
        this.closeOccurrenceModal();
        this.closeDeleteModal();
      }
    });

    document.querySelectorAll('.modal-overlay').forEach(overlay => {
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
          this.closeJobModal();
          this.closeOccurrenceModal();
          this.closeDeleteModal();
        }
      });
    });
  }

  // =========================================================================
  // Real-time Clock & Auto Refresh
  // =========================================================================
  startUtcClock() {
    const clockEl = document.getElementById('liveUtcClock');
    const update = () => {
      if (clockEl) {
        const now = new Date();
        clockEl.textContent = now.toUTCString().replace('GMT', 'UTC').split(' ').slice(4, 5)[0] + ' UTC';
      }
    };
    update();
    this.clockTimer = setInterval(update, 1000);
  }

  setupAutoRefresh() {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (this.refreshInterval > 0) {
      this.refreshTimer = setInterval(() => {
        this.refreshAll(false);
      }, this.refreshInterval);
    }
  }

  async refreshAll(manual = false) {
    if (manual) {
      const icon = document.querySelector('#manualRefreshBtn svg');
      if (icon) icon.style.animation = 'pulseDot 0.6s infinite linear';
      setTimeout(() => {
        if (icon) icon.style.animation = '';
      }, 700);
    }

    try {
      await Promise.all([
        this.fetchStats(),
        this.fetchHealth(),
        this.fetchJobs(),
        this.fetchOccurrences()
      ]);
      this.setSystemStatus(true, 'Cluster Active');
    } catch (err) {
      console.error('Refresh failed:', err);
      this.setSystemStatus(false, 'Connecting...');
    }
  }

  setSystemStatus(healthy, message) {
    const dot = document.getElementById('systemStatusDot');
    const text = document.getElementById('systemStatusText');
    if (dot) {
      dot.style.backgroundColor = healthy ? 'var(--success)' : 'var(--warning)';
      dot.style.boxShadow = healthy ? '0 0 8px var(--success)' : '0 0 8px var(--warning)';
    }
    if (text) {
      text.textContent = message;
    }
  }

  // =========================================================================
  // Tab Switching
  // =========================================================================
  switchTab(tabId) {
    this.currentTab = tabId;

    document.querySelectorAll('.nav-tab-link').forEach(link => {
      link.classList.toggle('active', link.getAttribute('data-tab') === tabId);
    });

    document.querySelectorAll('.tab-content').forEach(content => {
      content.classList.toggle('active', content.id === `tab-${tabId}`);
    });

    if (tabId === 'overview') {
      this.fetchStats();
    } else if (tabId === 'jobs') {
      this.fetchJobs();
    } else if (tabId === 'occurrences') {
      this.fetchOccurrences();
    }
  }

  // =========================================================================
  // API Calls & Rendering: Dashboard Overview
  // =========================================================================
  async fetchStats() {
    try {
      const res = await fetch(`${API_BASE}/dashboard/stats`);
      const json = await res.json();
      if (!json.success || !json.data) return;

      const d = json.data;
      // Metrics Cards
      this.updateText('statTotalJobs', d.totalJobs);
      this.updateText('statActiveJobs', `${d.activeJobs} Active`);
      this.updateText('statPausedJobs', `${d.pausedJobs} Paused`);

      this.updateText('statTotalOccurrences', d.totalOccurrences);
      this.updateText('statSucceededOccurrences', `${d.succeededOccurrences} Succeeded`);

      this.updateText('statSuccessRate', `${d.successRate}%`);
      this.updateText('statFailedOccurrences', d.failedOccurrences);
      this.updateText('statRetryingOccurrences', `${d.retryScheduledOccurrences} Re-scheduled`);

      this.updateText('statAvgLatency', `${d.avgLatencyMs} ms`);

      // Update badge counts
      this.updateText('jobsCountBadge', d.activeJobs);
      this.updateText('occurrencesCountBadge', d.inProgressOccurrences + d.retryScheduledOccurrences);

      // Render Charts
      this.renderTimelineChart(d.timeline || []);
      this.renderStatusDonut(d.stateDistribution || {});

      // Render Recent Occurrences Table
      this.renderRecentOccurrences(d.recentOccurrences || []);

      // Render Active Workers
      this.renderActiveWorkers(d.activeWorkers || []);
    } catch (err) {
      console.error('Failed to fetch stats:', err);
    }
  }

  async fetchHealth() {
    try {
      const res = await fetch(`${API_BASE}/dashboard/health`);
      const json = await res.json();
      if (json.success && json.data) {
        this.setSystemStatus(true, `Cluster Active (${json.data.currentWorkerId || 'Node'})`);
      }
    } catch (err) {
      this.setSystemStatus(false, 'Disconnected');
    }
  }

  renderTimelineChart(timeline) {
    const canvas = document.getElementById('timelineChartCanvas');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const width = canvas.parentElement.clientWidth;
    const height = canvas.parentElement.clientHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

    ctx.clearRect(0, 0, width, height);

    if (!timeline || timeline.length === 0) {
      ctx.fillStyle = '#64748b';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('No execution activity recorded in this window', width / 2, height / 2);
      return;
    }

    const padding = { top: 20, right: 20, bottom: 35, left: 35 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const maxVal = Math.max(5, ...timeline.map(t => Math.max(t.succeeded + t.failed, t.total)));
    const barWidth = Math.max(8, Math.min(28, (chartW / timeline.length) * 0.65));
    const step = chartW / timeline.length;

    // Draw Grid Lines
    ctx.strokeStyle = '#1e293b';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = padding.top + (chartH / 4) * i;
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(padding.left + chartW, y);
      ctx.stroke();

      const label = Math.round(maxVal - (maxVal / 4) * i);
      ctx.fillStyle = '#64748b';
      ctx.font = '10px monospace';
      ctx.textAlign = 'right';
      ctx.fillText(label, padding.left - 8, y + 3);
    }

    // Draw Bars
    timeline.forEach((pt, idx) => {
      const x = padding.left + idx * step + (step - barWidth) / 2;

      const succH = (pt.succeeded / maxVal) * chartH;
      const failH = (pt.failed / maxVal) * chartH;

      // Succeeded Bar
      if (succH > 0) {
        ctx.fillStyle = '#10b981';
        this.roundRect(ctx, x, padding.top + chartH - succH, barWidth, succH, 3);
        ctx.fill();
      }

      // Failed Bar (stacked or adjacent)
      if (failH > 0) {
        ctx.fillStyle = '#ef4444';
        this.roundRect(ctx, x, padding.top + chartH - succH - failH, barWidth, failH, 3);
        ctx.fill();
      }

      // X Label
      ctx.fillStyle = '#94a3b8';
      ctx.font = '10px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(pt.label, x + barWidth / 2, height - 10);
    });
  }

  renderStatusDonut(dist) {
    const canvas = document.getElementById('statusChartCanvas');
    const legendEl = document.getElementById('statusChartLegend');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const width = canvas.parentElement.clientWidth;
    const height = canvas.parentElement.clientHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

    ctx.clearRect(0, 0, width, height);

    const colors = {
      SUCCEEDED: '#10b981',
      FAILED: '#ef4444',
      IN_PROGRESS: '#0ea5e9',
      DISPATCHED: '#a855f7',
      PENDING: '#64748b',
      RETRY_SCHEDULED: '#f59e0b',
      CANCELLED: '#475569'
    };

    const total = Object.values(dist).reduce((acc, v) => acc + (v || 0), 0);

    if (total === 0) {
      ctx.fillStyle = '#64748b';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('No execution data yet', width / 2, height / 2);
      if (legendEl) legendEl.innerHTML = '';
      return;
    }

    const centerX = width / 2;
    const centerY = height / 2;
    const radius = Math.min(centerX, centerY) - 15;
    const innerRadius = radius * 0.62;

    let startAngle = -Math.PI / 2;

    Object.entries(dist).forEach(([state, count]) => {
      if (!count || count <= 0) return;
      const sliceAngle = (count / total) * 2 * Math.PI;

      ctx.beginPath();
      ctx.arc(centerX, centerY, radius, startAngle, startAngle + sliceAngle);
      ctx.arc(centerX, centerY, innerRadius, startAngle + sliceAngle, startAngle, true);
      ctx.closePath();

      ctx.fillStyle = colors[state] || '#94a3b8';
      ctx.fill();

      startAngle += sliceAngle;
    });

    // Center Total Count
    ctx.fillStyle = '#f8fafc';
    ctx.font = 'bold 16px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(total, centerX, centerY - 6);

    ctx.fillStyle = '#94a3b8';
    ctx.font = '10px sans-serif';
    ctx.fillText('Total Runs', centerX, centerY + 12);

    // Build Legend
    if (legendEl) {
      legendEl.innerHTML = Object.entries(dist)
        .filter(([_, count]) => count > 0)
        .map(([state, count]) => `
          <div class="legend-item">
            <span class="legend-color" style="background-color: ${colors[state] || '#94a3b8'};"></span>
            <span>${this.formatEnum(state)} (${count})</span>
          </div>
        `).join('');
    }
  }

  renderRecentOccurrences(occurrences) {
    const tbody = document.getElementById('recentOccurrencesTableBody');
    if (!tbody) return;

    if (!occurrences || occurrences.length === 0) {
      tbody.innerHTML = `<tr><td colspan="9" class="empty-state">No recent callback executions found. Trigger a job to see live runs!</td></tr>`;
      return;
    }

    tbody.innerHTML = occurrences.map(occ => `
      <tr>
        <td class="code-cell">#${occ.id}</td>
        <td style="font-weight: 600;">${this.escapeHtml(occ.jobName || 'Job #' + occ.jobId)}</td>
        <td>
          <span class="method-chip method-${(occ.httpMethod || 'POST').toLowerCase()}">${occ.httpMethod || 'POST'}</span>
          <span class="code-cell" style="margin-left: 0.35rem;" title="${this.escapeHtml(occ.httpUrl)}">${this.escapeHtml(occ.httpUrl)}</span>
        </td>
        <td>${this.renderOccurrenceStateBadge(occ.state)}</td>
        <td>${occ.attemptCount || 0} / ${occ.maxAttempts || 3}</td>
        <td>${this.renderStatusCodeBadge(occ.lastStatusCode)}</td>
        <td class="code-cell">${occ.lastLatencyMs != null ? occ.lastLatencyMs + ' ms' : '-'}</td>
        <td style="font-size: 0.8rem; color: var(--text-muted);">${this.formatDateTime(occ.scheduledFor)}</td>
        <td>
          <button class="btn btn-secondary btn-xs" onclick="app.inspectOccurrence(${occ.id})">Inspect</button>
        </td>
      </tr>
    `).join('');
  }

  renderActiveWorkers(workers) {
    const container = document.getElementById('activeWorkersList');
    if (!container) return;

    if (!workers || workers.length === 0) {
      container.innerHTML = `
        <div style="background-color: var(--bg-input); border: 1px solid var(--border-subtle); padding: 0.85rem; border-radius: var(--radius-md); font-size: 0.85rem; color: var(--text-muted);">
          No active worker records registered yet. Workers register automatically upon processing their first callback execution.
        </div>
      `;
      return;
    }

    container.innerHTML = workers.map(w => `
      <div style="display: flex; align-items: center; justify-content: space-between; background-color: var(--bg-input); border: 1px solid var(--border-subtle); padding: 0.75rem 1rem; border-radius: var(--radius-md);">
        <div style="display: flex; align-items: center; gap: 0.65rem;">
          <span class="status-dot" style="background-color: var(--success); box-shadow: 0 0 6px var(--success);"></span>
          <span style="font-family: var(--font-mono); font-size: 0.82rem; color: var(--text-main); font-weight: 600;">${this.escapeHtml(w)}</span>
        </div>
        <span class="status-badge status-active">Online</span>
      </div>
    `).join('');
  }

  // =========================================================================
  // API Calls & Rendering: Jobs Directory
  // =========================================================================
  async fetchJobs() {
    try {
      const params = new URLSearchParams({
        page: this.jobsPage,
        size: this.jobsPageSize
      });
      if (this.jobStatusFilter) params.append('status', this.jobStatusFilter);
      if (this.jobSearchQuery) params.append('search', this.jobSearchQuery);

      const res = await fetch(`${API_BASE}/jobs?${params}`);
      const json = await res.json();

      if (!json.success || !json.data) return;

      const paged = json.data;
      this.cachedJobs = paged.items || [];
      this.jobsTotal = paged.totalElements || 0;

      this.renderJobsTable(this.cachedJobs);
      this.updateJobsPagination(paged);
      this.populateOccurrenceJobFilter(this.cachedJobs);
    } catch (err) {
      console.error('Failed to fetch jobs:', err);
      this.showToast('error', 'Error', 'Failed to load jobs list.');
    }
  }

  renderJobsTable(jobs) {
    const tbody = document.getElementById('jobsTableBody');
    if (!tbody) return;

    if (!jobs || jobs.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="9" class="empty-state">
            <div class="empty-state-icon">📂</div>
            <h3>No Callback Jobs Found</h3>
            <p>You haven't scheduled any callback jobs matching the filter. Create a new job or seed demo test jobs.</p>
            <button class="btn btn-primary btn-sm" onclick="app.openJobModal()">+ Create Callback Job</button>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = jobs.map(job => {
      const scheduleInfo = job.scheduleType === 'CRON'
        ? `<div style="font-family: var(--font-mono); font-size: 0.78rem; color: #a5b4fc;">${this.escapeHtml(job.cronExpression)}</div>
           <div style="font-size: 0.72rem; color: var(--text-dark);">${this.escapeHtml(job.timeZone || 'UTC')}</div>`
        : `<div style="font-size: 0.78rem; color: #38bdf8;">One-Off Execution</div>
           <div style="font-size: 0.72rem; color: var(--text-dark);">${this.formatDateTime(job.runAt)}</div>`;

      const nextFire = job.nextFireTime
        ? `<div style="font-size: 0.8rem; font-weight: 500;">${this.formatRelativeTime(job.nextFireTime)}</div>
           <div style="font-size: 0.7rem; color: var(--text-dark);">${this.formatDateTime(job.nextFireTime)}</div>`
        : `<span style="font-size: 0.75rem; color: var(--text-dark);">Terminal / None</span>`;

      const statsDisplay = `
        <div style="font-size: 0.75rem;">
          <span style="font-weight: 600;">${job.totalOccurrences || 0}</span> runs • 
          <span style="color: var(--success);">${job.successfulOccurrences || 0}</span> succ • 
          <span style="color: var(--danger);">${job.failedOccurrences || 0}</span> fail
        </div>
      `;

      const isPaused = job.status === 'PAUSED';

      return `
        <tr>
          <td class="code-cell">#${job.id}</td>
          <td>
            <div style="font-weight: 600; font-size: 0.88rem; color: var(--text-main);">${this.escapeHtml(job.name)}</div>
            <div style="font-size: 0.7rem; color: var(--text-dark);">Created: ${this.formatDateTime(job.createdAt)}</div>
          </td>
          <td>
            <div style="display: flex; align-items: center; gap: 0.4rem;">
              <span class="method-chip method-${(job.httpMethod || 'POST').toLowerCase()}">${job.httpMethod || 'POST'}</span>
              <a href="${this.escapeHtml(job.httpUrl)}" target="_blank" rel="noopener" class="code-cell url-link" title="${this.escapeHtml(job.httpUrl)}">${this.escapeHtml(job.httpUrl)}</a>
            </div>
          </td>
          <td>${scheduleInfo}</td>
          <td style="font-size: 0.8rem;">
            <div>${job.maxAttempts} attempts max</div>
            <div style="color: var(--text-dark);">${job.timeoutMs} ms timeout</div>
          </td>
          <td>${nextFire}</td>
          <td>${this.renderJobStatusBadge(job.status)}</td>
          <td>${statsDisplay}</td>
          <td>
            <div class="action-buttons" style="justify-content: flex-end;">
              <button class="btn btn-success btn-xs" title="Trigger Run Now" onclick="app.triggerJob(${job.id})">⚡ Run</button>
              ${isPaused
                ? `<button class="btn btn-secondary btn-xs" title="Resume Job" onclick="app.resumeJob(${job.id})">▶ Resume</button>`
                : `<button class="btn btn-secondary btn-xs" title="Pause Job" onclick="app.pauseJob(${job.id})">⏸ Pause</button>`
              }
              <button class="btn btn-secondary btn-xs" title="Edit Job" onclick="app.editJob(${job.id})">✏ Edit</button>
              <button class="btn btn-secondary btn-xs" title="View Occurrences" onclick="app.viewJobOccurrences(${job.id})">👁 Runs</button>
              <button class="btn btn-danger btn-xs" title="Delete Job" onclick="app.promptDeleteJob(${job.id}, '${this.escapeHtml(job.name)}')">🗑</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  updateJobsPagination(paged) {
    const info = document.getElementById('jobsPaginationInfo');
    const indicator = document.getElementById('jobsPageIndicator');
    const prevBtn = document.getElementById('jobsPrevBtn');
    const nextBtn = document.getElementById('jobsNextBtn');

    const total = paged.totalElements || 0;
    const page = paged.page || 0;
    const size = paged.size || this.jobsPageSize;
    const totalPages = Math.max(1, paged.totalPages || 1);

    const start = total === 0 ? 0 : page * size + 1;
    const end = Math.min((page + 1) * size, total);

    if (info) info.textContent = `Showing ${start}-${end} of ${total} jobs`;
    if (indicator) indicator.textContent = `Page ${page + 1} of ${totalPages}`;
    if (prevBtn) prevBtn.disabled = page <= 0;
    if (nextBtn) nextBtn.disabled = page >= totalPages - 1;
  }

  populateOccurrenceJobFilter(jobs) {
    const select = document.getElementById('occurrenceJobFilter');
    if (!select) return;

    const currentVal = select.value;
    select.innerHTML = '<option value="">All Jobs</option>' + jobs.map(j => `
      <option value="${j.id}" ${currentVal == j.id ? 'selected' : ''}>#${j.id} - ${this.escapeHtml(j.name)}</option>
    `).join('');
  }

  // =========================================================================
  // API Calls & Rendering: Occurrences & Executions
  // =========================================================================
  async fetchOccurrences() {
    try {
      const params = new URLSearchParams({
        page: this.occPage,
        size: this.occPageSize
      });
      if (this.occJobIdFilter) params.append('jobId', this.occJobIdFilter);
      if (this.occStateFilter) params.append('state', this.occStateFilter);

      const res = await fetch(`${API_BASE}/occurrences?${params}`);
      const json = await res.json();

      if (!json.success || !json.data) return;

      const paged = json.data;
      this.occTotal = paged.totalElements || 0;

      this.renderOccurrencesTable(paged.items || []);
      this.updateOccurrencesPagination(paged);
    } catch (err) {
      console.error('Failed to fetch occurrences:', err);
    }
  }

  renderOccurrencesTable(occurrences) {
    const tbody = document.getElementById('occurrencesTableBody');
    if (!tbody) return;

    if (!occurrences || occurrences.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="10" class="empty-state">
            <div class="empty-state-icon">⏳</div>
            <h3>No Occurrences Found</h3>
            <p>No callback executions matched the selected filter criteria.</p>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = occurrences.map(occ => {
      const isTerminal = occ.state === 'SUCCEEDED' || occ.state === 'FAILED' || occ.state === 'CANCELLED';
      const isRetryable = occ.state === 'FAILED' || occ.state === 'CANCELLED' || occ.state === 'RETRY_SCHEDULED';

      return `
        <tr>
          <td class="code-cell">#${occ.id}</td>
          <td>
            <div style="font-weight: 600; font-size: 0.85rem;">${this.escapeHtml(occ.jobName || 'Job #' + occ.jobId)}</div>
            <div style="display: flex; align-items: center; gap: 0.35rem; margin-top: 0.2rem;">
              <span class="method-chip method-${(occ.httpMethod || 'POST').toLowerCase()}">${occ.httpMethod || 'POST'}</span>
              <span class="code-cell" style="font-size: 0.72rem;" title="${this.escapeHtml(occ.httpUrl)}">${this.escapeHtml(occ.httpUrl)}</span>
            </div>
          </td>
          <td style="font-size: 0.8rem; color: var(--text-muted);">${this.formatDateTime(occ.scheduledFor)}</td>
          <td>${this.renderOccurrenceStateBadge(occ.state)}</td>
          <td style="font-size: 0.8rem; font-weight: 600;">
            ${occ.attemptCount || 0} / ${occ.maxAttempts || 3}
          </td>
          <td>${this.renderStatusCodeBadge(occ.lastStatusCode)}</td>
          <td class="code-cell">${occ.lastLatencyMs != null ? occ.lastLatencyMs + ' ms' : '-'}</td>
          <td style="font-size: 0.78rem; color: var(--text-muted);">
            ${occ.nextAttemptAt ? this.formatDateTime(occ.nextAttemptAt) : '-'}
          </td>
          <td class="code-cell" style="font-size: 0.75rem;">
            ${occ.claimedBy ? this.escapeHtml(occ.claimedBy) : '<span style="color: var(--text-dark);">unclaimed</span>'}
          </td>
          <td>
            <div class="action-buttons" style="justify-content: flex-end;">
              <button class="btn btn-secondary btn-xs" title="Inspect Attempt Details" onclick="app.inspectOccurrence(${occ.id})">🔍 Inspect</button>
              ${isRetryable
                ? `<button class="btn btn-success btn-xs" title="Re-queue and Retry" onclick="app.retryOccurrence(${occ.id})">🔄 Retry</button>`
                : ''
              }
              ${!isTerminal
                ? `<button class="btn btn-danger btn-xs" title="Cancel Execution" onclick="app.cancelOccurrence(${occ.id})">✕ Cancel</button>`
                : ''
              }
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  updateOccurrencesPagination(paged) {
    const info = document.getElementById('occurrencesPaginationInfo');
    const indicator = document.getElementById('occurrencesPageIndicator');
    const prevBtn = document.getElementById('occurrencesPrevBtn');
    const nextBtn = document.getElementById('occurrencesNextBtn');

    const total = paged.totalElements || 0;
    const page = paged.page || 0;
    const size = paged.size || this.occPageSize;
    const totalPages = Math.max(1, paged.totalPages || 1);

    const start = total === 0 ? 0 : page * size + 1;
    const end = Math.min((page + 1) * size, total);

    if (info) info.textContent = `Showing ${start}-${end} of ${total} executions`;
    if (indicator) indicator.textContent = `Page ${page + 1} of ${totalPages}`;
    if (prevBtn) prevBtn.disabled = page <= 0;
    if (nextBtn) nextBtn.disabled = page >= totalPages - 1;
  }

  // =========================================================================
  // Occurrence Deep Dive Modal
  // =========================================================================
  async inspectOccurrence(id) {
    this.selectedOccurrenceId = id;
    const modal = document.getElementById('occurrenceModal');
    const body = document.getElementById('occurrenceModalBody');
    if (!modal || !body) return;

    body.innerHTML = `<div style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Loading occurrence details & attempt history...</div>`;
    modal.classList.add('open');

    try {
      const res = await fetch(`${API_BASE}/occurrences/${id}`);
      const json = await res.json();

      if (!json.success || !json.data) {
        body.innerHTML = `<div style="color: var(--danger); padding: 1.5rem;">Could not load occurrence #${id}.</div>`;
        return;
      }

      const occ = json.data;
      const attempts = occ.attempts || [];

      let attemptsHtml = '';
      if (attempts.length === 0) {
        attemptsHtml = `
          <div style="background-color: var(--bg-input); padding: 1rem; border-radius: var(--radius-md); font-size: 0.85rem; color: var(--text-muted); text-align: center;">
            No attempt records executed yet. Occurrence is currently waiting in state: <strong>${occ.state}</strong>.
          </div>
        `;
      } else {
        attemptsHtml = `
          <div class="timeline-stepper">
            ${attempts.map(att => {
              let dotClass = 'in_flight';
              if (att.outcome === 'SUCCESS') dotClass = 'success';
              else if (att.outcome && att.outcome !== 'IN_FLIGHT') dotClass = 'failed';

              return `
                <div class="timeline-step">
                  <div class="timeline-step-dot ${dotClass}"></div>
                  <div class="timeline-step-card">
                    <div class="timeline-step-header">
                      <div class="attempt-badge">Attempt #${att.attemptNumber}</div>
                      <div style="display: flex; align-items: center; gap: 0.5rem;">
                        ${this.renderStatusCodeBadge(att.statusCode)}
                        <span class="status-badge occ-${(att.outcome || 'IN_FLIGHT').toLowerCase()}">${att.outcome}</span>
                      </div>
                    </div>

                    <div class="attempt-meta">
                      <span>⏱ Latency: <strong>${att.latencyMs != null ? att.latencyMs + ' ms' : '-'}</strong></span>
                      <span>Worker: <strong>${this.escapeHtml(att.workerId || 'unknown')}</strong></span>
                      <span>Started: <strong>${this.formatDateTime(att.startedAt)}</strong></span>
                      ${att.finishedAt ? `<span>Finished: <strong>${this.formatDateTime(att.finishedAt)}</strong></span>` : ''}
                    </div>

                    ${att.errorMessage ? `
                      <div style="margin-top: 0.5rem; background-color: rgba(239, 68, 68, 0.1); border-left: 3px solid var(--danger); padding: 0.5rem 0.75rem; border-radius: 4px; font-size: 0.78rem; color: #fca5a5;">
                        <strong>Error:</strong> ${this.escapeHtml(att.errorMessage)}
                      </div>
                    ` : ''}

                    ${att.responseBody ? `
                      <div style="margin-top: 0.65rem;">
                        <div style="font-size: 0.72rem; font-weight: 600; color: var(--text-muted); margin-bottom: 0.2rem;">HTTP Response Body:</div>
                        <div class="code-snippet">
                          <button class="copy-btn" onclick="app.copyToClipboard('${this.escapeJs(att.responseBody)}')">Copy</button>
                          <code>${this.escapeHtml(att.responseBody)}</code>
                        </div>
                      </div>
                    ` : ''}
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        `;
      }

      body.innerHTML = `
        <!-- Occurrence Overview -->
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; background-color: var(--bg-input); border: 1px solid var(--border-subtle); padding: 1rem; border-radius: var(--radius-md); margin-bottom: 1.25rem;">
          <div>
            <div style="font-size: 0.72rem; color: var(--text-dark); text-transform: uppercase;">Job Name</div>
            <div style="font-weight: 600; font-size: 0.9rem;">${this.escapeHtml(occ.jobName)}</div>
          </div>
          <div>
            <div style="font-size: 0.72rem; color: var(--text-dark); text-transform: uppercase;">Current State</div>
            <div>${this.renderOccurrenceStateBadge(occ.state)}</div>
          </div>
          <div>
            <div style="font-size: 0.72rem; color: var(--text-dark); text-transform: uppercase;">Target URL</div>
            <div class="code-cell" style="font-size: 0.8rem;" title="${this.escapeHtml(occ.httpUrl)}">${occ.httpMethod} ${this.escapeHtml(occ.httpUrl)}</div>
          </div>
          <div>
            <div style="font-size: 0.72rem; color: var(--text-dark); text-transform: uppercase;">Scheduled For</div>
            <div style="font-size: 0.8rem;">${this.formatDateTime(occ.scheduledFor)}</div>
          </div>
        </div>

        <h4 style="font-size: 0.95rem; font-weight: 600; margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.4rem;">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>
          Execution Attempts & Timeline (${attempts.length} attempts)
        </h4>

        ${attemptsHtml}
      `;
    } catch (err) {
      console.error('Error opening occurrence modal:', err);
      body.innerHTML = `<div style="color: var(--danger); padding: 1.5rem;">Failed to load occurrence inspection data.</div>`;
    }
  }

  closeOccurrenceModal() {
    const modal = document.getElementById('occurrenceModal');
    if (modal) modal.classList.remove('open');
    this.selectedOccurrenceId = null;
  }

  // =========================================================================
  // Job Actions: Run, Pause, Resume, Delete, Seed
  // =========================================================================
  async triggerJob(id) {
    try {
      const res = await fetch(`${API_BASE}/jobs/${id}/trigger`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('success', 'Execution Queued', `Ad-hoc occurrence #${json.data.id} dispatched!`);
        this.fetchStats();
        this.fetchOccurrences();
      } else {
        this.showToast('error', 'Trigger Failed', json.message || 'Could not trigger job.');
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to connect to trigger API.');
    }
  }

  async pauseJob(id) {
    try {
      const res = await fetch(`${API_BASE}/jobs/${id}/pause`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('info', 'Job Paused', `Job #${id} is now PAUSED.`);
        this.fetchJobs();
        this.fetchStats();
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to pause job.');
    }
  }

  async resumeJob(id) {
    try {
      const res = await fetch(`${API_BASE}/jobs/${id}/resume`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('success', 'Job Resumed', `Job #${id} is now ACTIVE.`);
        this.fetchJobs();
        this.fetchStats();
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to resume job.');
    }
  }

  promptDeleteJob(id, name) {
    this.jobToDelete = { id, name };
    const nameEl = document.getElementById('deleteJobName');
    if (nameEl) nameEl.textContent = name || `#${id}`;
    const modal = document.getElementById('deleteJobModal');
    if (modal) modal.classList.add('open');
  }

  closeDeleteModal() {
    const modal = document.getElementById('deleteJobModal');
    if (modal) modal.classList.remove('open');
    this.jobToDelete = null;
  }

  async confirmDeleteJob() {
    if (!this.jobToDelete) return;
    const { id } = this.jobToDelete;
    const hardCheckbox = document.getElementById('hardDeleteCheckbox');
    const hard = hardCheckbox ? hardCheckbox.checked : false;

    try {
      const res = await fetch(`${API_BASE}/jobs/${id}?hard=${hard}`, { method: 'DELETE' });
      const json = await res.json();
      if (json.success) {
        this.showToast('info', 'Job Deleted', `Job #${id} was removed.`);
        this.closeDeleteModal();
        this.fetchJobs();
        this.fetchStats();
      } else {
        this.showToast('error', 'Delete Failed', json.message || 'Could not delete job.');
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to delete job.');
    }
  }

  async retryOccurrence(id) {
    try {
      const res = await fetch(`${API_BASE}/occurrences/${id}/retry`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('success', 'Requeued', `Occurrence #${id} requeued for immediate dispatch!`);
        this.closeOccurrenceModal();
        this.fetchOccurrences();
        this.fetchStats();
      } else {
        this.showToast('error', 'Retry Failed', json.message || 'Could not retry occurrence.');
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to retry occurrence.');
    }
  }

  async cancelOccurrence(id) {
    try {
      const res = await fetch(`${API_BASE}/occurrences/${id}/cancel`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('info', 'Cancelled', `Occurrence #${id} was cancelled.`);
        this.fetchOccurrences();
        this.fetchStats();
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to cancel occurrence.');
    }
  }

  async seedDemoJobs() {
    try {
      const res = await fetch(`${API_BASE}/jobs/seed-demo`, { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        this.showToast('success', 'Demo Seeded', 'Realistic demo jobs created successfully!');
        this.fetchJobs();
        this.fetchStats();
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to seed demo jobs.');
    }
  }

  viewJobOccurrences(jobId) {
    this.occJobIdFilter = String(jobId);
    const select = document.getElementById('occurrenceJobFilter');
    if (select) select.value = String(jobId);
    this.switchTab('occurrences');
  }

  // =========================================================================
  // Job Form: Modal, Tabs, Dynamic Rows, Save
  // =========================================================================
  openJobModal(job = null) {
    const modal = document.getElementById('jobModal');
    const title = document.getElementById('jobModalTitle');
    const form = document.getElementById('jobForm');
    if (!modal || !form) return;

    form.reset();
    this.switchModalTab('modaltab-basic');

    if (job) {
      title.innerHTML = `✏ Edit Callback Job #${job.id}`;
      document.getElementById('jobFormId').value = job.id;
      document.getElementById('jobNameInput').value = job.name || '';

      if (job.scheduleType === 'ONE_OFF') {
        document.getElementById('scheduleTypeOneOff').checked = true;
        this.toggleScheduleTypeFields('ONE_OFF');
        if (job.runAt) {
          const d = new Date(job.runAt);
          d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
          document.getElementById('jobRunAtInput').value = d.toISOString().slice(0, 16);
        }
      } else {
        document.getElementById('scheduleTypeCron').checked = true;
        this.toggleScheduleTypeFields('CRON');
        document.getElementById('jobCronInput').value = job.cronExpression || '0 */5 * * * *';
        document.getElementById('jobTimezoneInput').value = job.timeZone || 'UTC';
      }

      document.getElementById('jobHttpMethod').value = job.httpMethod || 'POST';
      document.getElementById('jobHttpUrl').value = job.httpUrl || '';
      document.getElementById('jobHttpBody').value = job.httpBody || '';
      document.getElementById('jobMaxAttempts').value = job.maxAttempts || 3;
      document.getElementById('jobTimeoutMs').value = job.timeoutMs || 5000;

      // Populate Headers & Query Params
      this.populateKvRows('headersContainer', job.httpHeaders);
      this.populateKvRows('queryParamsContainer', job.httpQueryParams);
    } else {
      title.innerHTML = `⚡ Create Callback Job`;
      document.getElementById('jobFormId').value = '';
      document.getElementById('scheduleTypeCron').checked = true;
      this.toggleScheduleTypeFields('CRON');
      document.getElementById('jobCronInput').value = '0 */5 * * * *';
      document.getElementById('jobTimezoneInput').value = 'UTC';
      document.getElementById('jobHttpMethod').value = 'POST';
      document.getElementById('jobMaxAttempts').value = 3;
      document.getElementById('jobTimeoutMs').value = 5000;

      this.initHeaderRows();
      this.initQueryParamRows();
    }

    modal.classList.add('open');
  }

  editJob(id) {
    const job = this.cachedJobs.find(j => j.id === id);
    if (job) {
      this.openJobModal(job);
    } else {
      fetch(`${API_BASE}/jobs/${id}`)
        .then(res => res.json())
        .then(json => {
          if (json.success && json.data) this.openJobModal(json.data);
        });
    }
  }

  closeJobModal() {
    const modal = document.getElementById('jobModal');
    if (modal) modal.classList.remove('open');
  }

  switchModalTab(tabId) {
    document.querySelectorAll('.modal-nav-tab').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-modaltab') === tabId);
    });
    document.querySelectorAll('.modal-tab-content').forEach(tab => {
      tab.style.display = tab.id === tabId ? 'block' : 'none';
    });
  }

  toggleScheduleTypeFields(type) {
    const cronDiv = document.getElementById('cronFieldsContainer');
    const oneOffDiv = document.getElementById('oneOffFieldsContainer');
    if (cronDiv && oneOffDiv) {
      if (type === 'ONE_OFF') {
        cronDiv.style.display = 'none';
        oneOffDiv.style.display = 'block';
        if (!document.getElementById('jobRunAtInput').value) {
          this.setRunAtOffset(300);
        }
      } else {
        cronDiv.style.display = 'block';
        oneOffDiv.style.display = 'none';
      }
    }
  }

  setCronPreset(expr) {
    const input = document.getElementById('jobCronInput');
    if (input) input.value = expr;
  }

  setRunAtOffset(seconds) {
    const input = document.getElementById('jobRunAtInput');
    if (!input) return;
    const now = new Date(Date.now() + seconds * 1000);
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    input.value = now.toISOString().slice(0, 16);
  }

  initHeaderRows() {
    this.populateKvRows('headersContainer', { 'Content-Type': 'application/json' });
  }

  initQueryParamRows() {
    this.populateKvRows('queryParamsContainer', {});
  }

  addHeaderRow(key = '', val = '') {
    this.appendKvRow('headersContainer', key, val, 'Header Key', 'Header Value');
  }

  addQueryParamRow(key = '', val = '') {
    this.appendKvRow('queryParamsContainer', key, val, 'Param Key', 'Param Value');
  }

  appendKvRow(containerId, key, val, keyPh, valPh) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'kv-row';
    row.innerHTML = `
      <input type="text" class="kv-input kv-key" placeholder="${keyPh}" value="${this.escapeHtml(key)}">
      <input type="text" class="kv-input kv-val" placeholder="${valPh}" value="${this.escapeHtml(val)}">
      <button type="button" class="btn btn-secondary btn-xs" style="color: var(--danger);" onclick="this.parentElement.remove()">✕</button>
    `;
    container.appendChild(row);
  }

  populateKvRows(containerId, map) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';
    if (!map || Object.keys(map).length === 0) {
      this.appendKvRow(containerId, '', '', 'Key', 'Value');
      return;
    }
    Object.entries(map).forEach(([k, v]) => {
      this.appendKvRow(containerId, k, v, 'Key', 'Value');
    });
  }

  extractKvMap(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return {};
    const map = {};
    container.querySelectorAll('.kv-row').forEach(row => {
      const key = row.querySelector('.kv-key').value.trim();
      const val = row.querySelector('.kv-val').value.trim();
      if (key) {
        map[key] = val;
      }
    });
    return map;
  }

  formatJsonBody() {
    const area = document.getElementById('jobHttpBody');
    if (!area || !area.value.trim()) return;
    try {
      const parsed = JSON.parse(area.value);
      area.value = JSON.stringify(parsed, null, 2);
      this.showToast('info', 'JSON Formatted', 'Request body formatted cleanly.');
    } catch (e) {
      this.showToast('error', 'Invalid JSON', 'Body is not valid JSON string: ' + e.message);
    }
  }

  insertBodyTemplate(type) {
    const area = document.getElementById('jobHttpBody');
    if (!area) return;

    if (type === 'payment') {
      area.value = JSON.stringify({
        event: 'payment.succeeded',
        order_id: 'ord_' + Math.floor(100000 + Math.random() * 900000),
        amount: 29900,
        currency: 'USD',
        customer: { id: 'cust_7812', email: 'payer@example.com' }
      }, null, 2);
    } else if (type === 'event') {
      area.value = JSON.stringify({
        event_type: 'user.signup',
        user_id: Math.floor(1000 + Math.random() * 9000),
        timestamp: new Date().toISOString(),
        metadata: { source: 'organic_search', referral: 'direct' }
      }, null, 2);
    }
  }

  async handleJobFormSubmit(e) {
    e.preventDefault();

    const id = document.getElementById('jobFormId').value;
    const name = document.getElementById('jobNameInput').value.trim();
    const scheduleType = document.querySelector('input[name="scheduleType"]:checked').value;
    const cronExpression = document.getElementById('jobCronInput').value.trim();
    const timeZone = document.getElementById('jobTimezoneInput').value;
    const runAtLocal = document.getElementById('jobRunAtInput').value;

    const httpMethod = document.getElementById('jobHttpMethod').value;
    const httpUrl = document.getElementById('jobHttpUrl').value.trim();
    const httpBody = document.getElementById('jobHttpBody').value;
    const maxAttempts = parseInt(document.getElementById('jobMaxAttempts').value, 10);
    const timeoutMs = parseInt(document.getElementById('jobTimeoutMs').value, 10);

    const httpHeaders = this.extractKvMap('headersContainer');
    const httpQueryParams = this.extractKvMap('queryParamsContainer');

    const payload = {
      name,
      scheduleType,
      cronExpression: scheduleType === 'CRON' ? cronExpression : null,
      timeZone: scheduleType === 'CRON' ? timeZone : 'UTC',
      runAt: scheduleType === 'ONE_OFF' && runAtLocal ? new Date(runAtLocal).toISOString() : null,
      httpMethod,
      httpUrl,
      httpHeaders,
      httpQueryParams,
      httpBody,
      maxAttempts,
      timeoutMs
    };

    const saveBtn = document.getElementById('saveJobBtn');
    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = 'Saving...';
    }

    try {
      const url = id ? `${API_BASE}/jobs/${id}` : `${API_BASE}/jobs`;
      const method = id ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const json = await res.json();

      if (json.success) {
        this.showToast('success', 'Job Saved', id ? `Job #${id} updated.` : `Job "${name}" scheduled!`);
        this.closeJobModal();
        this.fetchJobs();
        this.fetchStats();
      } else {
        this.showToast('error', 'Save Failed', json.message || 'Validation error.');
      }
    } catch (err) {
      this.showToast('error', 'Error', 'Failed to save callback job: ' + err.message);
    } finally {
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Callback Job';
      }
    }
  }

  // =========================================================================
  // Toast Notifications
  // =========================================================================
  showToast(type, title, message) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let icon = 'ℹ️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';

    toast.innerHTML = `
      <div class="toast-icon">${icon}</div>
      <div class="toast-content">
        <div class="toast-title">${this.escapeHtml(title)}</div>
        <div class="toast-message">${this.escapeHtml(message)}</div>
      </div>
    `;

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(60px)';
      setTimeout(() => toast.remove(), 250);
    }, 3500);
  }

  // =========================================================================
  // Formatting Helpers & Badges
  // =========================================================================
  renderJobStatusBadge(status) {
    const s = (status || 'ACTIVE').toUpperCase();
    let badgeClass = 'status-active';
    if (s === 'PAUSED') badgeClass = 'status-paused';
    if (s === 'COMPLETED') badgeClass = 'status-completed';
    if (s === 'DELETED') badgeClass = 'status-deleted';

    return `<span class="status-badge ${badgeClass}"><span class="status-dot ${s === 'ACTIVE' ? 'pulse' : ''}"></span>${s}</span>`;
  }

  renderOccurrenceStateBadge(state) {
    const s = (state || 'PENDING').toLowerCase();
    const isPulse = s === 'in_progress' || s === 'dispatched';
    return `<span class="status-badge occ-${s}">${isPulse ? '<span class="status-dot pulse"></span>' : ''}${this.formatEnum(state)}</span>`;
  }

  renderStatusCodeBadge(code) {
    if (!code) return `<span style="color: var(--text-dark);">-</span>`;
    let bg = 'rgba(100, 116, 139, 0.2)';
    let color = '#94a3b8';
    if (code >= 200 && code < 300) { bg = 'var(--success-bg)'; color = '#34d399'; }
    else if (code >= 400 && code < 500) { bg = 'var(--warning-bg)'; color = '#fbbf24'; }
    else if (code >= 500) { bg = 'var(--danger-bg)'; color = '#f87171'; }

    return `<span style="font-family: var(--font-mono); font-size: 0.75rem; font-weight: 700; background-color: ${bg}; color: ${color}; padding: 0.15rem 0.45rem; border-radius: 4px;">${code}</span>`;
  }

  formatEnum(str) {
    if (!str) return '';
    return str.replace(/_/g, ' ');
  }

  formatDateTime(isoStr) {
    if (!isoStr) return '-';
    try {
      const d = new Date(isoStr);
      return d.toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
    } catch {
      return isoStr;
    }
  }

  formatRelativeTime(isoStr) {
    if (!isoStr) return '-';
    try {
      const diffMs = new Date(isoStr).getTime() - Date.now();
      const absDiff = Math.abs(diffMs);
      const isPast = diffMs < 0;

      const sec = Math.floor(absDiff / 1000);
      const min = Math.floor(sec / 60);
      const hr = Math.floor(min / 60);
      const day = Math.floor(hr / 24);

      let text = '';
      if (sec < 60) text = `${sec}s`;
      else if (min < 60) text = `${min}m`;
      else if (hr < 24) text = `${hr}h ${min % 60}m`;
      else text = `${day}d`;

      return isPast ? `${text} ago` : `in ${text}`;
    } catch {
      return isoStr;
    }
  }

  updateText(elementId, text) {
    const el = document.getElementById(elementId);
    if (el) el.textContent = text != null ? text : '';
  }

  escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  escapeJs(str) {
    if (str == null) return '';
    return String(str).replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n');
  }

  copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
      this.showToast('info', 'Copied', 'Content copied to clipboard.');
    });
  }

  roundRect(ctx, x, y, width, height, radius) {
    if (width < 2 * radius) radius = width / 2;
    if (height < 2 * radius) radius = height / 2;
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + width, y, x + width, y + height, radius);
    ctx.arcTo(x + width, y + height, x, y + height, radius);
    ctx.arcTo(x, y + height, x, y, radius);
    ctx.arcTo(x, y, x + width, y, radius);
    ctx.closePath();
  }
}

// Instantiate global app
window.app = new CallbackApp();
