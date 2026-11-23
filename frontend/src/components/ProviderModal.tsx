import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Provider, ProviderInput } from '../api/types';
import { ErrorAlert } from './Common';

interface ProviderModalProps {
  provider?: Provider | null;
  onClose: () => void;
  onSuccess: (saved: Provider) => void;
}

export function ProviderModal({ provider, onClose, onSuccess }: ProviderModalProps) {
  const isEdit = !!provider;
  const [npi, setNpi] = useState(provider?.npi || '');
  const [firstName, setFirstName] = useState(provider?.firstName || '');
  const [lastName, setLastName] = useState(provider?.lastName || '');
  const [specialty, setSpecialty] = useState(provider?.specialty || '');
  const [taxId, setTaxId] = useState(provider?.taxId || '');
  const [active, setActive] = useState(provider ? provider.active : true);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);

    if (!/^\d{10}$/.test(npi.trim())) {
      setError('NPI must be exactly 10 digits.');
      setLoading(false);
      return;
    }

    const payload: ProviderInput = {
      npi: npi.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      specialty: specialty.trim() || null,
      taxId: taxId.trim() || null,
      active,
    };

    try {
      const saved = isEdit
        ? await api<Provider>(`/providers/${provider.id}`, { method: 'PUT', body: payload })
        : await api<Provider>('/providers', { method: 'POST', body: payload });
      onSuccess(saved);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Failed to save provider.');
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
        background: '#fff', borderRadius: 8, maxWidth: 550, width: '90%',
        boxShadow: '0 8px 30px rgba(0,0,0,0.2)',
      }}>
        <div className="modal-header" style={{
          padding: '16px 24px', borderBottom: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>{isEdit ? 'Edit Provider' : 'Add Healthcare Provider'}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Rendering provider master data</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ padding: '20px 24px' }}>
            <ErrorAlert message={error} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
              <div className="field">
                <label htmlFor="prv-npi">NPI (10 digits) <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="prv-npi" className="mono" required maxLength={10} value={npi}
                       onChange={(e) => setNpi(e.target.value)} placeholder="1234567890" disabled={isEdit} />
                {fieldErrors.npi && <span className="field-error">{fieldErrors.npi}</span>}
              </div>

              <div className="field">
                <label htmlFor="prv-tax">Tax ID / EIN</label>
                <input id="prv-tax" className="mono" value={taxId} onChange={(e) => setTaxId(e.target.value)}
                       placeholder="12-3456789" />
                {fieldErrors.taxId && <span className="field-error">{fieldErrors.taxId}</span>}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
              <div className="field">
                <label htmlFor="prv-first">First Name <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="prv-first" required value={firstName} onChange={(e) => setFirstName(e.target.value)} />
                {fieldErrors.firstName && <span className="field-error">{fieldErrors.firstName}</span>}
              </div>

              <div className="field">
                <label htmlFor="prv-last">Last Name <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="prv-last" required value={lastName} onChange={(e) => setLastName(e.target.value)} />
                {fieldErrors.lastName && <span className="field-error">{fieldErrors.lastName}</span>}
              </div>
            </div>

            <div className="field" style={{ marginBottom: 16 }}>
              <label htmlFor="prv-spec">Specialty</label>
              <input id="prv-spec" value={specialty} onChange={(e) => setSpecialty(e.target.value)}
                     placeholder="e.g. Cardiology, Internal Medicine" />
              {fieldErrors.specialty && <span className="field-error">{fieldErrors.specialty}</span>}
            </div>

            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                <strong>Active rendering provider</strong>
              </label>
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : (isEdit ? 'Update Provider' : 'Create Provider')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

