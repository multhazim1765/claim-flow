import { useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { PageResponse, Provider } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { EmptyState, ErrorAlert, Loading, Pagination } from '../components/Common';
import { ProviderModal } from '../components/ProviderModal';
import { CsvImportModal } from '../components/CsvImportModal';

export default function ProvidersPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [data, setData] = useState<PageResponse<Provider> | null>(null);
  const [q, setQ] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingProvider, setEditingProvider] = useState<Provider | null>(null);
  const [showImportModal, setShowImportModal] = useState(false);

  const loadProviders = () => {
    setLoading(true);
    const queryParams: Record<string, any> = { q, page, size: 20, sort: 'lastName' };
    if (activeFilter === 'active') queryParams.active = true;
    if (activeFilter === 'inactive') queryParams.active = false;

    api<PageResponse<Provider>>('/providers/search', { query: queryParams })
      .then((d) => { setData(d); setError(null); })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load providers.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadProviders();
  }, [q, activeFilter, page]);

  const handleToggleStatus = async (p: Provider) => {
    const action = p.active ? 'deactivate' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} provider ${p.displayName} (NPI ${p.npi})?`)) {
      return;
    }

    try {
      await api<Provider>(`/providers/${p.id}/status`, {
        method: 'PATCH',
        query: { active: !p.active },
      });
      setSuccessMsg(`Provider ${p.displayName} ${p.active ? 'deactivated' : 'reactivated'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
      loadProviders();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} provider.`);
    }
  };

  return (
    <>
      <PageHeader
        title="Providers"
        subtitle="Rendering providers identified by 10-digit NPI"
        actions={
          isAdmin && (
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-secondary" onClick={() => setShowImportModal(true)}>
                📥 Import CSV
              </button>
              <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
                + Add Provider
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
              <input id="search" placeholder="Provider name, NPI, or specialty (press Enter)"
                     defaultValue={q}
                     onKeyDown={(e) => {
                       if (e.key === 'Enter') { setPage(0); setQ((e.target as HTMLInputElement).value); }
                     }}
                     onBlur={(e) => { setPage(0); setQ(e.target.value); }} />
            </div>

            <div className="field" style={{ minWidth: 160 }}>
              <label htmlFor="filter-status">Status</label>
              <select id="filter-status" value={activeFilter} onChange={(e) => { setPage(0); setActiveFilter(e.target.value); }}>
                <option value="all">All Providers</option>
                <option value="active">Active Only</option>
                <option value="inactive">Inactive Only</option>
              </select>
            </div>
          </div>

          {loading && <Loading label="Loading providers" />}
          {!loading && data && data.content.length === 0 && <EmptyState title="No providers found" />}

          {!loading && data && data.content.length > 0 && (
            <>
              <table>
                <thead>
                  <tr>
                    <th>NPI</th>
                    <th>Name</th>
                    <th>Specialty</th>
                    <th>Tax ID</th>
                    <th>Status</th>
                    {isAdmin && <th style={{ textAlign: 'right' }}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id}>
                      <td className="mono">{p.npi}</td>
                      <td><strong>{p.displayName}</strong></td>
                      <td className="muted">{p.specialty || <span className="faint">&mdash;</span>}</td>
                      <td className="mono muted">{p.taxId || <span className="faint">&mdash;</span>}</td>
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
                              onClick={() => setEditingProvider(p)}
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
        <ProviderModal
          onClose={() => setShowAddModal(false)}
          onSuccess={(saved) => {
            setShowAddModal(false);
            setSuccessMsg(`Provider ${saved.displayName} registered successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadProviders();
          }}
        />
      )}

      {editingProvider && (
        <ProviderModal
          provider={editingProvider}
          onClose={() => setEditingProvider(null)}
          onSuccess={(saved) => {
            setEditingProvider(null);
            setSuccessMsg(`Provider ${saved.displayName} updated successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadProviders();
          }}
        />
      )}

      {showImportModal && (
        <CsvImportModal
          entityType="PROVIDERS"
          title="Import Providers"
          onClose={() => setShowImportModal(false)}
          onSuccess={() => {
            loadProviders();
          }}
        />
      )}
    </>
  );
}
