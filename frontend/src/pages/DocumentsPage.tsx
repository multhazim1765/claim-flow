import { useEffect, useState } from 'react';
import { api, downloadFile } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { Card, Spinner } from '../components/Common';
import type { AttachmentMetadataResponse } from '../api/types';

const CATEGORIES = [
  { value: '', label: 'All Categories' },
  { value: 'MEDICAL_REPORT', label: 'Medical Report' },
  { value: 'PRESCRIPTION', label: 'Prescription' },
  { value: 'LAB_REPORT', label: 'Lab Report' },
  { value: 'REFERRAL', label: 'Referral' },
  { value: 'PRIOR_AUTHORIZATION', label: 'Prior Authorization' },
  { value: 'ITEMIZED_BILL', label: 'Itemized Bill' },
  { value: 'DENIAL_LETTER', label: 'Denial Letter' },
  { value: 'APPEAL_SUPPORT', label: 'Appeal Support' },
  { value: 'INSURANCE_DOCUMENT', label: 'Insurance Document' },
  { value: 'DISCHARGE_SUMMARY', label: 'Discharge Summary' },
  { value: 'OTHER', label: 'Other' },
];

export default function DocumentsPage() {
  const { user } = useAuth();
  const canDownload = user?.role === 'ADMIN' || user?.role === 'BILLER';

  const [attachments, setAttachments] = useState<AttachmentMetadataResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [search, setSearch] = useState('');
  const [totalElements, setTotalElements] = useState(0);

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const query: Record<string, any> = { size: 50 };
      if (category) query.category = category;
      if (status) query.status = status;
      if (search) query.search = search;

      const res = await api<{ content: AttachmentMetadataResponse[]; totalElements: number }>(
        '/attachments',
        { query }
      );
      setAttachments(res.content || []);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      console.error('Failed to load attachments', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDocuments();
  }, [category, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadDocuments();
  };

  const downloadAttachment = async (att: AttachmentMetadataResponse) => {
    try {
      await downloadFile(
        `/claims/${att.claimId}/attachments/${att.id}/download`,
        undefined,
        att.originalFileName
      );
    } catch (err: any) {
      alert('Download failed: ' + err.message);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  return (
    <>
      <PageHeader
        title="Claim Supporting Documents"
        subtitle="Secure Clinical Records, Prior Authorizations, Denial Letters & Appeal Exhibits"
      />

      <div className="content">
        <div className="alert alert-info" style={{ marginBottom: 20 }}>
          <strong>Secure Document Repository:</strong> All attachments are validated by content sniffing (magic bytes), stored in private volume storage outside the webroot, and hashed with SHA-256 integrity checks.
        </div>

        {/* Filter and Search Bar */}
        <Card style={{ marginBottom: 20 }}>
          <form
            onSubmit={handleSearchSubmit}
            style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}
          >
            <div style={{ flex: 1, minWidth: 200 }}>
              <input
                type="text"
                placeholder="Search by file name or claim number..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                style={{ width: '100%', padding: '6px 10px', fontSize: 13, border: '1px solid #cbd5e1', borderRadius: 4 }}
              />
            </div>

            <div>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                style={{ padding: '6px 10px', fontSize: 13, border: '1px solid #cbd5e1', borderRadius: 4 }}
              >
                {CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                style={{ padding: '6px 10px', fontSize: 13, border: '1px solid #cbd5e1', borderRadius: 4 }}
              >
                <option value="ACTIVE">Active Documents</option>
                <option value="ARCHIVED">Archived Documents</option>
                <option value="">All Statuses</option>
              </select>
            </div>

            <button type="submit" className="btn btn-secondary btn-sm">
              Search
            </button>
          </form>
        </Card>

        {/* Documents Table */}
        <Card title={`Documents Repository (${totalElements})`}>
          {loading ? (
            <Spinner />
          ) : attachments.length === 0 ? (
            <div className="muted" style={{ textAlign: 'center', padding: 40 }}>
              No document attachments found matching your search.
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Document Name</th>
                  <th>Category</th>
                  <th>Claim Number</th>
                  <th>File Type</th>
                  <th>Size</th>
                  <th>Uploaded Date</th>
                  <th>Uploaded By</th>
                  <th>Security Scan</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {attachments.map((a) => (
                  <tr key={a.id} style={{ opacity: a.attachmentStatus === 'ARCHIVED' ? 0.6 : 1 }}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{a.originalFileName}</div>
                      {a.description && (
                        <div className="muted" style={{ fontSize: 11, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {a.description}
                        </div>
                      )}
                    </td>
                    <td>
                      <span className="pill pill-phase2" style={{ fontSize: 11 }}>
                        {a.documentCategory.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td>
                      <a href={`#/claims/${a.claimId}`} style={{ fontWeight: 600 }}>
                        {a.claimNumber}
                      </a>
                    </td>
                    <td style={{ fontSize: 12 }}>
                      <code>{a.contentType}</code>
                    </td>
                    <td style={{ fontSize: 12 }}>{formatFileSize(a.fileSize)}</td>
                    <td style={{ fontSize: 12 }}>{new Date(a.uploadedAt).toLocaleDateString()}</td>
                    <td style={{ fontSize: 12 }}>{a.uploadedBy}</td>
                    <td>
                      <span className="badge badge-ready" style={{ fontSize: 11 }}>
                        {a.securityScanStatus}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {canDownload ? (
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => downloadAttachment(a)}
                        >
                          Download
                        </button>
                      ) : (
                        <span className="muted" style={{ fontSize: 11 }}>Restricted</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>
    </>
  );
}

