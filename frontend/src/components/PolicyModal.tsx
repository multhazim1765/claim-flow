import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Payer, Policy, PolicyInput, PolicyPriority } from '../api/types';
import { ErrorAlert } from './Common';

interface PolicyModalProps {
  patientId: number;
  payers: Payer[];
  policy?: Policy | null;
  onClose: () => void;
  onSuccess: (saved: Policy) => void;
}

export function PolicyModal({ patientId, payers, policy, onClose, onSuccess }: PolicyModalProps) {
  const isEdit = !!policy;
  const [payerId, setPayerId] = useState<string>(policy ? String(policy.payerId) : (payers[0] ? String(payers[0].id) : ''));
  const [memberId, setMemberId] = useState<string>(policy?.memberId || '');
  const [groupNumber, setGroupNumber] = useState<string>(policy?.groupNumber || '');
  const [priority, setPriority] = useState<PolicyPriority>(policy?.priority || 'PRIMARY');
  const [effectiveDate, setEffectiveDate] = useState<string>(policy?.effectiveDate || new Date().toISOString().slice(0, 10));
  const [terminationDate, setTerminationDate] = useState<string>(policy?.terminationDate || '');
  const [active, setActive] = useState<boolean>(policy ? policy.active : true);

  // Optional demonstration benefit overrides
  const [demoCopay, setDemoCopay] = useState<string>(policy?.demoCopay != null ? String(policy.demoCopay) : '');
  const [demoCoinsurancePercent, setDemoCoinsurancePercent] = useState<string>(policy?.demoCoinsurancePercent != null ? String(policy.demoCoinsurancePercent) : '');
  const [demoDeductibleTotal, setDemoDeductibleTotal] = useState<string>(policy?.demoDeductibleTotal != null ? String(policy.demoDeductibleTotal) : '');
  const [demoDeductibleRemaining, setDemoDeductibleRemaining] = useState<string>(policy?.demoDeductibleRemaining != null ? String(policy.demoDeductibleRemaining) : '');
  const [demoOutOfPocketRemaining, setDemoOutOfPocketRemaining] = useState<string>(policy?.demoOutOfPocketRemaining != null ? String(policy.demoOutOfPocketRemaining) : '');
  const [showDemoSection, setShowDemoSection] = useState<boolean>(
    !!(policy && (policy.demoCopay != null || policy.demoCoinsurancePercent != null || policy.demoDeductibleTotal != null))
  );

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);

    if (terminationDate && terminationDate < effectiveDate) {
      setError('Termination date cannot be before effective date.');
      setLoading(false);
      return;
    }

    const anyDemo = demoCopay !== '' || demoCoinsurancePercent !== '' || demoDeductibleTotal !== '' || demoDeductibleRemaining !== '' || demoOutOfPocketRemaining !== '';
    let parsedCopay: number | null = null;
    let parsedCoins: number | null = null;
    let parsedDedTot: number | null = null;
    let parsedDedRem: number | null = null;
    let parsedOopRem: number | null = null;

    if (anyDemo) {
      if (demoCopay === '' || demoCoinsurancePercent === '' || demoDeductibleTotal === '' || demoDeductibleRemaining === '' || demoOutOfPocketRemaining === '') {
        setError('Complete custom demo profile required: all 5 demonstration benefit fields (copay, coinsurance %, total deductible, remaining deductible, out-of-pocket remaining) must be provided, or all left blank.');
        setLoading(false);
        return;
      }
      parsedCopay = Number(demoCopay);
      parsedCoins = Number(demoCoinsurancePercent);
      parsedDedTot = Number(demoDeductibleTotal);
      parsedDedRem = Number(demoDeductibleRemaining);
      parsedOopRem = Number(demoOutOfPocketRemaining);

      if (isNaN(parsedCopay) || parsedCopay < 0) {
        setError('Demonstration copay must be a non-negative number.');
        setLoading(false);
        return;
      }
      if (isNaN(parsedCoins) || parsedCoins < 0 || parsedCoins > 100) {
        setError('Demonstration coinsurance must be an integer between 0 and 100.');
        setLoading(false);
        return;
      }
      if (isNaN(parsedDedTot) || parsedDedTot < 0) {
        setError('Demonstration total deductible must be a non-negative number.');
        setLoading(false);
        return;
      }
      if (isNaN(parsedDedRem) || parsedDedRem < 0) {
        setError('Demonstration remaining deductible must be a non-negative number.');
        setLoading(false);
        return;
      }
      if (isNaN(parsedOopRem) || parsedOopRem < 0) {
        setError('Demonstration out-of-pocket remaining must be a non-negative number.');
        setLoading(false);
        return;
      }
      if (parsedDedRem > parsedDedTot) {
        setError(`Remaining deductible ($${parsedDedRem.toFixed(2)}) cannot exceed total deductible ($${parsedDedTot.toFixed(2)}).`);
        setLoading(false);
        return;
      }
    }

    const payload: PolicyInput = {
      patientId,
      payerId: Number(payerId),
      memberId: memberId.trim(),
      groupNumber: groupNumber.trim() || null,
      priority,
      effectiveDate,
      terminationDate: terminationDate || null,
      active,
      demoCopay: parsedCopay,
      demoCoinsurancePercent: parsedCoins != null ? Math.round(parsedCoins) : null,
      demoDeductibleTotal: parsedDedTot,
      demoDeductibleRemaining: parsedDedRem,
      demoOutOfPocketRemaining: parsedOopRem,
    };

    try {
      const saved = isEdit
        ? await api<Policy>(`/policies/${policy.id}`, { method: 'PUT', body: payload })
        : await api<Policy>('/policies', { method: 'POST', body: payload });
      onSuccess(saved);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Failed to save insurance policy.');
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
            <h3 style={{ margin: 0, fontSize: 18 }}>{isEdit ? 'Edit Insurance Policy' : 'Add Insurance Coverage'}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Payer policy and member identifiers</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ padding: '20px 24px' }}>
            <ErrorAlert message={error} />

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="pol-payer">Insurance Payer <span style={{ color: '#dc2626' }}>*</span></label>
              <select id="pol-payer" required value={payerId} onChange={(e) => setPayerId(e.target.value)}>
                {payers.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.payerCode}) - {p.planType}
                  </option>
                ))}
              </select>
              {fieldErrors.payerId && <span className="field-error">{fieldErrors.payerId}</span>}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
              <div className="field">
                <label htmlFor="pol-mem">Member ID <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="pol-mem" className="mono" required value={memberId} onChange={(e) => setMemberId(e.target.value)}
                       placeholder="MBR-123456" />
                {fieldErrors.memberId && <span className="field-error">{fieldErrors.memberId}</span>}
              </div>

              <div className="field">
                <label htmlFor="pol-grp">Group Number</label>
                <input id="pol-grp" className="mono" value={groupNumber} onChange={(e) => setGroupNumber(e.target.value)}
                       placeholder="GRP-7788" />
              </div>
            </div>

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="pol-prio">Priority / Coordination of Benefits <span style={{ color: '#dc2626' }}>*</span></label>
              <select id="pol-prio" required value={priority} onChange={(e) => setPriority(e.target.value as PolicyPriority)}>
                <option value="PRIMARY">PRIMARY (1st Payer)</option>
                <option value="SECONDARY">SECONDARY (2nd Payer)</option>
                <option value="TERTIARY">TERTIARY (3rd Payer)</option>
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              <div className="field">
                <label htmlFor="pol-eff">Effective Date <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="pol-eff" type="date" required value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)} />
                {fieldErrors.effectiveDate && <span className="field-error">{fieldErrors.effectiveDate}</span>}
              </div>

              <div className="field">
                <label htmlFor="pol-term">Termination Date</label>
                <input id="pol-term" type="date" value={terminationDate} onChange={(e) => setTerminationDate(e.target.value)} />
                {fieldErrors.terminationDate && <span className="field-error">{fieldErrors.terminationDate}</span>}
              </div>
            </div>

            <div style={{
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: 6,
              padding: '12px 14px',
              marginBottom: 16,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                <div>
                  <strong style={{ fontSize: 13, color: '#334155' }}>
                    Optional Demonstration Benefit Overrides
                  </strong>
                  <div style={{ fontSize: 11, color: '#64748b' }}>
                    Educational testing only. Leave blank to use payer defaults. If specified, all 5 fields are required.
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  style={{ fontSize: 11, padding: '2px 8px' }}
                  onClick={() => setShowDemoSection(!showDemoSection)}
                >
                  {showDemoSection ? 'Hide Overrides' : 'Configure Overrides'}
                </button>
              </div>

              {showDemoSection && (
                <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px dashed #cbd5e1' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
                    <div className="field">
                      <label htmlFor="pol-demo-copay" style={{ fontSize: 12 }}>Demo Copay ($)</label>
                      <input
                        id="pol-demo-copay"
                        type="number"
                        step="0.01"
                        min="0"
                        placeholder="e.g. 25.00"
                        value={demoCopay}
                        onChange={(e) => setDemoCopay(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="pol-demo-coins" style={{ fontSize: 12 }}>Demo Coinsurance (%)</label>
                      <input
                        id="pol-demo-coins"
                        type="number"
                        step="1"
                        min="0"
                        max="100"
                        placeholder="e.g. 20"
                        value={demoCoinsurancePercent}
                        onChange={(e) => setDemoCoinsurancePercent(e.target.value)}
                      />
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
                    <div className="field">
                      <label htmlFor="pol-demo-ded-tot" style={{ fontSize: 12 }}>Total Deductible ($)</label>
                      <input
                        id="pol-demo-ded-tot"
                        type="number"
                        step="0.01"
                        min="0"
                        placeholder="e.g. 500.00"
                        value={demoDeductibleTotal}
                        onChange={(e) => setDemoDeductibleTotal(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="pol-demo-ded-rem" style={{ fontSize: 12 }}>Remaining Deductible ($)</label>
                      <input
                        id="pol-demo-ded-rem"
                        type="number"
                        step="0.01"
                        min="0"
                        placeholder="e.g. 150.00"
                        value={demoDeductibleRemaining}
                        onChange={(e) => setDemoDeductibleRemaining(e.target.value)}
                      />
                    </div>
                  </div>

                  <div className="field" style={{ marginBottom: 4 }}>
                    <label htmlFor="pol-demo-oop-rem" style={{ fontSize: 12 }}>Out-of-Pocket Remaining ($)</label>
                    <input
                      id="pol-demo-oop-rem"
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder="e.g. 2000.00"
                      value={demoOutOfPocketRemaining}
                      onChange={(e) => setDemoOutOfPocketRemaining(e.target.value)}
                    />
                  </div>
                </div>
              )}
            </div>

            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                <strong>Active policy coverage</strong>
              </label>
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : (isEdit ? 'Update Policy' : 'Add Policy')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

