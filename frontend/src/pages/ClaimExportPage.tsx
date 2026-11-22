import { useEffect, useState } from 'react';
import { api, downloadFile, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { Card, Spinner, Money } from '../components/Common';
import type {
  EdiExportKpis,
  EligibleClaimDto,
  EdiExportBatchSummary,
  EdiExportBatchDetailResponse,
  EdiValidationResponse,
  Payer,
} from '../api/types';

export default function ClaimExportPage() {
  const { user } = useAuth();
  const canExport = user?.role === 'ADMIN' || user?.role === 'BILLER';

  const [activeTab, setActiveTab] = useState<'worklist' | 'history'>('worklist');
  const [kpis, setKpis] = useState<EdiExportKpis | null>(null);
  const [payers, setPayers] = useState<Payer[]>([]);
  const [selectedPayerId, setSelectedPayerId] = useState<number | ''>('');

  // Eligible claims state
  const [claims, setClaims] = useState<EligibleClaimDto[]>([]);
  const [selectedClaimIds, setSelectedClaimIds] = useState<number[]>([]);
  const [loadingClaims, setLoadingClaims] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Batch history state
  const [batches, setBatches] = useState<EdiExportBatchSummary[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(false);

  // Wizard & Manifest Modals
  const [validationResult, setValidationResult] = useState<EdiValidationResponse | null>(null);
  const [validating, setValidating] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [allowRebill, setAllowRebill] = useState(false);
  const [exportNotes, setExportNotes] = useState('');
  const [manifestBatch, setManifestBatch] = useState<EdiExportBatchDetailResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadKpis = async () => {
    try {
      const data = await api<EdiExportKpis>('/edi/837p/kpis');
      setKpis(data);
    } catch (err) {
      console.error('Failed to load EDI KPIs', err);
    }
  };

  const loadPayers = async () => {
    try {
      const data = await api<Payer[]>('/payers');
      setPayers(data);
    } catch (err) {
      console.error('Failed to load payers', err);
    }
  };

  const loadClaims = async () => {
    setLoadingClaims(true);
    try {
      const query: Record<string, any> = { size: 50 };
      if (selectedPayerId !== '') query.payerId = selectedPayerId;
      const res = await api<{ content: EligibleClaimDto[] }>('/edi/837p/eligible-claims', { query });
      setClaims(res.content || []);
    } catch (err) {
      console.error('Failed to load eligible claims', err);
    } finally {
      setLoadingClaims(false);
    }
  };

  const loadBatches = async () => {
    setLoadingBatches(true);
    try {
      const query: Record<string, any> = { size: 30 };
      if (selectedPayerId !== '') query.payerId = selectedPayerId;
      const res = await api<{ content: EdiExportBatchSummary[] }>('/edi/837p/exports', { query });
      setBatches(res.content || []);
    } catch (err) {
      console.error('Failed to load export batches', err);
    } finally {
      setLoadingBatches(false);
    }
  };

  useEffect(() => {
    loadKpis();
    loadPayers();
  }, []);

  useEffect(() => {
    if (activeTab === 'worklist') {
      loadClaims();
    } else {
      loadBatches();
    }
  }, [activeTab, selectedPayerId]);

  const toggleSelectAll = () => {
    if (selectedClaimIds.length === filteredClaims.length) {
      setSelectedClaimIds([]);
    } else {
      setSelectedClaimIds(filteredClaims.map((c) => c.id));
    }
  };

  const toggleSelectClaim = (id: number) => {
    if (selectedClaimIds.includes(id)) {
      setSelectedClaimIds(selectedClaimIds.filter((cid) => cid !== id));
    } else {
      setSelectedClaimIds([...selectedClaimIds, id]);
    }
  };

  const filteredClaims = claims.filter((c) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      c.claimNumber.toLowerCase().includes(q) ||
      c.patientName.toLowerCase().includes(q) ||
      c.payerName.toLowerCase().includes(q) ||
      c.providerName.toLowerCase().includes(q)
    );
  });

  const handleValidate = async () => {
    if (selectedClaimIds.length === 0) return;
    setValidating(true);
    setErrorMessage(null);
    try {
      const res = await api<EdiValidationResponse>('/edi/837p/validate', {
        method: 'POST',
        body: { claimIds: selectedClaimIds, payerId: selectedPayerId || undefined },
      });
      setValidationResult(res);
    } catch (err) {
      setErrorMessage(err instanceof ApiError ? err.detail : 'Validation request failed');
    } finally {
      setValidating(false);
    }
  };

  const startExportWizard = async () => {
    setValidationResult(null);
    setErrorMessage(null);
    setSuccessMessage(null);
    setWizardOpen(true);
    await handleValidate();
  };

  const handleExecuteExport = async () => {
    if (selectedClaimIds.length === 0) return;
    setExporting(true);
    setErrorMessage(null);
    try {
      const res = await api<EdiExportBatchDetailResponse>('/edi/837p/export', {
        method: 'POST',
        body: {
          claimIds: selectedClaimIds,
          payerId: selectedPayerId || undefined,
          allowRebill,
          notes: exportNotes || undefined,
        },
      });
      setSuccessMessage(`Export batch ${res.batchNumber} created successfully with ${res.claimCount} claims!`);
      setWizardOpen(false);
      setSelectedClaimIds([]);
      loadKpis();
      loadClaims();
      // Open manifest
      setManifestBatch(res);
    } catch (err) {
      setErrorMessage(err instanceof ApiError ? err.detail : 'Export generation failed');
    } finally {
      setExporting(false);
    }
  };

  const viewManifest = async (batchId: number) => {
    try {
      const data = await api<EdiExportBatchDetailResponse>(`/edi/837p/exports/${batchId}`);
      setManifestBatch(data);
    } catch (err) {
      alert('Failed to load batch manifest: ' + (err instanceof ApiError ? err.detail : 'Unknown error'));
    }
  };

  const downloadBatch = async (batchId: number, fileName: string) => {
    try {
      await downloadFile(`/edi/837p/exports/${batchId}/download`, undefined, fileName);
    } catch (err: any) {
      alert('Failed to download EDI file: ' + err.message);
    }
  };

  return (
    <>
      <PageHeader
        title="EDI 837P Professional Claim Export"
        subtitle="Synthetic X12 005010X222A1 Batch Export & Clearinghouse Staging"
        actions={
          canExport && (
            <div style={{ display: 'flex', gap: 10 }}>
              <button
                className="btn btn-secondary btn-sm"
                onClick={handleValidate}
                disabled={selectedClaimIds.length === 0 || validating}
              >
                {validating ? 'Validating...' : `Validate Selected (${selectedClaimIds.length})`}
              </button>
              <button
                className="btn btn-primary btn-sm"
                onClick={startExportWizard}
                disabled={selectedClaimIds.length === 0}
              >
                Export Batch ({selectedClaimIds.length}) &rarr;
              </button>
            </div>
          )
        }
      />

      <div className="content">
        {/* Educational Disclaimer Banner */}
        <div className="alert alert-info" style={{ marginBottom: 20 }}>
          <strong>Educational Demonstration:</strong> Synthetic ASC X12 837P claim file generation. Built for RCM architectural demonstration and clearinghouse staging simulation. Not certified for live payer transmission.
        </div>

        {errorMessage && (
          <div className="alert alert-error" style={{ marginBottom: 20 }}>
            {errorMessage}
          </div>
        )}
        {successMessage && (
          <div className="alert alert-success" style={{ marginBottom: 20 }}>
            {successMessage}
          </div>
        )}

        {/* KPI Summary Cards */}
        {kpis && (
          <div className="kpi-grid" style={{ marginBottom: 20 }}>
            <Card title="Eligible Claims">
              <div className="kpi-value">{kpis.eligibleClaimsCount}</div>
              <div className="muted" style={{ fontSize: 12 }}>SUBMITTED claims ready</div>
            </Card>
            <Card title="Total Batches">
              <div className="kpi-value">{kpis.totalBatchesCount}</div>
              <div className="muted" style={{ fontSize: 12 }}>Generated export batches</div>
            </Card>
            <Card title="Exported Claims">
              <div className="kpi-value">{kpis.totalExportedClaimsCount}</div>
              <div className="muted" style={{ fontSize: 12 }}>Total historical claims</div>
            </Card>
            <Card title="Total Exported Billed">
              <div className="kpi-value"><Money value={kpis.totalAmountExported} /></div>
              <div className="muted" style={{ fontSize: 12 }}>Cumulative gross charges</div>
            </Card>
          </div>
        )}

        {/* Tab Controls & Filters */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
          <div className="tab-group" style={{ display: 'flex', gap: 8 }}>
            <button
              className={`btn btn-sm ${activeTab === 'worklist' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setActiveTab('worklist')}
            >
              Eligible Claims Worklist
            </button>
            <button
              className={`btn btn-sm ${activeTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setActiveTab('history')}
            >
              Export Batch History
            </button>
          </div>

          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <select
              value={selectedPayerId}
              onChange={(e) => setSelectedPayerId(e.target.value ? Number(e.target.value) : '')}
              style={{ padding: '6px 10px', borderRadius: 4, border: '1px solid #cbd5e1', fontSize: 13 }}
            >
              <option value="">All Payers</option>
              {payers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.payerCode})
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* TAB 1: Eligible Claims Worklist */}
        {activeTab === 'worklist' && (
          <Card
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                <span>Submitted Claims Eligible for EDI Export ({filteredClaims.length})</span>
                <input
                  type="text"
                  placeholder="Search claim, patient, provider..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{ width: 260, padding: '4px 8px', fontSize: 12, border: '1px solid #cbd5e1', borderRadius: 4 }}
                />
              </div>
            }
          >
            {loadingClaims ? (
              <Spinner />
            ) : filteredClaims.length === 0 ? (
              <div className="muted" style={{ textAlign: 'center', padding: 30 }}>
                No submitted claims found matching the filter criteria.
              </div>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    {canExport && (
                      <th style={{ width: 36, textAlign: 'center' }}>
                        <input
                          type="checkbox"
                          checked={selectedClaimIds.length === filteredClaims.length && filteredClaims.length > 0}
                          onChange={toggleSelectAll}
                        />
                      </th>
                    )}
                    <th>Claim #</th>
                    <th>Patient</th>
                    <th>Payer</th>
                    <th>Provider</th>
                    <th>Service Dates</th>
                    <th style={{ textAlign: 'right' }}>Total Charge</th>
                    <th>Export Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredClaims.map((c) => {
                    const isSelected = selectedClaimIds.includes(c.id);
                    return (
                      <tr key={c.id} style={{ backgroundColor: isSelected ? '#f0fdf4' : undefined }}>
                        {canExport && (
                          <td style={{ textAlign: 'center' }}>
                            <input
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleSelectClaim(c.id)}
                            />
                          </td>
                        )}
                        <td>
                          <a href={`#/claims/${c.id}`} style={{ fontWeight: 600 }}>
                            {c.claimNumber}
                          </a>
                        </td>
                        <td>{c.patientName}</td>
                        <td>{c.payerName}</td>
                        <td>{c.providerName}</td>
                        <td style={{ fontSize: 12 }}>
                          {c.serviceDateFrom} {c.serviceDateTo && c.serviceDateTo !== c.serviceDateFrom ? ` - ${c.serviceDateTo}` : ''}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 600 }}>
                          <Money value={c.totalCharge} />
                        </td>
                        <td>
                          {c.previouslyExported ? (
                            <span className="badge badge-partial" title={`Exported at ${c.lastExportedAt}`}>
                              Previously Exported
                            </span>
                          ) : (
                            <span className="badge badge-ready">Ready for 837P</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </Card>
        )}

        {/* TAB 2: Batch History */}
        {activeTab === 'history' && (
          <Card title={`Generated EDI 837P Batches (${batches.length})`}>
            {loadingBatches ? (
              <Spinner />
            ) : batches.length === 0 ? (
              <div className="muted" style={{ textAlign: 'center', padding: 30 }}>
                No EDI 837P export batches generated yet.
              </div>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Batch #</th>
                    <th>Payer</th>
                    <th>Claim Count</th>
                    <th style={{ textAlign: 'right' }}>Total Billed</th>
                    <th>File Name</th>
                    <th>Export Date</th>
                    <th>Actor</th>
                    <th>Status</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {batches.map((b) => (
                    <tr key={b.id}>
                      <td style={{ fontWeight: 600 }}>{b.batchNumber}</td>
                      <td>{b.payerName}</td>
                      <td>{b.claimCount} claims</td>
                      <td style={{ textAlign: 'right', fontWeight: 600 }}>
                        <Money value={b.totalBilledAmount} />
                      </td>
                      <td><code>{b.fileName}</code></td>
                      <td style={{ fontSize: 12 }}>{new Date(b.exportedAt).toLocaleString()}</td>
                      <td>{b.createdBy}</td>
                      <td>
                        <span className="badge badge-paid">{b.exportStatus}</span>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ marginRight: 6 }}
                          onClick={() => viewManifest(b.id)}
                        >
                          Manifest
                        </button>
                        <button
                          className="btn btn-primary btn-sm"
                          onClick={() => downloadBatch(b.id, b.fileName)}
                        >
                          Download .837
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        )}

        {/* Validation Drawer / Results */}
        {validationResult && !wizardOpen && (
          <div style={{ marginTop: 20 }}>
            <Card
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                  <span>Pre-Export EDI Validation Results ({validationResult.validCount} valid, {validationResult.errorCount} errors)</span>
                  <button className="btn btn-secondary btn-sm" onClick={() => setValidationResult(null)}>
                    Dismiss
                  </button>
                </div>
              }
            >
              {validationResult.results.map((r) => (
                <div
                  key={r.claimId}
                  style={{
                    padding: '8px 12px',
                    borderBottom: '1px solid #e2e8f0',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    backgroundColor: r.valid ? '#f8fafc' : '#fef2f2',
                  }}
                >
                  <div>
                    <strong>{r.claimNumber}</strong> ({r.patientName} &bull; {r.payerName})
                    {r.errors.length > 0 && (
                      <ul style={{ margin: '4px 0 0 16px', color: '#dc2626', fontSize: 12 }}>
                        {r.errors.map((e, idx) => (
                          <li key={idx}>{e}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div>
                    {r.valid ? (
                      <span className="badge badge-paid">PASS</span>
                    ) : (
                      <span className="badge badge-denied">FAIL</span>
                    )}
                  </div>
                </div>
              ))}
            </Card>
          </div>
        )}

        {/* Export Wizard Modal */}
        {wizardOpen && (
          <div className="modal-backdrop">
            <div className="modal-card" style={{ maxWidth: 650 }}>
              <div className="modal-head">
                <h3>EDI 837P Export Generation Wizard</h3>
                <button className="btn-close" onClick={() => setWizardOpen(false)}>
                  &times;
                </button>
              </div>
              <div className="modal-body">
                <div style={{ marginBottom: 15 }}>
                  <p style={{ margin: '0 0 10px 0', fontSize: 13, color: '#334155' }}>
                    You have selected <strong>{selectedClaimIds.length}</strong> submitted claim(s) for synthetic ASC X12 837P export generation.
                  </p>
                </div>

                {validating && (
                  <div style={{ textAlign: 'center', padding: 20 }}>
                    <Spinner />
                    <div className="muted" style={{ marginTop: 8, fontSize: 13 }}>
                      Validating EDI mandatory data elements (NPI, ICD-10, CPT, member ID)...
                    </div>
                  </div>
                )}

                {validationResult && (
                  <div style={{ marginBottom: 15 }}>
                    <div
                      className={`alert ${validationResult.allValid ? 'alert-success' : 'alert-error'}`}
                      style={{ fontSize: 13 }}
                    >
                      {validationResult.allValid ? (
                        <span>All {validationResult.validCount} claims passed EDI 837P validation rules! Total charges: <Money value={validationResult.totalCharge} /></span>
                      ) : (
                        <span>{validationResult.errorCount} of {validationResult.results.length} claims failed validation rules.</span>
                      )}
                    </div>

                    {!validationResult.allValid && (
                      <div style={{ maxHeight: 160, overflowY: 'auto', border: '1px solid #fecaca', borderRadius: 4, padding: 8, backgroundColor: '#fff5f5' }}>
                        {validationResult.results.filter(r => !r.valid).map(r => (
                          <div key={r.claimId} style={{ marginBottom: 6, fontSize: 12 }}>
                            <strong>{r.claimNumber}:</strong>
                            <ul style={{ margin: '2px 0 0 16px', color: '#b91c1c' }}>
                              {r.errors.map((e, idx) => <li key={idx}>{e}</li>)}
                            </ul>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                <div className="field" style={{ marginTop: 15 }}>
                  <label>Export Notes / Reference</label>
                  <input
                    type="text"
                    placeholder="e.g. Weekly clearinghouse batch submission"
                    value={exportNotes}
                    onChange={(e) => setExportNotes(e.target.value)}
                    style={{ width: '100%', padding: '6px 10px', fontSize: 13 }}
                  />
                </div>

                <div style={{ marginTop: 10 }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={allowRebill}
                      onChange={(e) => setAllowRebill(e.target.checked)}
                    />
                    <span>Allow Re-export (overwrite/rebill claims already in previous batches)</span>
                  </label>
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button className="btn btn-secondary" onClick={() => setWizardOpen(false)} disabled={exporting}>
                  Cancel
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleExecuteExport}
                  disabled={exporting || (validationResult != null && !validationResult.allValid)}
                >
                  {exporting ? 'Generating 837P...' : 'Generate & Stage Batch'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Batch Manifest Modal */}
        {manifestBatch && (
          <div className="modal-backdrop">
            <div className="modal-card" style={{ maxWidth: 750 }}>
              <div className="modal-head">
                <h3>Batch Manifest: {manifestBatch.batchNumber}</h3>
                <button className="btn-close" onClick={() => setManifestBatch(null)}>
                  &times;
                </button>
              </div>
              <div className="modal-body">
                <div className="grid grid-2" style={{ marginBottom: 15, fontSize: 13 }}>
                  <div><strong>Payer:</strong> {manifestBatch.payerName}</div>
                  <div><strong>Control Numbers:</strong> ISA={manifestBatch.interchangeControlNumber} &bull; GS={manifestBatch.groupControlNumber} &bull; ST={manifestBatch.transactionControlNumber}</div>
                  <div><strong>Total Billed:</strong> <Money value={manifestBatch.totalBilledAmount} /></div>
                  <div><strong>File Name:</strong> <code>{manifestBatch.fileName}</code></div>
                  <div><strong>SHA-256 Hash:</strong> <code style={{ fontSize: 11 }}>{manifestBatch.contentHash.substring(0, 24)}...</code></div>
                  <div><strong>Exported:</strong> {new Date(manifestBatch.exportedAt).toLocaleString()} by {manifestBatch.createdBy}</div>
                </div>

                <h4 style={{ margin: '15px 0 8px 0', fontSize: 14 }}>Exported Claims ({manifestBatch.claims.length})</h4>
                <div style={{ maxHeight: 200, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 4 }}>
                  <table className="table" style={{ margin: 0 }}>
                    <thead>
                      <tr>
                        <th>Seq</th>
                        <th>Claim Number</th>
                        <th style={{ textAlign: 'right' }}>Billed Snapshot</th>
                        <th>Exported Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {manifestBatch.claims.map((c) => (
                        <tr key={c.claimId}>
                          <td>#{c.sequence}</td>
                          <td style={{ fontWeight: 600 }}>{c.claimNumber}</td>
                          <td style={{ textAlign: 'right' }}><Money value={c.chargeAmount} /></td>
                          <td style={{ fontSize: 12 }}>{new Date(c.exportedAt).toLocaleDateString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <button
                  className="btn btn-primary"
                  onClick={() => downloadBatch(manifestBatch.id, manifestBatch.fileName)}
                >
                  Download {manifestBatch.fileName}
                </button>
                <button className="btn btn-secondary" onClick={() => setManifestBatch(null)}>
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

