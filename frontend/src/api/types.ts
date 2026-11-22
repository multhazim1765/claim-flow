export type ClaimStatus =
  | 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED'
  | 'PARTIALLY_PAID' | 'PAID' | 'DENIED' | 'APPEALED' | 'VOID';

export type UserRole = 'ADMIN' | 'BILLER' | 'VIEWER';

export type PlanType =
  | 'COMMERCIAL' | 'MEDICARE' | 'MEDICAID' | 'TRICARE' | 'WORKERS_COMP' | 'SELF_PAY';

export type PolicyPriority = 'PRIMARY' | 'SECONDARY' | 'TERTIARY';

export interface LoginResponse {
  token: string;
  username: string;
  fullName: string;
  role: UserRole;
  expiresAt: string;
}

export interface CurrentUser {
  username: string;
  fullName: string;
  role: UserRole;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ClaimListItem {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  patientName: string;
  patientMrn: string;
  payerName: string;
  providerName: string;
  serviceDateFrom: string;
  serviceDateTo: string;
  totalCharge: number;
  paidAmount: number;
  outstanding: number;
  submittedAt: string | null;
  createdAt: string;
}

export interface ClaimLine {
  id: number;
  lineNumber: number;
  cptCode: string;
  modifiers: string | null;
  serviceDate: string;
  units: number;
  chargeAmount: number;
  allowedAmount: number;
  paidAmount: number;
  description: string | null;
}

export interface ClaimDiagnosis {
  id: number;
  icd10Code: string;
  description: string | null;
  sequenceNo: number;
}

export interface Claim {
  id: number;
  claimNumber: string;
  status: ClaimStatus;
  statusDescription: string;
  patientId: number;
  patientName: string;
  patientMrn: string;
  providerId: number;
  providerName: string;
  providerNpi: string;
  payerId: number;
  payerName: string;
  policyId: number | null;
  policyMemberId: string | null;
  serviceDateFrom: string;
  serviceDateTo: string;
  placeOfService: string;
  totalCharge: number;
  allowedAmount: number;
  paidAmount: number;
  patientResponsibility: number;
  outstanding: number;
  notes: string | null;
  submittedAt: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  editable: boolean;
  allowedTransitions: ClaimStatus[];
  lines: ClaimLine[];
  diagnoses: ClaimDiagnosis[];
}

export interface StatusHistoryEntry {
  id: number;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface StatusBucket {
  status: ClaimStatus;
  description: string;
  count: number;
  totalCharge: number;
  paidAmount: number;
}

export interface ClaimSummary {
  totalClaims: number;
  totalCharged: number;
  totalPaid: number;
  outstandingReceivable: number;
  byStatus: StatusBucket[];
}

export interface PatientSummary {
  id: number;
  mrn: string;
  displayName: string;
  dateOfBirth: string;
}

export interface Policy {
  id: number;
  patientId: number;
  payerId: number;
  payerName: string;
  memberId: string;
  groupNumber: string | null;
  priority: PolicyPriority;
  effectiveDate: string;
  terminationDate: string | null;
  activeToday: boolean;
}

export interface Patient {
  id: number;
  mrn: string;
  firstName: string;
  lastName: string;
  displayName: string;
  dateOfBirth: string;
  phone: string | null;
  email: string | null;
  addressLine1: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  policies: Policy[];
}

export interface Payer {
  id: number;
  payerCode: string;
  name: string;
  planType: PlanType;
  claimsAddress: string | null;
  phone: string | null;
  active: boolean;
}

export interface Provider {
  id: number;
  npi: string;
  firstName: string;
  lastName: string;
  displayName: string;
  specialty: string | null;
  taxId: string | null;
  active: boolean;
}

export interface ClaimLineInput {
  cptCode: string;
  modifiers?: string | null;
  serviceDate: string;
  units: number;
  chargeAmount: number;
  description?: string | null;
}

export interface ClaimDiagnosisInput {
  icd10Code: string;
  description?: string | null;
  sequenceNo: number;
}

export interface ClaimInput {
  patientId: number;
  providerId: number;
  payerId: number;
  policyId?: number | null;
  serviceDateFrom: string;
  serviceDateTo: string;
  placeOfService: string;
  notes?: string | null;
  lines: ClaimLineInput[];
  diagnoses: ClaimDiagnosisInput[];
}

export interface TransitionInput {
  targetStatus: ClaimStatus;
  reason?: string | null;
  paidAmount?: number | null;
  allowedAmount?: number | null;
}

export interface StatusTransitionMap {
  transitions: Record<string, string[]>;
  descriptions: Record<string, string>;
  terminal: string[];
}

export interface EligibilityCheckRequest {
  patientId: number;
  payerId: number;
  serviceDate: string;
  serviceTypeCode?: string;
  forceRefresh?: boolean;
}

export interface EligibilityCheckResponse {
  id: number;
  patientId: number;
  patientName: string;
  patientMrn: string;
  payerId: number;
  payerName: string;
  policyMemberId: string | null;
  active: boolean;
  checkedAt: string;
  planBegin: string | null;
  planEnd: string | null;
  copay: number | null;
  coinsurancePercent: number | null;
  deductibleTotal: number | null;
  deductibleRemaining: number | null;
  outOfPocketRemaining: number | null;
  cached: boolean;
  rawResponse: string | null;
}

export interface EligibilityHistoryItem {
  id: number;
  patientId: number;
  patientName: string;
  payerId: number;
  payerName: string;
  serviceDate: string;
  active: boolean;
  checkedAt: string;
  copay: number | null;
  coinsurancePercent: number | null;
  deductibleRemaining: number | null;
  cached: boolean;
  checkedBy: string;
}

export type DenialCategory =
  | 'AUTHORIZATION'
  | 'MEDICAL_NECESSITY'
  | 'TIMELY_FILING'
  | 'BILLING_CODING'
  | 'COVERAGE_ELIGIBILITY'
  | 'DUPLICATE'
  | 'OTHER';

export type DenialStatus =
  | 'OPEN'
  | 'APPEALED'
  | 'RESOLVED'
  | 'WRITTEN_OFF';

export type DenialUrgency =
  | 'OVERDUE'
  | 'CRITICAL'
  | 'WARNING'
  | 'NORMAL';

export type AppealOutcome =
  | 'UPHELD'
  | 'OVERTURNED'
  | 'PARTIAL'
  | 'WITHDRAWN';

export interface DenialWorklistItem {
  id: number;
  claimId: number;
  claimNumber: string;
  patientName: string;
  patientMrn: string;
  payerId: number;
  payerName: string;
  denialCode: string;
  remittanceCode: string | null;
  denialCategory: DenialCategory;
  reason: string;
  denialDate: string;
  appealDeadline: string;
  daysRemaining: number;
  urgency: DenialUrgency;
  amount: number;
  status: DenialStatus;
  createdBy: string;
  createdAt: string;
}

export interface CategoryBreakdown {
  category: DenialCategory;
  description: string;
  count: number;
  amount: number;
}

export interface DenialSummary {
  totalOpenDenials: number;
  totalOpenDeniedAmount: number;
  criticalDeadlines: number;
  warningDeadlines: number;
  normalDeadlines: number;
  overdueDeadlines: number;
  byCategory: Record<string, CategoryBreakdown>;
}

export interface DenialResponse {
  id: number;
  claimId: number;
  claimNumber: string;
  payerId: number;
  payerName: string;
  denialCode: string;
  remittanceCode: string | null;
  denialCategory: DenialCategory;
  reason: string;
  denialDate: string;
  appealDeadline: string;
  daysRemaining: number;
  urgency: DenialUrgency;
  amount: number;
  status: DenialStatus;
  createdBy: string;
  createdAt: string;
}

export interface CreateDenialInput {
  claimId: number;
  payerId?: number | null;
  denialCode: string;
  remittanceCode?: string | null;
  denialCategory: DenialCategory;
  reason: string;
  denialDate: string;
  appealDeadline: string;
  amount: number;
}

export interface FileAppealInput {
  denialId?: number | null;
  narrative: string;
  responseDeadlineDays?: number | null;
}

export interface RecordOutcomeInput {
  outcome: AppealOutcome;
  decidedOn: string;
  recoveredAmount?: number | null;
  decisionNotes?: string | null;
}

export interface AppealResponse {
  id: number;
  claimId: number;
  claimNumber: string;
  denialId: number | null;
  denialCode: string | null;
  level: number;
  levelDescription: string;
  filedOn: string;
  deadline: string;
  narrative: string;
  outcome: AppealOutcome | null;
  decidedOn: string | null;
  recoveredAmount: number | null;
  decisionNotes: string | null;
  resultingClaimStatus: ClaimStatus | null;
  filedBy: string;
  createdAt: string;
}

// =========================================================================
// Milestone 2.3 Remittance, Payment Posting, Reversal & AR Types
// =========================================================================

export type RemittanceBatchStatus =
  | 'RECEIVED'
  | 'VALIDATED'
  | 'BALANCED'
  | 'OUT_OF_BALANCE'
  | 'POSTED'
  | 'PARTIALLY_POSTED';

export type AdjustmentGroupCode = 'CO' | 'PR' | 'OA' | 'PI' | 'CR';

export type PaymentLedgerEntryType =
  | 'LEGACY_INITIAL_BALANCE'
  | 'PAYER_PAYMENT'
  | 'PAYER_REVERSAL'
  | 'CONTRACTUAL_WRITE_OFF'
  | 'CONTRACTUAL_ADJUSTMENT_REVERSAL'
  | 'PATIENT_PAYMENT'
  | 'PATIENT_ADJUSTMENT';

export interface RemittanceAdjustment {
  id: number;
  claimLineId: number | null;
  groupCode: AdjustmentGroupCode;
  reasonCode: string;
  remarkCode: string | null;
  amount: number;
  entryType: string | null;
}

export interface RemittanceVoucher {
  id: number;
  claimId: number | null;
  sequenceNo: number;
  claimNumber: string;
  payerIcn: string | null;
  clpStatusCode: string;
  billedAmount: number;
  allowedAmount: number;
  paidAmount: number;
  patientResponsibility: number;
  contractualAdj: number;
  otherAdj: number;
  entryType: string | null;
  balanceStatus: string;
  postingStatus: string;
  postedAt: string | null;
  postedBy: string | null;
  adjustments: RemittanceAdjustment[];
}

export interface RemittanceExceptionItem {
  id: number;
  remittanceId: number;
  batchControlNumber?: string;
  voucherId: number | null;
  claimNumber: string;
  patientName: string | null;
  serviceDate: string | null;
  billedAmount: number;
  paidAmount: number;
  exceptionType: string;
  errorMessage: string;
  status: 'UNRESOLVED' | 'RESOLVED' | 'IGNORED';
  resolutionAction: string | null;
  resolutionNotes: string | null;
  matchedClaimId: number | null;
  matchedClaimNumber: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

export interface RemittanceBatchListItem {
  id: number;
  batchControlNumber: string;
  payerId: number;
  payerName: string;
  paymentMethod: string;
  checkNumber: string;
  checkDate: string;
  totalPaid: number;
  claimsCount: number;
  matchedCount: number;
  exceptionCount: number;
  status: RemittanceBatchStatus;
  importedBy: string;
  importedAt: string;
}

export interface RemittanceBatchDetail {
  id: number;
  batchControlNumber: string;
  payerId: number;
  payerName: string;
  paymentMethod: string;
  checkNumber: string;
  checkDate: string;
  totalPaid: number;
  claimsCount: number;
  matchedCount: number;
  exceptionCount: number;
  status: RemittanceBatchStatus;
  importedBy: string;
  importedAt: string;
  vouchers: RemittanceVoucher[];
  exceptions: RemittanceExceptionItem[];
}

export interface ImportRemittanceClaimAdjustment {
  groupCode: AdjustmentGroupCode;
  reasonCode: string;
  remarkCode?: string | null;
  amount: number;
}

export interface ImportRemittanceClaimInput {
  claimNumber: string;
  payerIcn?: string | null;
  clpStatusCode: string;
  billedAmount: number;
  allowedAmount: number;
  paidAmount: number;
  patientResponsibility?: number | null;
  entryType?: string | null;
  adjustments?: ImportRemittanceClaimAdjustment[];
}

export interface ImportRemittanceRequest {
  payerId?: number | null;
  payerCode?: string | null;
  checkNumber: string;
  checkDate: string;
  paymentMethod?: string | null;
  totalPaid: number;
  claims: ImportRemittanceClaimInput[];
}

export interface ExceptionResolutionInput {
  action: string;
  matchedClaimId?: number | null;
  notes: string;
}

export interface VoucherPostingResponse {
  voucherId: number;
  claimId: number;
  claimNumber: string;
  previousStatus: ClaimStatus;
  newStatus: ClaimStatus;
  postedAmount: number;
  patientResponsibility: number;
  contractualWriteOff: number;
  cumulativeClaimPaid: number;
  isFutureDated: boolean;
  settlementNote: string | null;
  postedBy: string;
  postedAt: string;
}

export interface BatchPostingResponse {
  remittanceId: number;
  batchControlNumber: string;
  totalVouchers: number;
  postedCount: number;
  batchStatus: RemittanceBatchStatus;
  isFutureDated: boolean;
  settlementNote: string | null;
  postedAt: string;
  vouchers: VoucherPostingResponse[];
}

export interface PaymentLedgerItem {
  id: number;
  claimId: number;
  voucherId: number | null;
  remittanceId: number | null;
  entryType: PaymentLedgerEntryType;
  amount: number;
  payerIcn: string | null;
  checkNumber: string | null;
  checkDate: string | null;
  originalLedgerId: number | null;
  notes: string | null;
  postedBy: string;
  postedAt: string;
}

export interface PaymentReversalInput {
  originalLedgerId: number;
  reversalReason: string;
  checkNumber?: string | null;
  checkDate?: string | null;
  notes?: string | null;
}

export interface PaymentReversalResponse {
  claimId: number;
  claimNumber: string;
  originalLedgerId: number;
  reversedPaymentAmount: number;
  reversedContractualAdjustment: number;
  remainingPaidAmount: number;
  remainingContractualAdjustment: number;
  previousClaimStatus: ClaimStatus;
  updatedClaimStatus: ClaimStatus;
  reversalLedgerEntryId: number;
  coReversalLedgerEntryId: number | null;
  reversalReason: string;
  postedAt: string;
  postedBy: string;
}

export interface AccountsReceivableSummary {
  grossBilledCharges: number;
  legacyOpeningPayments: number;
  postedPayerPayments: number;
  payerPaymentReversals: number;
  netRecordedPayerPayments: number;
  verifiedContractualWriteOffs: number;
  adjudicatedPayerAr: number;
  patientAr: number;
  disputedAr: number;
  unadjudicatedSubmittedExposure: number;
  rejectedClaimExposure: number;
  draftClaimExposure: number;
  voidClaimExposure: number;
  legacyUnclassifiedBalances: number;
  totalCollectibleAr: number;
  totalOutstandingExposure: number;
  isBalanced: boolean;
  balanceDiscrepancy: number;
  totalClaimsCount: number;
  generatedAt: string;
}

export interface ClaimArBreakdown {
  claimId: number;
  claimNumber: string;
  status: ClaimStatus;
  totalCharge: number;
  allowedAmount: number;
  paidAmount: number;
  patientResponsibility: number;
  contractualAdjustment: number;
  remainingBalance: number;
  adjudicatedPayerAr: number;
  patientAr: number;
  disputedExposure: number;
  unadjudicatedExposure: number;
  legacyDiscrepancyNotes: string | null;
  ledgerEntriesCount: number;
}

// ============================================================================
// Milestone 2.4 Analytics & Intelligence DTOs
// ============================================================================

export interface FinancialKpis {
  grossBilledCharges: number;
  legacyOpeningPayments: number;
  grossPostedPayerPayments: number;
  payerPaymentReversals: number;
  netRecordedPayments: number;
  verifiedContractualWriteOffs: number;
  adjudicatedPayerAr: number;
  patientAr: number;
  disputedAr: number;
  unadjudicatedSubmittedExposure: number;
  rejectedClaimExposure: number;
  draftClaimExposure: number;
  voidClaimExposure: number;
  legacyUnclassifiedExposure: number;
  totalCollectibleAr: number;
  totalOutstandingExposure: number;
  reconciliationDiscrepancy: number;
  isBalanced: boolean;
}

export interface OperationalKpis {
  totalClaims: number;
  submittedClaims: number;
  acceptedClaims: number;
  paidClaims: number;
  partiallyPaidClaims: number;
  deniedClaims: number;
  appealedClaims: number;
  rejectedClaims: number;
  draftClaims: number;
  voidClaims: number;
  unresolvedExceptions: number;
  totalBatches: number;
  postedBatches: number;
}

export interface PerformanceKpis {
  denialRate: number;
  rejectionRate: number;
  appealOverturnRate: number;
  appealResolutionRate: number;
  avgAppealResolutionDays: number | null;
  avgSubmissionToPaymentDays: number | null;
  eligibilitySuccessRate: number;
  arOver90DaysPercent: number;
}

export interface AnalyticsOverviewResponse {
  financial: FinancialKpis;
  operational: OperationalKpis;
  performance: PerformanceKpis;
  generatedAt: string;
}

export interface AgingBucketItem {
  bucketKey: string;
  label: string;
  minDays: number;
  maxDays: number | null;
  claimCount: number;
  adjudicatedPayerAr: number;
  patientAr: number;
  totalCollectibleAr: number;
  disputedAr: number;
  unclassifiedExposure: number;
}

export interface ArAgingResponse {
  asOfDate: string;
  dateBasis: string;
  buckets: AgingBucketItem[];
  totalCollectibleAr: number;
  totalDisputedAr: number;
  totalActiveClaims: number;
}

export interface PayerPerformanceItem {
  payerId: number;
  payerCode: string;
  payerName: string;
  totalClaims: number;
  totalBilled: number;
  postedPayments: number;
  reversedPayments: number;
  netPayments: number;
  verifiedWriteOffs: number;
  adjudicatedPayerAr: number;
  patientAr: number;
  disputedExposure: number;
  deniedClaimsCount: number;
  denialRate: number;
  avgProcessingDays: number | null;
  agingDistribution: Record<string, number>;
}

export interface StatusBreakdownItem {
  status: ClaimStatus;
  description: string;
  claimCount: number;
  totalCharge: number;
  paidAmount: number;
  collectibleAr: number;
  contractualAdjustment: number;
}

export interface MonthlyPaymentTrendItem {
  period: string;
  postedPayerPayments: number;
  payerPaymentReversals: number;
  netPayerPayments: number;
  contractualWriteOffs: number;
  contractualReversals: number;
  netContractualAdjustments: number;
  transactionCount: number;
}

export interface PaymentTrendsResponse {
  monthlyTrends: MonthlyPaymentTrendItem[];
  totalHistoricalLegacyOpening: number;
  grandTotalNetPayments: number;
  grandTotalWriteOffs: number;
}

export interface MonthlyDenialTrendItem {
  period: string;
  newDenialsCount: number;
  newDenialsAmount: number;
  appealsFiledCount: number;
  appealsOverturnedCount: number;
  appealsUpheldCount: number;
}

export interface DenialTrendsResponse {
  monthlyTrends: MonthlyDenialTrendItem[];
  totalDenialsRecorded: number;
  totalDenialAmount: number;
}

export interface DenialReasonItem {
  code: string;
  category: string;
  categoryDescription: string;
  reason: string;
  payerName: string;
  count: number;
  totalAmount: number;
  percentageOfTotal: number;
}

export interface AppealLevelBreakdown {
  level: number;
  levelDescription: string;
  totalCount: number;
  overturnedCount: number;
  upheldCount: number;
  pendingCount: number;
}

export interface AppealPerformanceResponse {
  totalAppeals: number;
  pendingAppeals: number;
  decidedAppeals: number;
  overturnedCount: number;
  partiallyOverturnedCount: number;
  upheldCount: number;
  withdrawnCount: number;
  overturnRate: number;
  resolutionRate: number;
  avgResolutionDays: number | null;
  urgentDeadlinesCount: number;
  byLevel: AppealLevelBreakdown[];
}

export interface EligibilityPerformanceResponse {
  totalChecks: number;
  activeCoverageCount: number;
  inactiveCoverageCount: number;
  cachedChecksCount: number;
  activeCoveragePercent: number;
  cacheHitPercent: number;
}

export interface PriorityClaimItem {
  claimId: number;
  claimNumber: string;
  payerName: string;
  patientName: string;
  status: ClaimStatus;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  category: string;
  reason: string;
  recommendedAction: string;
  outstandingAmount: number;
  daysOutstanding: number;
  deadline: string | null;
}

// ============================================================================
// EDI 837P Professional Claim Export Types
// ============================================================================

export interface EdiExportKpis {
  eligibleClaimsCount: number;
  totalBatchesCount: number;
  totalExportedClaimsCount: number;
  totalAmountExported: number;
  validationFailuresCount: number;
}

export interface EligibleClaimDto {
  id: number;
  claimNumber: string;
  patientName: string;
  patientId: number | null;
  payerName: string;
  payerId: number | null;
  providerName: string;
  providerId: number | null;
  serviceDateFrom: string;
  serviceDateTo: string;
  status: ClaimStatus;
  totalCharge: number;
  previouslyExported: boolean;
  lastExportedAt: string | null;
}

export interface ValidateClaimsRequest {
  claimIds: number[];
  payerId?: number | null;
}

export interface ClaimValidationResult {
  claimId: number;
  claimNumber: string;
  valid: boolean;
  errors: string[];
  totalCharge: number;
  patientName: string;
  payerName: string;
}

export interface EdiValidationResponse {
  allValid: boolean;
  validCount: number;
  errorCount: number;
  totalCharge: number;
  results: ClaimValidationResult[];
}

export interface Export837pRequest {
  claimIds: number[];
  payerId?: number | null;
  allowRebill?: boolean;
  notes?: string;
}

export interface ExportedClaimItem {
  claimId: number;
  claimNumber: string;
  chargeAmount: number;
  sequence: number;
  exportedAt: string;
}

export interface EdiExportBatchSummary {
  id: number;
  batchNumber: string;
  payerId: number | null;
  payerName: string;
  exportStatus: string;
  claimCount: number;
  totalBilledAmount: number;
  fileName: string;
  exportedAt: string;
  createdBy: string;
}

export interface EdiExportBatchDetailResponse {
  id: number;
  batchNumber: string;
  payerId: number | null;
  payerName: string;
  interchangeControlNumber: string;
  groupControlNumber: string;
  transactionControlNumber: string;
  exportStatus: string;
  claimCount: number;
  totalBilledAmount: number;
  fileName: string;
  contentHash: string;
  exportedAt: string;
  createdBy: string;
  validationSummary: string | null;
  claims: ExportedClaimItem[];
  downloadUrl: string;
}

// ============================================================================
// Secure Claim Document Attachment Types
// ============================================================================

export type DocumentCategory =
  | 'MEDICAL_REPORT'
  | 'PRESCRIPTION'
  | 'LAB_REPORT'
  | 'REFERRAL'
  | 'PRIOR_AUTHORIZATION'
  | 'ITEMIZED_BILL'
  | 'DENIAL_LETTER'
  | 'APPEAL_SUPPORT'
  | 'INSURANCE_DOCUMENT'
  | 'DISCHARGE_SUMMARY'
  | 'OTHER';

export interface AttachmentMetadataResponse {
  id: number;
  claimId: number;
  claimNumber: string;
  documentCategory: DocumentCategory;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  sha256Hash: string;
  description?: string | null;
  attachmentStatus: 'ACTIVE' | 'ARCHIVED';
  securityScanStatus: string;
  uploadedBy: string;
  uploadedAt: string;
  archivedBy?: string | null;
  archivedAt?: string | null;
  archiveReason?: string | null;
  downloadUrl: string;
}

export interface AttachmentAuditResponse {
  id: number;
  attachmentId: number;
  claimId: number;
  action: string;
  actor: string;
  actedAt: string;
  notes?: string | null;
}

