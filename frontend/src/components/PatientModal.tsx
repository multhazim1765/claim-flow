import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Patient, PatientInput } from '../api/types';
import { ErrorAlert } from './Common';

interface PatientModalProps {
  patient?: Patient | null;
  onClose: () => void;
  onSuccess: (saved: Patient) => void;
}

export function PatientModal({ patient, onClose, onSuccess }: PatientModalProps) {
  const isEdit = !!patient;
  const [mrn, setMrn] = useState(patient?.mrn || '');
  const [firstName, setFirstName] = useState(patient?.firstName || '');
  const [lastName, setLastName] = useState(patient?.lastName || '');
  const [dateOfBirth, setDateOfBirth] = useState(patient?.dateOfBirth || '');
  const [phone, setPhone] = useState(patient?.phone || '');
  const [email, setEmail] = useState(patient?.email || '');
  const [addressLine1, setAddressLine1] = useState(patient?.addressLine1 || '');
  const [city, setCity] = useState(patient?.city || '');
  const [state, setState] = useState(patient?.state || '');
  const [postalCode, setPostalCode] = useState(patient?.postalCode || '');
  const [active, setActive] = useState(patient ? patient.active : true);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);

    const payload: PatientInput = {
      mrn: mrn.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      dateOfBirth,
      phone: phone.trim() || null,
      email: email.trim() || null,
      addressLine1: addressLine1.trim() || null,
      city: city.trim() || null,
      state: state.trim().toUpperCase() || null,
      postalCode: postalCode.trim() || null,
      active,
    };

    try {
      const saved = isEdit
        ? await api<Patient>(`/patients/${patient.id}`, { method: 'PUT', body: payload })
        : await api<Patient>('/patients', { method: 'POST', body: payload });
      onSuccess(saved);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Failed to save patient.');
      }
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
        background: '#fff', borderRadius: 8, maxWidth: 650, width: '90%',
        maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 8px 30px rgba(0,0,0,0.2)',
      }}>
        <div className="modal-header" style={{
          padding: '16px 24px', borderBottom: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>{isEdit ? 'Edit Patient' : 'Register New Patient'}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Demographics and clinical identity</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, overflowY: 'hidden' }}>
          <div className="modal-body" style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
            <ErrorAlert message={error} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div className="field">
                <label htmlFor="p-mrn">MRN <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="p-mrn" className="mono" required value={mrn} onChange={(e) => setMrn(e.target.value)}
                       placeholder="MRN-000001" disabled={isEdit} />
                {fieldErrors.mrn && <span className="field-error">{fieldErrors.mrn}</span>}
              </div>

              <div className="field">
                <label htmlFor="p-dob">Date of Birth <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="p-dob" type="date" required value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} />
                {fieldErrors.dateOfBirth && <span className="field-error">{fieldErrors.dateOfBirth}</span>}
              </div>

              <div className="field">
                <label htmlFor="p-first">First Name <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="p-first" required value={firstName} onChange={(e) => setFirstName(e.target.value)} />
                {fieldErrors.firstName && <span className="field-error">{fieldErrors.firstName}</span>}
              </div>

              <div className="field">
                <label htmlFor="p-last">Last Name <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="p-last" required value={lastName} onChange={(e) => setLastName(e.target.value)} />
                {fieldErrors.lastName && <span className="field-error">{fieldErrors.lastName}</span>}
              </div>

              <div className="field">
                <label htmlFor="p-phone">Phone</label>
                <input id="p-phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="617-555-0100" />
                {fieldErrors.phone && <span className="field-error">{fieldErrors.phone}</span>}
              </div>

              <div className="field">
                <label htmlFor="p-email">Email</label>
                <input id="p-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="patient@example.com" />
                {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
              </div>

              <div className="field" style={{ gridColumn: 'span 2' }}>
                <label htmlFor="p-addr">Address Line</label>
                <input id="p-addr" value={addressLine1} onChange={(e) => setAddressLine1(e.target.value)} placeholder="123 Main St" />
              </div>

              <div className="field">
                <label htmlFor="p-city">City</label>
                <input id="p-city" value={city} onChange={(e) => setCity(e.target.value)} placeholder="Boston" />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div className="field">
                  <label htmlFor="p-state">State</label>
                  <input id="p-state" maxLength={2} value={state} onChange={(e) => setState(e.target.value.toUpperCase())} placeholder="MA" />
                  {fieldErrors.state && <span className="field-error">{fieldErrors.state}</span>}
                </div>
                <div className="field">
                  <label htmlFor="p-zip">ZIP Code</label>
                  <input id="p-zip" maxLength={10} value={postalCode} onChange={(e) => setPostalCode(e.target.value)} placeholder="02115" />
                  {fieldErrors.postalCode && <span className="field-error">{fieldErrors.postalCode}</span>}
                </div>
              </div>
            </div>

            <div style={{ marginTop: 16 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                <strong>Active patient record</strong> (uncheck to soft-archive)
              </label>
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : (isEdit ? 'Update Patient' : 'Create Patient')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

