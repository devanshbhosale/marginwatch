const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function fmtINR(value) {
  const n = Number(value);
  return Number.isFinite(n) ? inr.format(n) : '-';
}

// One decimal only when fractional: 9.4 -> "9.4", 12 -> "12".
export function fmtPct(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '-';
  const r = Math.round(n * 10) / 10;
  return Number.isInteger(r) ? String(r) : r.toFixed(1);
}

function parts(value) {
  if (!value) return null;
  const d = new Date(value);
  if (isNaN(d.getTime())) return null;
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  return { dd, mmm: MONTHS[d.getMonth()], yyyy: d.getFullYear(), mm, hh, mi };
}

// DD-MMM-YYYY, e.g. 29-Aug-2026
export function fmtDate(value) {
  const p = parts(value);
  return p ? `${p.dd}-${p.mmm}-${p.yyyy}` : '-';
}

// DD-MMM-YYYY HH:mm
export function fmtDateTime(value) {
  const p = parts(value);
  return p ? `${p.dd}-${p.mmm}-${p.yyyy} ${p.hh}:${p.mi}` : '-';
}

// 29 Aug, for chart axes
export function fmtDayShort(value) {
  const p = parts(value ? `${value}T00:00:00` : value);
  return p ? `${p.dd} ${p.mmm}` : '-';
}

// Maps a PriceFileStatus to its chip tone class.
export function fileStatusTone(status) {
  switch (status) {
    case 'PARSED':
      return 'green';
    case 'UPLOADED':
    case 'PARSING':
      return 'amber';
    case 'FAILED':
      return 'red';
    default:
      return 'grey';
  }
}

// Maps a RowStatus to its chip tone class.
export function rowStatusTone(status) {
  switch (status) {
    case 'PENDING':
      return 'amber';
    case 'APPLIED':
      return 'green';
    case 'SKIPPED':
      return 'grey';
    default:
      return 'grey';
  }
}

// Maps a RowFlag to its chip tone class.
export function flagTone(flag) {
  switch (flag) {
    case 'OK':
      return 'green';
    case 'BREACH':
      return 'red';
    case 'NEW_SKU':
      return 'amber';
    case 'NOT_FOUND':
    case 'DUPLICATE':
      return 'grey';
    default:
      return 'grey';
  }
}

export function FileStatusChip({ status }) {
  const spinning = status === 'UPLOADED' || status === 'PARSING';
  return (
    <span className={`chip chip-${fileStatusTone(status)}`}>
      {spinning && <span className="spin" />}
      {status}
    </span>
  );
}

export function FlagChip({ flag }) {
  if (!flag) return null;
  return <span className={`chip chip-${flagTone(flag)}`}>{flag}</span>;
}

export function RowStatusChip({ status }) {
  return <span className={`chip chip-${rowStatusTone(status)}`}>{status}</span>;
}

export { MONTHS };
