import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  ExceptionResolutionInput,
  PageResponse,
  RemittanceExceptionItem,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card,
  DateText,
  EmptyState,
  ErrorAlert,
  ExceptionStatusBadge,
  Loading,
  Modal,
  Money,
  Pagination,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

const STATUS_FILTER_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'UNRESOLVED', label: 'Unresolved Only' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'IGNORED', label: 'Ignored' },
];

const RESOLUTION_ACTIONS = [
  { value: 'MATCH_MANUAL', label: 'Match to Valid Claim', desc: 'Link this unmatched voucher to an existing verified claim ID.' },
  { value: 'VERIFIED_ELIGIBLE', label: 'Verified Policy Coverage', desc: 'Confirmed payer coverage was active during service date.' },
  { value: 'EXCLUDE_INVALID', label: 'Exclude Invalid Voucher', desc: 'Voucher was submitted in error or belongs to another provider.' },
  { value: 'REVALIDATE', label: 'Revalidate Financial Balances', desc: 'Financial calculations recalculated and verified.' },
];

export default function RemittanceExceptionsPage() {
  const { canWrite } = useAuth();

  const [exceptions, setExceptions] = useState<RemittanceExceptionItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('UNRESOLVED');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Resolution Modal State
  const [resolveTarget, setResolveTarget] = useState<RemittanceExceptionItem | null>(null);
  const [actionVal, setActionVal] = useState('MATCH_MANUAL');
  const [matchedClaimIdStr, setMatchedClaimIdStr] = useState('');
  const [notesVal, setNotesVal] = useState('');
  const [resolveBusy, setResolveBusy] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);
  const [resolveSuccess, setResolveSuccess] = useState<string | null>(null);

  const fetchExceptions = useCallback(async (p: number, s: string) => {
    setLoading(true);
    setError(null);
    try {
      const query: Record<string, string | number> = {
        page: p,
        size: 15,
        sort: 'id,desc',
      };
      if (s) query.status = s;

      const resp = await api<PageResponse<RemittanceExceptionItem>>('/remittances/exceptions', { query });
      setExceptions(resp.content);
      setTotalElements(resp.totalElements);
      setTotalPages(resp.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to load remittance exceptions.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchExceptions(page, statusFilter);
  }, [fetchExceptions, page, statusFilter]);

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handleStatusChange = (e: ChangeEvent<HTMLSelectElement>) => {
    setStatusFilter(e.target.value);
    setPage(0);
  };

  const handleResolveSubmit = async () => {
    if (!resolveTarget) return;
    if (!notesVal.trim()) {
      setResolveError('Resolution notes are required.');
      return;
    }

    setResolveBusy(true);
    setResolveError(null);

    try {
      const payload: ExceptionResolutionInput = {
        action: actionVal,
        notes: notesVal.trim(),
        matchedClaimId: matchedClaimIdStr.trim() ? Number(matchedClaimIdStr.trim()) : null,
      };

      await api<RemittanceExceptionItem>(
        `/remittances/exceptions/${resolveTarget.id}/resolve`,
        {
          method: 'POST',
          body: payload,
        }
      );

      setResolveSuccess('Exception resolved successfully.');
      setTimeout(() => {
        setResolveTarget(null);
        setResolveSuccess(null);
        fetchExceptions(page, statusFilter);
      }, 1000);
    } catch (err) {
      setResolveError(err instanceof ApiError ? err.detail : 'Failed to resolve exception.');
    } finally {
      setResolveBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Remittance Claim Exceptions"
        subtitle="Review unmatched claims, variance discrepancies, and resolve ingestion errors"
        actions={
          <div className="btn-row">
            <Link to="/remittances" className="btn btn-secondary btn-sm">&larr; Back to Remittances</Link>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => fetchExceptions(page, statusFilter)}
            >
              Refresh
            </button>
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        <Card title={`Ingestion Exceptions (${totalElements})`} tight>
          <div className="filters">
            <div className="field">
              <label htmlFor="excStatusFilter">Status Filter</label>
              <select id="excStatusFilter" value={statusFilter} onChange={handleStatusChange}>
                {STATUS_FILTER_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {loading ? (
            <Loading label="Loading exceptions" />
          ) : exceptions.length === 0 ? (
            <EmptyState
              title="No remittance exceptions found"
              hint="All remittance batches have matched claims and balanced payments."
            />
          ) : (
            <>
              <div style={{ overflowX: 'auto' }}>
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Batch</th>
                      <th>Claim #</th>
                      <th>Patient</th>
                      <th>Service Date</th>
                      <th className="num">Billed</th>
                      <th className="num">Paid</th>
                      <th>Exception Type</th>
                      <th>Error Description</th>
                      <th>Status</th>
                      <th>Resolution</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {exceptions.map((exc) => {
                      const canResolveThis = canWrite && exc.status === 'UNRESOLVED';

                      return (
                        <tr key={exc.id}>
                          <td className="mono">{exc.id}</td>
                          <td>
                            <Link to={`/remittances/${exc.remittanceId}`} className="mono">
                              Batch #{exc.remittanceId}
                            </Link>
                          </td>
                          <td className="mono font-semibold">{exc.claimNumber}</td>
                          <td>{exc.patientName || '—'}</td>
                          <td><DateText value={exc.serviceDate} /></td>
                          <td className="num"><Money value={exc.billedAmount} /></td>
                          <td className="num font-semibold"><Money value={exc.paidAmount} /></td>
                          <td>
                            <span className="pill font-semibold">{exc.exceptionType}</span>
                          </td>
                          <td style={{ color: 'var(--danger)', fontSize: 12, maxWidth: 240 }}>
                            {exc.errorMessage}
                          </td>
                          <td>
                            <ExceptionStatusBadge status={exc.status} />
                          </td>
                          <td style={{ fontSize: 12 }}>
                            {exc.status === 'RESOLVED' ? (
                              <div>
                                <span className="pill">{exc.resolutionAction || 'RESOLVED'}</span>
                                {exc.matchedClaimId && (
                                  <div style={{ marginTop: 2 }}>
                                    <Link to={`/claims/${exc.matchedClaimId}`}>
                                      Claim #{exc.matchedClaimId}
                                    </Link>
                                  </div>
                                )}
                                {exc.resolvedBy && (
                                  <div className="faint" style={{ fontSize: 11, marginTop: 2 }}>
                                    by {exc.resolvedBy}
                                  </div>
                                )}
                              </div>
                            ) : (
                              <span className="faint">—</span>
                            )}
                          </td>
                          <td>
                            {canResolveThis ? (
                              <button
                                className="btn btn-sm"
                                style={{ fontSize: 11, padding: '3px 8px' }}
                                onClick={() => {
                                  setResolveTarget(exc);
                                  setActionVal('MATCH_MANUAL');
                                  setMatchedClaimIdStr(exc.matchedClaimId ? String(exc.matchedClaimId) : '');
                                  setNotesVal('');
                                  setResolveError(null);
                                  setResolveSuccess(null);
                                }}
                              >
                                Resolve
                              </button>
                            ) : (
                              <span className="faint" style={{ fontSize: 11 }}>—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <Pagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                onChange={handlePageChange}
              />
            </>
          )}
        </Card>
      </div>

      {/* Resolve Exception Modal */}
      {resolveTarget && (
        <Modal
          title={`Resolve Remittance Exception #${resolveTarget.id}`}
          onClose={() => !resolveBusy && setResolveTarget(null)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={resolveBusy}
                onClick={() => setResolveTarget(null)}
              >
                Cancel
              </button>
              <button
                className="btn"
                disabled={resolveBusy || !notesVal.trim()}
                onClick={handleResolveSubmit}
              >
                {resolveBusy ? 'Saving Resolution...' : 'Confirm Resolution'}
              </button>
            </>
          }
        >
          {resolveError && <div className="alert alert-error" style={{ marginBottom: 12 }}>{resolveError}</div>}
          {resolveSuccess && <div className="alert alert-ok" style={{ marginBottom: 12 }}>{resolveSuccess}</div>}

          <div style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 13, marginBottom: 4 }}>
              <strong>Claim Number:</strong> <code>{resolveTarget.claimNumber}</code> &middot; <strong>Type:</strong> <span className="pill">{resolveTarget.exceptionType}</span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--danger)', marginBottom: 12 }}>
              <strong>Error:</strong> {resolveTarget.errorMessage}
            </div>
          </div>

          <div className="field">
            <label htmlFor="resolveActionSelect">Resolution Action *</label>
            <select
              id="resolveActionSelect"
              value={actionVal}
              onChange={(e) => setActionVal(e.target.value)}
              disabled={resolveBusy}
            >
              {RESOLUTION_ACTIONS.map((a) => (
                <option key={a.value} value={a.value}>
                  {a.label}
                </option>
              ))}
            </select>
            <div className="field-hint">
              {RESOLUTION_ACTIONS.find((a) => a.value === actionVal)?.desc}
            </div>
          </div>

          {actionVal === 'MATCH_MANUAL' && (
            <div className="field">
              <label htmlFor="matchedClaimIdInput">Matched Internal Claim ID</label>
              <input
                id="matchedClaimIdInput"
                type="number"
                placeholder="e.g. 8"
                value={matchedClaimIdStr}
                onChange={(e) => setMatchedClaimIdStr(e.target.value)}
                disabled={resolveBusy}
              />
              <div className="field-hint">Enter the ID of the verified claim to link this remittance voucher to.</div>
            </div>
          )}

          <div className="field">
            <label htmlFor="resolveNotesInput">Resolution Audit Notes *</label>
            <textarea
              id="resolveNotesInput"
              rows={3}
              placeholder="Document the investigation steps, clearinghouse reference, or rationale for this resolution..."
              value={notesVal}
              onChange={(e) => setNotesVal(e.target.value)}
              disabled={resolveBusy}
            />
            <div className="field-hint">Required for compliance audit and revenue cycle provenance.</div>
          </div>
        </Modal>
      )}
    </>
  );
}
