import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Payer, PayerInput, PlanType } from '../api/types';
import { ErrorAlert } from './Common';

interface PayerModalProps {
  payer?: Payer | null;
  onClose: () => void;
  onSuccess: (saved: Payer) => void;
}

export function PayerModal({ payer, onClose, onSuccess }: PayerModalProps) {
  const isEdit = !!payer;
  const [payerCode, setPayerCode] = useState(payer?.payerCode || '');
  const [name, setName] = useState(payer?.name || '');
  const [planType, setPlanType] = useState<PlanType>(payer?.planType || 'COMMERCIAL');
  const [claimsAddress, setClaimsAddress] = useState(payer?.claimsAddress || '');
  const [phone, setPhone] = useState(payer?.phone || '');
  const [active, setActive] = useState(payer ? payer.active : true);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);

    const payload: PayerInput = {
      payerCode: payerCode.trim().toUpperCase(),
      name: name.trim(),
      planType,
      claimsAddress: claimsAddress.trim() || null,
      phone: phone.trim() || null,
      active,
    };

    try {
      const saved = isEdit
        ? await api<Payer>(`/payers/${payer.id}`, { method: 'PUT', body: payload })
        : await api<Payer>('/payers', { method: 'POST', body: payload });
      onSuccess(saved);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Failed to save payer.');
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
            <h3 style={{ margin: 0, fontSize: 18 }}>{isEdit ? 'Edit Payer' : 'Add Insurance Payer'}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Master payer configuration</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ padding: '20px 24px' }}>
            <ErrorAlert message={error} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
              <div className="field">
                <label htmlFor="pyr-code">Payer Code <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="pyr-code" className="mono" required value={payerCode} onChange={(e) => setPayerCode(e.target.value)}
                       placeholder="PAY-001" disabled={isEdit} />
                {fieldErrors.payerCode && <span className="field-error">{fieldErrors.payerCode}</span>}
              </div>

              <div className="field">
                <label htmlFor="pyr-plan">Plan Type <span style={{ color: '#dc2626' }}>*</span></label>
                <select id="pyr-plan" required value={planType} onChange={(e) => setPlanType(e.target.value as PlanType)}>
                  <option value="COMMERCIAL">COMMERCIAL</option>
                  <option value="MEDICARE">MEDICARE</option>
                  <option value="MEDICAID">MEDICAID</option>
                  <option value="TRICARE">TRICARE</option>
                  <option value="WORKERS_COMP">WORKERS COMP</option>
                  <option value="SELF_PAY">SELF PAY</option>
                </select>
              </div>
            </div>

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="pyr-name">Payer Name <span style={{ color: '#dc2626' }}>*</span></label>
              <input id="pyr-name" required value={name} onChange={(e) => setName(e.target.value)}
                     placeholder="e.g. Blue Cross Blue Shield" />
              {fieldErrors.name && <span className="field-error">{fieldErrors.name}</span>}
            </div>

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="pyr-addr">Claims Billing Address</label>
              <input id="pyr-addr" value={claimsAddress} onChange={(e) => setClaimsAddress(e.target.value)}
                     placeholder="PO Box 1234, City, ST 00000" />
            </div>

            <div className="field" style={{ marginBottom: 16 }}>
              <label htmlFor="pyr-phone">Claims Phone</label>
              <input id="pyr-phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="800-555-0199" />
            </div>

            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                <strong>Active payer</strong>
              </label>
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : (isEdit ? 'Update Payer' : 'Create Payer')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

