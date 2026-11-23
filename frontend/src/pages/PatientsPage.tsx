import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { PageResponse, Patient, PatientSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { DateText, EmptyState, ErrorAlert, Loading, Pagination } from '../components/Common';
import { PatientModal } from '../components/PatientModal';
import { CsvImportModal } from '../components/CsvImportModal';

export default function PatientsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canEdit = user?.role === 'ADMIN' || user?.role === 'BILLER';

  const [data, setData] = useState<PageResponse<PatientSummary> | null>(null);
  const [q, setQ] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingPatient, setEditingPatient] = useState<Patient | null>(null);
  const [showImportModal, setShowImportModal] = useState(false);

  const loadPatients = () => {
    setLoading(true);
    const queryParams: Record<string, any> = { q, page, size: 20, sort: 'lastName' };
    if (activeFilter === 'active') queryParams.active = true;
    if (activeFilter === 'inactive') queryParams.active = false;

    api<PageResponse<PatientSummary>>('/patients', { query: queryParams })
      .then((d) => { setData(d); setError(null); })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load patients.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadPatients();
  }, [q, activeFilter, page]);

  const handleEdit = async (e: React.MouseEvent, patientId: number) => {
    e.stopPropagation();
    try {
      const fullPatient = await api<Patient>(`/patients/${patientId}`);
      setEditingPatient(fullPatient);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to fetch patient details.');
    }
  };

  const handleToggleStatus = async (e: React.MouseEvent, p: PatientSummary) => {
    e.stopPropagation();
    const action = p.active ? 'archive' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} patient ${p.displayName} (${p.mrn})?`)) {
      return;
    }

    try {
      await api<Patient>(`/patients/${p.id}/status`, {
        method: 'PATCH',
        query: { active: !p.active },
      });
      setSuccessMsg(`Patient ${p.displayName} ${p.active ? 'archived' : 'reactivated'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
      loadPatients();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} patient.`);
    }
  };

  return (
    <>
      <PageHeader
        title="Patients"
        subtitle="Demographics and master clinical identities"
        actions={
          canEdit && (
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-secondary" onClick={() => setShowImportModal(true)}>
                📥 Import CSV
              </button>
              <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
                + Add Patient
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
              <input id="search" placeholder="Name or MRN (press Enter)"
                     defaultValue={q}
                     onKeyDown={(e) => {
                       if (e.key === 'Enter') { setPage(0); setQ((e.target as HTMLInputElement).value); }
                     }}
                     onBlur={(e) => { setPage(0); setQ(e.target.value); }} />
            </div>

            <div className="field" style={{ minWidth: 160 }}>
              <label htmlFor="filter-status">Status</label>
              <select id="filter-status" value={activeFilter} onChange={(e) => { setPage(0); setActiveFilter(e.target.value); }}>
                <option value="all">All Patients</option>
                <option value="active">Active Only</option>
                <option value="inactive">Archived / Inactive</option>
              </select>
            </div>
          </div>

          {loading && <Loading label="Loading patients" />}
          {!loading && data && data.content.length === 0 && <EmptyState title="No patients found" />}

          {!loading && data && data.content.length > 0 && (
            <>
              <table>
                <thead>
                  <tr>
                    <th>MRN</th>
                    <th>Name</th>
                    <th>Date of birth</th>
                    <th>Status</th>
                    {canEdit && <th style={{ textAlign: 'right' }}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id} className="clickable" onClick={() => navigate(`/patients/${p.id}`)}>
                      <td className="mono">{p.mrn}</td>
                      <td><strong>{p.displayName}</strong></td>
                      <td><DateText value={p.dateOfBirth} /></td>
                      <td>
                        <span className={p.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                          {p.active ? 'ACTIVE' : 'ARCHIVED'}
                        </span>
                      </td>
                      {canEdit && (
                        <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                          <div style={{ display: 'inline-flex', gap: 6 }}>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={(e) => handleEdit(e, p.id)}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={(e) => handleToggleStatus(e, p)}
                            >
                              {p.active ? 'Archive' : 'Reactivate'}
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
        <PatientModal
          onClose={() => setShowAddModal(false)}
          onSuccess={(saved) => {
            setShowAddModal(false);
            setSuccessMsg(`Patient ${saved.displayName} registered successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadPatients();
          }}
        />
      )}

      {editingPatient && (
        <PatientModal
          patient={editingPatient}
          onClose={() => setEditingPatient(null)}
          onSuccess={(saved) => {
            setEditingPatient(null);
            setSuccessMsg(`Patient ${saved.displayName} updated successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadPatients();
          }}
        />
      )}

      {showImportModal && (
        <CsvImportModal
          entityType="PATIENTS"
          title="Import Patients"
          onClose={() => setShowImportModal(false)}
          onSuccess={() => {
            loadPatients();
          }}
        />
      )}
    </>
  );
}
