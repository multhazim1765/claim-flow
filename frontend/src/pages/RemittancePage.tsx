import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  AccountsReceivableSummary,
  PageResponse,
  Payer,
  RemittanceBatchListItem,
  RemittanceExceptionItem,
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
  Pagination,
  RemittanceStatusBadge,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'All batch statuses' },
  { value: 'RECEIVED', label: 'Received' },
  { value: 'VALIDATED', label: 'Validated' },
  { value: 'BALANCED', label: 'Balanced' },
  { value: 'OUT_OF_BALANCE', label: 'Out of Balance' },
  { value: 'POSTED', label: 'Posted' },
  { value: 'PARTIALLY_POSTED', label: 'Partially Posted' },
];

export default function RemittancePage() {
  const { canWrite } = useAuth();
  const navigate = useNavigate();

  // Worklist state
  const [batches, setBatches] = useState<RemittanceBatchListItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [payerFilter, setPayerFilter] = useState('');
  const [payers, setPayers] = useState<Payer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Financial KPIs
  const [arSummary, setArSummary] = useState<AccountsReceivableSummary | null>(null);
  const [unresolvedExceptionsCount, setUnresolvedExceptionsCount] = useState<number>(0);

  // Import Modal state
  const [showImportModal, setShowImportModal] = useState(false);
  const [importMode, setImportMode] = useState<'file' | 'json'>('file');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jsonText, setJsonText] = useState('');
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [importSuccess, setImportSuccess] = useState<string | null>(null);

  // Load payers for filter
  useEffect(() => {
    api<Payer[]>('/payers')
      .then(setPayers)
      .catch(() => { /* ignore */ });
  }, []);

  // Fetch KPI data
  const fetchKpis = useCallback(async () => {
    try {
      const [ar, exc] = await Promise.all([
        api<AccountsReceivableSummary>('/financial/ar-summary'),
        api<PageResponse<RemittanceExceptionItem>>('/remittances/exceptions', {
          query: { status: 'UNRESOLVED', size: 1 },
        }),
      ]);
      setArSummary(ar);
      setUnresolvedExceptionsCount(exc.totalElements);
    } catch {
      // Non-blocking for worklist
    }
  }, []);

  // Fetch batches worklist
  const fetchBatches = useCallback(async (p: number, s: string, payerIdStr: string) => {
    setLoading(true);
    setError(null);
    try {
      const query: Record<string, string | number> = {
        page: p,
        size: 15,
        sort: 'importedAt,desc',
      };
      if (s) query.status = s;
      if (payerIdStr) query.payerId = Number(payerIdStr);

      const resp = await api<PageResponse<RemittanceBatchListItem>>('/remittances', { query });
      setBatches(resp.content);
      setTotalElements(resp.totalElements);
      setTotalPages(resp.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to load remittance batches.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchKpis();
  }, [fetchKpis]);

  useEffect(() => {
    fetchBatches(page, statusFilter, payerFilter);
  }, [fetchBatches, page, statusFilter, payerFilter]);

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handleStatusChange = (e: ChangeEvent<HTMLSelectElement>) => {
    setStatusFilter(e.target.value);
    setPage(0);
  };

  const handlePayerChange = (e: ChangeEvent<HTMLSelectElement>) => {
    setPayerFilter(e.target.value);
    setPage(0);
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const f = e.target.files[0];
      if (f.size > 10 * 1024 * 1024) {
        setImportError('File size exceeds the 10MB limit.');
        setSelectedFile(null);
        return;
      }
      setSelectedFile(f);
      setImportError(null);
    }
  };

  const handleImportSubmit = async () => {
    setImportError(null);
    setImportSuccess(null);
    setImporting(true);

    try {
      if (importMode === 'file') {
        if (!selectedFile) {
          setImportError('Please select a file to import.');
          setImporting(false);
          return;
        }

        const formData = new FormData();
        formData.append('file', selectedFile);

        const resp = await api<{ id: number; batchControlNumber: string; claimsCount: number; matchedCount: number; exceptionCount: number }>(
          '/edi/835/import',
          {
            method: 'POST',
            body: formData,
          }
        );

        setImportSuccess(
          `Batch ${resp.batchControlNumber} imported successfully with ${resp.claimsCount} claims (${resp.matchedCount} matched, ${resp.exceptionCount} exceptions).`
        );
        setTimeout(() => {
          setShowImportModal(false);
          navigate(`/remittances/${resp.id}`);
        }, 1200);
      } else {
        if (!jsonText.trim()) {
          setImportError('Please enter a JSON payload.');
          setImporting(false);
          return;
        }
        let parsed: unknown;
        try {
          parsed = JSON.parse(jsonText);
        } catch {
          setImportError('Invalid JSON format. Please verify your payload.');
          setImporting(false);
          return;
        }

        const resp = await api<{ id: number; batchControlNumber: string; claimsCount: number; matchedCount: number; exceptionCount: number }>(
          '/edi/835/import',
          {
            method: 'POST',
            body: parsed,
          }
        );

        setImportSuccess(
          `Batch ${resp.batchControlNumber} imported successfully with ${resp.claimsCount} claims (${resp.matchedCount} matched, ${resp.exceptionCount} exceptions).`
        );
        setTimeout(() => {
          setShowImportModal(false);
          navigate(`/remittances/${resp.id}`);
        }, 1200);
      }

      // Refresh list and KPIs
      fetchBatches(page, statusFilter, payerFilter);
      fetchKpis();
    } catch (err) {
      setImportError(err instanceof ApiError ? err.detail : 'Remittance import failed.');
    } finally {
      setImporting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Remittance Management"
        subtitle="Electronic Remittance Advice (ERA 835) ingestion, reconciliation, and payment posting"
        actions={
          <div className="btn-row">
            <Link to="/remittances/exceptions" className="btn btn-secondary btn-sm">
              View Exceptions {unresolvedExceptionsCount > 0 && `(${unresolvedExceptionsCount})`}
            </Link>
            {canWrite && (
              <button
                className="btn btn-sm"
                onClick={() => {
                  setShowImportModal(true);
                  setImportError(null);
                  setImportSuccess(null);
                  setSelectedFile(null);
                  setJsonText('');
                }}
              >
                + Import Synthetic ERA 835
              </button>
            )}
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />

        {/* Remittance KPI Dashboard */}
        <div className="grid grid-4" style={{ marginBottom: 20 }}>
          <div className="card stat">
            <div className="stat-label">Total Batches</div>
            <div className="stat-value">{totalElements}</div>
            <div className="stat-note">ingested remittance files</div>
          </div>
          <div className="card stat">
            <div className="stat-label">Net Posted Payments</div>
            <div className="stat-value">
              <Money value={arSummary?.netRecordedPayerPayments} />
            </div>
            <div className="stat-note">
              Posted: <Money value={arSummary?.postedPayerPayments} /> &middot; Legacy: <Money value={arSummary?.legacyOpeningPayments} />
            </div>
          </div>
          <div className="card stat">
            <div className="stat-label">Payment Reversals</div>
            <div className="stat-value" style={{ color: 'var(--danger)' }}>
              <Money value={arSummary?.payerPaymentReversals} />
            </div>
            <div className="stat-note">audit-restored balances</div>
          </div>
          <div className="card stat">
            <div className="stat-label">Open Exceptions</div>
            <div className="stat-value" style={{ color: unresolvedExceptionsCount > 0 ? 'var(--warn)' : 'var(--ok)' }}>
              {unresolvedExceptionsCount}
            </div>
            <div className="stat-note">
              <Link to="/remittances/exceptions" style={{ fontSize: 11 }}>
                unmatched / out of balance &rarr;
              </Link>
            </div>
          </div>
        </div>

        {/* Batches Worklist Card */}
        <Card
          title="Remittance Advice Batches"
          tight
          actions={
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => {
                fetchBatches(page, statusFilter, payerFilter);
                fetchKpis();
              }}
            >
              Refresh
            </button>
          }
        >
          {/* Filters */}
          <div className="filters">
            <div className="field">
              <label htmlFor="statusFilter">Status</label>
              <select id="statusFilter" value={statusFilter} onChange={handleStatusChange}>
                {STATUS_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="payerFilter">Payer</label>
              <select id="payerFilter" value={payerFilter} onChange={handlePayerChange}>
                <option value="">All Payers</option>
                {payers.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {loading ? (
            <Loading label="Loading remittance batches" />
          ) : batches.length === 0 ? (
            <EmptyState
              title="No remittance batches found"
              hint="Import a synthetic ERA 835 file or JSON payload to begin reconciliation."
            />
          ) : (
            <>
              <table>
                <thead>
                  <tr>
                    <th>Batch Control #</th>
                    <th>Payer</th>
                    <th>Check / EFT Ref</th>
                    <th>Effective Date</th>
                    <th>Imported At</th>
                    <th className="num">Reported Total</th>
                    <th className="num">Claims</th>
                    <th className="num">Matched</th>
                    <th className="num">Exceptions</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {batches.map((b) => (
                    <tr key={b.id}>
                      <td className="mono">
                        <Link to={`/remittances/${b.id}`}>{b.batchControlNumber}</Link>
                      </td>
                      <td>{b.payerName}</td>
                      <td className="mono" style={{ fontSize: 12 }}>
                        {b.checkNumber || '&mdash;'}
                        {b.paymentMethod && <span className="faint" style={{ marginLeft: 6 }}>({b.paymentMethod})</span>}
                      </td>
                      <td>
                        <DateText value={b.checkDate} />
                      </td>
                      <td>
                        <DateTimeText value={b.importedAt} />
                      </td>
                      <td className="num font-semibold">
                        <Money value={b.totalPaid} />
                      </td>
                      <td className="num">{b.claimsCount}</td>
                      <td className="num" style={{ color: 'var(--ok)' }}>
                        {b.matchedCount}
                      </td>
                      <td className="num" style={{ color: b.exceptionCount > 0 ? 'var(--danger)' : 'inherit' }}>
                        {b.exceptionCount}
                      </td>
                      <td>
                        <RemittanceStatusBadge status={b.status} />
                      </td>
                      <td>
                        <Link to={`/remittances/${b.id}`} className="btn btn-secondary btn-sm">
                          View Details &rarr;
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

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

      {/* Import Modal */}
      {showImportModal && (
        <Modal
          title="Import Synthetic ERA 835 Remittance"
          onClose={() => !importing && setShowImportModal(false)}
          footer={
            <>
              <button
                className="btn btn-secondary"
                disabled={importing}
                onClick={() => setShowImportModal(false)}
              >
                Cancel
              </button>
              <button
                className="btn"
                disabled={importing || (importMode === 'file' && !selectedFile) || (importMode === 'json' && !jsonText.trim())}
                onClick={handleImportSubmit}
              >
                {importing ? 'Importing & Validating...' : 'Import & Stage'}
              </button>
            </>
          }
        >
          <div
            style={{
              padding: '10px 14px',
              background: 'var(--accent-soft)',
              borderRadius: 'var(--radius-s)',
              color: 'var(--accent)',
              fontSize: 12,
              marginBottom: 16,
              border: '1px solid #bfdbfe',
            }}
          >
            <strong>Educational Synthetic Notice:</strong> Educational synthetic ERA 835 processing. Not a certified insurance clearinghouse. Importing a remittance stages vouchers and exceptions without auto-posting payments.
          </div>

          {importError && <div className="alert alert-error" style={{ marginBottom: 14 }}>{importError}</div>}
          {importSuccess && <div className="alert alert-ok" style={{ marginBottom: 14 }}>{importSuccess}</div>}

          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <button
              type="button"
              className={`btn btn-sm ${importMode === 'file' ? '' : 'btn-secondary'}`}
              onClick={() => setImportMode('file')}
            >
              Upload File (.835 / .txt / .json)
            </button>
            <button
              type="button"
              className={`btn btn-sm ${importMode === 'json' ? '' : 'btn-secondary'}`}
              onClick={() => setImportMode('json')}
            >
              Paste JSON Payload
            </button>
          </div>

          {importMode === 'file' ? (
            <div className="field">
              <label htmlFor="eraFile">Select EDI 835 or JSON Remittance File</label>
              <input
                id="eraFile"
                type="file"
                accept=".835,.txt,.json,.edi"
                onChange={handleFileChange}
                disabled={importing}
              />
              <div className="field-hint">
                Supports ANSI ASC X12N 835 (005010X221A1) synthetic files, text files, or structured JSON. Maximum size: 10MB.
              </div>
            </div>
          ) : (
            <div className="field">
              <label htmlFor="eraJson">JSON Remittance Payload</label>
              <textarea
                id="eraJson"
                rows={10}
                style={{ fontFamily: 'var(--mono)', fontSize: 12 }}
                placeholder='{\n  "payerCode": "BCBS",\n  "checkNumber": "EFT-2026-9999",\n  "checkDate": "2026-11-20",\n  "totalPaid": 540.00,\n  "claims": [...]\n}'
                value={jsonText}
                onChange={(e) => setJsonText(e.target.value)}
                disabled={importing}
              />
              <div className="field-hint">
                Enter a valid <code>ImportRemittanceRequest</code> JSON payload with claims, amounts, and adjustments.
              </div>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}
