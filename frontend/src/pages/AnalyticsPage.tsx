import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, downloadCsv } from '../api/client';
import type {
  AnalyticsOverviewResponse,
  ArAgingResponse,
  PayerPerformanceItem,
  MonthlyPaymentTrendItem,
  PaymentTrendsResponse,
  DenialTrendsResponse,
  DenialReasonItem,
  AppealPerformanceResponse,
  PriorityClaimItem,
} from '../api/types';
import { PageHeader } from '../components/Layout';
import { Card, EmptyState, ErrorAlert, Loading, Money, StatusBadge } from '../components/Common';

export default function AnalyticsPage() {
  const [overview, setOverview] = useState<AnalyticsOverviewResponse | null>(null);
  const [aging, setAging] = useState<ArAgingResponse | null>(null);
  const [payers, setPayers] = useState<PayerPerformanceItem[]>([]);
  const [paymentTrends, setPaymentTrends] = useState<PaymentTrendsResponse | null>(null);
  const [denialTrends, setDenialTrends] = useState<DenialTrendsResponse | null>(null);
  const [denialReasons, setDenialReasons] = useState<DenialReasonItem[]>([]);
  const [appealPerf, setAppealPerf] = useState<AppealPerformanceResponse | null>(null);
  const [priorities, setPriorities] = useState<PriorityClaimItem[]>([]);

  const [asOfDate, setAsOfDate] = useState<string>('2026-11-20');
  const [activeTab, setActiveTab] = useState<'aging' | 'payers' | 'trends' | 'denials' | 'priorities'>('aging');
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = async (dateParam?: string) => {
    setLoading(true);
    setError(null);
    try {
      const q = dateParam ? { asOfDate: dateParam } : { asOfDate };
      const [ov, ag, py, pt, dt, dr, ap, pr] = await Promise.all([
        api<AnalyticsOverviewResponse>('/analytics/overview', { query: q }),
        api<ArAgingResponse>('/analytics/ar-aging', { query: q }),
        api<PayerPerformanceItem[]>('/analytics/by-payer'),
        api<PaymentTrendsResponse>('/analytics/payment-trends'),
        api<DenialTrendsResponse>('/analytics/denial-trends'),
        api<DenialReasonItem[]>('/analytics/denial-reasons'),
        api<AppealPerformanceResponse>('/analytics/appeals'),
        api<PriorityClaimItem[]>('/analytics/priority-claims'),
      ]);
      setOverview(ov);
      setAging(ag);
      setPayers(py);
      setPaymentTrends(pt);
      setDenialTrends(dt);
      setDenialReasons(dr);
      setAppealPerf(ap);
      setPriorities(pr);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Failed to load revenue cycle analytics.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleExport = async (type: string) => {
    setExporting(type);
    try {
      await downloadCsv('/analytics/export', { type, asOfDate }, `claimflow-${type}-${asOfDate}.csv`);
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Export failed.');
    } finally {
      setExporting(null);
    }
  };

  const handleDateChange = (newDate: string) => {
    setAsOfDate(newDate);
    loadData(newDate);
  };

  const fin = overview?.financial;
  const op = overview?.operational;
  const perf = overview?.performance;

  return (
    <>
      <PageHeader
        title="Revenue Cycle Intelligence"
        subtitle="Segmented AR aging, payer performance, denial trends, and actionable worklists"
        actions={
          <div className="btn-row" style={{ alignItems: 'center' }}>
            <label style={{ fontSize: '13px', fontWeight: 600, color: 'var(--muted)' }}>
              As of Date:
            </label>
            <input
              type="date"
              className="input input-sm"
              value={asOfDate}
              onChange={(e) => handleDateChange(e.target.value)}
              style={{ width: '150px' }}
            />
            <button className="btn btn-secondary btn-sm" onClick={() => loadData()}>
              Refresh
            </button>
            <div className="dropdown" style={{ display: 'inline-block' }}>
              <button
                className="btn btn-primary btn-sm"
                disabled={exporting !== null}
                onClick={() => {
                  const select = document.getElementById('export-menu');
                  if (select) select.style.display = select.style.display === 'block' ? 'none' : 'block';
                }}
              >
                {exporting ? `Exporting ${exporting}...` : 'Export CSV ▾'}
              </button>
              <div
                id="export-menu"
                style={{
                  display: 'none',
                  position: 'absolute',
                  right: 0,
                  top: '100%',
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: '6px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                  zIndex: 20,
                  minWidth: '180px',
                  padding: '4px 0',
                }}
                onMouseLeave={(e) => (e.currentTarget.style.display = 'none')}
              >
                <button
                  className="btn btn-ghost"
                  style={{ width: '100%', textAlign: 'left', padding: '6px 14px', fontSize: '13px' }}
                  onClick={() => {
                    handleExport('ar-aging');
                    const menu = document.getElementById('export-menu');
                    if (menu) menu.style.display = 'none';
                  }}
                >
                  AR Aging Distribution
                </button>
                <button
                  className="btn btn-ghost"
                  style={{ width: '100%', textAlign: 'left', padding: '6px 14px', fontSize: '13px' }}
                  onClick={() => {
                    handleExport('by-payer');
                    const menu = document.getElementById('export-menu');
                    if (menu) menu.style.display = 'none';
                  }}
                >
                  Payer Performance
                </button>
                <button
                  className="btn btn-ghost"
                  style={{ width: '100%', textAlign: 'left', padding: '6px 14px', fontSize: '13px' }}
                  onClick={() => {
                    handleExport('denials');
                    const menu = document.getElementById('export-menu');
                    if (menu) menu.style.display = 'none';
                  }}
                >
                  Denial Analysis
                </button>
                <button
                  className="btn btn-ghost"
                  style={{ width: '100%', textAlign: 'left', padding: '6px 14px', fontSize: '13px' }}
                  onClick={() => {
                    handleExport('appeals');
                    const menu = document.getElementById('export-menu');
                    if (menu) menu.style.display = 'none';
                  }}
                >
                  Appeal Outcomes
                </button>
                <button
                  className="btn btn-ghost"
                  style={{ width: '100%', textAlign: 'left', padding: '6px 14px', fontSize: '13px' }}
                  onClick={() => {
                    handleExport('priority-claims');
                    const menu = document.getElementById('export-menu');
                    if (menu) menu.style.display = 'none';
                  }}
                >
                  Priority Worklist
                </button>
              </div>
            </div>
          </div>
        }
      />

      <div className="content">
        <ErrorAlert message={error} />
        {loading && <Loading label="Calculating revenue cycle analytics" />}

        {overview && fin && perf && (
          <>
            {/* Reconciled Header Banner */}
            <div
              className="card"
              style={{
                marginBottom: '18px',
                borderLeft: fin.isBalanced ? '4px solid var(--ok)' : '4px solid var(--danger)',
                background: fin.isBalanced ? 'rgba(16, 185, 129, 0.04)' : 'rgba(239, 68, 68, 0.04)',
                padding: '12px 18px',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <strong style={{ color: fin.isBalanced ? 'var(--ok)' : 'var(--danger)' }}>
                    {fin.isBalanced ? '✓ Reconciled AR Position' : '⚠ Reconciliation Discrepancy'}
                  </strong>
                  <span style={{ marginLeft: '12px', fontSize: '13px', color: 'var(--muted)' }}>
                    Gross Charges ($<Money value={fin.grossBilledCharges} />) = Net Payments ($
                    <Money value={fin.netRecordedPayments} />) + Verified Write-Offs ($
                    <Money value={fin.verifiedContractualWriteOffs} />) + Total Outstanding Exposure ($
                    <Money value={fin.totalOutstandingExposure} />) [Includes Collectible AR $<Money value={fin.totalCollectibleAr} />, Disputed $<Money value={fin.disputedAr} />, Void/Rescinded $<Money value={fin.voidClaimExposure} />]
                  </span>
                </div>
                <div style={{ fontSize: '13px', fontWeight: 600 }}>
                  Audit Discrepancy: <span style={{ color: fin.isBalanced ? 'var(--ok)' : 'var(--danger)' }}>$<Money value={fin.reconciliationDiscrepancy} /></span>
                </div>
              </div>
            </div>

            {/* Financial Overview Cards */}
            <div className="grid grid-4" style={{ marginBottom: '18px' }}>
              <div className="card stat" style={{ borderTop: '3px solid var(--accent)' }}>
                <div className="stat-label">Total Collectible AR</div>
                <div className="stat-value" style={{ color: 'var(--accent)' }}>
                  <Money value={fin.totalCollectibleAr} />
                </div>
                <div className="stat-note">
                  Payer: <Money value={fin.adjudicatedPayerAr} /> &middot; Patient: <Money value={fin.patientAr} />
                </div>
              </div>

              <div className="card stat" style={{ borderTop: '3px solid var(--warn)' }}>
                <div className="stat-label">Disputed Exposure</div>
                <div className="stat-value" style={{ color: 'var(--warn)' }}>
                  <Money value={fin.disputedAr} />
                </div>
                <div className="stat-note">
                  Denied & under active appeal
                </div>
              </div>

              <div className="card stat" style={{ borderTop: '3px solid var(--ok)' }}>
                <div className="stat-label">Net Recorded Payments</div>
                <div className="stat-value" style={{ color: 'var(--ok)' }}>
                  <Money value={fin.netRecordedPayments} />
                </div>
                <div className="stat-note">
                  Legacy: <Money value={fin.legacyOpeningPayments} /> &middot; Posted: <Money value={fin.grossPostedPayerPayments - fin.payerPaymentReversals} />
                </div>
              </div>

              <div className="card stat" style={{ borderTop: '3px solid var(--neutral)' }}>
                <div className="stat-label">Verified Write-Offs</div>
                <div className="stat-value">
                  <Money value={fin.verifiedContractualWriteOffs} />
                </div>
                <div className="stat-note">
                  Contractual CO-45 adjustments
                </div>
              </div>
            </div>

            {/* Performance KPIs Cards */}
            <div className="grid grid-4" style={{ marginBottom: '24px' }}>
              <div className="card stat">
                <div className="stat-label">Denial Rate</div>
                <div className="stat-value">{perf.denialRate.toFixed(1)}%</div>
                <div className="stat-note">
                  {op?.deniedClaims} denied out of {op?.totalClaims} claims
                </div>
              </div>

              <div className="card stat">
                <div className="stat-label">Appeal Overturn Rate</div>
                <div className="stat-value" style={{ color: perf.appealOverturnRate > 0 ? 'var(--ok)' : undefined }}>
                  {perf.appealOverturnRate.toFixed(1)}%
                </div>
                <div className="stat-note">
                  Avg resolution: {perf.avgAppealResolutionDays ? `${perf.avgAppealResolutionDays.toFixed(0)} days` : 'N/A'}
                </div>
              </div>

              <div className="card stat">
                <div className="stat-label">AR Over 90 Days</div>
                <div className="stat-value" style={{ color: perf.arOver90DaysPercent > 20 ? 'var(--danger)' : undefined }}>
                  {perf.arOver90DaysPercent.toFixed(1)}%
                </div>
                <div className="stat-note">
                  Aged receivables ratio
                </div>
              </div>

              <div className="card stat">
                <div className="stat-label">Eligibility Verification</div>
                <div className="stat-value">{perf.eligibilitySuccessRate.toFixed(1)}%</div>
                <div className="stat-note">Active coverage verified</div>
              </div>
            </div>

            {/* Navigation Tabs */}
            <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid var(--border)', marginBottom: '18px' }}>
              <button
                className={`btn btn-sm ${activeTab === 'aging' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setActiveTab('aging')}
              >
                AR Aging Buckets
              </button>
              <button
                className={`btn btn-sm ${activeTab === 'payers' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setActiveTab('payers')}
              >
                Payer Performance ({payers.length})
              </button>
              <button
                className={`btn btn-sm ${activeTab === 'trends' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setActiveTab('trends')}
              >
                Payment Trends
              </button>
              <button
                className={`btn btn-sm ${activeTab === 'denials' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setActiveTab('denials')}
              >
                Denial & Appeal Intelligence
              </button>
              <button
                className={`btn btn-sm ${activeTab === 'priorities' ? 'btn-primary' : 'btn-ghost'}`}
                onClick={() => setActiveTab('priorities')}
              >
                Priority Worklist ({priorities.length})
              </button>
            </div>

            {/* TAB 1: AR Aging */}
            {activeTab === 'aging' && aging && (
              <Card
                title={`Accounts Receivable Aging (As of ${aging.asOfDate})`}
                actions={
                  <span style={{ fontSize: '13px', color: 'var(--muted)' }}>
                    Total Collectible: <strong><Money value={aging.totalCollectibleAr} /></strong> across {aging.totalActiveClaims} active claims
                  </span>
                }
              >
                {/* Visual Aging Distribution Bar */}
                <div style={{ marginBottom: '20px' }}>
                  <div style={{ display: 'flex', height: '24px', borderRadius: '4px', overflow: 'hidden', marginBottom: '8px' }}>
                    {aging.buckets.map((b, idx) => {
                      const pct = aging.totalCollectibleAr > 0 ? (b.totalCollectibleAr / aging.totalCollectibleAr) * 100 : 0;
                      const colors = ['#10b981', '#3b82f6', '#f59e0b', '#f97316', '#ef4444'];
                      return (
                        <div
                          key={b.bucketKey}
                          style={{
                            width: `${pct}%`,
                            backgroundColor: colors[idx % colors.length],
                            minWidth: pct > 0 ? '4px' : '0px',
                            transition: 'width 0.3s ease',
                          }}
                          title={`${b.label}: $${b.totalCollectibleAr.toFixed(2)} (${pct.toFixed(1)}%)`}
                        />
                      );
                    })}
                  </div>
                  <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', fontSize: '12px', color: 'var(--muted)' }}>
                    {aging.buckets.map((b, idx) => {
                      const colors = ['#10b981', '#3b82f6', '#f59e0b', '#f97316', '#ef4444'];
                      return (
                        <div key={b.bucketKey} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <span style={{ width: '10px', height: '10px', borderRadius: '2px', backgroundColor: colors[idx % colors.length] }} />
                          <span>{b.label}: <Money value={b.totalCollectibleAr} /> ({b.claimCount})</span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                <div className="table-responsive">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Aging Bucket</th>
                        <th>Days Range</th>
                        <th style={{ textAlign: 'right' }}>Active Claims</th>
                        <th style={{ textAlign: 'right' }}>Adjudicated Payer AR</th>
                        <th style={{ textAlign: 'right' }}>Patient Responsibility</th>
                        <th style={{ textAlign: 'right' }}>Total Collectible AR</th>
                        <th style={{ textAlign: 'right' }}>Disputed Exposure</th>
                      </tr>
                    </thead>
                    <tbody>
                      {aging.buckets.map((b) => (
                        <tr key={b.bucketKey}>
                          <td><strong>{b.label}</strong></td>
                          <td>{b.minDays} – {b.maxDays !== null ? `${b.maxDays} days` : '120+ days'}</td>
                          <td style={{ textAlign: 'right' }}>{b.claimCount}</td>
                          <td style={{ textAlign: 'right' }}><Money value={b.adjudicatedPayerAr} /></td>
                          <td style={{ textAlign: 'right' }}><Money value={b.patientAr} /></td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}>
                            <Money value={b.totalCollectibleAr} />
                          </td>
                          <td style={{ textAlign: 'right', color: 'var(--warn)' }}>
                            <Money value={b.disputedAr} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ fontWeight: 600, borderTop: '2px solid var(--border)' }}>
                        <td colSpan={2}>Total Collectible AR</td>
                        <td style={{ textAlign: 'right' }}>{aging.totalActiveClaims}</td>
                        <td style={{ textAlign: 'right' }}>
                          <Money value={aging.buckets.reduce((acc, b) => acc + b.adjudicatedPayerAr, 0)} />
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <Money value={aging.buckets.reduce((acc, b) => acc + b.patientAr, 0)} />
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--accent)' }}>
                          <Money value={aging.totalCollectibleAr} />
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--warn)' }}>
                          <Money value={aging.totalDisputedAr} />
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </Card>
            )}

            {/* TAB 2: Payer Performance */}
            {activeTab === 'payers' && (
              <Card title="Payer Performance & Receivables">
                {payers.length === 0 ? (
                  <EmptyState title="No payer data recorded" />
                ) : (
                  <div className="table-responsive">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Payer</th>
                          <th style={{ textAlign: 'right' }}>Claims</th>
                          <th style={{ textAlign: 'right' }}>Total Billed</th>
                          <th style={{ textAlign: 'right' }}>Net Payments</th>
                          <th style={{ textAlign: 'right' }}>Write-Offs</th>
                          <th style={{ textAlign: 'right' }}>Payer AR</th>
                          <th style={{ textAlign: 'right' }}>Patient AR</th>
                          <th style={{ textAlign: 'right' }}>Disputed</th>
                          <th style={{ textAlign: 'right' }}>Denial Rate</th>
                          <th style={{ textAlign: 'right' }}>Avg Days</th>
                        </tr>
                      </thead>
                      <tbody>
                        {payers.map((p) => (
                          <tr key={p.payerId}>
                            <td>
                              <strong>{p.payerName}</strong>
                              <div style={{ fontSize: '11px', color: 'var(--muted)' }}>{p.payerCode}</div>
                            </td>
                            <td style={{ textAlign: 'right' }}>{p.totalClaims}</td>
                            <td style={{ textAlign: 'right' }}><Money value={p.totalBilled} /></td>
                            <td style={{ textAlign: 'right', color: 'var(--ok)' }}><Money value={p.netPayments} /></td>
                            <td style={{ textAlign: 'right' }}><Money value={p.verifiedWriteOffs} /></td>
                            <td style={{ textAlign: 'right', fontWeight: 600 }}><Money value={p.adjudicatedPayerAr} /></td>
                            <td style={{ textAlign: 'right' }}><Money value={p.patientAr} /></td>
                            <td style={{ textAlign: 'right', color: 'var(--warn)' }}><Money value={p.disputedExposure} /></td>
                            <td style={{ textAlign: 'right' }}>
                              <span style={{ color: p.denialRate > 20 ? 'var(--danger)' : undefined }}>
                                {p.denialRate.toFixed(1)}%
                              </span>
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              {p.avgProcessingDays ? `${p.avgProcessingDays.toFixed(0)}d` : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            )}

            {/* TAB 3: Payment Trends */}
            {activeTab === 'trends' && paymentTrends && (
              <Card
                title="Historical Payment Ledger Trends"
                actions={
                  <span style={{ fontSize: '13px', color: 'var(--muted)' }}>
                    Grand Total Net Payments: <strong><Money value={paymentTrends.grandTotalNetPayments} /></strong> &middot; Write-Offs: <strong><Money value={paymentTrends.grandTotalWriteOffs} /></strong>
                  </span>
                }
              >
                <div className="table-responsive">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Period</th>
                        <th style={{ textAlign: 'right' }}>Transactions</th>
                        <th style={{ textAlign: 'right' }}>Posted Payments</th>
                        <th style={{ textAlign: 'right' }}>Reversals</th>
                        <th style={{ textAlign: 'right' }}>Net Payments</th>
                        <th style={{ textAlign: 'right' }}>Contractual Write-Offs</th>
                        <th style={{ textAlign: 'right' }}>CO Reversals</th>
                        <th style={{ textAlign: 'right' }}>Net Write-Offs</th>
                      </tr>
                    </thead>
                    <tbody>
                      {paymentTrends.monthlyTrends.map((t: MonthlyPaymentTrendItem) => (
                        <tr key={t.period}>
                          <td><strong>{t.period}</strong></td>
                          <td style={{ textAlign: 'right' }}>{t.transactionCount}</td>
                          <td style={{ textAlign: 'right' }}><Money value={t.postedPayerPayments} /></td>
                          <td style={{ textAlign: 'right', color: 'var(--danger)' }}><Money value={t.payerPaymentReversals} /></td>
                          <td style={{ textAlign: 'right', fontWeight: 600, color: 'var(--ok)' }}><Money value={t.netPayerPayments} /></td>
                          <td style={{ textAlign: 'right' }}><Money value={t.contractualWriteOffs} /></td>
                          <td style={{ textAlign: 'right', color: 'var(--danger)' }}><Money value={t.contractualReversals} /></td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}><Money value={t.netContractualAdjustments} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card>
            )}

            {/* TAB 4: Denial & Appeal Intelligence */}
            {activeTab === 'denials' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '18px' }}>
                <Card title="Denial Root Causes (CARC Analysis)">
                  {denialReasons.length === 0 ? (
                    <EmptyState title="No denials recorded" />
                  ) : (
                    <div className="table-responsive">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>CARC Code</th>
                            <th>Category</th>
                            <th style={{ textAlign: 'right' }}>Count</th>
                            <th style={{ textAlign: 'right' }}>Amount</th>
                            <th style={{ textAlign: 'right' }}>% Total</th>
                          </tr>
                        </thead>
                        <tbody>
                          {denialReasons.map((dr) => (
                            <tr key={`${dr.code}-${dr.payerName}`}>
                              <td>
                                <strong>{dr.code}</strong>
                                <div style={{ fontSize: '11px', color: 'var(--muted)' }}>{dr.reason}</div>
                              </td>
                              <td><span className="badge">{dr.category}</span></td>
                              <td style={{ textAlign: 'right' }}>{dr.count}</td>
                              <td style={{ textAlign: 'right' }}><Money value={dr.totalAmount} /></td>
                              <td style={{ textAlign: 'right' }}>{dr.percentageOfTotal.toFixed(1)}%</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </Card>

                <Card title="Appeal Outcomes by Escalation Level">
                  {appealPerf ? (
                    <div>
                      <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', fontSize: '13px' }}>
                        <div>Total Appeals: <strong>{appealPerf.totalAppeals}</strong></div>
                        <div>Decided: <strong>{appealPerf.decidedAppeals}</strong></div>
                        <div>Pending: <strong>{appealPerf.pendingAppeals}</strong></div>
                        <div>Overturned: <strong style={{ color: 'var(--ok)' }}>{appealPerf.overturnedCount}</strong></div>
                      </div>
                      <div className="table-responsive">
                        <table className="table">
                          <thead>
                            <tr>
                              <th>Level</th>
                              <th style={{ textAlign: 'right' }}>Total</th>
                              <th style={{ textAlign: 'right' }}>Overturned</th>
                              <th style={{ textAlign: 'right' }}>Upheld</th>
                              <th style={{ textAlign: 'right' }}>Pending</th>
                            </tr>
                          </thead>
                          <tbody>
                            {appealPerf.byLevel.map((lvl) => (
                              <tr key={lvl.level}>
                                <td>
                                  <strong>Level {lvl.level}</strong>
                                  <div style={{ fontSize: '11px', color: 'var(--muted)' }}>{lvl.levelDescription}</div>
                                </td>
                                <td style={{ textAlign: 'right' }}>{lvl.totalCount}</td>
                                <td style={{ textAlign: 'right', color: 'var(--ok)' }}>{lvl.overturnedCount}</td>
                                <td style={{ textAlign: 'right', color: 'var(--danger)' }}>{lvl.upheldCount}</td>
                                <td style={{ textAlign: 'right' }}>{lvl.pendingCount}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  ) : (
                    <EmptyState title="No appeal data available" />
                  )}
                </Card>

                {denialTrends && denialTrends.monthlyTrends.length > 0 && (
                  <div style={{ gridColumn: '1 / -1', marginTop: '12px' }}>
                    <Card
                      title="Monthly Denial & Appeal Trends"
                      actions={
                        <span style={{ fontSize: '13px', color: 'var(--muted)' }}>
                          Total Recorded Denials: <strong>{denialTrends.totalDenialsRecorded}</strong> &middot; Total Denied Amount: <strong><Money value={denialTrends.totalDenialAmount} /></strong>
                        </span>
                      }
                    >
                      <div className="table-responsive">
                        <table className="table">
                          <thead>
                            <tr>
                              <th>Period</th>
                              <th style={{ textAlign: 'right' }}>New Denials</th>
                              <th style={{ textAlign: 'right' }}>Denied Amount</th>
                              <th style={{ textAlign: 'right' }}>Appeals Filed</th>
                              <th style={{ textAlign: 'right' }}>Overturned</th>
                              <th style={{ textAlign: 'right' }}>Upheld</th>
                            </tr>
                          </thead>
                          <tbody>
                            {denialTrends.monthlyTrends.map((dt) => (
                              <tr key={dt.period}>
                                <td><strong>{dt.period}</strong></td>
                                <td style={{ textAlign: 'right' }}>{dt.newDenialsCount}</td>
                                <td style={{ textAlign: 'right', color: 'var(--danger)' }}><Money value={dt.newDenialsAmount} /></td>
                                <td style={{ textAlign: 'right' }}>{dt.appealsFiledCount}</td>
                                <td style={{ textAlign: 'right', color: 'var(--ok)' }}>{dt.appealsOverturnedCount}</td>
                                <td style={{ textAlign: 'right', color: 'var(--danger)' }}>{dt.appealsUpheldCount}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </Card>
                  </div>
                )}
              </div>
            )}

            {/* TAB 5: Priority Worklist */}
            {activeTab === 'priorities' && (
              <Card
                title="Revenue Cycle Smart Priority Worklist"
                actions={
                  <span style={{ fontSize: '13px', color: 'var(--muted)' }}>
                    {priorities.length} claims requiring billing or management action
                  </span>
                }
              >
                {priorities.length === 0 ? (
                  <EmptyState title="All claims are up to date" hint="No urgent follow-ups required at this time." />
                ) : (
                  <div className="table-responsive">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>Priority</th>
                          <th>Claim Number</th>
                          <th>Patient / Payer</th>
                          <th>Status</th>
                          <th style={{ textAlign: 'right' }}>Outstanding</th>
                          <th style={{ textAlign: 'right' }}>Aging</th>
                          <th>Root Issue</th>
                          <th>Recommended Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {priorities.map((item) => (
                          <tr key={item.claimId}>
                            <td>
                              <span
                                className="badge"
                                style={{
                                  backgroundColor:
                                    item.priority === 'HIGH'
                                      ? 'var(--danger-soft)'
                                      : item.priority === 'MEDIUM'
                                      ? 'var(--warn-soft)'
                                      : 'var(--info-soft)',
                                  color:
                                    item.priority === 'HIGH'
                                      ? 'var(--danger)'
                                      : item.priority === 'MEDIUM'
                                      ? 'var(--warn)'
                                      : 'var(--info)',
                                  fontWeight: 600,
                                }}
                              >
                                {item.priority}
                              </span>
                            </td>
                            <td>
                              <Link to={`/claims/${item.claimId}`} style={{ fontWeight: 600 }}>
                                {item.claimNumber}
                              </Link>
                            </td>
                            <td>
                              <div>{item.patientName}</div>
                              <div style={{ fontSize: '11px', color: 'var(--muted)' }}>{item.payerName}</div>
                            </td>
                            <td><StatusBadge status={item.status} /></td>
                            <td style={{ textAlign: 'right', fontWeight: 600 }}>
                              <Money value={item.outstandingAmount} />
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              {item.daysOutstanding} days
                            </td>
                            <td>
                              <div style={{ fontWeight: 500, fontSize: '13px' }}>{item.category}</div>
                              <div style={{ fontSize: '12px', color: 'var(--muted)' }}>{item.reason}</div>
                            </td>
                            <td>
                              <span style={{ fontSize: '13px', color: 'var(--accent)' }}>
                                {item.recommendedAction}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Card>
            )}
          </>
        )}
      </div>
    </>
  );
}
