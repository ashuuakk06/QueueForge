import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity, ArrowDownLeft, ArrowRight, ArrowUpRight, Bell, Boxes, Check, CheckCircle2,
  ChevronDown, ChevronLeft, ChevronRight, CircleHelp, Clock3, Copy, Cpu, Database,
  Ellipsis, ExternalLink, FileClock, Filter, Gauge, GitBranch, Layers3, LoaderCircle,
  LogOut, Menu, MoreHorizontal, Pause, Play, Plus, Radio, RefreshCw, Search, Send,
  Settings2, ShieldCheck, Sparkles, Square, TerminalSquare, Timer, Trash2, TriangleAlert,
  Workflow, X, Zap,
} from 'lucide-react';

const API = '/api';
const JOB_TYPES = ['ECHO', 'TRANSFORM', 'DELAY', 'SIMULATE_FAILURE'];
const EMPTY_DASHBOARD = {
  totalJobs: 0, jobsToday: 0, queued: 0, waiting: 0, running: 0, completed: 0, failed: 0,
  redisReady: 0, redisProcessing: 0, workers: 4, recentJobs: [], activity: [],
};

async function request(path, { token, ...options } = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const error = new Error(data?.error || data?.message || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}

function formatDate(value, options = {}) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {
    month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', ...options,
  }).format(new Date(value));
}

function timeAgo(value) {
  if (!value) return 'Not started';
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function shortId(id) { return id ? `#${id.slice(0, 8)}` : '—'; }

function StatusBadge({ status }) {
  const variants = {
    COMPLETED: ['complete', 'Completed'], RUNNING: ['running', 'Running'], QUEUED: ['queued', 'Queued'],
    WAITING: ['waiting', 'Waiting'], SCHEDULED: ['waiting', 'Scheduled'], RETRY_WAIT: ['retry', 'Retrying'],
    FAILED: ['failed', 'Failed'], CANCELLED: ['cancelled', 'Cancelled'],
  };
  const [kind, label] = variants[status] || ['queued', status || 'Unknown'];
  return <span className={`status-badge ${kind}`}><i />{label}</span>;
}

function BrandMark({ small = false }) {
  return <span className={`brand-mark${small ? ' small' : ''}`}><span /><span /><span /><span /></span>;
}

function IconButton({ title, children, onClick, className = '', disabled = false }) {
  return <button className={`icon-button ${className}`} title={title} aria-label={title} onClick={onClick} disabled={disabled}>{children}</button>;
}

function Sidebar({ page, setPage, user, jobsToday, onLogout, mobileOpen, closeMobile }) {
  const nav = [
    { id: 'dashboard', label: 'Overview', icon: Gauge },
    { id: 'jobs', label: 'Jobs', icon: Layers3, count: null },
    { id: 'pipelines', label: 'Pipelines', icon: Workflow },
    { id: 'workers', label: 'Workers', icon: Cpu },
  ];
  return <>
    {mobileOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={closeMobile} />}
    <aside className={`sidebar${mobileOpen ? ' open' : ''}`}>
      <div className="sidebar-brand"><BrandMark /><span>queueforge<span className="brand-dot">.</span></span><button className="mobile-close" onClick={closeMobile}><X size={18} /></button></div>
      <div className="workspace-switcher">
        <div className="workspace-icon">{(user?.name || 'Q').slice(0, 1).toUpperCase()}</div><div className="workspace-copy"><b>{(user?.name || 'My').split(' ')[0]}’s workspace</b><span>Personal workspace</span></div><ChevronDown size={15} />
      </div>
      <div className="nav-label">WORKSPACE</div>
      <nav className="primary-nav">
        {nav.map(({ id, label, icon: Icon, count }) => <button key={id} className={`nav-item${page === id ? ' active' : ''}`} onClick={() => { setPage(id); closeMobile(); }}>
          <Icon size={17} strokeWidth={1.9} /><span>{label}</span>{count != null && <span className="nav-count">{count}</span>}
        </button>)}
      </nav>
      <div className="nav-label nav-label-row">DEVELOP<span className="nav-label-rule" /></div>
      <nav className="primary-nav">
        <button className={`nav-item${page === 'api' ? ' active' : ''}`} onClick={() => { setPage('api'); closeMobile(); }}><TerminalSquare size={17} /><span>API reference</span><ExternalLink size={13} className="nav-external" /></button>
        <button className={`nav-item${page === 'settings' ? ' active' : ''}`} onClick={() => { setPage('settings'); closeMobile(); }}><Settings2 size={17} /><span>Settings</span></button>
      </nav>
      <div className="sidebar-bottom">
        <div className="usage-box"><div className="usage-title"><span>Jobs submitted</span><span>Today</span></div><div className="usage-number"><b>{jobsToday || 0}</b><span> this workspace</span></div><div className="usage-track"><i style={{ width: `${Math.min((jobsToday || 0) * 4, 100)}%` }} /></div><p>Live count · resets at midnight.</p></div>
        <button className="help-link"><CircleHelp size={16} /> Help & support <ArrowRight size={14} /></button>
        <div className="profile-row"><div className="avatar">{(user?.name || 'A').slice(0, 1).toUpperCase()}</div><div className="profile-copy"><b>{user?.name || 'Workspace member'}</b><span>{user?.email || 'demo@queueforge.dev'}</span></div><button title="Sign out" className="logout-button" onClick={onLogout}><LogOut size={16} /></button></div>
      </div>
    </aside>
  </>;
}

function TopBar({ page, setPage, user, onNewJob, onMenu }) {
  const titles = { dashboard: 'Overview', jobs: 'Jobs', pipelines: 'Pipelines', workers: 'Workers', settings: 'Settings', api: 'API reference' };
  const date = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'long', day: 'numeric' }).format(new Date());
  return <header className="topbar">
    <div className="topbar-left"><button className="mobile-menu" onClick={onMenu}><Menu size={19} /></button><div className="breadcrumbs"><span>Workspace</span><ChevronRight size={14} /><b>{titles[page] || 'Overview'}</b></div></div>
    <div className="topbar-right"><span className="today-date">{date}</span><span className="topbar-divider" /><span className="connection-pill"><i /> Live updates active</span><IconButton title="Notifications"><Bell size={17} /><i className="notification-dot" /></IconButton><button className="top-avatar" onClick={() => setPage('settings')} title={user?.name}>{(user?.name || 'A').slice(0, 1).toUpperCase()}</button><button className="top-create" onClick={onNewJob}><Plus size={16} />New job</button></div>
  </header>;
}

function PageHeading({ eyebrow, title, subtitle, action }) {
  return <div className="page-heading"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{subtitle}</p></div>{action}</div>;
}

function MetricCard({ label, value, icon: Icon, tone, detail, trend, delta }) {
  return <article className="metric-card"><div className="metric-top"><span>{label}</span><span className={`metric-icon ${tone}`}><Icon size={17} strokeWidth={1.9} /></span></div><div className="metric-value">{value.toLocaleString()}</div><div className="metric-foot"><span className={trend === 'down' ? 'trend down' : 'trend'}>{trend === 'down' ? <ArrowDownLeft size={13} /> : <ArrowUpRight size={13} />}{delta}</span><span>{detail}</span></div></article>;
}

function ActivityChart({ activity }) {
  const width = 760; const height = 210; const padX = 8; const padY = 15;
  const rows = activity?.length ? activity : Array.from({ length: 12 }, (_, i) => ({ hour: `${String(i * 2).padStart(2, '0')}:00`, submitted: 0, completed: 0 }));
  const max = Math.max(5, ...rows.map((point) => Math.max(point.submitted, point.completed)));
  const x = (i) => padX + (i * (width - padX * 2)) / Math.max(rows.length - 1, 1);
  const y = (value) => height - padY - (value / max) * (height - padY * 2);
  const line = (key) => rows.map((point, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(point[key])}`).join(' ');
  const area = `${line('submitted')} L ${x(rows.length - 1)} ${height - padY} L ${x(0)} ${height - padY} Z`;
  return <div className="chart-wrap">
    <div className="chart-legend"><span><i className="legend-dot submitted-dot" />Jobs submitted</span><span><i className="legend-dot complete-dot" />Completed</span></div>
    <svg className="activity-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Jobs submitted and completed over the last 12 hours">
      <defs><linearGradient id="submitted-fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#b8e5cf" stopOpacity=".36" /><stop offset="100%" stopColor="#b8e5cf" stopOpacity="0" /></linearGradient></defs>
      {[0, .33, .66, 1].map((n) => <g key={n}><line x1="0" x2={width} y1={padY + n * (height - padY * 2)} y2={padY + n * (height - padY * 2)} stroke="#e9ede9" strokeDasharray={n === 1 ? '0' : '3 5'} /><text x="0" y={padY + n * (height - padY * 2) - 4} fill="#a0aaa4" fontSize="10" fontFamily="DM Mono">{Math.round(max * (1 - n))}</text></g>)}
      <path d={area} fill="url(#submitted-fill)" /><path d={line('submitted')} fill="none" stroke="#76be98" strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" /><path d={line('completed')} fill="none" stroke="#5364bd" strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" />
      {rows.map((point, i) => <g key={point.hour}><circle cx={x(i)} cy={y(point.submitted)} r="3" fill="#fff" stroke="#76be98" strokeWidth="2" /><circle cx={x(i)} cy={y(point.completed)} r="3" fill="#fff" stroke="#5364bd" strokeWidth="2" /></g>)}
      {rows.filter((_, i) => i % 2 === 0 || i === rows.length - 1).map((point) => { const i = rows.indexOf(point); return <text key={`${point.hour}-label`} x={x(i)} y={height - 1} textAnchor={i === 0 ? 'start' : i === rows.length - 1 ? 'end' : 'middle'} fill="#98a29d" fontSize="10" fontFamily="DM Mono">{point.hour}</text>; })}
    </svg>
  </div>;
}

function JobsTable({ jobs, onSelect, onRetry, onCancel, compact = false }) {
  if (!jobs?.length) return <div className="empty-state"><div className="empty-icon"><Layers3 size={20} /></div><b>No jobs here yet</b><span>Submit a job and it will appear in this list.</span></div>;
  return <div className={`table-scroll${compact ? ' compact' : ''}`}><table className="jobs-table"><thead><tr><th>JOB</th><th>TYPE</th><th>STATUS</th><th>ATTEMPTS</th><th>CREATED</th><th aria-label="Actions" /></tr></thead><tbody>
    {jobs.map((job) => <tr key={job.id} onClick={() => onSelect(job)} className="job-row"><td><div className="job-name-cell"><span className={`type-glyph ${job.type?.toLowerCase()}`}>{job.type === 'TRANSFORM' ? <Sparkles size={14} /> : job.type === 'DELAY' ? <Clock3 size={14} /> : job.type === 'SIMULATE_FAILURE' ? <TriangleAlert size={14} /> : <Zap size={14} />}</span><span><b>{job.name}</b><small>{shortId(job.id)} · {job.dependsOn?.length ? `${job.dependsOn.length} prerequisite${job.dependsOn.length > 1 ? 's' : ''}` : 'No dependencies'}</small></span></div></td><td><span className="type-label">{job.type?.replace('_', ' ')}</span></td><td><StatusBadge status={job.status} /></td><td><span className="attempts"><b>{job.attempts}</b><span> / {job.maxAttempts}</span></span></td><td><span className="created-date">{formatDate(job.createdAt)}</span></td><td><div className="row-actions" onClick={(event) => event.stopPropagation()}>
      {(job.status === 'FAILED' || job.status === 'CANCELLED') && <IconButton title="Retry job" onClick={() => onRetry(job)}><RefreshCw size={15} /></IconButton>}
      {['QUEUED', 'WAITING', 'SCHEDULED', 'RETRY_WAIT'].includes(job.status) && <IconButton title="Cancel job" onClick={() => onCancel(job)}><X size={15} /></IconButton>}
      <IconButton title="View details" onClick={() => onSelect(job)}><MoreHorizontal size={17} /></IconButton></div></td></tr>)}
  </tbody></table></div>;
}

function DashboardPage({ dashboard, jobs, setPage, onSelect, onRetry, onCancel, onNewJob, user }) {
  const firstName = (user?.name || 'there').split(' ')[0];
  const completeRate = dashboard.totalJobs ? Math.round((dashboard.completed / dashboard.totalJobs) * 100) : 0;
  return <div className="page-content">
    <PageHeading eyebrow="YOUR WORKSPACE · LIVE" title={`Good morning, ${firstName}.`} subtitle="Here’s what’s happening across your queues today." action={<button className="button-primary" onClick={onNewJob}><Plus size={16} />Create a job</button>} />
    <div className="metric-grid">
      <MetricCard label="Jobs today" value={dashboard.jobsToday} icon={Layers3} tone="mint" detail="submitted since midnight" delta="Live" />
      <MetricCard label="In the queue" value={dashboard.queued + dashboard.waiting} icon={Clock3} tone="amber" detail={`${dashboard.queued} ready · ${dashboard.waiting} waiting`} delta="Live" />
      <MetricCard label="Currently running" value={dashboard.running} icon={Activity} tone="blue" detail={`${dashboard.workers} workers configured`} delta="Live" />
      <MetricCard label="Completed" value={dashboard.completed} icon={CheckCircle2} tone="lavender" detail={`${completeRate}% of all jobs`} delta={`${dashboard.failed} failed`} trend="down" />
    </div>
    <div className="dashboard-grid">
      <section className="panel activity-panel"><div className="panel-heading"><div><h2>Queue activity</h2><p>Submissions and successful runs · last 12 hours</p></div><button className="select-pill">Last 12 hours<ChevronDown size={14} /></button></div><ActivityChart activity={dashboard.activity} /></section>
      <section className="panel worker-summary"><div className="panel-heading"><div><h2>Worker pool</h2><p>Execution capacity at a glance</p></div><button className="text-action" onClick={() => setPage('workers')}>View all <ArrowRight size={14} /></button></div>
        <div className="pool-ring-wrap"><div className="pool-ring" style={{ background: `conic-gradient(#72bb90 0deg ${dashboard.workers ? Math.round((dashboard.running / dashboard.workers) * 360) : 0}deg, #e9f0ea ${dashboard.workers ? Math.round((dashboard.running / dashboard.workers) * 360) : 0}deg 360deg)` }}><div><b>{dashboard.running}<small>/{dashboard.workers}</small></b><span>worker slots busy</span></div></div><div className="pool-note"><span className="online-dot" />{dashboard.workerInstances} worker process{dashboard.workerInstances === 1 ? '' : 'es'} online<p>Worker containers poll Redis and recover expired leases automatically.</p></div></div>
        <div className="pool-foot"><span><i className="mini-dot busy-dot" />Busy <b>{dashboard.running}</b></span><span><i className="mini-dot idle-dot" />Idle <b>{Math.max(0, dashboard.workers - dashboard.running)}</b></span><span><i className="mini-dot queued-dot" />Ready <b>{dashboard.redisReady}</b></span></div>
      </section>
    </div>
    <section className="panel recent-panel"><div className="panel-heading"><div><h2>Recent jobs</h2><p>The latest work submitted to your workspace</p></div><button className="text-action" onClick={() => setPage('jobs')}>All jobs <ArrowRight size={14} /></button></div><JobsTable jobs={dashboard.recentJobs} onSelect={onSelect} onRetry={onRetry} onCancel={onCancel} compact /></section>
    <div className="dashboard-footnote"><span><Radio size={13} />Refreshing every 5 seconds</span><span>Queue depth <b>{dashboard.redisReady}</b><i className="foot-sep">·</i>Processing <b>{dashboard.redisProcessing}</b></span></div>
  </div>;
}

function JobsPage({ jobs, onSelect, onRetry, onCancel, onNewJob }) {
  const [tab, setTab] = useState('ALL');
  const [search, setSearch] = useState('');
  const filtered = useMemo(() => jobs.filter((job) => (tab === 'ALL' || job.status === tab) && `${job.name} ${job.type} ${job.id}`.toLowerCase().includes(search.toLowerCase())), [jobs, tab, search]);
  const tabs = ['ALL', 'RUNNING', 'QUEUED', 'COMPLETED', 'FAILED'];
  return <div className="page-content">
    <PageHeading eyebrow="EXECUTION LOG" title="Jobs" subtitle="Submit, inspect, and manage every job in your workspace." action={<button className="button-primary" onClick={onNewJob}><Plus size={16} />New job</button>} />
    <section className="panel jobs-panel"><div className="jobs-toolbar"><div className="filter-tabs">{tabs.map((name) => <button key={name} className={tab === name ? 'selected' : ''} onClick={() => setTab(name)}>{name === 'ALL' ? 'All jobs' : name[0] + name.slice(1).toLowerCase()}{name === 'ALL' && <span>{jobs.length}</span>}</button>)}</div><div className="table-tools"><label className="search-field"><Search size={15} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search jobs…" /></label><button className="tool-button"><Filter size={15} /><span>Filter</span></button></div></div>
      <JobsTable jobs={filtered} onSelect={onSelect} onRetry={onRetry} onCancel={onCancel} /><div className="table-footer"><span>Showing <b>{filtered.length}</b> of <b>{jobs.length}</b> jobs</span><div><button className="pagination-button" disabled><ChevronLeft size={15} /></button><span className="page-number">1</span><button className="pagination-button" disabled><ChevronRight size={15} /></button></div></div>
    </section>
    <div className="jobs-info-row"><div className="info-icon"><ShieldCheck size={17} /></div><p><b>Reliable by design.</b> Jobs persist in PostgreSQL, coordinate through Redis, and recover automatically if a worker stops responding.</p><button onClick={() => window.open('/actuator/health', '_blank')}>System health <ExternalLink size={13} /></button></div>
  </div>;
}

function WorkersPage({ dashboard }) {
  const [activeTab, setActiveTab] = useState('workers');
  const total = dashboard.workers || 4;
  const running = Math.min(dashboard.running || 0, total);
  const workers = Array.from({ length: total }, (_, index) => ({ id: `worker-${String(index + 1).padStart(2, '0')}`, state: index < running ? 'busy' : 'idle' }));
  return <div className="page-content">
    <PageHeading eyebrow="COMPUTE" title="Workers" subtitle="Monitor the worker processes that claim and execute queued jobs." action={<span className="live-label"><i />LIVE STATUS</span>} />
    <div className="worker-stats"><div className="worker-stat"><span>Worker threads online</span><b>{total}</b><small><i className="online-dot" />{dashboard.workerInstances} worker process{dashboard.workerInstances === 1 ? '' : 'es'}</small></div><div className="worker-stat"><span>Currently executing</span><b>{running}</b><small>Across all worker threads</small></div><div className="worker-stat"><span>Redis ready queue</span><b>{dashboard.redisReady}</b><small>Waiting to be claimed</small></div><div className="worker-stat"><span>Processing list</span><b>{dashboard.redisProcessing}</b><small>In-flight leases</small></div></div>
    <section className="panel workers-panel"><div className="panel-heading"><div><h2>Worker capacity</h2><p>{dashboard.workerInstances} worker process{dashboard.workerInstances === 1 ? '' : 'es'} · {total} execution slots online</p></div><div className="live-label"><i />HEALTHY</div></div><div className="worker-cards">{workers.map((worker, index) => <article className={`worker-card ${worker.state}`} key={worker.id}><div className="worker-card-top"><span className="worker-avatar"><Cpu size={17} /></span><span className={`worker-state ${worker.state}`}><i />{worker.state === 'busy' ? 'Executing' : 'Idle'}</span></div><h3>{worker.id}</h3><p>Redis-coordinated worker thread</p><div className="worker-card-meta"><span><span>Concurrency</span><b>1 thread</b></span><span><span>Heartbeat</span><b>Live</b></span></div>{worker.state === 'busy' && dashboard.recentJobs.filter((job) => job.status === 'RUNNING')[index] && <div className="worker-current"><span>RUNNING JOB</span><b>{dashboard.recentJobs.filter((job) => job.status === 'RUNNING')[index]?.name || 'Executing task'}</b></div>}</article>)}</div></section>
    <section className="panel redis-panel"><div className="panel-heading"><div><h2>Redis coordination</h2><p>Queue state shared between API and worker processes</p></div><span className="connected-pill"><i />CONNECTED</span></div><div className="redis-grid"><div><div className="redis-key"><span className="redis-ledger"><Database size={15} /></span><span><code>queueforge:jobs:ready</code><small>Ready list · FIFO</small></span></div><b>{dashboard.redisReady}</b><span>items</span></div><ArrowRight size={17} className="redis-flow-arrow" /><div><div className="redis-key"><span className="redis-ledger processing"><Activity size={15} /></span><span><code>queueforge:jobs:processing</code><small>In-flight leases · recoverable</small></span></div><b>{dashboard.redisProcessing}</b><span>items</span></div></div></section>
  </div>;
}

function PipelinesPage({ jobs, onSelect, onNewJob }) {
  const pipelineJobs = jobs.filter((job) => job.dependsOn?.length || jobs.some((other) => other.dependsOn?.includes(job.id)));
  const rootJobs = pipelineJobs.filter((job) => !job.dependsOn?.length);
  const childJobs = pipelineJobs.filter((job) => job.dependsOn?.length);
  return <div className="page-content">
    <PageHeading eyebrow="DEPENDENCY AWARE" title="Pipelines" subtitle="Jobs wait for their prerequisites and move forward as dependencies complete." action={<button className="button-primary" onClick={onNewJob}><Plus size={16} />Build a pipeline</button>} />
    <section className="pipeline-hero"><div className="pipeline-hero-copy"><div className="pipeline-badge"><GitBranch size={15} />DEPENDENCY GRAPH</div><h2>Work that moves<br />in the right order.</h2><p>QueueForge resolves dependencies before dispatching a task. A job only runs after each prerequisite succeeds.</p><div className="pipeline-feature-list"><span><Check size={14} />Dependency-aware scheduling</span><span><Check size={14} />Automatic retry and backoff</span><span><Check size={14} />Failed prerequisites stop the chain</span></div></div><div className="pipeline-visual"><div className="pipeline-rail" /><div className="flow-node node-one"><span className="flow-node-icon mint"><Boxes size={16} /></span><span><b>Normalize records</b><small>TRANSFORM · 240 ms</small></span><CheckCircle2 size={17} className="flow-success" /></div><div className="flow-node node-two"><span className="flow-node-icon violet"><Send size={16} /></span><span><b>Send notification</b><small>ECHO · waits for job above</small></span><Clock3 size={17} className="flow-wait" /></div><div className="flow-node node-three"><span className="flow-node-icon peach"><FileClock size={16} /></span><span><b>Archive report</b><small>DELAY · after notification</small></span><Clock3 size={17} className="flow-wait" /></div><div className="flow-caption"><span className="mini-dot complete-dot" />Job succeeded <span className="mini-dot queued-dot" />Waiting for dependency</div></div></section>
    <section className="panel dependency-panel"><div className="panel-heading"><div><h2>Dependency jobs</h2><p>{pipelineJobs.length} jobs connected to a prerequisite</p></div><span className="dependency-count"><GitBranch size={14} />{childJobs.length} downstream</span></div>
      {pipelineJobs.length ? <div className="dependency-list">{rootJobs.map((job) => <div className="dependency-root" key={job.id}><div className="dependency-job" onClick={() => onSelect(job)}><span className="dependency-icon"><Layers3 size={15} /></span><span><b>{job.name}</b><small>{shortId(job.id)} · prerequisite</small></span><StatusBadge status={job.status} /></div>{pipelineJobs.filter((child) => child.dependsOn?.includes(job.id)).map((child) => <div className="dependency-child" key={child.id}><span className="branch-stem" /><div className="dependency-job" onClick={() => onSelect(child)}><span className="dependency-icon child"><ArrowDownLeft size={15} /></span><span><b>{child.name}</b><small>{shortId(child.id)} · depends on {job.name}</small></span><StatusBadge status={child.status} /></div></div>)}</div>)}</div> : <div className="empty-state"><div className="empty-icon"><Workflow size={20} /></div><b>No dependency chains yet</b><span>Create a job and choose an existing prerequisite to build your first pipeline.</span><button className="button-secondary" onClick={onNewJob}><Plus size={15} />Create dependent job</button></div>}
    </section>
  </div>;
}

function SettingsPage({ user, onLogout }) {
  const [copied, setCopied] = useState(false);
  async function copyEmail() { await navigator.clipboard?.writeText(user?.email || 'demo@queueforge.dev'); setCopied(true); setTimeout(() => setCopied(false), 1800); }
  return <div className="page-content">
    <PageHeading eyebrow="WORKSPACE PREFERENCES" title="Settings" subtitle="Your account and QueueForge environment details." />
    <div className="settings-layout"><section className="panel settings-card"><div className="settings-section-head"><div><h2>Profile</h2><p>Account details for the current workspace member.</p></div><button className="button-secondary" onClick={onLogout}><LogOut size={14} />Sign out</button></div><div className="profile-large"><div className="avatar large">{(user?.name || 'A').slice(0, 1).toUpperCase()}</div><div><b>{user?.name || 'Workspace member'}</b><span>Workspace administrator</span></div><span className="active-account"><i />ACTIVE</span></div><div className="settings-fields"><label>Display name<input value={user?.name || ''} readOnly /></label><label>Email address<div className="input-with-action"><input value={user?.email || ''} readOnly /><button onClick={copyEmail}><Copy size={14} />{copied ? 'Copied' : 'Copy'}</button></div></label><label>Account ID<input value={user?.id || ''} readOnly /></label></div></section>
      <section className="panel settings-card"><div className="settings-section-head"><div><h2>Infrastructure</h2><p>Connected services for this QueueForge instance.</p></div><span className="connected-pill"><i />HEALTHY</span></div><div className="infra-list"><div><span className="infra-icon pg"><Database size={17} /></span><span><b>PostgreSQL</b><small>Authoritative job store · Flyway migrations</small></span><span className="infra-status">Connected</span></div><div><span className="infra-icon redis"><Zap size={17} /></span><span><b>Redis</b><small>Queue coordination · ready and processing lists</small></span><span className="infra-status">Connected</span></div><div><span className="infra-icon mongo"><Activity size={17} /></span><span><b>MongoDB</b><small>Append-only job event timeline</small></span><span className="infra-status">Connected</span></div></div></section>
    </div>
    <section className="panel settings-card security-card"><div className="settings-section-head"><div><h2>Security & access</h2><p>Authentication and operational defaults.</p></div><ShieldCheck size={20} className="security-shield" /></div><div className="security-grid"><div><span>Authentication</span><b>JWT bearer tokens</b><small>Token expires 24 hours after sign-in</small></div><div><span>Password storage</span><b>BCrypt · cost factor 12</b><small>Passwords are never returned by the API</small></div><div><span>Worker recovery</span><b>2 minute lease timeout</b><small>Stale jobs are retried on the next recovery pass</small></div></div></section>
  </div>;
}

function ApiPage() {
  const sample = `curl -X POST http://localhost:8080/api/jobs \\\n+  -H "Authorization: Bearer <your-token>" \\\n+  -H "Content-Type: application/json" \\\n+  -d '{
    "name": "Normalize customer data",
    "type": "TRANSFORM",
    "payload": { "text": "acme supply order 4821" },
    "maxAttempts": 4,
    "retryDelaySeconds": 10,
    "priority": 7
  }'`;
  return <div className="page-content"><PageHeading eyebrow="DEVELOPER TOOLS" title="API reference" subtitle="QueueForge exposes a REST API for submitting and managing work." />
    <div className="api-layout"><section className="panel api-methods"><div className="panel-heading"><div><h2>Core endpoints</h2><p>All job endpoints require a JWT bearer token.</p></div><span className="api-version">v1</span></div>{[
      ['POST', '/api/auth/register', 'Create an account'], ['POST', '/api/auth/login', 'Issue an access token'], ['GET', '/api/dashboard', 'Read live queue metrics'], ['GET', '/api/jobs', 'List your jobs'], ['POST', '/api/jobs', 'Submit a job'], ['POST', '/api/jobs/{id}/retry', 'Retry a failed job'], ['POST', '/api/jobs/{id}/cancel', 'Cancel pending work'], ['GET', '/api/jobs/{id}/events', 'Read execution history'],
    ].map(([method, path, description]) => <div className="api-endpoint" key={path}><code className={`method ${method.toLowerCase()}`}>{method}</code><code className="endpoint-path">{path}</code><span>{description}</span></div>)}</section>
      <section className="panel code-panel"><div className="panel-heading"><div><h2>Submit your first job</h2><p>Example request using a transformed payload.</p></div><span className="language-tag">HTTP</span></div><pre><code>{sample}</code></pre><div className="supported-types"><b>Built-in handlers</b><span><Zap size={13} />ECHO</span><span><Sparkles size={13} />TRANSFORM</span><span><Clock3 size={13} />DELAY</span><span><TriangleAlert size={13} />SIMULATE_FAILURE</span></div></section></div>
  </div>;
}

function JobModal({ job, jobs, onClose, onSubmit, busy }) {
  const [name, setName] = useState('');
  const [type, setType] = useState('TRANSFORM');
  const [payload, setPayload] = useState('{\n  "text": "hello queueforge"\n}');
  const [attempts, setAttempts] = useState(3);
  const [retryDelay, setRetryDelay] = useState(10);
  const [priority, setPriority] = useState(5);
  const [runAt, setRunAt] = useState('');
  const [dependsOn, setDependsOn] = useState([]);
  const [formError, setFormError] = useState('');
  useEffect(() => {
    const examples = {
      ECHO: '{\n  "message": "hello queueforge",\n  "source": "dashboard"\n}',
      TRANSFORM: '{\n  "text": "hello queueforge"\n}',
      DELAY: '{\n  "milliseconds": 1500\n}',
      SIMULATE_FAILURE: '{\n  "failUntilAttempt": 1\n}',
    };
    setPayload(examples[type]);
  }, [type]);
  function toggleDependency(id) { setDependsOn((current) => current.includes(id) ? current.filter((value) => value !== id) : [...current, id]); }
  async function submit(event) {
    event.preventDefault(); setFormError('');
    let parsed;
    try { parsed = JSON.parse(payload); } catch { setFormError('Payload must be valid JSON.'); return; }
    if (!name.trim()) { setFormError('Give this job a name.'); return; }
    await onSubmit({ name: name.trim(), type, payload: parsed, maxAttempts: Number(attempts), retryDelaySeconds: Number(retryDelay), priority: Number(priority), ...(runAt ? { runAt: new Date(runAt).toISOString() } : {}), dependsOn });
  }
  const dependencyOptions = jobs.filter((candidate) => !['FAILED', 'CANCELLED'].includes(candidate.status));
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><div className="modal-card job-modal"><div className="modal-heading"><div><span className="modal-kicker"><Plus size={13} />NEW EXECUTION</span><h2>Submit a job</h2><p>Queue work now or schedule it for later.</p></div><IconButton title="Close" onClick={onClose}><X size={18} /></IconButton></div>
    <form onSubmit={submit}>
      <label className="form-label">Job name<input autoFocus maxLength={160} value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Process customer import" /></label>
      <div className="form-two-col"><label className="form-label">Handler<select value={type} onChange={(event) => setType(event.target.value)}>{JOB_TYPES.map((item) => <option value={item} key={item}>{item.replace('_', ' ')}</option>)}</select><small>{type === 'ECHO' ? 'Returns the payload as the job result.' : type === 'TRANSFORM' ? 'Uppercases the text field and returns its length.' : type === 'DELAY' ? 'Waits for a bounded duration to demonstrate async work.' : 'Fails a chosen number of attempts to demonstrate retry.'}</small></label><label className="form-label">Priority<span className="select-with-help"><select value={priority} onChange={(event) => setPriority(event.target.value)}>{[10, 9, 8, 7, 6, 5, 4, 3, 2, 1].map((value) => <option key={value}>{value}</option>)}</select><small>Higher priority runs first.</small></span></label></div>
      <label className="form-label">Payload <span className="label-suffix">JSON object</span><textarea className="json-input" rows={5} spellCheck="false" value={payload} onChange={(event) => setPayload(event.target.value)} /></label>
      <div className="form-three-col"><label className="form-label">Max attempts<input type="number" min="1" max="10" value={attempts} onChange={(event) => setAttempts(event.target.value)} /></label><label className="form-label">Retry delay (sec)<input type="number" min="1" max="3600" value={retryDelay} onChange={(event) => setRetryDelay(event.target.value)} /></label><label className="form-label">Run at <input type="datetime-local" value={runAt} onChange={(event) => setRunAt(event.target.value)} /><small>Leave empty to run now.</small></label></div>
      {dependencyOptions.length > 0 && <div className="form-label dependency-picker"><span>Wait for jobs <span className="label-suffix">optional</span></span><div className="dependency-options">{dependencyOptions.slice(0, 8).map((candidate) => <label key={candidate.id} className="dependency-option"><input type="checkbox" checked={dependsOn.includes(candidate.id)} onChange={() => toggleDependency(candidate.id)} /><span className="check-square"><Check size={12} /></span><span><b>{candidate.name}</b><small>{shortId(candidate.id)} · {candidate.status.toLowerCase()}</small></span></label>)}</div><small>The new job starts after each selected job completes successfully.</small></div>}
      {formError && <div className="form-error"><TriangleAlert size={15} />{formError}</div>}
      <div className="modal-footer"><span><ShieldCheck size={14} />Persistent · retryable · observable</span><div><button className="button-secondary" type="button" onClick={onClose}>Cancel</button><button className="button-primary" type="submit" disabled={busy}>{busy ? <LoaderCircle size={15} className="spin" /> : <Plus size={15} />}{busy ? 'Submitting…' : 'Submit job'}</button></div></div>
    </form></div></div>;
}

function JobDetails({ job, token, onClose, onRetry, onCancel }) {
  const [events, setEvents] = useState([]);
  const [loadingEvents, setLoadingEvents] = useState(true);
  useEffect(() => {
    let alive = true;
    const loadEvents = () => request(`/jobs/${job.id}/events`, { token })
      .then((data) => { if (alive) setEvents(data || []); })
      .catch(() => {})
      .finally(() => { if (alive) setLoadingEvents(false); });
    loadEvents();
    const timer = setInterval(loadEvents, 5000);
    return () => { alive = false; clearInterval(timer); };
  }, [job.id, token]);
  return <div className="modal-backdrop detail-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><aside className="details-drawer"><div className="drawer-head"><div><span className="drawer-kicker">JOB DETAILS</span><h2>{job.name}</h2><span className="drawer-id">{job.id}</span></div><IconButton title="Close" onClick={onClose}><X size={18} /></IconButton></div>
    <div className="drawer-status"><StatusBadge status={job.status} /><span>{job.type?.replace('_', ' ')}</span>{job.priority >= 8 && <span className="priority-tag">HIGH PRIORITY</span>}</div>
    {(job.status === 'FAILED' || job.status === 'CANCELLED' || job.errorMessage) && <div className="error-callout"><TriangleAlert size={16} /><span><b>{job.status === 'FAILED' ? 'Job failed' : 'Execution note'}</b><small>{job.errorMessage || 'This job was cancelled before execution.'}</small></span></div>}
    <div className="detail-stats"><div><span>Attempts</span><b>{job.attempts}<small> / {job.maxAttempts}</small></b></div><div><span>Priority</span><b>{job.priority}<small> / 10</small></b></div><div><span>Retry delay</span><b>{job.retryDelaySeconds}<small> sec</small></b></div></div>
    <section className="drawer-section"><div className="drawer-section-title"><b>Payload</b><span>JSON</span></div><pre className="payload-block">{JSON.stringify(job.payload || {}, null, 2)}</pre></section>
    {job.result && <section className="drawer-section"><div className="drawer-section-title"><b>Result</b><span>JSON</span></div><pre className="payload-block result-block">{JSON.stringify(job.result, null, 2)}</pre></section>}
    {job.dependsOn?.length > 0 && <section className="drawer-section"><div className="drawer-section-title"><b>Prerequisites</b><span>{job.dependsOn.length}</span></div><div className="prerequisite-chips">{job.dependsOn.map((id) => <span key={id}><GitBranch size={13} />{shortId(id)}</span>)}</div></section>}
    <section className="drawer-section timeline-section"><div className="drawer-section-title"><b>Execution timeline</b><span>MongoDB event log</span></div>{loadingEvents ? <div className="timeline-loading"><LoaderCircle className="spin" size={16} />Loading history…</div> : events.length ? <div className="timeline">{events.map((event) => <div className="timeline-item" key={event.id}><span className={`timeline-marker ${event.event}`}><i /></span><div><b>{event.event.replaceAll('_', ' ')}</b><p>{event.message}</p><time>{formatDate(event.occurredAt)}</time></div></div>)}</div> : <p className="timeline-empty">No execution events have been recorded.</p>}</section>
    <div className="drawer-footer">{(job.status === 'FAILED' || job.status === 'CANCELLED') && <button className="button-secondary" onClick={() => onRetry(job)}><RefreshCw size={14} />Retry job</button>}{['QUEUED', 'WAITING', 'SCHEDULED', 'RETRY_WAIT'].includes(job.status) && <button className="button-secondary danger-outline" onClick={() => onCancel(job)}><X size={14} />Cancel job</button>}<button className="button-primary" onClick={onClose}>Done</button></div>
  </aside></div>;
}

function LoginPage({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('demo@queueforge.dev');
  const [password, setPassword] = useState('QueueForge!2026');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const data = await request(`/auth/${mode}`, { method: 'POST', body: JSON.stringify(mode === 'login' ? { email, password } : { name, email, password }) });
      onLogin(data);
    } catch (exception) { setError(exception.message); } finally { setBusy(false); }
  }
  return <main className="login-screen"><div className="login-left"><div className="login-brand"><BrandMark /><span>queueforge<span className="brand-dot">.</span></span></div><div className="login-story"><span className="login-overline"><span />DISTRIBUTED JOB EXECUTION</span><h1>Work moves<br />better <em>together.</em></h1><p>One reliable place to submit, schedule, and observe every background job.</p><div className="login-proof"><span><Check size={14} />Dependency aware</span><span><Check size={14} />Retries built in</span><span><Check size={14} />Workers recover</span></div></div><div className="login-visual"><div className="login-grid" /><div className="login-flow flow-a"><span className="flow-badge"><Zap size={15} /></span><span><b>Import customer data</b><small>COMPLETED · 240 ms</small></span><CheckCircle2 size={17} /></div><div className="login-flow flow-b"><span className="flow-badge violet"><Workflow size={15} /></span><span><b>Send notification</b><small>WAITING ON DEPENDENCY</small></span><Clock3 size={16} /></div><div className="login-orbit orbit-one" /><div className="login-orbit orbit-two" /><div className="login-caption"><i />LIVE QUEUE · REDIS COORDINATED</div></div><div className="login-left-footer"><span>BUILT FOR THE WORK THAT HAPPENS AFTER THE REQUEST.</span><span>© QUEUEFORGE 2026</span></div></div>
    <div className="login-right"><form className="login-form" onSubmit={submit}><span className="login-form-eyebrow">WELCOME BACK</span><h2>{mode === 'login' ? 'Sign in to QueueForge' : 'Create your workspace'}</h2><p>{mode === 'login' ? 'Your queues are ready when you are.' : 'Start processing jobs in a few seconds.'}</p>{mode === 'register' && <label>Full name<input autoComplete="name" value={name} onChange={(event) => setName(event.target.value)} placeholder="Alex Morgan" required /></label>}<label>Email address<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="login-error"><TriangleAlert size={15} />{error}</div>}<button className="button-primary login-submit" type="submit" disabled={busy}>{busy ? <LoaderCircle size={16} className="spin" /> : <ArrowRight size={16} />}{busy ? 'Connecting…' : mode === 'login' ? 'Sign in' : 'Create account'}</button><div className="login-switch">{mode === 'login' ? 'New to QueueForge?' : 'Already have an account?'} <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Create account' : 'Sign in'}</button></div><div className="login-demo"><div><Sparkles size={15} /><span><b>Demo workspace</b><small>Explore a seeded account with live jobs</small></span></div><button type="button" onClick={() => { setMode('login'); setEmail('demo@queueforge.dev'); setPassword('QueueForge!2026'); }}>Use demo <ArrowRight size={13} /></button></div><div className="login-secure"><ShieldCheck size={14} />Passwords are encrypted · Sessions use signed JWT</div></form><div className="login-right-foot"><span>QUEUEFORGE ENGINE</span><span>JAVA · REDIS · POSTGRESQL</span></div></div></main>;
}

function Toast({ toast, onClose }) {
  if (!toast) return null;
  return <div className={`toast ${toast.type || 'success'}`}><span className="toast-icon">{toast.type === 'error' ? <TriangleAlert size={16} /> : <CheckCircle2 size={16} />}</span><span>{toast.message}</span><button onClick={onClose}><X size={14} /></button></div>;
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('queueforge.token'));
  const [user, setUser] = useState(() => { try { return JSON.parse(localStorage.getItem('queueforge.user')); } catch { return null; } });
  const [page, setPage] = useState('dashboard');
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD);
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [selectedJob, setSelectedJob] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState(null);
  const [mobileOpen, setMobileOpen] = useState(false);

  const loadData = useCallback(async () => {
    if (!token) return;
    try {
      const [summary, jobList] = await Promise.all([request('/dashboard', { token }), request('/jobs', { token })]);
      setDashboard({ ...EMPTY_DASHBOARD, ...summary }); setJobs(jobList || []); setApiError('');
    } catch (error) {
      if (error.status === 401) { localStorage.removeItem('queueforge.token'); localStorage.removeItem('queueforge.user'); setToken(null); setUser(null); }
      else setApiError(error.message || 'The QueueForge API could not be reached.');
    } finally { setLoading(false); }
  }, [token]);

  useEffect(() => {
    if (!token) { setLoading(false); return; }
    let mounted = true;
    const run = () => { if (mounted) loadData(); };
    run(); const timer = setInterval(run, 5000);
    return () => { mounted = false; clearInterval(timer); };
  }, [token, loadData]);

  useEffect(() => {
    if (token && !user) request('/auth/me', { token }).then((value) => { setUser(value); localStorage.setItem('queueforge.user', JSON.stringify(value)); }).catch(() => {});
  }, [token, user]);

  useEffect(() => { if (toast) { const timer = setTimeout(() => setToast(null), 3600); return () => clearTimeout(timer); } }, [toast]);

  function login(data) { localStorage.setItem('queueforge.token', data.token); localStorage.setItem('queueforge.user', JSON.stringify(data.user)); setToken(data.token); setUser(data.user); setLoading(true); setPage('dashboard'); setToast({ message: `Welcome back, ${data.user.name.split(' ')[0]}.` }); }
  function logout() { localStorage.removeItem('queueforge.token'); localStorage.removeItem('queueforge.user'); setToken(null); setUser(null); setDashboard(EMPTY_DASHBOARD); setJobs([]); }

  async function submitJob(payload) {
    setSubmitting(true);
    try {
      const created = await request('/jobs', { token, method: 'POST', body: JSON.stringify(payload) });
      setShowCreate(false); setToast({ message: `“${created.name}” added to the queue.` }); setSelectedJob(created); await loadData();
    } catch (error) { setToast({ type: 'error', message: error.message }); }
    finally { setSubmitting(false); }
  }

  async function jobAction(job, action) {
    try {
      const updated = await request(`/jobs/${job.id}/${action}`, { token, method: 'POST' });
      setSelectedJob((current) => current?.id === updated.id ? updated : current);
      setToast({ message: action === 'retry' ? `“${job.name}” is queued for retry.` : `“${job.name}” was cancelled.` });
      await loadData();
    } catch (error) { setToast({ type: 'error', message: error.message }); }
  }

  if (!token) return <><LoginPage onLogin={login} /><Toast toast={toast} onClose={() => setToast(null)} /></>;

  return <div className="app-shell"><Sidebar page={page} setPage={setPage} user={user} jobsToday={dashboard.jobsToday} onLogout={logout} mobileOpen={mobileOpen} closeMobile={() => setMobileOpen(false)} /><div className="main-shell"><TopBar page={page} setPage={setPage} user={user} onNewJob={() => setShowCreate(true)} onMenu={() => setMobileOpen(true)} />
    {apiError && <div className="api-warning"><TriangleAlert size={15} /><span>API connection issue: {apiError}</span><button onClick={loadData}><RefreshCw size={14} />Retry</button></div>}
    {loading ? <div className="loading-page"><span className="loader-mark"><BrandMark /></span><span>Connecting to your queue…</span></div> : <>
      {page === 'dashboard' && <DashboardPage dashboard={dashboard} jobs={jobs} setPage={setPage} onSelect={setSelectedJob} onRetry={(job) => jobAction(job, 'retry')} onCancel={(job) => jobAction(job, 'cancel')} onNewJob={() => setShowCreate(true)} user={user} />}
      {page === 'jobs' && <JobsPage jobs={jobs} onSelect={setSelectedJob} onRetry={(job) => jobAction(job, 'retry')} onCancel={(job) => jobAction(job, 'cancel')} onNewJob={() => setShowCreate(true)} />}
      {page === 'workers' && <WorkersPage dashboard={dashboard} />}
      {page === 'pipelines' && <PipelinesPage jobs={jobs} onSelect={setSelectedJob} onNewJob={() => setShowCreate(true)} />}
      {page === 'settings' && <SettingsPage user={user} onLogout={logout} />}
      {page === 'api' && <ApiPage />}
    </>}
    <footer className="app-footer"><span><BrandMark small />QUEUEFORGE ENGINE</span><span>POSTGRESQL <i /> REDIS <i /> MONGODB</span><span>BUILT FOR RELIABLE WORK</span></footer>
  </div>
  {showCreate && <JobModal jobs={jobs} onClose={() => setShowCreate(false)} onSubmit={submitJob} busy={submitting} />}
  {selectedJob && <JobDetails job={jobs.find((job) => job.id === selectedJob.id) || selectedJob} token={token} onClose={() => setSelectedJob(null)} onRetry={(job) => jobAction(job, 'retry')} onCancel={(job) => jobAction(job, 'cancel')} />}
  <Toast toast={toast} onClose={() => setToast(null)} />
  </div>;
}
