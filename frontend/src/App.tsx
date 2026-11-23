import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ClaimsListPage from './pages/ClaimsListPage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import ClaimFormPage from './pages/ClaimFormPage';
import PatientsPage from './pages/PatientsPage';
import PatientDetailPage from './pages/PatientDetailPage';
import PayersPage from './pages/PayersPage';
import ProvidersPage from './pages/ProvidersPage';
import EligibilityPage from './pages/EligibilityPage';
import DenialsPage from './pages/DenialsPage';
import RemittancePage from './pages/RemittancePage';
import RemittanceDetailPage from './pages/RemittanceDetailPage';
import RemittanceExceptionsPage from './pages/RemittanceExceptionsPage';
import AnalyticsPage from './pages/AnalyticsPage';
import ClaimExportPage from './pages/ClaimExportPage';
import DocumentsPage from './pages/DocumentsPage';
import UsersPage from './pages/UsersPage';
import type { ReactElement } from 'react';

function RequireAuth({ children }: { children: ReactElement }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

function RequireRole({ roles, children }: { roles: string[]; children: ReactElement }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}

function Router() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/claims" element={<ClaimsListPage />} />
        <Route path="/claims/new" element={
          <RequireRole roles={['ADMIN', 'BILLER']}>
            <ClaimFormPage mode="create" />
          </RequireRole>
        } />
        <Route path="/claims/:id" element={<ClaimDetailPage />} />
        <Route path="/claims/:id/edit" element={
          <RequireRole roles={['ADMIN', 'BILLER']}>
            <ClaimFormPage mode="edit" />
          </RequireRole>
        } />
        <Route path="/patients" element={<PatientsPage />} />
        <Route path="/patients/:id" element={<PatientDetailPage />} />
        <Route path="/payers" element={<PayersPage />} />
        <Route path="/providers" element={<ProvidersPage />} />
        <Route path="/eligibility" element={<EligibilityPage />} />
        <Route path="/denials" element={<DenialsPage />} />
        <Route path="/remittances" element={<RemittancePage />} />
        <Route path="/remittances/exceptions" element={<RemittanceExceptionsPage />} />
        <Route path="/remittances/:id" element={<RemittanceDetailPage />} />
        <Route path="/claim-exports" element={<ClaimExportPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/users" element={
          <RequireRole roles={['ADMIN']}>
            <UsersPage />
          </RequireRole>
        } />
        {/* Safe redirection of legacy /roadmap route */}
        <Route path="/roadmap" element={<Navigate to="/" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Router />
      </HashRouter>
    </AuthProvider>
  );
}
