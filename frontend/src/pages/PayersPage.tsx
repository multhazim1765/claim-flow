import { useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { PageResponse, Payer } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { EmptyState, ErrorAlert, Loading, Pagination } from '../components/Common';
import { PayerModal } from '../components/PayerModal';
import { CsvImportModal } from '../components/CsvImportModal';

export default function PayersPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [data, setData] = useState<PageResponse<Payer> | null>(null);
  const [q, setQ] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingPayer, setEditingPayer] = useState<Payer | null>(null);
  const [showImportModal, setShowImportModal] = useState(false);

  const loadPayers = () => {
    setLoading(true);
    const queryParams: Record<string, any> = { q, page, size: 20, sort: 'name' };
    if (activeFilter === 'active') queryParams.active = true;
    if (activeFilter === 'inactive') queryParams.active = false;

    api<PageResponse<Payer>>('/payers/search', { query: queryParams })
      .then((d) => { setData(d); setError(null); })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load payers.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadPayers();
  }, [q, activeFilter, page]);

  const handleToggleStatus = async (p: Payer) => {
    const action = p.active ? 'deactivate' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} payer ${p.name} (${p.payerCode})?`)) {
      return;
    }

    try {
      await api<Payer>(`/payers/${p.id}/status`, {
        method: 'PATCH',
        query: { active: !p.active },
      });
      setSuccessMsg(`Payer ${p.name} ${p.active ? 'deactivated' : 'reactivated'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
      loadPayers();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} payer.`);
    }
  };

  return (
    <>
      <PageHeader
        title="Payers"
        subtitle="Insurance companies claims are billed to"
        actions={
          isAdmin && (
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-secondary" onClick={() => setShowImportModal(true)}>
                📥 Import CSV
              </button>
              <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
                + Add Payer
              </button>
            </div>
          )
        }
      />
      <div className="content">
        <ErrorAlert message={error} />
        {successMsg && (
          <div style={{
            padding: '10px 16px', background: '#ecfdf5', border: '1px solid #a7f3d0',
            color: '#065f46', borderRadius: 6, marginBottom: 16, fontSize: 13,
          }}>
            {successMsg}
          </div>
        )}

        <div className="card">
          <div className="filters" style={{ display: 'flex', gap: 16, alignItems: 'flex-end', marginBottom: 16 }}>
            <div className="field grow">
              <label htmlFor="search">Search</label>
              <input id="search" placeholder="Payer name or code (press Enter)"
                     defaultValue={q}
                     onKeyDown={(e) => {
                       if (e.key === 'Enter') { setPage(0); setQ((e.target as HTMLInputElement).value); }
                     }}
                     onBlur={(e) => { setPage(0); setQ(e.target.value); }} />
            </div>

            <div className="field" style={{ minWidth: 160 }}>
              <label htmlFor="filter-status">Status</label>
              <select id="filter-status" value={activeFilter} onChange={(e) => { setPage(0); setActiveFilter(e.target.value); }}>
                <option value="all">All Payers</option>
                <option value="active">Active Only</option>
                <option value="inactive">Inactive Only</option>
              </select>
            </div>
          </div>

          {loading && <Loading label="Loading payers" />}
          {!loading && data && data.content.length === 0 && <EmptyState title="No payers found" />}

          {!loading && data && data.content.length > 0 && (
            <>
              <table>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Name</th>
                    <th>Plan type</th>
                    <th>Claims address</th>
                    <th>Phone</th>
                    <th>Status</th>
                    {isAdmin && <th style={{ textAlign: 'right' }}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id}>
                      <td className="mono">{p.payerCode}</td>
                      <td><strong>{p.name}</strong></td>
                      <td><span className="pill">{p.planType.replace('_', ' ')}</span></td>
                      <td className="muted">{p.claimsAddress || <span className="faint">&mdash;</span>}</td>
                      <td className="muted nowrap">{p.phone || <span className="faint">&mdash;</span>}</td>
                      <td>
                        <span className={p.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                          {p.active ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      {isAdmin && (
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', gap: 6 }}>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => setEditingPayer(p)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => handleToggleStatus(p)}
                            >
                              {p.active ? 'Deactivate' : 'Reactivate'}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination page={data.page} totalPages={data.totalPages}
                          totalElements={data.totalElements} onChange={setPage} />
            </>
          )}
        </div>
      </div>

      {showAddModal && (
        <PayerModal
          onClose={() => setShowAddModal(false)}
          onSuccess={(saved) => {
            setShowAddModal(false);
            setSuccessMsg(`Payer ${saved.name} created successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadPayers();
          }}
        />
      )}

      {editingPayer && (
        <PayerModal
          payer={editingPayer}
          onClose={() => setEditingPayer(null)}
          onSuccess={(saved) => {
            setEditingPayer(null);
            setSuccessMsg(`Payer ${saved.name} updated successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadPayers();
          }}
        />
      )}

      {showImportModal && (
        <CsvImportModal
          entityType="PAYERS"
          title="Import Payers"
          onClose={() => setShowImportModal(false)}
          onSuccess={() => {
            loadPayers();
          }}
        />
      )}
    </>
  );
}
