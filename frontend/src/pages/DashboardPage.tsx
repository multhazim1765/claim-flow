import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { AccountsReceivableSummary, ClaimListItem, ClaimSummary, PageResponse } from '../api/types';
import { PageHeader } from '../components/Layout';
import { Card, DateText, ErrorAlert, Loading, Money, StatusBadge } from '../components/Common';

export default function DashboardPage() {
  const [summary, setSummary] = useState<ClaimSummary | null>(null);
  const [arSummary, setArSummary] = useState<AccountsReceivableSummary | null>(null);
  const [recent, setRecent] = useState<ClaimListItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const [s, r, ar] = await Promise.all([
          api<ClaimSummary>('/claims/summary'),
          api<PageResponse<ClaimListItem>>('/claims', { query: { size: 8, sort: 'createdAt,desc' } }),
          api<AccountsReceivableSummary>('/financial/ar-summary').catch(() => null),
        ]);
        if (!alive) return;
        setSummary(s);
        setRecent(r.content);
        setArSummary(ar);
      } catch (err) {
        if (alive) setError(err instanceof ApiError ? err.detail : 'Could not load the dashboard.');
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, []);

  const collectionRate = summary && summary.totalCharged > 0
    ? (summary.totalPaid / summary.totalCharged) * 100
    : 0;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Claims pipeline and receivable position" />
      <div className="content">
        <ErrorAlert message={error} />
        {loading && <Loading />}

        {summary && (
          <>
            <div className="grid grid-4">
              <div className="card stat">
                <div className="stat-label">Total claims</div>
                <div className="stat-value">{summary.totalClaims.toLocaleString()}</div>
                <div className="stat-note">across every status</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Charged</div>
                <div className="stat-value"><Money value={summary.totalCharged} /></div>
                <div className="stat-note">gross billed amount</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Collected</div>
                <div className="stat-value"><Money value={summary.totalPaid} /></div>
                <div className="stat-note">{collectionRate.toFixed(1)}% of charges</div>
              </div>
              <div className="card stat">
                <div className="stat-label">Legacy Gross Outstanding</div>
                <div className="stat-value"><Money value={summary.outstandingReceivable} /></div>
                <div className="stat-note">legacy unsegregated formula</div>
              </div>
            </div>

            {/* High-Integrity Segmented AR Position (Milestone 2.3B2 / 2.3C) */}
            {arSummary && (
              <div className="mt-16">
                <Card
                  title="Segmented Accounts Receivable Reconciliation"
                  actions={
                    <span
                      className="pill"
                      style={{
                        backgroundColor: arSummary.isBalanced ? 'var(--ok-soft)' : 'var(--danger-soft)',
                        color: arSummary.isBalanced ? 'var(--ok)' : 'var(--danger)',
                        fontWeight: 600,
                      }}
                    >
                      {arSummary.isBalanced ? '✓ Reconciled ($0.00 Discrepancy)' : 'Variance Discrepancy'}
                    </span>
                  }
                >
                  <div className="grid grid-4">
                    <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                      <div className="stat-label">Confirmed Collectible AR</div>
                      <div className="stat-value font-semibold" style={{ color: 'var(--ok)' }}>
                        <Money value={arSummary.totalCollectibleAr} />
                      </div>
                      <div className="stat-note">Adjudicated payer + patient obligations</div>
                    </div>
                    <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                      <div className="stat-label">Adjudicated Payer AR</div>
                      <div className="stat-value font-semibold" style={{ color: 'var(--accent)' }}>
                        <Money value={arSummary.adjudicatedPayerAr} />
                      </div>
                      <div className="stat-note">Patient AR: <Money value={arSummary.patientAr} /></div>
                    </div>
                    <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                      <div className="stat-label">Disputed Denial Exposure</div>
                      <div className="stat-value font-semibold" style={{ color: 'var(--warn)' }}>
                        <Money value={arSummary.disputedAr} />
                      </div>
                      <div className="stat-note">Under active appeal / denial tracking</div>
                    </div>
                    <div className="card stat" style={{ background: 'var(--surface-2)' }}>
                      <div className="stat-label">Verified Write-Offs (CO)</div>
                      <div className="stat-value font-semibold">
                        <Money value={arSummary.verifiedContractualWriteOffs} />
                      </div>
                      <div className="stat-note">
                        Unadjudicated: <Money value={arSummary.unadjudicatedSubmittedExposure} /> &middot; Void/Rescinded: <Money value={arSummary.voidClaimExposure} />
                      </div>
                    </div>
                  </div>
                </Card>
              </div>
            )}

            <div className="grid grid-2 mt-16">
              <Card title="Pipeline by status" tight>
                <table>
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th className="num">Claims</th>
                      <th className="num">Charged</th>
                      <th className="num">Paid</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.byStatus.filter((b) => b.count > 0).map((b) => (
                      <tr key={b.status}>
                        <td>
                          <Link to={`/claims?status=${b.status}`}><StatusBadge status={b.status} /></Link>
                          <div className="faint" style={{ fontSize: 11, marginTop: 3 }}>{b.description}</div>
                        </td>
                        <td className="num">{b.count}</td>
                        <td className="num"><Money value={b.totalCharge} muted /></td>
                        <td className="num"><Money value={b.paidAmount} muted /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>

              <Card title="Recent claims" tight
                    actions={<Link className="btn btn-secondary btn-sm" to="/claims">View all</Link>}>
                <table>
                  <thead>
                    <tr>
                      <th>Claim</th>
                      <th>Patient</th>
                      <th>Service</th>
                      <th className="num">Charge</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recent.map((c) => (
                      <tr key={c.id}>
                        <td className="mono"><Link to={`/claims/${c.id}`}>{c.claimNumber}</Link></td>
                        <td>{c.patientName}</td>
                        <td><DateText value={c.serviceDateFrom} /></td>
                        <td className="num"><Money value={c.totalCharge} /></td>
                        <td><StatusBadge status={c.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            </div>

            <div className="mt-16">
              <div className="alert alert-warn">
                <strong>Phase 1 build.</strong> AR aging, denial-rate analytics, EDI 837P export,
                835 remittance posting, eligibility checks, appeals and attachments are specified
                but deliberately not implemented. See the{' '}
                <Link to="/roadmap">Phase 2 roadmap</Link>.
              </div>
            </div>
          </>
        )}
      </div>
    </>
  );
}
