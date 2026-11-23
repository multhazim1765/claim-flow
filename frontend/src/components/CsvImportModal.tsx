import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { CsvPreviewResponse, CsvImportResultResponse } from '../api/types';
import { ErrorAlert } from './Common';

interface CsvImportModalProps {
  entityType: 'PATIENTS' | 'PROVIDERS' | 'PAYERS' | 'POLICIES';
  title: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function CsvImportModal({ entityType, title, onClose, onSuccess }: CsvImportModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [csvText, setCsvText] = useState<string>('');
  const [useTextInput, setUseTextInput] = useState<boolean>(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<CsvPreviewResponse | null>(null);
  const [abortOnError, setAbortOnError] = useState(true);
  const [result, setResult] = useState<CsvImportResultResponse | null>(null);

  const downloadTemplate = async () => {
    try {
      const token = localStorage.getItem('cms.token');
      const response = await fetch(`/api/import/template/${entityType}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!response.ok) throw new Error('Failed to download template');
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${entityType.toLowerCase()}_template.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not download template.');
    }
  };

  const handlePreview = async () => {
    setError(null);
    setLoading(true);
    setResult(null);

    try {
      let previewData: CsvPreviewResponse;
      if (useTextInput) {
        if (!csvText.trim()) {
          setError('Please paste CSV content.');
          setLoading(false);
          return;
        }
        previewData = await api<CsvPreviewResponse>(`/import/preview-text/${entityType}`, {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain' },
          body: csvText,
        });
      } else {
        if (!file) {
          setError('Please select a CSV file.');
          setLoading(false);
          return;
        }
        const formData = new FormData();
        formData.append('file', file);
        const token = localStorage.getItem('cms.token');
        const res = await fetch(`/api/import/preview/${entityType}`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: formData,
        });
        if (!res.ok) {
          const errJson = await res.json().catch(() => ({}));
          throw new Error(errJson.detail || 'Failed to preview CSV.');
        }
        previewData = await res.json();
      }
      setPreview(previewData);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : err instanceof Error ? err.message : 'Failed to preview CSV.');
    } finally {
      setLoading(false);
    }
  };

  const handleExecute = async () => {
    if (!preview || preview.rows.length === 0) return;
    setError(null);
    setLoading(true);

    try {
      const rowsPayload = preview.rows.map((r) => r.data);
      const res = await api<CsvImportResultResponse>(`/import/execute/${entityType}`, {
        method: 'POST',
        body: {
          filename: file ? file.name : 'text-import.csv',
          rows: rowsPayload,
          abortOnError,
        },
      });
      setResult(res);
      if (res.successCount > 0) {
        onSuccess();
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Bulk import execution failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose} style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1000,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div className="modal" onClick={(e) => e.stopPropagation()} style={{
        background: '#fff', borderRadius: 8, maxWidth: 900, width: '90%',
        maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 8px 30px rgba(0,0,0,0.2)',
      }}>
        <div className="modal-header" style={{
          padding: '16px 24px', borderBottom: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>{title}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Bulk import {entityType.toLowerCase()} via RFC 4180 CSV</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <div className="modal-body" style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          <ErrorAlert message={error} />

          {result ? (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <div style={{
                fontSize: 48, marginBottom: 16,
                color: result.status === 'COMPLETED' ? '#16a34a' : (result.status === 'PARTIAL' ? '#ca8a04' : '#dc2626')
              }}>
                {result.status === 'COMPLETED' ? '✓' : (result.status === 'PARTIAL' ? '⚠' : '✕')}
              </div>
              <h4 style={{ margin: '0 0 8px' }}>
                Import {result.status}
              </h4>
              <p style={{ color: '#475569', margin: '0 0 16px' }}>
                Batch #{result.batchId}: <strong>{result.successCount}</strong> imported successfully, <strong>{result.errorCount}</strong> errors.
              </p>
              {result.errorMessages.length > 0 && (
                <div style={{ textAlign: 'left', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 6, padding: 12, maxHeight: 150, overflowY: 'auto' }}>
                  <strong style={{ color: '#991b1b', fontSize: 13 }}>Import Warnings & Errors:</strong>
                  <ul style={{ margin: '4px 0 0', paddingLeft: 20, fontSize: 12, color: '#b91c1c' }}>
                    {result.errorMessages.map((msg, i) => <li key={i}>{msg}</li>)}
                  </ul>
                </div>
              )}
            </div>
          ) : (
            <>
              <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center', justifyContent: 'space-between' }}>
                <button type="button" className="btn btn-secondary btn-sm" onClick={downloadTemplate}>
                  📥 Download CSV Template
                </button>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button type="button" className={`btn btn-sm ${!useTextInput ? 'btn-primary' : 'btn-secondary'}`}
                          onClick={() => setUseTextInput(false)}>
                    File Upload
                  </button>
                  <button type="button" className={`btn btn-sm ${useTextInput ? 'btn-primary' : 'btn-secondary'}`}
                          onClick={() => setUseTextInput(true)}>
                    Paste Text
                  </button>
                </div>
              </div>

              {!useTextInput ? (
                <div style={{
                  border: '2px dashed #cbd5e1', borderRadius: 6, padding: '24px',
                  textAlign: 'center', background: '#f8fafc', marginBottom: 16,
                }}>
                  <input type="file" accept=".csv" id="csv-file-input" style={{ display: 'none' }}
                         onChange={(e) => setFile(e.target.files?.[0] || null)} />
                  <label htmlFor="csv-file-input" className="btn btn-secondary" style={{ cursor: 'pointer' }}>
                    Choose CSV File
                  </label>
                  <div style={{ marginTop: 8, fontSize: 13, color: '#64748b' }}>
                    {file ? <strong>Selected: {file.name} ({(file.size / 1024).toFixed(1)} KB)</strong> : 'Supports UTF-8 CSV with headers'}
                  </div>
                </div>
              ) : (
                <div style={{ marginBottom: 16 }}>
                  <label style={{ display: 'block', marginBottom: 4, fontWeight: 500, fontSize: 13 }}>
                    CSV Data (including header row)
                  </label>
                  <textarea
                    rows={6}
                    value={csvText}
                    onChange={(e) => setCsvText(e.target.value)}
                    placeholder="paste comma-separated values here..."
                    style={{ width: '100%', fontFamily: 'monospace', fontSize: 12, padding: 8, borderRadius: 4, border: '1px solid #cbd5e1' }}
                  />
                </div>
              )}

              <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
                <button type="button" className="btn btn-primary" onClick={handlePreview} disabled={loading}>
                  {loading ? 'Validating...' : '🔍 Preview & Validate CSV'}
                </button>
              </div>

              {preview && (
                <div style={{ marginTop: 16 }}>
                  <div style={{
                    display: 'flex', gap: 16, padding: '12px 16px', background: '#f1f5f9',
                    borderRadius: 6, marginBottom: 12, fontSize: 13,
                  }}>
                    <div>Total Rows: <strong>{preview.totalRows}</strong></div>
                    <div style={{ color: '#16a34a' }}>Valid: <strong>{preview.validRows}</strong></div>
                    <div style={{ color: preview.errorRows > 0 ? '#dc2626' : '#64748b' }}>
                      Errors: <strong>{preview.errorRows}</strong>
                    </div>
                    <div style={{ color: preview.duplicateRows > 0 ? '#ca8a04' : '#64748b' }}>
                      Duplicates: <strong>{preview.duplicateRows}</strong>
                    </div>
                  </div>

                  <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 4 }}>
                    <table style={{ fontSize: 12, margin: 0, width: '100%' }}>
                      <thead>
                        <tr>
                          <th style={{ width: 45 }}>#</th>
                          <th style={{ width: 70 }}>Status</th>
                          {preview.headers.map((h) => <th key={h}>{h}</th>)}
                          <th>Validation Notes</th>
                        </tr>
                      </thead>
                      <tbody>
                        {preview.rows.map((r) => (
                          <tr key={r.rowIndex} style={{
                            backgroundColor: !r.valid ? '#fef2f2' : (r.duplicate ? '#fefce8' : 'transparent'),
                          }}>
                            <td>{r.rowIndex}</td>
                            <td>
                              <span className={r.valid ? 'badge badge-PAID' : 'badge badge-VOID'}>
                                {r.valid ? 'VALID' : 'INVALID'}
                              </span>
                            </td>
                            {preview.headers.map((h) => (
                              <td key={h} className="mono" style={{ whiteSpace: 'nowrap' }}>
                                {r.data[h] || <span className="muted">&mdash;</span>}
                              </td>
                            ))}
                            <td>
                              {r.errors.length > 0 ? (
                                <span style={{ color: '#b91c1c' }}>{r.errors.join('; ')}</span>
                              ) : (
                                <span style={{ color: '#16a34a' }}>Ready to import</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <label style={{ fontSize: 13, display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                      <input type="checkbox" checked={abortOnError} onChange={(e) => setAbortOnError(e.target.checked)} />
                      Abort entire import if any validation errors occur (Atomic Rollback)
                    </label>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal-footer" style={{
          padding: '16px 24px', borderTop: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'flex-end', gap: 12,
        }}>
          {result ? (
            <button className="btn btn-primary" onClick={onClose}>Done</button>
          ) : (
            <>
              <button className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
              {preview && (
                <button
                  className="btn btn-primary"
                  onClick={handleExecute}
                  disabled={loading || preview.validRows === 0 || (abortOnError && preview.errorRows > 0)}
                >
                  {loading ? 'Importing...' : `Confirm Import (${preview.validRows} Records)`}
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
