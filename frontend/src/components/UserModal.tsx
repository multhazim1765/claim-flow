import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { CreateUserInput, UpdateUserInput, UserAccount, UserRole } from '../api/types';
import { ErrorAlert } from './Common';

interface UserModalProps {
  user?: UserAccount | null;
  onClose: () => void;
  onSuccess: (saved: UserAccount) => void;
}

export function UserModal({ user, onClose, onSuccess }: UserModalProps) {
  const isEdit = !!user;
  const [username, setUsername] = useState(user?.username || '');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [role, setRole] = useState<UserRole>(user?.role || 'BILLER');
  const [enabled, setEnabled] = useState(user ? user.enabled : true);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);

    try {
      if (isEdit) {
        const payload: UpdateUserInput = {
          fullName: fullName.trim(),
          role,
          enabled,
        };
        const saved = await api<UserAccount>(`/users/${user.id}`, { method: 'PUT', body: payload });
        onSuccess(saved);
      } else {
        const payload: CreateUserInput = {
          username: username.trim().toLowerCase(),
          password,
          fullName: fullName.trim(),
          role,
          enabled,
        };
        const saved = await api<UserAccount>('/users', { method: 'POST', body: payload });
        onSuccess(saved);
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Failed to save user account.');
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
        background: '#fff', borderRadius: 8, maxWidth: 500, width: '90%',
        boxShadow: '0 8px 30px rgba(0,0,0,0.2)',
      }}>
        <div className="modal-header" style={{
          padding: '16px 24px', borderBottom: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>{isEdit ? 'Edit User Account' : 'Create User Account'}</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>Role-based user authorization</span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ padding: '20px 24px' }}>
            <ErrorAlert message={error} />

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="usr-name">Username <span style={{ color: '#dc2626' }}>*</span></label>
              <input id="usr-name" className="mono" required value={username} onChange={(e) => setUsername(e.target.value)}
                     placeholder="johndoe" disabled={isEdit} />
              {fieldErrors.username && <span className="field-error">{fieldErrors.username}</span>}
            </div>

            {!isEdit && (
              <div className="field" style={{ marginBottom: 12 }}>
                <label htmlFor="usr-pwd">Initial Password <span style={{ color: '#dc2626' }}>*</span></label>
                <input id="usr-pwd" type="password" required minLength={6} value={password} onChange={(e) => setPassword(e.target.value)} />
                {fieldErrors.password && <span className="field-error">{fieldErrors.password}</span>}
              </div>
            )}

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="usr-full">Full Name <span style={{ color: '#dc2626' }}>*</span></label>
              <input id="usr-full" required value={fullName} onChange={(e) => setFullName(e.target.value)}
                     placeholder="John Doe" />
              {fieldErrors.fullName && <span className="field-error">{fieldErrors.fullName}</span>}
            </div>

            <div className="field" style={{ marginBottom: 16 }}>
              <label htmlFor="usr-role">Assigned Role <span style={{ color: '#dc2626' }}>*</span></label>
              <select id="usr-role" required value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
                <option value="ADMIN">ADMIN (Full Administrative Control)</option>
                <option value="BILLER">BILLER (Claims & Billing Operations)</option>
                <option value="VIEWER">VIEWER (Read-Only Access)</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
                <strong>Account Enabled</strong>
              </label>
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving...' : (isEdit ? 'Update Account' : 'Create User')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface ResetPasswordModalProps {
  user: UserAccount;
  onClose: () => void;
  onSuccess: () => void;
}

export function ResetPasswordModal({ user, onClose, onSuccess }: ResetPasswordModalProps) {
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (newPassword.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    setLoading(true);
    try {
      await api<UserAccount>(`/users/${user.id}/reset-password`, {
        method: 'POST',
        body: { newPassword },
      });
      onSuccess();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to reset password.');
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
        background: '#fff', borderRadius: 8, maxWidth: 450, width: '90%',
        boxShadow: '0 8px 30px rgba(0,0,0,0.2)',
      }}>
        <div className="modal-header" style={{
          padding: '16px 24px', borderBottom: '1px solid #e2e8f0',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>Reset Password</h3>
            <span style={{ fontSize: 13, color: '#64748b' }}>User: <strong>{user.username}</strong></span>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body" style={{ padding: '20px 24px' }}>
            <ErrorAlert message={error} />

            <div className="field" style={{ marginBottom: 12 }}>
              <label htmlFor="rst-pwd">New Password <span style={{ color: '#dc2626' }}>*</span></label>
              <input id="rst-pwd" type="password" required minLength={6} value={newPassword}
                     onChange={(e) => setNewPassword(e.target.value)} />
            </div>

            <div className="field">
              <label htmlFor="rst-conf">Confirm New Password <span style={{ color: '#dc2626' }}>*</span></label>
              <input id="rst-conf" type="password" required minLength={6} value={confirmPassword}
                     onChange={(e) => setConfirmPassword(e.target.value)} />
            </div>
          </div>

          <div className="modal-footer" style={{
            padding: '16px 24px', borderTop: '1px solid #e2e8f0',
            display: 'flex', justifyContent: 'flex-end', gap: 12,
          }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Resetting...' : 'Set New Password'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

