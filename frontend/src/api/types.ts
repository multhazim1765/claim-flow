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
