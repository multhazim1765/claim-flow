import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { ClaimListItem, PageResponse, Patient, Payer, Policy } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import {
  Card, DateText, EmptyState, ErrorAlert, Loading, Money, StatusBadge,
} from '../components/Common';
import { PatientModal } from '../components/PatientModal';
import { PolicyModal } from '../components/PolicyModal';

export default function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canEdit = user?.role === 'ADMIN' || user?.role === 'BILLER';

  const [patient, setPatient] = useState<Patient | null>(null);
  const [claims, setClaims] = useState<ClaimListItem[]>([]);
  const [payers, setPayers] = useState<Payer[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showEditPatientModal, setShowEditPatientModal] = useState(false);
  const [showAddPolicyModal, setShowAddPolicyModal] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<Policy | null>(null);

  const loadData = () => {
    if (!id) return;
    Promise.all([
      api<Patient>(`/patients/${id}`),
      api<PageResponse<ClaimListItem>>('/claims', { query: { patientId: id, size: 50 } }),
      api<Payer[]>('/payers'),
    ])
      .then(([p, c, pay]) => {
        setPatient(p);
        setClaims(c.content);
        setPayers(pay);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load the patient.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, [id]);

  const handleTogglePatientStatus = async () => {
    if (!patient) return;
    const action = patient.active ? 'archive' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} patient ${patient.displayName} (${patient.mrn})?`)) {
      return;
    }
    try {
      const updated = await api<Patient>(`/patients/${patient.id}/status`, {
        method: 'PATCH',
        query: { active: !patient.active },
      });
      setPatient(updated);
      setSuccessMsg(`Patient ${updated.displayName} ${updated.active ? 'reactivated' : 'archived'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} patient.`);
    }
  };

  const handleDeactivatePolicy = async (pol: Policy) => {
    const action = pol.active ? 'deactivate' : 'reactivate';
    if (!window.confirm(`Are you sure you want to ${action} coverage for ${pol.payerName} (${pol.memberId})?`)) {
      return;
    }
    try {
      await api<Policy>(`/policies/${pol.id}/status`, {
        method: 'PATCH',
        query: { active: !pol.active },
      });
      setSuccessMsg(`Policy ${pol.memberId} ${pol.active ? 'deactivated' : 'reactivated'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
      loadData();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} policy.`);
    }
  };

  if (loading) return <><PageHeader title="Patient" /><div className="content"><Loading /></div></>;
  if (!patient) return <><PageHeader title="Patient" /><div className="content"><ErrorAlert message={error} /></div></>;

  return (
    <>
      <PageHeader
        title={patient.displayName}
        subtitle={`MRN ${patient.mrn}`}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Link className="btn btn-secondary" to="/patients">Back to patients</Link>
            {canEdit && (
              <>
                <button className="btn btn-secondary" onClick={() => setShowEditPatientModal(true)}>
                  Edit Demographics
                </button>
                <button className="btn btn-secondary" onClick={handleTogglePatientStatus}>
                  {patient.active ? 'Archive Patient' : 'Reactivate Patient'}
                </button>
              </>
            )}
          </div>
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

        <div className="grid grid-2">
          <Card title="Demographics">
            <dl className="kv">
              <dt>MRN</dt><dd className="mono">{patient.mrn}</dd>
              <dt>Status</dt>
              <dd>
                <span className={patient.active ? 'badge badge-PAID' : 'badge badge-VOID'}>
                  {patient.active ? 'ACTIVE' : 'ARCHIVED'}
                </span>
              </dd>
              <dt>Date of birth</dt><dd><DateText value={patient.dateOfBirth} /></dd>
              <dt>Phone</dt><dd>{patient.phone ?? <span className="faint">&mdash;</span>}</dd>
              <dt>Email</dt><dd>{patient.email ?? <span className="faint">&mdash;</span>}</dd>
              <dt>Address</dt>
              <dd>
                {patient.addressLine1 ? (
                  <>
                    {patient.addressLine1}<br />
                    {patient.city}, {patient.state} {patient.postalCode}
                  </>
                ) : <span className="faint">&mdash;</span>}
              </dd>
            </dl>
          </Card>

          <Card
            title="Insurance Coverage"
            actions={
              canEdit && (
                <button className="btn btn-primary btn-sm" onClick={() => setShowAddPolicyModal(true)}>
                  + Add Policy
                </button>
              )
            }
            tight
          >
            {patient.policies.length === 0
              ? <EmptyState title="No coverage on file" hint="Add an insurance policy to bill claims." />
              : (
                <table>
                  <thead>
                    <tr>
                      <th>Priority</th>
                      <th>Payer</th>
                      <th>Member ID</th>
                      <th>Effective</th>
                      <th>Status</th>
                      <th style={{ textAlign: 'right' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {patient.policies.map((p) => (
                      <tr key={p.id}>
                        <td><span className="pill">{p.priority}</span></td>
                        <td><strong>{p.payerName}</strong></td>
                        <td className="mono">
                          {p.memberId}
                          {p.hasCustomDemoProfile && (
                            <div>
                              <span className="pill pill-phase2" style={{ fontSize: 10, marginTop: 2 }}>
                                Custom Demo Benefits
                              </span>
                            </div>
                          )}
                        </td>
                        <td>
                          <DateText value={p.effectiveDate} />
                          {p.terminationDate && (
                            <span className="muted" style={{ fontSize: 11, display: 'block' }}>
                              Thru <DateText value={p.terminationDate} />
                            </span>
                          )}
                        </td>
                        <td>
                          <span className={p.active && p.activeToday ? 'badge badge-PAID' : 'badge badge-VOID'}>
                            {!p.active ? 'INACTIVE' : (p.activeToday ? 'ACTIVE' : 'TERMINATED')}
                          </span>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', gap: 6 }}>
                            <Link className="btn btn-secondary btn-sm" to={`/eligibility?patientId=${patient.id}&payerId=${p.payerId}&policyId=${p.id}`}>
                              Verify
                            </Link>
                            {canEdit && (
                              <>
                                <button className="btn btn-secondary btn-sm" onClick={() => setEditingPolicy(p)}>
                                  Edit
                                </button>
                                <button className="btn btn-secondary btn-sm" onClick={() => handleDeactivatePolicy(p)}>
                                  {p.active ? 'Deactivate' : 'Reactivate'}
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
          </Card>
        </div>

        <div className="mt-16">
          <Card title={`Claims History (${claims.length})`} tight>
            {claims.length === 0
              ? <EmptyState title="No claims for this patient yet" />
              : (
                <table>
                  <thead>
                    <tr>
                      <th>Claim number</th>
                      <th>Payer</th>
                      <th>Service dates</th>
                      <th className="num">Charged</th>
                      <th className="num">Paid</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {claims.map((c) => (
                      <tr key={c.id}>
                        <td className="mono"><Link to={`/claims/${c.id}`}>{c.claimNumber}</Link></td>
                        <td>{c.payerName}</td>
                        <td><DateText value={c.serviceDateFrom} /></td>
                        <td className="num"><Money value={c.totalCharge} /></td>
                        <td className="num"><Money value={c.paidAmount} muted /></td>
                        <td><StatusBadge status={c.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
          </Card>
        </div>
      </div>

      {showEditPatientModal && (
        <PatientModal
          patient={patient}
          onClose={() => setShowEditPatientModal(false)}
          onSuccess={(saved) => {
            setShowEditPatientModal(false);
            setPatient(saved);
            setSuccessMsg(`Patient demographics updated successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
          }}
        />
      )}

      {showAddPolicyModal && (
        <PolicyModal
          patientId={patient.id}
          payers={payers}
          onClose={() => setShowAddPolicyModal(false)}
          onSuccess={() => {
            setShowAddPolicyModal(false);
            setSuccessMsg(`New insurance policy added.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadData();
          }}
        />
      )}

      {editingPolicy && (
        <PolicyModal
          patientId={patient.id}
          payers={payers}
          policy={editingPolicy}
          onClose={() => setEditingPolicy(null)}
          onSuccess={() => {
            setEditingPolicy(null);
            setSuccessMsg(`Insurance policy updated.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadData();
          }}
        />
      )}
    </>
  );
}
