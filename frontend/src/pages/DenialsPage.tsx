import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  CreateDenialInput,
  DenialCategory,
  DenialStatus,
  DenialSummary,
  DenialUrgency,
  DenialWorklistItem,
  FileAppealInput,
  PageResponse,
  Payer,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  DateText,
  EmptyState,
  ErrorAlert,
  Loading,
  Modal,
  Money,
  Pagination,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

const CATEGORIES: { value: DenialCategory; label: string }[] = [
  { value: 'AUTHORIZATION', label: 'Prior Authorization' },
  { value: 'MEDICAL_NECESSITY', label: 'Medical Necessity' },
  { value: 'TIMELY_FILING', label: 'Timely Filing' },
  { value: 'BILLING_CODING', label: 'Billing & Coding' },
  { value: 'COVERAGE_ELIGIBILITY', label: 'Coverage & Eligibility' },
  { value: 'DUPLICATE', label: 'Duplicate Claim' },
  { value: 'OTHER', label: 'Other Administrative' },
];

const STATUSES: { value: DenialStatus; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'APPEALED', label: 'Appealed' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'WRITTEN_OFF', label: 'Written Off' },
];

const URGENCIES: { value: DenialUrgency; label: string }[] = [
  { value: 'OVERDUE', label: 'Overdue (< 0 days)' },
  { value: 'CRITICAL', label: 'Critical (≤ 7 days)' },
  { value: 'WARNING', label: 'Warning (8–30 days)' },
  { value: 'NORMAL', label: 'Normal (> 30 days)' },
];

function UrgencyBadge({ urgency, daysRemaining }: { urgency: DenialUrgency; daysRemaining: number }) {
  let bg = '#e8effc';
  let color = '#1f5fd6';
  let label = `${daysRemaining}d left`;

  if (urgency === 'OVERDUE') {
    bg = '#fdeaea';
    color = '#b32020';
    label = `${Math.abs(daysRemaining)}d overdue`;
  } else if (urgency === 'CRITICAL') {
    bg = '#fdf1dc';
    color = '#8a5a06';
    label = `${daysRemaining}d left (urgent)`;
  } else if (urgency === 'WARNING') {
    bg = '#fef9e7';
    color = '#7d6608';
    label = `${daysRemaining}d left`;
  } else {
    bg = '#e2f4ee';
    color = '#13795b';
  }

  return (
    <span
      className="badge"
      style={{
        backgroundColor: bg,
        color,
        fontWeight: 600,
        fontSize: '11px',
        padding: '2px 8px',
        borderRadius: '999px',
        display: 'inline-block',
      }}
    >
      {label}
    </span>
  );
}

function DenialStatusBadge({ status }: { status: DenialStatus }) {
  let bg = '#eceff3';
  let color = '#4a5462';

  if (status === 'OPEN') {
    bg = '#fdf1dc';
    color = '#8a5a06';
  } else if (status === 'APPEALED') {
    bg = '#e6eefa';
    color = '#1d4373';
  } else if (status === 'RESOLVED') {
    bg = '#e2f4ee';
    color = '#13795b';
  } else if (status === 'WRITTEN_OFF') {
    bg = '#fdeaea';
    color = '#7f1717';
  }

  return (
    <span
      className="badge"
      style={{
        backgroundColor: bg,
        color,
        fontWeight: 600,
        fontSize: '11px',
        padding: '2px 8px',
        borderRadius: '999px',
        display: 'inline-block',
      }}
    >
      {status.replace('_', ' ')}
    </span>
  );
}

export default function DenialsPage() {
  const { canWrite } = useAuth();

  // Summary state
  const [summary, setSummary] = useState<DenialSummary | null>(null);

  // Filters state
  const [payers, setPayers] = useState<Payer[]>([]);
  const [filterPayerId, setFilterPayerId] = useState<string>('');
  const [filterCategory, setFilterCategory] = useState<string>('');
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [filterUrgency, setFilterUrgency] = useState<string>('');

  // Worklist state
  const [page, setPage] = useState<number>(0);
  const [data, setData] = useState<PageResponse<DenialWorklistItem> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Create Denial Modal
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [createClaimId, setCreateClaimId] = useState<string>('');
  const [createDenialCode, setCreateDenialCode] = useState<string>('');
  const [createRemittanceCode, setCreateRemittanceCode] = useState<string>('');
  const [createCategory, setCreateCategory] = useState<DenialCategory>('AUTHORIZATION');
  const [createReason, setCreateReason] = useState<string>('');
  const [createDenialDate, setCreateDenialDate] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [createDeadline, setCreateDeadline] = useState<string>(() => {
    const d = new Date();
    d.setDate(d.getDate() + 60);
    return d.toISOString().slice(0, 10);
  });
  const [createAmount, setCreateAmount] = useState<string>('');
  const [createBusy, setCreateBusy] = useState<boolean>(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Quick Appeal Modal
  const [appealTarget, setAppealTarget] = useState<DenialWorklistItem | null>(null);
  const [appealNarrative, setAppealNarrative] = useState<string>('');
  const [appealDeadlineDays, setAppealDeadlineDays] = useState<number>(45);
  const [appealBusy, setAppealBusy] = useState<boolean>(false);
  const [appealError, setAppealError] = useState<string | null>(null);

  // Load payers for filter
  useEffect(() => {
    api<Payer[]>('/payers')
      .then(setPayers)
      .catch(() => {});
  }, []);

  // Load summary metrics
  const loadSummary = useCallback(async () => {
    try {
      const res = await api<DenialSummary>('/denials/summary');
      setSummary(res);
    } catch {
      // Non-fatal
    }
  }, []);

  // Load worklist
  const loadWorklist = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const query: Record<string, string | number> = {
        page,
        size: 15,
        sort: 'appealDeadline,asc',
      };
      if (filterPayerId) query.payerId = Number(filterPayerId);
      if (filterCategory) query.category = filterCategory;
      if (filterStatus) query.status = filterStatus;
      if (filterUrgency) query.urgency = filterUrgency;

      const res = await api<PageResponse<DenialWorklistItem>>('/denials', { query });
      setData(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not load denials worklist.');
    } finally {
      setLoading(false);
    }
  }, [page, filterPayerId, filterCategory, filterStatus, filterUrgency]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    void loadWorklist();
  }, [loadWorklist]);

  const resetFilters = () => {
    setFilterPayerId('');
    setFilterCategory('');
    setFilterStatus('');
    setFilterUrgency('');
    setPage(0);
  };

  const handleCreateDenial = async () => {
    if (!createClaimId || !createDenialCode || !createReason || !createDenialDate || !createDeadline || !createAmount) {
      setCreateError('Please complete all required fields.');
      return;
    }
    const amt = parseFloat(createAmount);
    if (isNaN(amt) || amt < 0) {
      setCreateError('Please enter a valid positive amount.');
      return;
    }

    setCreateBusy(true);
    setCreateError(null);
    try {
      const payload: CreateDenialInput = {
        claimId: Number(createClaimId),
        denialCode: createDenialCode.trim().toUpperCase(),
        remittanceCode: createRemittanceCode.trim().toUpperCase() || null,
        denialCategory: createCategory,
        reason: createReason.trim(),
        denialDate: createDenialDate,
        appealDeadline: createDeadline,
        amount: amt,
      };
      await api('/denials', { method: 'POST', body: payload });
      setShowCreateModal(false);
      // Reset form
      setCreateClaimId('');
      setCreateDenialCode('');
      setCreateRemittanceCode('');
      setCreateReason('');
      setCreateAmount('');
      // Reload both
      await Promise.all([loadSummary(), loadWorklist()]);
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.detail : 'Failed to record denial.');
    } finally {
      setCreateBusy(false);
    }
  };

  const handleFileAppeal = async () => {
    if (!appealTarget) return;
    if (!appealNarrative.trim() || appealNarrative.trim().length < 5) {
      setAppealError('Please provide a substantive appeal narrative (at least 5 characters).');
      return;
    }

    setAppealBusy(true);
    setAppealError(null);
    try {
      const payload: FileAppealInput = {
        denialId: appealTarget.id,
        narrative: appealNarrative.trim(),
        responseDeadlineDays: appealDeadlineDays || 45,
      };
      await api(`/claims/${appealTarget.claimId}/appeal`, { method: 'POST', body: payload });
      setAppealTarget(null);
      setAppealNarrative('');
      await Promise.all([loadSummary(), loadWorklist()]);
    } catch (err) {
      setAppealError(err instanceof ApiError ? err.detail : 'Failed to file appeal.');
    } finally {
      setAppealBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Denials & Appeals Worklist"
        subtitle="Revenue cycle denial management, timely filing deadlines, and multi-level appeal tracking"
        actions={
          canWrite && (
            <button className="btn" onClick={() => setShowCreateModal(true)}>
              + Record Denial
            </button>
          )
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        {!canWrite && (
          <div className="alert alert-info">
            You are signed in as a viewer. Denial and appeal actions are restricted to billing and administrative roles.
          </div>
        )}

        {/* KPI Summary Row */}
        {summary && (
          <div className="grid grid-4" style={{ marginBottom: 20 }}>
            <div className="card stat">
              <div className="stat-label">Total Open Denials</div>
              <div className="stat-value">{summary.totalOpenDenials}</div>
              <div className="stat-note">
                Outstanding: <Money value={summary.totalOpenDeniedAmount} />
              </div>
            </div>

            <div className="card stat">
              <div className="stat-label" style={{ color: summary.criticalDeadlines > 0 ? '#8a5a06' : undefined }}>
                Critical Deadlines
              </div>
              <div
                className="stat-value"
                style={{ color: summary.criticalDeadlines > 0 ? '#8a5a06' : undefined }}
              >
                {summary.criticalDeadlines}
              </div>
              <div className="stat-note">≤ 7 days until deadline expires</div>
            </div>

            <div className="card stat">
              <div className="stat-label" style={{ color: summary.overdueDeadlines > 0 ? '#b32020' : undefined }}>
                Overdue Deadlines
              </div>
              <div
                className="stat-value"
                style={{ color: summary.overdueDeadlines > 0 ? '#b32020' : undefined }}
              >
                {summary.overdueDeadlines}
              </div>
              <div className="stat-note">Timely filing window elapsed</div>
            </div>

            <div className="card stat">
              <div className="stat-label">Warning / Normal</div>
              <div className="stat-value">
                {summary.warningDeadlines + summary.normalDeadlines}
              </div>
              <div className="stat-note">
                {summary.warningDeadlines} warning &middot; {summary.normalDeadlines} normal
              </div>
            </div>
          </div>
        )}

        {/* Category Breakdown Chips */}
        {summary && Object.keys(summary.byCategory).length > 0 && (
          <div className="card" style={{ marginBottom: 20, padding: '12px 16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>
                Open by Category:
              </span>
              {Object.entries(summary.byCategory).map(([catKey, catData]) => (
                <button
                  key={catKey}
                  type="button"
                  onClick={() => {
                    setFilterCategory(filterCategory === catKey ? '' : catKey);
                    setPage(0);
                  }}
                  className="pill"
                  style={{
                    cursor: 'pointer',
                    background: filterCategory === catKey ? 'var(--accent-soft)' : 'var(--surface-2)',
                    borderColor: filterCategory === catKey ? 'var(--accent)' : 'var(--border)',
                    color: filterCategory === catKey ? 'var(--accent)' : 'var(--text)',
                    padding: '4px 9px',
                    fontWeight: 500,
                  }}
                >
                  {catKey.replace('_', ' ')}: <strong>{catData.count}</strong> (<Money value={catData.amount} />)
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Filter Bar */}
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="filters">
            <div className="field">
              <label htmlFor="filterPayer">Payer</label>
              <select
                id="filterPayer"
                value={filterPayerId}
                onChange={(e) => {
                  setFilterPayerId(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Payers</option>
                {payers.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="filterCat">Category</label>
              <select
                id="filterCat"
                value={filterCategory}
                onChange={(e) => {
                  setFilterCategory(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Categories</option>
                {CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="filterStatus">Status</label>
              <select
                id="filterStatus"
                value={filterStatus}
                onChange={(e) => {
                  setFilterStatus(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Statuses</option>
                {STATUSES.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="filterUrgency">Urgency</label>
              <select
                id="filterUrgency"
                value={filterUrgency}
                onChange={(e) => {
                  setFilterUrgency(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Urgencies</option>
                {URGENCIES.map((u) => (
                  <option key={u.value} value={u.value}>
                    {u.label}
                  </option>
                ))}
              </select>
            </div>

            {(filterPayerId || filterCategory || filterStatus || filterUrgency) && (
              <div className="field" style={{ alignSelf: 'flex-end' }}>
                <button type="button" className="btn btn-secondary btn-sm" onClick={resetFilters}>
                  Clear Filters
                </button>
              </div>
            )}
          </div>

          {/* Worklist Table */}
          {loading ? (
            <Loading label="Loading denials worklist" />
          ) : !data || data.content.length === 0 ? (
            <EmptyState
              title="No denials found"
              hint={
                filterPayerId || filterCategory || filterStatus || filterUrgency
                  ? 'Try clearing or modifying the active filters.'
                  : 'No claim denials have been recorded yet.'
              }
            />
          ) : (
            <>
              <table>
                <thead>
                  <tr>
                    <th>Claim #</th>
                    <th>Patient</th>
                    <th>Payer</th>
                    <th>Denial Code</th>
                    <th>Category</th>
                    <th className="num">Denied Amt</th>
                    <th>Denial Date</th>
                    <th>Deadline</th>
                    <th>Urgency</th>
                    <th>Status</th>
                    <th className="right">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link to={`/claims/${item.claimId}`} style={{ fontWeight: 600 }}>
                          {item.claimNumber}
                        </Link>
                      </td>
                      <td>
                        <div>{item.patientName}</div>
                        <div className="faint mono" style={{ fontSize: 11 }}>
                          {item.patientMrn}
                        </div>
                      </td>
                      <td>{item.payerName}</td>
                      <td>
                        <span className="mono" style={{ fontWeight: 600 }}>
                          {item.denialCode}
                        </span>
                        {item.remittanceCode && (
                          <span className="faint mono" style={{ marginLeft: 6, fontSize: 11 }}>
                            /{item.remittanceCode}
                          </span>
                        )}
                        <div className="muted" style={{ fontSize: 11, maxWidth: 220, whiteSpace: 'normal' }}>
                          {item.reason}
                        </div>
                      </td>
                      <td>
                        <span className="pill">{item.denialCategory.replace('_', ' ')}</span>
                      </td>
                      <td className="num" style={{ fontWeight: 600 }}>
                        <Money value={item.amount} />
                      </td>
                      <td>
                        <DateText value={item.denialDate} />
                      </td>
                      <td>
                        <DateText value={item.appealDeadline} />
                      </td>
                      <td>
                        <UrgencyBadge urgency={item.urgency} daysRemaining={item.daysRemaining} />
                      </td>
                      <td>
                        <DenialStatusBadge status={item.status} />
                      </td>
                      <td className="right">
                        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
                          <Link to={`/claims/${item.claimId}`} className="btn btn-secondary btn-sm">
                            View Claim
                          </Link>
                          <Link to={`/claims/${item.claimId}#attachments`} className="btn btn-secondary btn-sm" title="View or attach supporting documentation">
                            Docs
                          </Link>
                          {canWrite && item.status === 'OPEN' && item.daysRemaining >= 0 && (
                            <button
                              type="button"
                              className="btn btn-sm"
                              onClick={() => {
                                setAppealTarget(item);
                                setAppealNarrative('');
                                setAppealDeadlineDays(45);
                                setAppealError(null);
                              }}
                            >
                              Appeal
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <Pagination
                page={page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onChange={setPage}
              />
            </>
          )}
        </div>
      </div>

      {/* Record Denial Modal */}
      {showCreateModal && (
        <Modal
          title="Record Claim Denial"
          onClose={() => setShowCreateModal(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                onClick={() => setShowCreateModal(false)}
                disabled={createBusy}
              >
                Cancel
              </button>
              <button className="btn" onClick={handleCreateDenial} disabled={createBusy}>
                {createBusy ? 'Recording...' : 'Record Denial'}
              </button>
            </>
          }
        >
          {createError && <div className="alert alert-error">{createError}</div>}

          <div className="field">
            <label htmlFor="createClaimId">Claim ID *</label>
            <input
              id="createClaimId"
              type="number"
              placeholder="e.g. 4"
              value={createClaimId}
              onChange={(e) => setCreateClaimId(e.target.value)}
              autoFocus
            />
            <div className="field-hint">The internal ID of the claim that received a denial remittance.</div>
          </div>

          <div className="grid grid-2">
            <div className="field">
              <label htmlFor="createDenialCode">Denial Code (CARC) *</label>
              <input
                id="createDenialCode"
                type="text"
                placeholder="e.g. CO-197 or PR-204"
                maxLength={10}
                value={createDenialCode}
                onChange={(e) => setCreateDenialCode(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="createRemittanceCode">Remittance Advice Code (RARC)</label>
              <input
                id="createRemittanceCode"
                type="text"
                placeholder="e.g. N130"
                maxLength={10}
                value={createRemittanceCode}
                onChange={(e) => setCreateRemittanceCode(e.target.value)}
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="createCat">Denial Category *</label>
            <select
              id="createCat"
              value={createCategory}
              onChange={(e) => setCreateCategory(e.target.value as DenialCategory)}
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="field">
            <label htmlFor="createReason">Denial Reason Description *</label>
            <textarea
              id="createReason"
              rows={3}
              placeholder="Detailed payer denial justification as stated on 835 ERA..."
              value={createReason}
              onChange={(e) => setCreateReason(e.target.value)}
            />
          </div>

          <div className="grid grid-3">
            <div className="field">
              <label htmlFor="createDenialDate">Denial Date *</label>
              <input
                id="createDenialDate"
                type="date"
                value={createDenialDate}
                onChange={(e) => setCreateDenialDate(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="createDeadline">Appeal Deadline *</label>
              <input
                id="createDeadline"
                type="date"
                value={createDeadline}
                onChange={(e) => setCreateDeadline(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="createAmount">Denied Amount ($) *</label>
              <input
                id="createAmount"
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                value={createAmount}
                onChange={(e) => setCreateAmount(e.target.value)}
              />
            </div>
          </div>
        </Modal>
      )}

      {/* File Appeal Quick Modal */}
      {appealTarget && (
        <Modal
          title={`File Appeal — Claim ${appealTarget.claimNumber}`}
          onClose={() => setAppealTarget(null)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                onClick={() => setAppealTarget(null)}
                disabled={appealBusy}
              >
                Cancel
              </button>
              <button className="btn" onClick={handleFileAppeal} disabled={appealBusy}>
                {appealBusy ? 'Filing Appeal...' : 'Submit Appeal to Payer'}
              </button>
            </>
          }
        >
          {appealError && <div className="alert alert-error">{appealError}</div>}

          <div style={{ marginBottom: 14, padding: '10px 12px', background: 'var(--surface-2)', borderRadius: 'var(--radius-s)' }}>
            <div style={{ fontSize: 13 }}>
              <strong>Denial:</strong> <span className="mono">{appealTarget.denialCode}</span> ({appealTarget.denialCategory.replace('_', ' ')})
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
              <strong>Reason:</strong> {appealTarget.reason}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
              <strong>Amount at Risk:</strong> <Money value={appealTarget.amount} /> &middot;{' '}
              <strong>Appeal Deadline:</strong> <DateText value={appealTarget.appealDeadline} />
            </div>
          </div>

          <div className="field">
            <label htmlFor="appealNarrative">Appeal Medical / Contractual Narrative *</label>
            <textarea
              id="appealNarrative"
              rows={4}
              placeholder="State the medical necessity rationale, clinical coding justification, or policy documentation supporting reversal..."
              value={appealNarrative}
              onChange={(e) => setAppealNarrative(e.target.value)}
              autoFocus
            />
            <div className="field-hint">Required. Explains clinical or contractual basis of appeal.</div>
          </div>

          <div className="field">
            <label htmlFor="appealDays">Payer Response Deadline (Days)</label>
            <input
              id="appealDays"
              type="number"
              min={1}
              max={365}
              value={appealDeadlineDays}
              onChange={(e) => setAppealDeadlineDays(Number(e.target.value))}
            />
            <div className="field-hint">Statutory or contractual payer turnaround time (default 45 days).</div>
          </div>
        </Modal>
      )}
    </>
  );
}
