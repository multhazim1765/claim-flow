import { useCallback, useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type {
  EligibilityCheckRequest,
  EligibilityCheckResponse,
  EligibilityHistoryItem,
  PageResponse,
  PatientSummary,
  Payer,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import {
  Card,
  DateTimeText,
  DateText,
  EmptyState,
  ErrorAlert,
  Loading,
  Money,
} from '../components/Common';
import { useAuth } from '../auth/AuthContext';

const SERVICE_TYPE_CODES = [
  { code: '30', label: '30 - Health Benefit Plan Coverage' },
  { code: '1', label: '1 - Medical Care' },
  { code: '33', label: '33 - Chiropractic' },
  { code: '35', label: '35 - Dental Care' },
  { code: '88', label: '88 - Pharmacy' },
  { code: '98', label: '98 - Professional (Physician) Visit' },
];

export default function EligibilityPage() {
  const [searchParams] = useSearchParams();
  const { canWrite } = useAuth();

  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [payers, setPayers] = useState<Payer[]>([]);

  const [selectedPatientId, setSelectedPatientId] = useState<string>(
    searchParams.get('patientId') ?? ''
  );
  const [selectedPayerId, setSelectedPayerId] = useState<string>(
    searchParams.get('payerId') ?? ''
  );
  const [serviceDate, setServiceDate] = useState<string>(
    () => new Date().toISOString().slice(0, 10)
  );
  const [serviceTypeCode, setServiceTypeCode] = useState<string>('30');

  const [loadingInitial, setLoadingInitial] = useState(true);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [result, setResult] = useState<EligibilityCheckResponse | null>(null);
  const [history, setHistory] = useState<EligibilityHistoryItem[]>([]);
  const [showRaw, setShowRaw] = useState(false);

  // Load patient list and payer list
  useEffect(() => {
    let alive = true;
    Promise.all([
      api<PageResponse<PatientSummary>>('/patients', { query: { size: 100 } }),
      api<Payer[]>('/payers'),
    ])
      .then(([pts, pys]) => {
        if (!alive) return;
        setPatients(pts.content);
        setPayers(pys);
        if (!selectedPatientId && pts.content.length > 0) {
          setSelectedPatientId(String(pts.content[0].id));
        }
        if (!selectedPayerId && pys.length > 0) {
          setSelectedPayerId(String(pys[0].id));
        }
      })
      .catch((err) => {
        if (alive) {
          setError(err instanceof ApiError ? err.detail : 'Could not load reference data.');
        }
      })
      .finally(() => {
        if (alive) setLoadingInitial(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Fetch patient history when patient changes
  const loadHistory = useCallback(async (patientId: string) => {
    if (!patientId) {
      setHistory([]);
      return;
    }
    try {
      const items = await api<EligibilityHistoryItem[]>('/eligibility/history', {
        query: { patientId: Number(patientId) },
      });
      setHistory(items);
    } catch {
      // history load failure is non-fatal to the main page
      setHistory([]);
    }
  }, []);

  useEffect(() => {
    if (selectedPatientId) {
      void loadHistory(selectedPatientId);
    }
  }, [selectedPatientId, loadHistory]);

  const runCheck = async (forceRefresh = false) => {
    if (!selectedPatientId || !selectedPayerId || !serviceDate) {
      setError('Please select a patient, payer, and service date.');
      return;
    }

    setChecking(true);
    setError(null);
    try {
      const req: EligibilityCheckRequest = {
        patientId: Number(selectedPatientId),
        payerId: Number(selectedPayerId),
        serviceDate,
        serviceTypeCode,
        forceRefresh,
      };

      const res = await api<EligibilityCheckResponse>('/eligibility/check', {
        method: 'POST',
        body: req,
      });
      setResult(res);
      await loadHistory(selectedPatientId);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Eligibility check failed.');
    } finally {
      setChecking(false);
    }
  };

  if (loadingInitial) {
    return (
      <>
        <PageHeader title="Real-Time Eligibility (270/271)" subtitle="ClaimFlow Revenue Cycle Intelligence" />
        <div className="content">
          <Loading label="Loading patient and payer directory" />
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Real-Time Eligibility (270/271)"
        subtitle="Verify patient insurance coverage, copay, coinsurance, and deductibles before service"
        actions={
          <div className="btn-row">
            <button
              className="btn btn-secondary"
              disabled={!canWrite || checking || !selectedPatientId || !selectedPayerId}
              onClick={() => runCheck(true)}
              title={!canWrite ? 'Read-only access (ADMIN or BILLER role required)' : undefined}
            >
              Force Refresh
            </button>
            <button
              className="btn btn-primary"
              disabled={!canWrite || checking || !selectedPatientId || !selectedPayerId}
              onClick={() => runCheck(false)}
              title={!canWrite ? 'Read-only access (ADMIN or BILLER role required)' : undefined}
            >
              {checking ? 'Inquiring Payer...' : 'Check Eligibility'}
            </button>
          </div>
        }
      />

      <div className="content">
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <div>
            <strong>SIMULATED ELIGIBILITY — NOT REAL PAYER VERIFICATION</strong>
            <div style={{ fontSize: 12, marginTop: 2 }}>
              Deterministic ANSI X12 270/271 clearinghouse gateway simulation for ClaimFlow demonstration.
            </div>
          </div>
          <span className="pill pill-phase2">Milestone 2.1</span>
        </div>

        <ErrorAlert message={error} />

        <div className="grid grid-2">
          {/* Inquiry Form */}
          <Card title="Eligibility Inquiry (270 Request)">
            <div className="form">
              <div className="form-group">
                <label htmlFor="patient-select">Patient</label>
                <select
                  id="patient-select"
                  className="input"
                  value={selectedPatientId}
                  onChange={(e) => {
                    setSelectedPatientId(e.target.value);
                    setResult(null);
                  }}
                >
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.displayName} ({p.mrn})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="payer-select">Payer</label>
                <select
                  id="payer-select"
                  className="input"
                  value={selectedPayerId}
                  onChange={(e) => {
                    setSelectedPayerId(e.target.value);
                    setResult(null);
                  }}
                >
                  {payers.map((py) => (
                    <option key={py.id} value={py.id}>
                      {py.name} ({py.planType})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-row">
                <div className="form-group" style={{ flex: 1 }}>
                  <label htmlFor="service-date">Date of Service</label>
                  <input
                    id="service-date"
                    type="date"
                    className="input"
                    value={serviceDate}
                    onChange={(e) => setServiceDate(e.target.value)}
                  />
                </div>

                <div className="form-group" style={{ flex: 1.5 }}>
                  <label htmlFor="service-type">Service Type Code</label>
                  <select
                    id="service-type"
                    className="input"
                    value={serviceTypeCode}
                    onChange={(e) => setServiceTypeCode(e.target.value)}
                  >
                    {SERVICE_TYPE_CODES.map((st) => (
                      <option key={st.code} value={st.code}>
                        {st.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div style={{ marginTop: 12 }}>
                {!canWrite && (
                  <span className="faint" style={{ fontSize: 12 }}>
                    * Signed in with a read-only role (VIEWER). Verification inquiries require BILLER or ADMIN.
                  </span>
                )}
              </div>
            </div>
          </Card>

          {/* Verification Result Card */}
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                <span>Coverage Adjudication (271 Response)</span>
                {result && (
                  <span className={result.cached ? 'badge badge-SUBMITTED' : 'badge badge-ACCEPTED'}>
                    {result.cached ? 'CACHED (24h Hit)' : 'FRESH VERIFIED'}
                  </span>
                )}
              </div>
            }
          >
            {checking && <Loading label="Communicating with payer clearinghouse" />}

            {!checking && !result && (
              <EmptyState
                title="No verification performed yet"
                hint="Select a patient and payer, then click Check Eligibility above."
              />
            )}

            {!checking && result && (
              <div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    borderRadius: 6,
                    marginBottom: 16,
                    background: result.active ? 'var(--ok-soft)' : 'var(--danger-soft)',
                    border: `1px solid ${result.active ? 'var(--ok)' : 'var(--danger)'}`,
                  }}
                >
                  <div>
                    <strong style={{ fontSize: 15, color: result.active ? 'var(--ok)' : 'var(--danger)' }}>
                      {result.active ? 'COVERAGE ACTIVE' : 'COVERAGE INACTIVE / TERMINATED'}
                    </strong>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                      Verified on <DateTimeText value={result.checkedAt} />
                    </div>
                  </div>
                  <span className={result.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                    {result.active ? 'ACTIVE' : 'INACTIVE'}
                  </span>
                </div>

                <dl className="kv">
                  <dt>Patient</dt>
                  <dd>
                    <Link to={`/patients/${result.patientId}`}>
                      <strong>{result.patientName}</strong>
                    </Link>{' '}
                    <span className="mono faint">({result.patientMrn})</span>
                  </dd>

                  <dt>Payer</dt>
                  <dd>{result.payerName}</dd>

                  <dt>Member ID</dt>
                  <dd className="mono">{result.policyMemberId ?? <span className="faint">None on file</span>}</dd>

                  <dt>Plan Dates</dt>
                  <dd>
                    <DateText value={result.planBegin} /> &mdash;{' '}
                    {result.planEnd ? <DateText value={result.planEnd} /> : <span className="faint">Open-ended</span>}
                  </dd>

                  <dt>Copay</dt>
                  <dd>
                    <strong><Money value={result.copay} /></strong>
                  </dd>

                  <dt>Coinsurance</dt>
                  <dd>
                    {result.coinsurancePercent != null ? `${result.coinsurancePercent}%` : <span className="faint">&mdash;</span>}
                  </dd>

                  <dt>Deductible (Total / Rem.)</dt>
                  <dd>
                    <Money value={result.deductibleTotal} /> /{' '}
                    <span style={{ color: result.deductibleRemaining && result.deductibleRemaining > 0 ? 'var(--warn)' : 'var(--ok)' }}>
                      <Money value={result.deductibleRemaining} />
                    </span>
                  </dd>

                  <dt>Out-of-Pocket Remaining</dt>
                  <dd>
                    <Money value={result.outOfPocketRemaining} />
                  </dd>
                </dl>

                <div style={{ marginTop: 16, borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => setShowRaw((v) => !v)}
                  >
                    {showRaw ? 'Hide ANSI X12 271 EDI Payload' : 'View ANSI X12 271 EDI Payload'}
                  </button>

                  {showRaw && result.rawResponse && (
                    <div style={{ marginTop: 8 }}>
                      <pre
                        className="mono"
                        style={{
                          background: '#101722',
                          color: '#17c3a2',
                          padding: 12,
                          borderRadius: 6,
                          fontSize: 11,
                          overflowX: 'auto',
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-all',
                        }}
                      >
                        {result.rawResponse}
                      </pre>
                    </div>
                  )}
                </div>
              </div>
            )}
          </Card>
        </div>

        {/* Audit History Card */}
        <div style={{ marginTop: 24 }}>
          <Card title={`Eligibility Verification Audit Trail (${history.length})`} tight>
            {history.length === 0 ? (
              <EmptyState
                title="No prior verification records"
                hint="Verification checks performed for this patient will be persisted here for appeals and claims compliance."
              />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Checked At</th>
                    <th>Patient</th>
                    <th>Payer</th>
                    <th>Service Date</th>
                    <th>Status</th>
                    <th className="num">Copay</th>
                    <th className="num">Ded. Rem.</th>
                    <th>Source</th>
                    <th>Biller</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((h) => (
                    <tr key={h.id}>
                      <td><DateTimeText value={h.checkedAt} /></td>
                      <td>{h.patientName}</td>
                      <td>{h.payerName}</td>
                      <td><DateText value={h.serviceDate} /></td>
                      <td>
                        <span className={h.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                          {h.active ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      <td className="num"><Money value={h.copay} /></td>
                      <td className="num"><Money value={h.deductibleRemaining} /></td>
                      <td>
                        <span className={h.cached ? 'pill pill-phase2' : 'pill pill-terminal'}>
                          {h.cached ? 'Cache' : 'Gateway'}
                        </span>
                      </td>
                      <td className="faint">{h.checkedBy}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}

