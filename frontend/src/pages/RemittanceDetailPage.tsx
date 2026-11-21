import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  BatchPostingResponse,
  RemittanceBatchDetail,
  RemittanceVoucher,
  VoucherPostingResponse,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card,
  DateText,
  DateTimeText,
  EmptyState,
  ErrorAlert,
  Loading,
  Modal,
  Money,
  PostingStatusBadge,
  RemittanceStatusBadge,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

export default function RemittanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { canWrite } = useAuth();

  const [batch, setBatch] = useState<RemittanceBatchDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Expanded voucher adjustments state: set of voucher IDs
  const [expandedVouchers, setExpandedVouchers] = useState<Set<number>>(new Set());

  // Post Voucher Modal State
  const [postingVoucherTarget, setPostingVoucherTarget] = useState<RemittanceVoucher | null>(null);
  const [postingVoucherBusy, setPostingVoucherBusy] = useState(false);
  const [postingVoucherError, setPostingVoucherError] = useState<string | null>(null);
  const [voucherPostResult, setVoucherPostResult] = useState<VoucherPostingResponse | null>(null);

  // Post Batch Modal State
  const [showBatchPostModal, setShowBatchPostModal] = useState(false);
  const [batchPostingBusy, setBatchPostingBusy] = useState(false);
  const [batchPostingError, setBatchPostingError] = useState<string | null>(null);
  const [batchPostResult, setBatchPostResult] = useState<BatchPostingResponse | null>(null);

  const fetchBatch = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const data = await api<RemittanceBatchDetail>(`/remittances/${id}`);
      setBatch(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to load remittance batch details.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchBatch();
  }, [fetchBatch]);

  const toggleExpand = (voucherId: number) => {
    setExpandedVouchers((prev) => {
      const next = new Set(prev);
      if (next.has(voucherId)) {
        next.delete(voucherId);
      } else {
        next.add(voucherId);
      }
      return next;
    });
  };

  const handlePostSingleVoucher = async () => {
    if (!postingVoucherTarget) return;
    setPostingVoucherBusy(true);
    setPostingVoucherError(null);
    try {
      const result = await api<VoucherPostingResponse>(
        `/remittances/vouchers/${postingVoucherTarget.id}/post`,
        { method: 'POST' }
      );
      setVoucherPostResult(result);
      fetchBatch();
    } catch (err) {
      setPostingVoucherError(err instanceof ApiError ? err.detail : 'Failed to post voucher payment.');
    } finally {
      setPostingVoucherBusy(false);
    }
  };

  const handlePostBatch = async () => {
    if (!batch) return;
    setBatchPostingBusy(true);
    setBatchPostingError(null);
    try {
      const result = await api<BatchPostingResponse>(
        `/remittances/${batch.id}/post`,
        { method: 'POST' }
      );
      setBatchPostResult(result);
      fetchBatch();
    } catch (err) {
      setBatchPostingError(err instanceof ApiError ? err.detail : 'Failed to post remittance batch.');
    } finally {
      setBatchPostingBusy(false);
    }
  };

  if (loading) {
    return (
      <>
        <PageHeader title="Remittance Advice Detail" subtitle="Loading..." />
        <div className="content"><Loading label="Loading batch details" /></div>
      </>
    );
  }

  if (error || !batch) {
    return (
      <>
        <PageHeader title="Remittance Advice Detail" subtitle="Error" />
        <div className="content">
          <ErrorAlert message={error || 'Batch not found'} />
          <Link to="/remittances" className="btn btn-secondary btn-sm">&larr; Back to Remittances</Link>
        </div>
      </>
    );
  }

  const unpostedVouchers = batch.vouchers.filter((v) => v.postingStatus !== 'POSTED');
  const canPostBatch = canWrite && unpostedVouchers.length > 0 && batch.status !== 'OUT_OF_BALANCE';

  return (
    <>
      <PageHeader
        title={`Batch: ${batch.batchControlNumber}`}
        subtitle={`${batch.payerName} &middot; Check/EFT ${batch.checkNumber || '—'} &middot; Effective: ${batch.checkDate}`}
        actions={
          <div className="btn-row">
            <Link to="/remittances" className="btn btn-secondary btn-sm">&larr; All Batches</Link>
            {batch.exceptions.length > 0 && (
              <Link to="/remittances/exceptions" className="btn btn-secondary btn-sm">
                View Exceptions ({batch.exceptions.length})
              </Link>
            )}
            {canPostBatch && (
              <button
                className="btn btn-sm"
                onClick={() => {
                  setShowBatchPostModal(true);
                  setBatchPostingError(null);
                  setBatchPostResult(null);
                }}
              >
                Post Entire Batch ({unpostedVouchers.length} vouchers)
              </button>
            )}
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        {/* Batch Metadata Cards */}
        <div className="grid grid-4" style={{ marginBottom: 20 }}>
          <div className="card stat">
            <div className="stat-label">Batch Status</div>
            <div className="stat-value" style={{ fontSize: 18, marginTop: 4 }}>
              <RemittanceStatusBadge status={batch.status} />
            </div>
            <div className="stat-note">Method: {batch.paymentMethod || 'EFT/Check'}</div>
          </div>
          <div className="card stat">
            <div className="stat-label">Reported Paid Total</div>
            <div className="stat-value"><Money value={batch.totalPaid} /></div>
            <div className="stat-note">Check Date: <DateText value={batch.checkDate} /></div>
          </div>
          <div className="card stat">
            <div className="stat-label">Vouchers / Matched</div>
            <div className="stat-value">
              {batch.matchedCount} / {batch.claimsCount}
            </div>
            <div className="stat-note">{batch.vouchers.length} staged vouchers</div>
          </div>
          <div className="card stat">
            <div className="stat-label">Import Provenance</div>
            <div className="stat-value" style={{ fontSize: 13, fontWeight: 550, marginTop: 6 }}>
              {batch.importedBy || 'system'}
            </div>
            <div className="stat-note"><DateTimeText value={batch.importedAt} /></div>
          </div>
        </div>

        {/* Vouchers Section */}
        <Card
          title={`Remittance Claim Vouchers (${batch.vouchers.length})`}
          tight
        >
          {batch.vouchers.length === 0 ? (
            <EmptyState
              title="No claim vouchers found"
              hint="This batch does not contain any parsed claim payment vouchers."
            />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Seq</th>
                    <th>Claim #</th>
                    <th>Payer ICN</th>
                    <th>CLP Status</th>
                    <th className="num">Billed</th>
                    <th className="num">Allowed</th>
                    <th className="num">Paid</th>
                    <th className="num">Patient Resp</th>
                    <th className="num">Contractual Adj</th>
                    <th>Bal Status</th>
                    <th>Posting</th>
                    <th>Entry Type</th>
                    <th>Adjustments</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {batch.vouchers.map((v) => {
                    const isExpanded = expandedVouchers.has(v.id);
                    const canPostThis = canWrite && v.postingStatus !== 'POSTED' && v.balanceStatus === 'BALANCED';

                    return (
                      <>
                        <tr key={v.id}>
                          <td className="mono">{v.sequenceNo}</td>
                          <td className="mono font-semibold">
                            {v.claimId ? (
                              <Link to={`/claims/${v.claimId}`}>{v.claimNumber}</Link>
                            ) : (
                              <span>{v.claimNumber}</span>
                            )}
                          </td>
                          <td className="mono" style={{ fontSize: 12 }}>{v.payerIcn || '—'}</td>
                          <td>
                            <span className="pill" title={`CLP Claim Status Code ${v.clpStatusCode}`}>
                              {v.clpStatusCode === '1' ? '1 (Primary)' : v.clpStatusCode === '2' ? '2 (Secondary)' : v.clpStatusCode === '4' ? '4 (Denial)' : v.clpStatusCode === '22' ? '22 (Reversal)' : v.clpStatusCode}
                            </span>
                          </td>
                          <td className="num"><Money value={v.billedAmount} /></td>
                          <td className="num"><Money value={v.allowedAmount} /></td>
                          <td className="num font-semibold" style={{ color: 'var(--ok)' }}>
                            <Money value={v.paidAmount} />
                          </td>
                          <td className="num"><Money value={v.patientResponsibility} /></td>
                          <td className="num"><Money value={v.contractualAdj} /></td>
                          <td>
                            <span
                              className="badge"
                              style={{
                                backgroundColor: v.balanceStatus === 'BALANCED' ? 'var(--ok-soft)' : 'var(--danger-soft)',
                                color: v.balanceStatus === 'BALANCED' ? 'var(--ok)' : 'var(--danger)',
                              }}
                            >
                              {v.balanceStatus}
                            </span>
                          </td>
                          <td>
                            <PostingStatusBadge status={v.postingStatus} />
                          </td>
                          <td>
                            <span className="pill">{v.entryType || 'ORIGINAL'}</span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => toggleExpand(v.id)}
                              style={{ fontSize: 11, padding: '2px 7px' }}
                            >
                              {v.adjustments.length} CAS {isExpanded ? '▲' : '▼'}
                            </button>
                          </td>
                          <td>
                            {canPostThis ? (
                              <button
                                className="btn btn-sm"
                                style={{ fontSize: 11, padding: '3px 8px' }}
                                onClick={() => {
                                  setPostingVoucherTarget(v);
                                  setPostingVoucherError(null);
                                  setVoucherPostResult(null);
                                }}
                              >
                                Post Payment
                              </button>
                            ) : v.postingStatus === 'POSTED' ? (
                              <span className="faint" style={{ fontSize: 11 }}>
                                Posted {v.postedAt && <DateTimeText value={v.postedAt} />}
                              </span>
                            ) : (
                              <span className="faint" style={{ fontSize: 11 }}>Ineligible</span>
                            )}
                          </td>
                        </tr>

                        {/* Collapsible CAS adjustments row */}
                        {isExpanded && (
                          <tr key={`cas-${v.id}`} style={{ background: 'var(--surface-2)' }}>
                            <td colSpan={14} style={{ padding: '12px 18px' }}>
                              <div style={{ fontWeight: 600, fontSize: 12, marginBottom: 6 }}>
                                Claim Adjustment Segments (CAS) for Claim {v.claimNumber}:
                              </div>
                              {v.adjustments.length === 0 ? (
                                <div className="faint" style={{ fontSize: 12 }}>No adjustment lines reported for this voucher.</div>
                              ) : (
                                <table style={{ background: 'var(--surface)', fontSize: 12, border: '1px solid var(--border)' }}>
                                  <thead>
                                    <tr>
                                      <th>Group Code</th>
                                      <th>CARC Reason Code</th>
                                      <th>RARC Remark Code</th>
                                      <th className="num">Adjustment Amount</th>
                                      <th>Entry Type</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {v.adjustments.map((adj) => (
                                      <tr key={adj.id}>
                                        <td>
                                          <span className="pill" style={{ fontWeight: 600 }}>
                                            {adj.groupCode === 'CO' ? 'CO (Contractual Obligation)' : adj.groupCode === 'PR' ? 'PR (Patient Responsibility)' : adj.groupCode === 'OA' ? 'OA (Other Adjustment)' : adj.groupCode === 'PI' ? 'PI (Payer Initiation)' : adj.groupCode}
                                          </span>
                                        </td>
                                        <td className="mono font-semibold">{adj.reasonCode}</td>
                                        <td className="mono">{adj.remarkCode || '—'}</td>
                                        <td className="num font-semibold"><Money value={adj.amount} /></td>
                                        <td><span className="pill">{adj.entryType || 'ORIGINAL'}</span></td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                            </td>
                          </tr>
                        )}
                      </>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        {/* Batch Exceptions Card if any */}
        {batch.exceptions.length > 0 && (
          <div style={{ marginTop: 24 }}>
            <Card
              title={`Batch Ingestion Exceptions (${batch.exceptions.length})`}
              tight
              actions={
                <Link to="/remittances/exceptions" className="btn btn-secondary btn-sm">
                  Manage All Exceptions &rarr;
                </Link>
              }
            >
              <table>
                <thead>
                  <tr>
                    <th>Claim #</th>
                    <th>Patient</th>
                    <th>Service Date</th>
                    <th className="num">Billed</th>
                    <th className="num">Paid</th>
                    <th>Exception Type</th>
                    <th>Error Message</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {batch.exceptions.map((exc) => (
                    <tr key={exc.id}>
                      <td className="mono">{exc.claimNumber}</td>
                      <td>{exc.patientName || '—'}</td>
                      <td><DateText value={exc.serviceDate} /></td>
                      <td className="num"><Money value={exc.billedAmount} /></td>
                      <td className="num"><Money value={exc.paidAmount} /></td>
                      <td><span className="pill font-semibold">{exc.exceptionType}</span></td>
                      <td style={{ color: 'var(--danger)', fontSize: 12 }}>{exc.errorMessage}</td>
                      <td>
                        <span
                          className="badge"
                          style={{
                            backgroundColor: exc.status === 'RESOLVED' ? 'var(--ok-soft)' : 'var(--danger-soft)',
                            color: exc.status === 'RESOLVED' ? 'var(--ok)' : 'var(--danger)',
                          }}
                        >
                          {exc.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>
        )}
      </div>

      {/* Single Voucher Post Modal */}
      {postingVoucherTarget && (
        <Modal
          title={`Confirm Payment Posting — Claim ${postingVoucherTarget.claimNumber}`}
          onClose={() => !postingVoucherBusy && setPostingVoucherTarget(null)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={postingVoucherBusy}
                onClick={() => setPostingVoucherTarget(null)}
              >
                {voucherPostResult ? 'Close' : 'Cancel'}
              </button>
              {!voucherPostResult && (
                <button
                  className="btn"
                  disabled={postingVoucherBusy}
                  onClick={handlePostSingleVoucher}
                >
                  {postingVoucherBusy ? 'Posting to Ledger...' : 'Confirm & Post Payment'}
                </button>
              )}
            </>
          }
        >
          {postingVoucherError && <div className="alert alert-error">{postingVoucherError}</div>}

          {voucherPostResult ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div className="alert alert-ok">
                Payment successfully posted to Claim <strong>{voucherPostResult.claimNumber}</strong>.
              </div>
              <div className="card" style={{ padding: 14 }}>
                <div style={{ fontSize: 13, marginBottom: 6 }}>
                  <strong>Status Transition:</strong> <code>{voucherPostResult.previousStatus}</code> &rarr; <code>{voucherPostResult.newStatus}</code>
                </div>
                <div style={{ fontSize: 13, marginBottom: 6 }}>
                  <strong>Posted Amount:</strong> <Money value={voucherPostResult.postedAmount} />
                </div>
                <div style={{ fontSize: 13, marginBottom: 6 }}>
                  <strong>Contractual Write-Off:</strong> <Money value={voucherPostResult.contractualWriteOff} />
                </div>
                <div style={{ fontSize: 13, marginBottom: 6 }}>
                  <strong>Cumulative Claim Paid:</strong> <Money value={voucherPostResult.cumulativeClaimPaid} />
                </div>
                {voucherPostResult.isFutureDated && (
                  <div className="alert alert-warn" style={{ marginTop: 8, fontSize: 12 }}>
                    <strong>Future-Dated Settlement:</strong> {voucherPostResult.settlementNote || 'Payment has a future check date.'}
                  </div>
                )}
              </div>
              {voucherPostResult.claimId && (
                <Link to={`/claims/${voucherPostResult.claimId}`} className="btn btn-sm">
                  Navigate to Claim Detail &rarr;
                </Link>
              )}
            </div>
          ) : (
            <div>
              <p style={{ fontSize: 13, marginBottom: 14 }}>
                You are about to post the adjudicated payment from <strong>{batch.payerName}</strong> to Claim <strong>{postingVoucherTarget.claimNumber}</strong>.
              </p>

              <div className="grid grid-2" style={{ marginBottom: 14 }}>
                <div className="card" style={{ padding: 12 }}>
                  <div className="stat-label">Payer Payment</div>
                  <div className="stat-value" style={{ fontSize: 18, color: 'var(--ok)' }}>
                    <Money value={postingVoucherTarget.paidAmount} />
                  </div>
                </div>
                <div className="card" style={{ padding: 12 }}>
                  <div className="stat-label">Contractual Write-Off (CO)</div>
                  <div className="stat-value" style={{ fontSize: 18, color: 'var(--warn)' }}>
                    <Money value={postingVoucherTarget.contractualAdj} />
                  </div>
                </div>
              </div>

              <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>
                <strong>Patient Responsibility:</strong> <Money value={postingVoucherTarget.patientResponsibility} />
              </div>

              <div
                style={{
                  padding: '8px 12px',
                  background: 'var(--surface-2)',
                  borderRadius: 'var(--radius-s)',
                  fontSize: 12,
                  color: 'var(--text-muted)',
                }}
              >
                <strong>Financial Ledger Safeguards:</strong>
                <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
                  <li>Inserts immutable audit records in <code>claim_payment_ledger</code>.</li>
                  <li>Updates claim paid balances and transitions status to PARTIALLY_PAID or PAID.</li>
                  <li>Enforces voucher idempotency to prevent duplicate posting.</li>
                </ul>
              </div>
            </div>
          )}
        </Modal>
      )}

      {/* Batch Post Modal */}
      {showBatchPostModal && (
        <Modal
          title={`Post Entire Remittance Batch — ${batch.batchControlNumber}`}
          onClose={() => !batchPostingBusy && setShowBatchPostModal(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={batchPostingBusy}
                onClick={() => setShowBatchPostModal(false)}
              >
                {batchPostResult ? 'Close' : 'Cancel'}
              </button>
              {!batchPostResult && (
                <button
                  className="btn"
                  disabled={batchPostingBusy}
                  onClick={handlePostBatch}
                >
                  {batchPostingBusy ? 'Posting Entire Batch...' : `Confirm & Post ${unpostedVouchers.length} Vouchers`}
                </button>
              )}
            </>
          }
        >
          {batchPostingError && <div className="alert alert-error">{batchPostingError}</div>}

          {batchPostResult ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div className="alert alert-ok">
                Batch posted successfully! Posted <strong>{batchPostResult.postedCount}</strong> of <strong>{batchPostResult.totalVouchers}</strong> vouchers.
              </div>
              <div className="card" style={{ padding: 14 }}>
                <div style={{ fontSize: 13, marginBottom: 6 }}>
                  <strong>Batch Status:</strong> <RemittanceStatusBadge status={batchPostResult.batchStatus} />
                </div>
                {batchPostResult.isFutureDated && (
                  <div className="alert alert-warn" style={{ marginTop: 8, fontSize: 12 }}>
                    <strong>Future-Dated Settlement:</strong> {batchPostResult.settlementNote || 'Payment has a future check date.'}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div>
              <p style={{ fontSize: 13, marginBottom: 14 }}>
                Are you sure you want to post all <strong>{unpostedVouchers.length}</strong> eligible vouchers in batch <strong>{batch.batchControlNumber}</strong>?
              </p>

              <div
                style={{
                  padding: '10px 14px',
                  background: 'var(--warn-soft)',
                  borderRadius: 'var(--radius-s)',
                  color: 'var(--warn)',
                  fontSize: 12,
                  marginBottom: 14,
                }}
              >
                <strong>All-or-Nothing Transaction:</strong> Batch posting processes all valid vouchers atomically. If any balanced voucher fails posting constraints, the entire transaction rolls back.
              </div>

              <div style={{ fontSize: 13, marginBottom: 8 }}>
                <strong>Payer:</strong> {batch.payerName}
              </div>
              <div style={{ fontSize: 13, marginBottom: 8 }}>
                <strong>Reported Payment Total:</strong> <Money value={batch.totalPaid} />
              </div>
              <div style={{ fontSize: 13, marginBottom: 14 }}>
                <strong>Check Number:</strong> {batch.checkNumber || '—'} &middot; Effective: <DateText value={batch.checkDate} />
              </div>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}

