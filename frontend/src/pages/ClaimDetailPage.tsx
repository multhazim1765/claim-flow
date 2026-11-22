import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, downloadFile, ApiError } from '../api/client';
import type {
  AppealOutcome,
  AppealResponse,
  AttachmentAuditResponse,
  AttachmentMetadataResponse,
  Claim,
  ClaimArBreakdown,
  ClaimStatus,
  DenialWorklistItem,
  DocumentCategory,
  FileAppealInput,
  PageResponse,
  PaymentLedgerItem,
  PaymentReversalInput,
  PaymentReversalResponse,
  RecordOutcomeInput,
  StatusHistoryEntry,
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
  StatusBadge,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

/** Transitions that must carry a reason, mirroring the server-side rules. */
const REASON_REQUIRED: ClaimStatus[] = ['REJECTED', 'DENIED', 'VOID'];
/** Transitions that must carry money. */
const PAYMENT_REQUIRED: ClaimStatus[] = ['PAID', 'PARTIALLY_PAID'];

const APPEAL_OUTCOMES: { value: AppealOutcome; label: string; desc: string }[] = [
  { value: 'OVERTURNED', label: 'Overturned (Approved)', desc: 'Payer overturned denial in full; claim approved for payment.' },
  { value: 'PARTIAL', label: 'Partially Overturned', desc: 'Payer approved a portion of the denied charges.' },
  { value: 'UPHELD', label: 'Upheld (Denial Maintained)', desc: 'Payer reaffirmed the denial; appeal rejected.' },
  { value: 'WITHDRAWN', label: 'Withdrawn', desc: 'Provider / biller withdrew the appeal.' },
];

const ATTACHMENT_CATEGORIES: { value: DocumentCategory; label: string }[] = [
  { value: 'MEDICAL_REPORT', label: 'Medical Report / Clinical Note' },
  { value: 'PRESCRIPTION', label: 'Prescription' },
  { value: 'LAB_REPORT', label: 'Lab Report / Pathology' },
  { value: 'REFERRAL', label: 'Referral Order' },
  { value: 'PRIOR_AUTHORIZATION', label: 'Prior Authorization' },
  { value: 'ITEMIZED_BILL', label: 'Itemized Bill' },
  { value: 'DENIAL_LETTER', label: 'Denial Letter' },
  { value: 'APPEAL_SUPPORT', label: 'Appeal Evidence Exhibit' },
  { value: 'INSURANCE_DOCUMENT', label: 'Insurance / Eligibility Document' },
  { value: 'DISCHARGE_SUMMARY', label: 'Discharge Summary' },
  { value: 'OTHER', label: 'Other Document' },
];

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

function OutcomeBadge({ outcome }: { outcome: AppealOutcome | null }) {
  if (!outcome) {
    return (
      <span
        className="badge"
        style={{
          backgroundColor: '#e6eefa',
          color: '#1d4373',
          fontWeight: 600,
          fontSize: '11px',
          padding: '2px 8px',
          borderRadius: '999px',
        }}
      >
        Pending Decision
      </span>
    );
  }

  let bg = '#eceff3';
  let color = '#4a5462';

  if (outcome === 'OVERTURNED') {
    bg = '#e2f4ee';
    color = '#13795b';
  } else if (outcome === 'PARTIAL') {
    bg = '#fdf1dc';
    color = '#8a5a06';
  } else if (outcome === 'UPHELD') {
    bg = '#fdeaea';
    color = '#b32020';
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
      }}
    >
      {outcome}
    </span>
  );
}

export default function ClaimDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { canWrite, isAdmin } = useAuth();

  const [claim, setClaim] = useState<Claim | null>(null);
  const [history, setHistory] = useState<StatusHistoryEntry[]>([]);
  const [appeals, setAppeals] = useState<AppealResponse[]>([]);
  const [denials, setDenials] = useState<DenialWorklistItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Generic transition modal state
  const [target, setTarget] = useState<ClaimStatus | null>(null);
  const [reason, setReason] = useState('');
  const [allowed, setAllowed] = useState('');
  const [paid, setPaid] = useState('');
  const [busy, setBusy] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // File appeal modal state
  const [showAppealModal, setShowAppealModal] = useState(false);
  const [appealNarrative, setAppealNarrative] = useState('');
  const [appealDays, setAppealDays] = useState(45);
  const [appealBusy, setAppealBusy] = useState(false);
  const [appealError, setAppealError] = useState<string | null>(null);

  // Record outcome modal state
  const [outcomeTarget, setOutcomeTarget] = useState<AppealResponse | null>(null);
  const [outcomeVal, setOutcomeVal] = useState<AppealOutcome>('OVERTURNED');
  const [decidedOn, setDecidedOn] = useState(() => new Date().toISOString().slice(0, 10));
  const [recoveredAmount, setRecoveredAmount] = useState('');
  const [decisionNotes, setDecisionNotes] = useState('');
  const [outcomeBusy, setOutcomeBusy] = useState(false);
  const [outcomeError, setOutcomeError] = useState<string | null>(null);

  // Financial Reconciliation and Ledger State
  const [arBreakdown, setArBreakdown] = useState<ClaimArBreakdown | null>(null);
  const [ledger, setLedger] = useState<PaymentLedgerItem[]>([]);
  const [reversalTarget, setReversalTarget] = useState<PaymentLedgerItem | null>(null);
  const [reversalReason, setReversalReason] = useState('');
  const [reversalCheckNumber, setReversalCheckNumber] = useState('');
  const [reversalCheckDate, setReversalCheckDate] = useState('');
  const [reversalNotes, setReversalNotes] = useState('');
  const [reversalBusy, setReversalBusy] = useState(false);
  const [reversalError, setReversalError] = useState<string | null>(null);
  const [reversalSuccess, setReversalSuccess] = useState<string | null>(null);

  // Document Attachments State
  const [attachments, setAttachments] = useState<AttachmentMetadataResponse[]>([]);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [archiveModalOpen, setArchiveModalOpen] = useState(false);
  const [archiveTarget, setArchiveTarget] = useState<AttachmentMetadataResponse | null>(null);
  const [archiveReason, setArchiveReason] = useState('');
  const [archiveBusy, setArchiveBusy] = useState(false);
  const [archiveError, setArchiveError] = useState<string | null>(null);

  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadCategory, setUploadCategory] = useState<DocumentCategory>('MEDICAL_REPORT');
  const [uploadDesc, setUploadDesc] = useState('');
  const [uploadBusy, setUploadBusy] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [attAudits, setAttAudits] = useState<AttachmentAuditResponse[]>([]);
  const [attAuditsLoading, setAttAuditsLoading] = useState(false);
  const [selectedAttForHistory, setSelectedAttForHistory] = useState<AttachmentMetadataResponse | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [c, h, a, d, ar, led, atts] = await Promise.all([
        api<Claim>(`/claims/${id}`),
        api<StatusHistoryEntry[]>(`/claims/${id}/history`),
        api<AppealResponse[]>(`/claims/${id}/appeals`),
        api<PageResponse<DenialWorklistItem>>('/denials', { query: { size: 100 } }).catch(() => null),
        api<ClaimArBreakdown>(`/claims/${id}/ar-breakdown`).catch(() => null),
        api<PaymentLedgerItem[]>(`/claims/${id}/ledger`).catch(() => []),
        api<AttachmentMetadataResponse[]>(`/claims/${id}/attachments`).catch(() => []),
      ]);
      setClaim(c);
      setHistory(h);
      setAppeals(a);
      if (d) {
        setDenials(d.content.filter((item) => item.claimId === Number(id)));
      }
      setArBreakdown(ar);
      setLedger(led || []);
      setAttachments(atts || []);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not load this claim.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const openTransition = (status: ClaimStatus) => {
    // If user clicked APPEALED, route them to the dedicated File Appeal modal
    if (status === 'APPEALED') {
      setAppealNarrative('');
      setAppealDays(45);
      setAppealError(null);
      setShowAppealModal(true);
      return;
    }
    setTarget(status);
    setReason('');
    setModalError(null);
    setAllowed(
      claim && claim.allowedAmount > 0 ? String(claim.allowedAmount) : String(claim?.totalCharge ?? '')
    );
    setPaid('');
  };

  const submitTransition = async () => {
    if (!target || !claim) return;
    setBusy(true);
    setModalError(null);
    try {
      await api<Claim>(`/claims/${claim.id}/transition`, {
        method: 'POST',
        body: {
          targetStatus: target,
          reason: reason || null,
          allowedAmount: PAYMENT_REQUIRED.includes(target) && allowed ? Number(allowed) : null,
          paidAmount: PAYMENT_REQUIRED.includes(target) && paid ? Number(paid) : null,
        },
      });
      setTarget(null);
      await load();
    } catch (err) {
      setModalError(err instanceof ApiError ? err.detail : 'The transition failed.');
    } finally {
      setBusy(false);
    }
  };

  const submitFileAppeal = async () => {
    if (!claim) return;
    if (!appealNarrative.trim() || appealNarrative.trim().length < 5) {
      setAppealError('Please provide a substantive narrative explaining the appeal justification (at least 5 characters).');
      return;
    }

    setAppealBusy(true);
    setAppealError(null);
    try {
      // Find open denial ID if available
      const openDenial = denials.find((d) => d.status === 'OPEN');
      const payload: FileAppealInput = {
        denialId: openDenial ? openDenial.id : null,
        narrative: appealNarrative.trim(),
        responseDeadlineDays: appealDays || 45,
      };
      await api(`/claims/${claim.id}/appeal`, {
        method: 'POST',
        body: payload,
      });
      setShowAppealModal(false);
      setAppealNarrative('');
      await load();
    } catch (err) {
      setAppealError(err instanceof ApiError ? err.detail : 'Failed to file appeal.');
    } finally {
      setAppealBusy(false);
    }
  };

  const openRecordOutcome = (appeal: AppealResponse) => {
    setOutcomeTarget(appeal);
    setOutcomeVal('OVERTURNED');
    setDecidedOn(new Date().toISOString().slice(0, 10));
    setRecoveredAmount(claim ? String(claim.totalCharge) : '');
    setDecisionNotes('');
    setOutcomeError(null);
  };

  const submitRecordOutcome = async () => {
    if (!outcomeTarget) return;
    setOutcomeBusy(true);
    setOutcomeError(null);
    try {
      const recAmt = recoveredAmount ? parseFloat(recoveredAmount) : null;
      const payload: RecordOutcomeInput = {
        outcome: outcomeVal,
        decidedOn,
        recoveredAmount: recAmt !== null && !isNaN(recAmt) ? recAmt : null,
        decisionNotes: decisionNotes.trim() || null,
      };
      await api(`/appeals/${outcomeTarget.id}/outcome`, {
        method: 'POST',
        body: payload,
      });
      setOutcomeTarget(null);
      await load();
    } catch (err) {
      setOutcomeError(err instanceof ApiError ? err.detail : 'Failed to record appeal decision.');
    } finally {
      setOutcomeBusy(false);
    }
  };

  const remove = async () => {
    if (!claim) return;
    try {
      await api<void>(`/claims/${claim.id}`, { method: 'DELETE' });
      navigate('/claims');
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not delete the claim.');
    }
  };

  const openReversal = (entry: PaymentLedgerItem) => {
    setReversalTarget(entry);
    setReversalReason('');
    setReversalCheckNumber(entry.checkNumber || '');
    setReversalCheckDate(entry.checkDate || '');
    setReversalNotes('');
    setReversalError(null);
    setReversalSuccess(null);
  };

  const submitReversal = async () => {
    if (!reversalTarget || !claim) return;
    if (!reversalReason.trim()) {
      setReversalError('Reversal reason is required.');
      return;
    }
    setReversalBusy(true);
    setReversalError(null);
    try {
      const payload: PaymentReversalInput = {
        originalLedgerId: reversalTarget.id,
        reversalReason: reversalReason.trim(),
        checkNumber: reversalCheckNumber.trim() || null,
        checkDate: reversalCheckDate || null,
        notes: reversalNotes.trim() || null,
      };
      await api<PaymentReversalResponse>(`/claims/${claim.id}/reversals`, {
        method: 'POST',
        body: payload,
      });
      setReversalSuccess('Payment reversed successfully. Financial balances and claim status updated.');
      setTimeout(async () => {
        setReversalTarget(null);
        setReversalSuccess(null);
        await load();
      }, 1200);
    } catch (err) {
      setReversalError(err instanceof ApiError ? err.detail : 'Failed to reverse payment.');
    } finally {
      setReversalBusy(false);
    }
  };

  const submitUploadAttachment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!claim || !uploadFile) return;

    setUploadBusy(true);
    setUploadError(null);
    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      formData.append('documentCategory', uploadCategory);
      if (uploadDesc.trim()) {
        formData.append('description', uploadDesc.trim());
      }

      await api(`/claims/${claim.id}/attachments`, {
        method: 'POST',
        body: formData,
      });

      setUploadModalOpen(false);
      setUploadFile(null);
      setUploadDesc('');
      await load();
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.detail : 'Failed to upload document.');
    } finally {
      setUploadBusy(false);
    }
  };

  const openArchiveModal = (att: AttachmentMetadataResponse) => {
    setArchiveTarget(att);
    setArchiveReason('');
    setArchiveError(null);
    setArchiveModalOpen(true);
  };

  const submitArchiveAttachment = async () => {
    if (!claim || !archiveTarget) return;
    if (!archiveReason.trim()) {
      setArchiveError('Archive reason is required.');
      return;
    }

    setArchiveBusy(true);
    setArchiveError(null);
    try {
      await api(`/claims/${claim.id}/attachments/${archiveTarget.id}/archive`, {
        method: 'POST',
        body: { archiveReason: archiveReason.trim() },
      });
      setArchiveModalOpen(false);
      setArchiveTarget(null);
      await load();
    } catch (err) {
      setArchiveError(err instanceof ApiError ? err.detail : 'Failed to archive attachment.');
    } finally {
      setArchiveBusy(false);
    }
  };

  const openHistoryModal = async (att: AttachmentMetadataResponse) => {
    if (!claim) return;
    setSelectedAttForHistory(att);
    setAttAuditsLoading(true);
    setHistoryModalOpen(true);
    try {
      const data = await api<AttachmentAuditResponse[]>(`/claims/${claim.id}/attachments/${att.id}/history`);
      setAttAudits(data || []);
    } catch (err) {
      console.error('Failed to load attachment history', err);
    } finally {
      setAttAuditsLoading(false);
    }
  };

  const downloadAttachmentFile = async (att: AttachmentMetadataResponse) => {
    if (!claim) return;
    try {
      await downloadFile(`/claims/${claim.id}/attachments/${att.id}/download`, undefined, att.originalFileName);
    } catch (err: any) {
      alert('Download failed: ' + err.message);
    }
  };

  if (loading) return <><PageHeader title="Claim" /><div className="content"><Loading /></div></>;
  if (!claim) return <><PageHeader title="Claim" /><div className="content"><ErrorAlert message={error} /></div></>;

  // Filter out outcome-driven transitions if claim is currently APPEALED (since outcomes go through /outcome endpoint)
  const genericTransitions =
    claim.status === 'APPEALED'
      ? claim.allowedTransitions.filter((s) => s === 'VOID')
      : claim.allowedTransitions;

  const pendingAppeal = appeals.find((a) => a.outcome === null);
  const activeDenial = denials.find((d) => d.status === 'OPEN' || d.status === 'APPEALED');

  return (
    <>
      <PageHeader
        title={claim.claimNumber}
        subtitle={<>{claim.patientName} &middot; {claim.payerName} &middot; {claim.statusDescription}</>}
        actions={
          <>
            <Link className="btn btn-secondary" to="/claims">Back to claims</Link>
            {canWrite && claim.editable && (
              <Link className="btn btn-secondary" to={`/claims/${claim.id}/edit`}>Edit</Link>
            )}
            {isAdmin && claim.status === 'DRAFT' && (
              <button className="btn btn-danger" onClick={remove}>Delete</button>
            )}
          </>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        {/* Workflow Actions */}
        {canWrite && (genericTransitions.length > 0 || claim.status === 'DENIED' || claim.status === 'APPEALED') && (
          <div className="card" style={{ marginBottom: 16 }}>
            <div className="card-head">
              <h2>Workflow & Actions</h2>
              <StatusBadge status={claim.status} />
            </div>
            <div className="card-body">
              <div className="btn-row" style={{ alignItems: 'center' }}>
                {/* Dedicated Action for DENIED claims: File Appeal */}
                {claim.status === 'DENIED' && (
                  <button
                    className="btn"
                    onClick={() => {
                      setAppealNarrative('');
                      setAppealDays(45);
                      setAppealError(null);
                      setShowAppealModal(true);
                    }}
                  >
                    File Appeal
                  </button>
                )}

                {/* Dedicated Action for APPEALED claims: Record Decision */}
                {claim.status === 'APPEALED' && pendingAppeal && (
                  <button
                    className="btn"
                    onClick={() => openRecordOutcome(pendingAppeal)}
                  >
                    Record Payer Decision ({pendingAppeal.levelDescription})
                  </button>
                )}

                {/* Generic Transitions */}
                {genericTransitions.map((s) => (
                  <button key={s} className="btn btn-secondary" onClick={() => openTransition(s)}>
                    {s === 'APPEALED' ? 'File Appeal' : s.replace('_', ' ')}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {!canWrite && (
          <div className="alert alert-info">
            You are signed in as a viewer. Claim and appeal actions are restricted to billing and administrative roles.
          </div>
        )}

        {/* Denial & Appeals Section */}
        {(denials.length > 0 || appeals.length > 0 || claim.status === 'DENIED' || claim.status === 'APPEALED') && (
          <div className="card" style={{ marginBottom: 16 }}>
            <div className="card-head">
              <h2>Denial Management & Appeals</h2>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {activeDenial && (
                  <span className="pill" style={{ fontWeight: 600 }}>
                    {activeDenial.denialCategory.replace('_', ' ')} &middot; {activeDenial.denialCode}
                  </span>
                )}
                <StatusBadge status={claim.status} />
              </div>
            </div>
            <div className="card-body">
              {/* Active Denial Card Summary */}
              {activeDenial && (
                <div
                  style={{
                    padding: '12px 14px',
                    background: 'var(--surface-2)',
                    borderRadius: 'var(--radius-s)',
                    marginBottom: 16,
                  }}
                >
                  <div className="grid grid-4" style={{ rowGap: 8 }}>
                    <div>
                      <div className="faint" style={{ fontSize: 11, textTransform: 'uppercase' }}>CARC / RARC</div>
                      <div style={{ fontWeight: 600 }}>
                        {activeDenial.denialCode}
                        {activeDenial.remittanceCode ? ` / ${activeDenial.remittanceCode}` : ''}
                      </div>
                    </div>
                    <div>
                      <div className="faint" style={{ fontSize: 11, textTransform: 'uppercase' }}>Denied Amount</div>
                      <div style={{ fontWeight: 600 }}><Money value={activeDenial.amount} /></div>
                    </div>
                    <div>
                      <div className="faint" style={{ fontSize: 11, textTransform: 'uppercase' }}>Appeal Deadline</div>
                      <div style={{ fontWeight: 600 }}>
                        <DateText value={activeDenial.appealDeadline} />{' '}
                        <span
                          style={{
                            fontSize: 11,
                            color: activeDenial.daysRemaining < 0 ? 'var(--danger)' : activeDenial.daysRemaining <= 7 ? 'var(--warn)' : 'var(--text-muted)',
                          }}
                        >
                          ({activeDenial.daysRemaining < 0 ? `${Math.abs(activeDenial.daysRemaining)}d overdue` : `${activeDenial.daysRemaining}d remaining`})
                        </span>
                      </div>
                    </div>
                    <div>
                      <div className="faint" style={{ fontSize: 11, textTransform: 'uppercase' }}>Denial Status</div>
                      <div style={{ fontWeight: 600 }}>{activeDenial.status}</div>
                    </div>
                  </div>
                  <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                    <strong>Reason:</strong> {activeDenial.reason}
                  </div>
                </div>
              )}

              {/* Appeal Progression Timeline */}
              <h3 style={{ marginBottom: 12 }}>Appeal Progression</h3>
              {appeals.length === 0 ? (
                <div className="muted" style={{ fontSize: 13, padding: '8px 0' }}>
                  No appeals have been filed for this claim yet.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {appeals.map((a) => (
                    <div
                      key={a.id}
                      style={{
                        border: '1px solid var(--border)',
                        borderRadius: 'var(--radius-s)',
                        padding: '12px 14px',
                        background: a.outcome === null ? 'var(--surface-2)' : 'var(--surface)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ fontWeight: 700, fontSize: 13 }}>
                            Level {a.level}: {a.levelDescription}
                          </span>
                          <OutcomeBadge outcome={a.outcome} />
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          Filed: <DateText value={a.filedOn} /> by {a.filedBy}
                        </div>
                      </div>

                      <div style={{ fontSize: 12, marginBottom: 8, color: 'var(--text-muted)' }}>
                        <strong>Payer Response Due:</strong> <DateText value={a.deadline} />
                      </div>

                      <div style={{ fontSize: 13, background: 'var(--surface)', padding: '8px 10px', borderRadius: 4, border: '1px solid var(--border)' }}>
                        <strong>Appeal Narrative:</strong>
                        <div style={{ marginTop: 2, whiteSpace: 'pre-wrap' }}>{a.narrative}</div>
                      </div>

                      {/* Outcome Details */}
                      {a.outcome !== null && (
                        <div
                          style={{
                            marginTop: 10,
                            padding: '8px 10px',
                            background: a.outcome === 'OVERTURNED' ? '#e2f4ee' : a.outcome === 'PARTIAL' ? '#fdf1dc' : '#eceff3',
                            borderRadius: 4,
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                            <span>
                              Decision: {a.outcome} &middot; Decided: <DateText value={a.decidedOn} />
                            </span>
                            {a.recoveredAmount !== null && (
                              <span>
                                Recovered / Adjudicated: <Money value={a.recoveredAmount} />
                              </span>
                            )}
                          </div>
                          {a.decisionNotes && (
                            <div style={{ fontSize: 12, marginTop: 4, color: 'var(--text)' }}>
                              <strong>Notes:</strong> {a.decisionNotes}
                            </div>
                          )}
                          <div style={{ fontSize: 11, marginTop: 4, color: 'var(--text-muted)', fontStyle: 'italic' }}>
                            Note: Adjudicated amount reflects payer-accepted liability. Cash receipt and payment posting are recorded separately upon 835 remittance receipt.
                          </div>
                        </div>
                      )}

                      {/* Pending Outcome Action */}
                      {a.outcome === null && canWrite && (
                        <div style={{ marginTop: 10, display: 'flex', justifyContent: 'flex-end' }}>
                          <button
                            type="button"
                            className="btn btn-sm"
                            onClick={() => openRecordOutcome(a)}
                          >
                            Record Payer Decision
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        <div className="grid grid-2">
          <Card title="Claim">
            <dl className="kv">
              <dt>Status</dt><dd><StatusBadge status={claim.status} /></dd>
              <dt>Patient</dt>
              <dd><Link to={`/patients/${claim.patientId}`}>{claim.patientName}</Link>{' '}
                  <span className="faint mono">{claim.patientMrn}</span></dd>
              <dt>Provider</dt>
              <dd>{claim.providerName} <span className="faint mono">NPI {claim.providerNpi}</span></dd>
              <dt>Payer</dt><dd>{claim.payerName}</dd>
              <dt>Member ID</dt>
              <dd>{claim.policyMemberId ?? <span className="faint">no policy attached</span>}</dd>
              <dt>Service dates</dt>
              <dd><DateText value={claim.serviceDateFrom} /> &ndash; <DateText value={claim.serviceDateTo} /></dd>
              <dt>Place of service</dt><dd className="mono">{claim.placeOfService}</dd>
              <dt>Submitted</dt><dd><DateTimeText value={claim.submittedAt} /></dd>
              <dt>Created by</dt><dd>{claim.createdBy} &middot; <DateTimeText value={claim.createdAt} /></dd>
              {claim.notes && <><dt>Notes</dt><dd>{claim.notes}</dd></>}
            </dl>
          </Card>

          <Card title="Financials">
            <dl className="kv">
              <dt>Total charged</dt><dd><Money value={claim.totalCharge} /></dd>
              <dt>Allowed</dt><dd><Money value={claim.allowedAmount} muted /></dd>
              <dt>Paid</dt><dd><Money value={claim.paidAmount} muted /></dd>
              <dt>Patient responsibility</dt><dd><Money value={claim.patientResponsibility} muted /></dd>
              <dt>Outstanding</dt><dd><strong><Money value={claim.outstanding} /></strong></dd>
            </dl>

            <h3 className="mt-24" style={{ marginBottom: 8 }}>Diagnoses</h3>
            <table>
              <thead><tr><th>#</th><th>ICD-10</th><th>Description</th></tr></thead>
              <tbody>
                {claim.diagnoses.map((d) => (
                  <tr key={d.id}>
                    <td className="faint">{d.sequenceNo}</td>
                    <td className="mono">{d.icd10Code}</td>
                    <td className="muted">{d.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </div>

        <div className="mt-16">
          <Card title="Service lines" tight>
            <table>
              <thead>
                <tr>
                  <th>#</th><th>CPT</th><th>Mods</th><th>Service date</th>
                  <th className="num">Units</th><th className="num">Charge</th>
                  <th className="num">Allowed</th><th className="num">Paid</th><th>Description</th>
                </tr>
              </thead>
              <tbody>
                {claim.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="faint">{l.lineNumber}</td>
                    <td className="mono">{l.cptCode}</td>
                    <td className="mono faint">{l.modifiers ?? '—'}</td>
                    <td><DateText value={l.serviceDate} /></td>
                    <td className="num">{l.units}</td>
                    <td className="num"><Money value={l.chargeAmount} /></td>
                    <td className="num"><Money value={l.allowedAmount} muted /></td>
                    <td className="num"><Money value={l.paidAmount} muted /></td>
                    <td className="muted">{l.description}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5} className="right"><strong>Total</strong></td>
                  <td className="num"><strong><Money value={claim.totalCharge} /></strong></td>
                  <td className="num"><Money value={claim.allowedAmount} muted /></td>
                  <td className="num"><Money value={claim.paidAmount} muted /></td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </Card>
        </div>

        <div className="mt-16">
          <Card title="Financial Reconciliation & AR Position" tight>
            {arBreakdown ? (
              <div style={{ padding: 16 }}>
                {arBreakdown.legacyDiscrepancyNotes && (
                  <div className="alert alert-warn" style={{ marginBottom: 14 }}>
                    <strong>Legacy Discrepancy:</strong> {arBreakdown.legacyDiscrepancyNotes}
                  </div>
                )}

                <div className="grid grid-4" style={{ marginBottom: 16 }}>
                  <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                    <div className="stat-label">Billed Charge</div>
                    <div className="stat-value font-semibold"><Money value={arBreakdown.totalCharge} /></div>
                    <div className="stat-note">Allowed: <Money value={arBreakdown.allowedAmount} /></div>
                  </div>
                  <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                    <div className="stat-label">Net Recorded Paid</div>
                    <div className="stat-value font-semibold" style={{ color: 'var(--ok)' }}><Money value={arBreakdown.paidAmount} /></div>
                    <div className="stat-note">Contractual Write-Off: <Money value={arBreakdown.contractualAdjustment} /></div>
                  </div>
                  <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                    <div className="stat-label">Active Payer AR</div>
                    <div className="stat-value font-semibold" style={{ color: 'var(--accent)' }}><Money value={arBreakdown.adjudicatedPayerAr} /></div>
                    <div className="stat-note">Patient AR: <Money value={arBreakdown.patientAr} /></div>
                  </div>
                  <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                    <div className="stat-label">Remaining Balance</div>
                    <div className="stat-value font-semibold"><Money value={arBreakdown.remainingBalance} /></div>
                    <div className="stat-note">Disputed Exposure: <Money value={arBreakdown.disputedExposure} /></div>
                  </div>
                </div>
              </div>
            ) : null}

            {/* Payment Ledger Audit Trail */}
            <div style={{ borderTop: '1px solid var(--border)', padding: '12px 16px', background: 'var(--surface-2)', fontWeight: 600 }}>
              Payment Posting & Reversal Ledger ({ledger.length} entries)
            </div>

            {ledger.length === 0 ? (
              <div style={{ padding: 16 }}>
                <EmptyState
                  title="No payment ledger entries recorded"
                  hint="Payments and write-offs posted via synthetic ERA 835 will appear in this immutable audit ledger."
                />
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table>
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Date</th>
                      <th>Entry Type</th>
                      <th>Ref / Check #</th>
                      <th className="num">Signed Amount</th>
                      <th>Payer ICN</th>
                      <th>Posted By</th>
                      <th>Notes</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ledger.map((entry) => {
                      const isReversal = entry.entryType === 'PAYER_REVERSAL' || entry.entryType === 'CONTRACTUAL_ADJUSTMENT_REVERSAL';
                      const isCo = entry.entryType === 'CONTRACTUAL_WRITE_OFF' || entry.entryType === 'CONTRACTUAL_ADJUSTMENT_REVERSAL';
                      const isAlreadyReversed = ledger.some(
                        (l) => l.entryType === 'PAYER_REVERSAL' && l.originalLedgerId === entry.id
                      );
                      const canReverseThis = canWrite && entry.entryType === 'PAYER_PAYMENT' && !isAlreadyReversed;

                      return (
                        <tr key={entry.id} style={{ background: isReversal ? 'var(--danger-soft)' : undefined }}>
                          <td className="mono">{entry.id}</td>
                          <td><DateTimeText value={entry.postedAt} /></td>
                          <td>
                            <span
                              className="pill font-semibold"
                              style={{
                                color: isReversal ? 'var(--danger)' : isCo ? 'var(--warn)' : 'var(--ok)',
                              }}
                            >
                              {entry.entryType.replace(/_/g, ' ')}
                            </span>
                          </td>
                          <td className="mono" style={{ fontSize: 12 }}>
                            {entry.checkNumber || '—'}
                            {entry.checkDate && <span className="faint" style={{ marginLeft: 4 }}>({entry.checkDate})</span>}
                          </td>
                          <td className="num font-semibold" style={{ color: isReversal ? 'var(--danger)' : 'inherit' }}>
                            <Money value={entry.amount} />
                          </td>
                          <td className="mono" style={{ fontSize: 12 }}>{entry.payerIcn || '—'}</td>
                          <td>{entry.postedBy}</td>
                          <td style={{ fontSize: 12, maxWidth: 220 }} className="muted">{entry.notes || '—'}</td>
                          <td>
                            {canReverseThis ? (
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                style={{ fontSize: 11, padding: '3px 8px', color: 'var(--danger)' }}
                                onClick={() => openReversal(entry)}
                              >
                                Reverse Payment
                              </button>
                            ) : isAlreadyReversed ? (
                              <span className="badge" style={{ backgroundColor: 'var(--danger-soft)', color: 'var(--danger)', fontSize: 10 }}>
                                Reversed
                              </span>
                            ) : entry.originalLedgerId ? (
                              <span className="faint" style={{ fontSize: 11 }}>
                                Reversal of #{entry.originalLedgerId}
                              </span>
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
            )}
          </Card>
        </div>

        <div className="mt-16">
          <Card
            title={`Documents & Supporting Attachments (${attachments.filter((a) => a.attachmentStatus === 'ACTIVE').length})`}
            tight
            actions={
              canWrite && (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => {
                    setUploadFile(null);
                    setUploadCategory('MEDICAL_REPORT');
                    setUploadDesc('');
                    setUploadError(null);
                    setUploadModalOpen(true);
                  }}
                >
                  + Upload Document
                </button>
              )
            }
          >
            {attachments.length === 0 ? (
              <div className="muted" style={{ padding: '24px 20px', textAlign: 'center', fontSize: 13 }}>
                No documents or clinical exhibits attached to this claim yet.
                {canWrite && (
                  <div style={{ marginTop: 8 }}>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => {
                        setUploadFile(null);
                        setUploadCategory('MEDICAL_REPORT');
                        setUploadDesc('');
                        setUploadError(null);
                        setUploadModalOpen(true);
                      }}
                    >
                      Attach Medical Report, Prior Auth, or Denial Letter
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table>
                  <thead>
                    <tr>
                      <th>Document</th>
                      <th>Category</th>
                      <th>Type & Size</th>
                      <th>Uploaded Date</th>
                      <th>Uploaded By</th>
                      <th>Status</th>
                      <th>Scan Status</th>
                      <th style={{ textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {attachments.map((att) => (
                      <tr key={att.id} style={{ opacity: att.attachmentStatus === 'ARCHIVED' ? 0.6 : 1 }}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{att.originalFileName}</div>
                          {att.description && (
                            <div className="muted" style={{ fontSize: 11, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {att.description}
                            </div>
                          )}
                          {att.archiveReason && (
                            <div style={{ fontSize: 11, color: 'var(--danger)' }}>
                              Archived: {att.archiveReason} ({att.archivedBy})
                            </div>
                          )}
                        </td>
                        <td>
                          <span className="pill pill-phase2" style={{ fontSize: 11 }}>
                            {att.documentCategory.replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td style={{ fontSize: 12 }}>
                          <code>{att.contentType}</code> &middot; {formatBytes(att.fileSize)}
                        </td>
                        <td style={{ fontSize: 12 }}>
                          {new Date(att.uploadedAt).toLocaleDateString()}
                        </td>
                        <td style={{ fontSize: 12 }}>{att.uploadedBy}</td>
                        <td>
                          <span className={`badge ${att.attachmentStatus === 'ACTIVE' ? 'badge-paid' : 'badge-denied'}`} style={{ fontSize: 10 }}>
                            {att.attachmentStatus}
                          </span>
                        </td>
                        <td>
                          <span className="badge badge-ready" style={{ fontSize: 10 }}>
                            {att.securityScanStatus}
                          </span>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            style={{ marginRight: 4, fontSize: 11, padding: '2px 6px' }}
                            onClick={() => openHistoryModal(att)}
                          >
                            History
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            style={{ marginRight: 4, fontSize: 11, padding: '2px 6px' }}
                            onClick={() => downloadAttachmentFile(att)}
                          >
                            Download
                          </button>
                          {canWrite && att.attachmentStatus === 'ACTIVE' && (
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              style={{ fontSize: 11, padding: '2px 6px' }}
                              onClick={() => openArchiveModal(att)}
                            >
                              Archive
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>

        <div className="mt-16">
          <Card title="Audit trail">
            <ul className="timeline">
              {history.map((h, i) => (
                <li key={h.id} className={i === history.length - 1 ? 'current' : undefined}>
                  <div className="timeline-when"><DateTimeText value={h.changedAt} /> &middot; {h.changedBy}</div>
                  <div className="timeline-what">
                    {h.fromStatus ? <>{h.fromStatus.replace('_', ' ')} &rarr; </> : 'Created as '}
                    <StatusBadge status={h.toStatus} />
                  </div>
                  {h.reason && <div className="timeline-why">{h.reason}</div>}
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>

      {/* Generic Transition Modal */}
      {target && (
        <Modal
          title={`Move to ${target.replace('_', ' ')}`}
          onClose={() => setTarget(null)}
          footer={
            <>
              <button className="btn btn-secondary" onClick={() => setTarget(null)} disabled={busy}>Cancel</button>
              <button className="btn" onClick={submitTransition} disabled={busy}>
                {busy ? 'Working' : 'Confirm'}
              </button>
            </>
          }
        >
          {modalError && <div className="alert alert-error">{modalError}</div>}

          {PAYMENT_REQUIRED.includes(target) && (
            <>
              <div className="field">
                <label htmlFor="allowed">Allowed amount</label>
                <input id="allowed" type="number" step="0.01" min="0"
                       value={allowed} onChange={(e) => setAllowed(e.target.value)} />
                <div className="field-hint">What the payer contractually allows. Defaults to the charge.</div>
              </div>
              <div className="field">
                <label htmlFor="paid">Paid amount</label>
                <input id="paid" type="number" step="0.01" min="0"
                       value={paid} onChange={(e) => setPaid(e.target.value)} autoFocus />
                <div className="field-hint">
                  {target === 'PAID'
                    ? 'Must not exceed the allowed amount.'
                    : 'Must be greater than zero and less than the allowed amount.'}
                </div>
              </div>
            </>
          )}

          <div className="field">
            <label htmlFor="reason">
              Reason {REASON_REQUIRED.includes(target) ? '' : <span className="faint">(optional)</span>}
            </label>
            <textarea id="reason" value={reason} onChange={(e) => setReason(e.target.value)}
                      placeholder={target === 'DENIED' ? 'e.g. CO-197 precertification absent' : ''} />
            {REASON_REQUIRED.includes(target) && (
              <div className="field-hint">Required. It is written to the permanent audit trail.</div>
            )}
          </div>
        </Modal>
      )}

      {/* File Appeal Modal */}
      {showAppealModal && (
        <Modal
          title={`File Appeal — Claim ${claim.claimNumber}`}
          onClose={() => setShowAppealModal(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                onClick={() => setShowAppealModal(false)}
                disabled={appealBusy}
              >
                Cancel
              </button>
              <button className="btn" onClick={submitFileAppeal} disabled={appealBusy}>
                {appealBusy ? 'Submitting...' : 'File Appeal'}
              </button>
            </>
          }
        >
          {appealError && <div className="alert alert-error">{appealError}</div>}

          <div className="field">
            <label htmlFor="appealNarrative">Appeal Medical / Contractual Narrative *</label>
            <textarea
              id="appealNarrative"
              rows={4}
              placeholder="State the clinical, medical necessity, or contractual justification for reversing the denial..."
              value={appealNarrative}
              onChange={(e) => setAppealNarrative(e.target.value)}
              autoFocus
            />
            <div className="field-hint">Required (min 5 characters). Documents the basis of appeal for payer review.</div>
          </div>

          <div className="field">
            <label htmlFor="appealDays">Payer Response Deadline (Days)</label>
            <input
              id="appealDays"
              type="number"
              min={1}
              max={365}
              value={appealDays}
              onChange={(e) => setAppealDays(Number(e.target.value))}
            />
            <div className="field-hint">Contractual turnaround time window (default 45 days).</div>
          </div>
        </Modal>
      )}

      {/* Record Outcome Modal */}
      {outcomeTarget && (
        <Modal
          title={`Record Payer Decision — Level ${outcomeTarget.level}`}
          onClose={() => setOutcomeTarget(null)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                onClick={() => setOutcomeTarget(null)}
                disabled={outcomeBusy}
              >
                Cancel
              </button>
              <button className="btn" onClick={submitRecordOutcome} disabled={outcomeBusy}>
                {outcomeBusy ? 'Saving...' : 'Record Outcome'}
              </button>
            </>
          }
        >
          {outcomeError && <div className="alert alert-error">{outcomeError}</div>}

          <div className="field">
            <label htmlFor="outcomeSelect">Payer Decision Outcome *</label>
            <select
              id="outcomeSelect"
              value={outcomeVal}
              onChange={(e) => setOutcomeVal(e.target.value as AppealOutcome)}
            >
              {APPEAL_OUTCOMES.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <div className="field-hint">
              {APPEAL_OUTCOMES.find((o) => o.value === outcomeVal)?.desc}
            </div>
          </div>

          <div className="grid grid-2">
            <div className="field">
              <label htmlFor="decidedOn">Decided On *</label>
              <input
                id="decidedOn"
                type="date"
                value={decidedOn}
                onChange={(e) => setDecidedOn(e.target.value)}
              />
            </div>

            {(outcomeVal === 'OVERTURNED' || outcomeVal === 'PARTIAL') && (
              <div className="field">
                <label htmlFor="recoveredAmt">Adjudicated / Recovered Amount ($)</label>
                <input
                  id="recoveredAmt"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={recoveredAmount}
                  onChange={(e) => setRecoveredAmount(e.target.value)}
                />
              </div>
            )}
          </div>

          <div className="field">
            <label htmlFor="decisionNotes">Payer Determination Notes</label>
            <textarea
              id="decisionNotes"
              rows={3}
              placeholder="Explanations or reference numbers provided in the payer's determination letter..."
              value={decisionNotes}
              onChange={(e) => setDecisionNotes(e.target.value)}
            />
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
            <strong>Financial Separation:</strong> Recording an overturned appeal transitions the claim to <code>ACCEPTED</code>. Actual payment receipt and cash balance updates remain separated until remittance posting.
          </div>
        </Modal>
      )}

      {/* Payment Reversal Modal */}
      {reversalTarget && (
        <Modal
          title={`Execute Payment Reversal — Ledger #${reversalTarget.id}`}
          onClose={() => !reversalBusy && setReversalTarget(null)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={reversalBusy}
                onClick={() => setReversalTarget(null)}
              >
                Cancel
              </button>
              <button
                className="btn btn-danger"
                disabled={reversalBusy || !reversalReason.trim()}
                onClick={submitReversal}
              >
                {reversalBusy ? 'Reversing Payment...' : 'Confirm Append-Only Reversal'}
              </button>
            </>
          }
        >
          {reversalError && <div className="alert alert-error" style={{ marginBottom: 12 }}>{reversalError}</div>}
          {reversalSuccess && <div className="alert alert-ok" style={{ marginBottom: 12 }}>{reversalSuccess}</div>}

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
            <strong>Financial Reversal Rules:</strong> Reversing this payment appends a signed negative <code>PAYER_REVERSAL</code> entry to the ledger and reverses associated contractual write-offs. Claim status will reopen if remaining balance is outstanding.
          </div>

          <div className="card" style={{ padding: 12, marginBottom: 14, background: 'var(--surface-2)' }}>
            <div style={{ fontSize: 13, marginBottom: 4 }}>
              <strong>Original Payment Amount:</strong> <Money value={reversalTarget.amount} />
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              <strong>Reference:</strong> Check {reversalTarget.checkNumber || '—'} &middot; ICN {reversalTarget.payerIcn || '—'}
            </div>
          </div>

          <div className="field">
            <label htmlFor="revReasonInput">Reversal Reason *</label>
            <input
              id="revReasonInput"
              type="text"
              placeholder="e.g. Recoupment for claim correction / Overpayment recovery"
              value={reversalReason}
              onChange={(e) => setReversalReason(e.target.value)}
              disabled={reversalBusy}
              autoFocus
            />
            <div className="field-hint">Required for financial compliance and ledger audit provenance.</div>
          </div>

          <div className="grid grid-2">
            <div className="field">
              <label htmlFor="revCheckNumber">Supporting Check / EFT #</label>
              <input
                id="revCheckNumber"
                type="text"
                value={reversalCheckNumber}
                onChange={(e) => setReversalCheckNumber(e.target.value)}
                disabled={reversalBusy}
              />
            </div>
            <div className="field">
              <label htmlFor="revCheckDate">Check Date</label>
              <input
                id="revCheckDate"
                type="date"
                value={reversalCheckDate}
                onChange={(e) => setReversalCheckDate(e.target.value)}
                disabled={reversalBusy}
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="revNotesInput">Additional Notes</label>
            <textarea
              id="revNotesInput"
              rows={2}
              placeholder="Investigation notes or supporting synthetic remittance voucher details..."
              value={reversalNotes}
              onChange={(e) => setReversalNotes(e.target.value)}
              disabled={reversalBusy}
            />
          </div>
        </Modal>
      )}

      {/* Upload Document Modal */}
      {uploadModalOpen && (
        <Modal
          title={`Attach Document — Claim ${claim.claimNumber}`}
          onClose={() => !uploadBusy && setUploadModalOpen(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={uploadBusy}
                onClick={() => setUploadModalOpen(false)}
              >
                Cancel
              </button>
              <button
                className="btn"
                disabled={uploadBusy || !uploadFile}
                onClick={submitUploadAttachment}
              >
                {uploadBusy ? 'Uploading & Verifying...' : 'Upload Document'}
              </button>
            </>
          }
        >
          {uploadError && <div className="alert alert-error" style={{ marginBottom: 12 }}>{uploadError}</div>}

          <div
            style={{
              padding: '10px 14px',
              background: 'var(--surface-2)',
              borderRadius: 'var(--radius-s)',
              fontSize: 12,
              marginBottom: 14,
            }}
          >
            <strong>Attachment Security:</strong> Files are strictly verified against extension allowlists and magic byte signatures (PDF, JPEG, PNG). Stored securely outside web root with SHA-256 integrity hashing (Max 10MB).
          </div>

          <div className="field">
            <label htmlFor="uploadDocCategory">Document Category *</label>
            <select
              id="uploadDocCategory"
              value={uploadCategory}
              onChange={(e) => setUploadCategory(e.target.value as DocumentCategory)}
              disabled={uploadBusy}
            >
              {ATTACHMENT_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="field">
            <label htmlFor="uploadFileField">Select File (PDF, JPEG, PNG - Max 10MB) *</label>
            <input
              id="uploadFileField"
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  setUploadFile(e.target.files[0]);
                }
              }}
              disabled={uploadBusy}
            />
            {uploadFile && (
              <div className="field-hint" style={{ color: 'var(--brand)', fontWeight: 500 }}>
                Selected: {uploadFile.name} ({formatBytes(uploadFile.size)})
              </div>
            )}
          </div>

          <div className="field">
            <label htmlFor="uploadDescField">Description / Clinical Purpose (Optional)</label>
            <textarea
              id="uploadDescField"
              rows={2}
              placeholder="e.g. Operative report supporting modifier 22, denial explanation exhibit..."
              value={uploadDesc}
              onChange={(e) => setUploadDesc(e.target.value)}
              disabled={uploadBusy}
            />
          </div>
        </Modal>
      )}

      {/* Archive Document Modal */}
      {archiveModalOpen && archiveTarget && (
        <Modal
          title={`Archive Document — ${archiveTarget.originalFileName}`}
          onClose={() => !archiveBusy && setArchiveModalOpen(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={archiveBusy}
                onClick={() => setArchiveModalOpen(false)}
              >
                Cancel
              </button>
              <button
                className="btn btn-danger"
                disabled={archiveBusy || !archiveReason.trim()}
                onClick={submitArchiveAttachment}
              >
                {archiveBusy ? 'Archiving...' : 'Confirm Archival'}
              </button>
            </>
          }
        >
          {archiveError && <div className="alert alert-error" style={{ marginBottom: 12 }}>{archiveError}</div>}

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
            <strong>Logical Archival Notice:</strong> Archiving marks this document inactive and records a permanent audit entry with your user identity and reason. File bytes are preserved on disk for regulatory audit compliance.
          </div>

          <div className="field">
            <label htmlFor="archiveReasonField">Archive Reason *</label>
            <input
              id="archiveReasonField"
              type="text"
              placeholder="e.g. Superseded by corrected document / Attached to wrong claim"
              value={archiveReason}
              onChange={(e) => setArchiveReason(e.target.value)}
              disabled={archiveBusy}
              autoFocus
            />
            <div className="field-hint">Required for HIPAA / HITRUST audit trails.</div>
          </div>
        </Modal>
      )}

      {/* Attachment Audit History Modal */}
      {historyModalOpen && selectedAttForHistory && (
        <Modal
          title={`Document Audit History — ${selectedAttForHistory.originalFileName}`}
          onClose={() => setHistoryModalOpen(false)}
          footer={
            <button className="btn btn-secondary" onClick={() => setHistoryModalOpen(false)}>
              Close
            </button>
          }
        >
          <div style={{ marginBottom: 12, fontSize: 12, color: 'var(--text-muted)' }}>
            Document ID: #{selectedAttForHistory.id} &middot; Category: {selectedAttForHistory.documentCategory.replace('_', ' ')} &middot; SHA-256: <code style={{ fontSize: 10 }}>{selectedAttForHistory.sha256Hash?.slice(0, 16)}...</code>
          </div>

          {attAuditsLoading ? (
            <Loading label="Loading audit trail" />
          ) : attAudits.length === 0 ? (
            <EmptyState title="No audit entries" hint="No audit events recorded for this attachment." />
          ) : (
            <ul className="timeline">
              {attAudits.map((a, i) => (
                <li key={a.id} className={i === attAudits.length - 1 ? 'current' : undefined}>
                  <div className="timeline-when">
                    <DateTimeText value={a.actedAt} /> &middot; {a.actor}
                  </div>
                  <div className="timeline-what">
                    <span className="badge" style={{ marginRight: 6, fontWeight: 600 }}>
                      {a.action}
                    </span>
                  </div>
                  {a.notes && <div className="timeline-why">{a.notes}</div>}
                </li>
              ))}
            </ul>
          )}
        </Modal>
      )}
    </>
  );
}
