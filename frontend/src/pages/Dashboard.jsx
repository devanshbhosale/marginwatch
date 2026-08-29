import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Chart from 'chart.js/auto';
import api, { apiError } from '../api';
import { fmtINR, fmtPct, fmtDate, fmtDayShort, FlagChip } from '../format';

const TEAL = '#0f766e';
const TEAL_FILL = 'rgba(15, 118, 110, 0.12)';
const FONT_SANS = "'IBM Plex Sans', sans-serif";
const FONT_MONO = "'IBM Plex Mono', monospace";

export default function Dashboard() {
  const [d, setD] = useState(null);
  const [err, setErr] = useState('');
  const lineRef = useRef(null);
  const barRef = useRef(null);

  useEffect(() => {
    api.get('/dashboard').then((r) => setD(r.data)).catch((e) => setErr(apiError(e)));
  }, []);

  useEffect(() => {
    if (!d || !lineRef.current) return;
    const days = (d.changesPerDay || []).slice(-14);
    const chart = new Chart(lineRef.current, {
      type: 'line',
      data: {
        labels: days.map((s) => fmtDayShort(s.date)),
        datasets: [
          {
            label: 'Cost changes applied',
            data: days.map((s) => Number(s.applied) || 0),
            borderColor: TEAL,
            backgroundColor: TEAL_FILL,
            borderWidth: 2,
            pointRadius: 2,
            fill: true,
            tension: 0.25,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            titleFont: { family: FONT_SANS, size: 11.5 },
            bodyFont: { family: FONT_MONO, size: 11.5 },
          },
        },
        scales: {
          x: { grid: { color: '#efeeea' }, ticks: { font: { family: FONT_MONO, size: 10.5 }, color: '#6a6f76' } },
          y: {
            beginAtZero: true,
            ticks: { precision: 0, font: { family: FONT_MONO, size: 10.5 }, color: '#6a6f76' },
            grid: { color: '#efeeea' },
          },
        },
      },
    });
    return () => chart.destroy();
  }, [d]);

  useEffect(() => {
    if (!d || !barRef.current) return;
    const cats = d.marginByCategory || [];
    const chart = new Chart(barRef.current, {
      type: 'bar',
      data: {
        labels: cats.map((c) => c.category),
        datasets: [
          {
            label: 'Avg margin %',
            data: cats.map((c) => Number(c.avgMarginPct) || 0),
            backgroundColor: TEAL,
            borderRadius: 2,
            maxBarThickness: 34,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            titleFont: { family: FONT_SANS, size: 11.5 },
            bodyFont: { family: FONT_MONO, size: 11.5 },
            callbacks: { label: (ctx) => ` Avg margin: ${fmtPct(ctx.parsed.y)}%` },
          },
        },
        scales: {
          x: { grid: { color: '#efeeea' }, ticks: { font: { family: FONT_MONO, size: 10.5 }, color: '#6a6f76' } },
          y: {
            beginAtZero: true,
            ticks: {
              font: { family: FONT_MONO, size: 10.5 },
              color: '#6a6f76',
              callback: (v) => `${v}%`,
            },
            grid: { color: '#efeeea' },
          },
        },
      },
    });
    return () => chart.destroy();
  }, [d]);

  if (err) return <div className="callout callout-red">{err}</div>;
  if (!d) return <p className="muted">Loading dashboard...</p>;

  const stats = d.stats || {};
  const heat = d.heatmap || [];
  const movers = d.topMovers || [];

  const vendors = [...new Set(heat.map((h) => h.vendorCode))].sort();
  const categories = [...new Set(heat.map((h) => h.category))].sort();
  const maxBreaches = Math.max(1, ...heat.map((h) => h.breaches || 0));
  const breachesFor = (v, c) => {
    const hit = heat.find((h) => h.vendorCode === v && h.category === c);
    return hit ? hit.breaches || 0 : 0;
  };

  return (
    <div>
      <div className="stat-row stat-row-5">
        <div className="stat">
          <div className="stat-label">SKUs monitored</div>
          <div className="stat-value">{stats.skusMonitored ?? 0}</div>
          <div className="stat-sub">in the catalog</div>
        </div>
        <div className="stat">
          <div className="stat-label">Pending rows</div>
          <div className="stat-value">{stats.pendingRows ?? 0}</div>
          <div className="stat-sub">awaiting review</div>
        </div>
        <div className="stat">
          <div className="stat-label">Pending breaches</div>
          <div className="stat-value" style={(stats.pendingBreaches || 0) > 0 ? { color: 'var(--red)' } : undefined}>
            {stats.pendingBreaches ?? 0}
          </div>
          <div className="stat-sub">need admin override</div>
        </div>
        <div className="stat">
          <div className="stat-label">Margin at risk</div>
          <div className="stat-value" style={(stats.marginAtRisk || 0) < 0 ? { color: 'var(--red)' } : undefined}>
            {fmtINR(stats.marginAtRisk || 0)}
          </div>
          <div className="stat-sub">pending breach impact</div>
        </div>
        <div className="stat">
          <div className="stat-label">Files · 30 days</div>
          <div className="stat-value">{stats.filesLast30d ?? 0}</div>
          <div className="stat-sub">vendor price files</div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-h">
            <span className="card-title">Cost changes applied per day — last 14 days</span>
          </div>
          <div className="card-b">
            <div className="chart-box">
              <canvas ref={lineRef} />
            </div>
          </div>
        </div>
        <div className="card">
          <div className="card-h">
            <span className="card-title">Avg margin by category</span>
          </div>
          <div className="card-b">
            <div className="chart-box">
              <canvas ref={barRef} />
            </div>
          </div>
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-h">
          <span className="card-title">Breach heatmap — vendors × categories</span>
        </div>
        <div className="table-scroll">
          {heat.length === 0 ? (
            <div className="empty">No breaches recorded — all applied costs respect the margin floors.</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Vendor</th>
                  {categories.map((c) => (
                    <th key={c} className="center">{c}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {vendors.map((v) => (
                  <tr key={v}>
                    <td className="mono">{v}</td>
                    {categories.map((c) => {
                      const n = breachesFor(v, c);
                      const alpha = n === 0 ? 0 : 0.12 + (n / maxBreaches) * 0.78;
                      return (
                        <td
                          key={c}
                          className="heatmap-cell"
                          style={
                            n > 0
                              ? {
                                  background: `rgba(179, 38, 30, ${alpha.toFixed(2)})`,
                                  color: alpha > 0.5 ? '#fff' : 'var(--red)',
                                  fontWeight: 600,
                                }
                              : undefined
                          }
                        >
                          {n > 0 ? n : ''}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-h">
          <span className="card-title">Top movers — latest applied cost changes</span>
        </div>
        <div className="table-scroll">
          {movers.length === 0 ? (
            <div className="empty">No cost changes applied yet.</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Vendor</th>
                  <th>Old cost → New cost</th>
                  <th className="right">Change</th>
                  <th>Flag</th>
                  <th>File date</th>
                </tr>
              </thead>
              <tbody>
                {movers.map((m, i) => (
                  <tr key={i}>
                    <td className="mono">
                      {m.fileId ? <Link to={`/files/${m.fileId}`}>{m.sku}</Link> : m.sku}
                    </td>
                    <td>
                      <span className="chip chip-wh">{m.vendorCode}</span>
                    </td>
                    <td className="mono nowrap">
                      {fmtINR(m.oldCost)} → {fmtINR(m.newCost)}
                    </td>
                    <td className={'right mono nowrap ' + (m.changePct > 0 ? 'delta-up' : m.changePct < 0 ? 'delta-down' : '')}>
                      {m.changePct > 0 ? '+' : ''}
                      {fmtPct(m.changePct)}%
                    </td>
                    <td>
                      <FlagChip flag={m.flag} />
                    </td>
                    <td className="nowrap">{fmtDate(m.fileUploadedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
