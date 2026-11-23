import { useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { UserAccount } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { DateText, EmptyState, ErrorAlert, Loading } from '../components/Common';
import { ResetPasswordModal, UserModal } from '../components/UserModal';

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Modals
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingUser, setEditingUser] = useState<UserAccount | null>(null);
  const [resetUser, setResetUser] = useState<UserAccount | null>(null);

  const loadUsers = () => {
    setLoading(true);
    api<UserAccount[]>('/users')
      .then((data) => {
        setUsers(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.detail : 'Could not load users.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleToggleStatus = async (u: UserAccount) => {
    const action = u.enabled ? 'deactivate' : 'activate';
    if (!window.confirm(`Are you sure you want to ${action} account for ${u.username}?`)) {
      return;
    }

    try {
      await api<UserAccount>(`/users/${u.id}/status`, {
        method: 'PATCH',
        query: { enabled: !u.enabled },
      });
      setSuccessMsg(`User ${u.username} ${u.enabled ? 'deactivated' : 'activated'} successfully.`);
      setTimeout(() => setSuccessMsg(null), 4000);
      loadUsers();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : `Failed to ${action} user.`);
    }
  };

  return (
    <>
      <PageHeader
        title="User Management"
        subtitle="System accounts and role-based access control"
        actions={
          <button className="btn btn-primary" onClick={() => setShowAddModal(true)}>
            + Add User
          </button>
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
          {loading && <Loading label="Loading users" />}
          {!loading && users.length === 0 && <EmptyState title="No users found" />}

          {!loading && users.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Full Name</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className="mono">
                      <strong>{u.username}</strong>
                      {currentUser?.username === u.username && (
                        <span className="pill" style={{ marginLeft: 8, fontSize: 10 }}>You</span>
                      )}
                    </td>
                    <td>{u.fullName}</td>
                    <td>
                      <span className={`badge ${u.role === 'ADMIN' ? 'badge-PAID' : (u.role === 'BILLER' ? 'badge-SUBMITTED' : 'badge-VOID')}`}>
                        {u.role}
                      </span>
                    </td>
                    <td>
                      <span className={u.enabled ? 'badge badge-PAID' : 'badge badge-VOID'}>
                        {u.enabled ? 'ENABLED' : 'DISABLED'}
                      </span>
                    </td>
                    <td><DateText value={u.createdAt} /></td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: 6 }}>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => setEditingUser(u)}
                        >
                          Edit
                        </button>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => setResetUser(u)}
                        >
                          Reset Pwd
                        </button>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => handleToggleStatus(u)}
                          disabled={currentUser?.username === u.username && u.enabled}
                        >
                          {u.enabled ? 'Disable' : 'Enable'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {showAddModal && (
        <UserModal
          onClose={() => setShowAddModal(false)}
          onSuccess={(saved) => {
            setShowAddModal(false);
            setSuccessMsg(`User ${saved.username} created successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadUsers();
          }}
        />
      )}

      {editingUser && (
        <UserModal
          user={editingUser}
          onClose={() => setEditingUser(null)}
          onSuccess={(saved) => {
            setEditingUser(null);
            setSuccessMsg(`User ${saved.username} updated successfully.`);
            setTimeout(() => setSuccessMsg(null), 4000);
            loadUsers();
          }}
        />
      )}

      {resetUser && (
        <ResetPasswordModal
          user={resetUser}
          onClose={() => setResetUser(null)}
          onSuccess={() => {
            setResetUser(null);
            setSuccessMsg(`Password reset for ${resetUser.username}.`);
            setTimeout(() => setSuccessMsg(null), 4000);
          }}
        />
      )}
    </>
  );
}

