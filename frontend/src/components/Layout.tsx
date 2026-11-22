import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { ReactNode } from 'react';

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const signOut = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">CF</div>
          <div>
            <div className="brand-text">ClaimFlow</div>
            <div className="brand-sub">Healthcare Revenue Cycle Intelligence</div>
          </div>
        </div>

        <nav className="nav">
          <div className="nav-label">Claims</div>
          <NavLink to="/" end>Dashboard</NavLink>
          <NavLink to="/claims">All claims</NavLink>
          {user?.role !== 'VIEWER' && <NavLink to="/claims/new">New claim</NavLink>}

          <div className="nav-label">Revenue Cycle</div>
          <NavLink to="/eligibility">Eligibility</NavLink>
          <NavLink to="/claim-exports">EDI 837P Exports</NavLink>
          <NavLink to="/denials">Denials & Appeals</NavLink>
          <NavLink to="/remittances">Remittances</NavLink>
          <NavLink to="/remittances/exceptions">Remittance Exceptions</NavLink>
          <NavLink to="/analytics">Analytics & AR</NavLink>
          <NavLink to="/documents">Documents</NavLink>

          <div className="nav-label">Reference data</div>
          <NavLink to="/patients">Patients</NavLink>
          <NavLink to="/payers">Payers</NavLink>
          <NavLink to="/providers">Providers</NavLink>
        </nav>

        <div className="sidebar-footer">
          <strong>{user?.fullName}</strong>
          <span>{user?.role}</span>
          <div style={{ marginTop: 10 }}>
            <button className="btn btn-secondary btn-sm" onClick={signOut}>Sign out</button>
          </div>
        </div>
      </aside>

      <div className="main">
        <Outlet />
      </div>
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }:
  { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="topbar">
      <div className="topbar-title">
        <h1>{title}</h1>
        {subtitle && <span className="topbar-sub">{subtitle}</span>}
      </div>
      {actions && <div className="btn-row">{actions}</div>}
    </header>
  );
}
