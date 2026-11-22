package com.claire.claims.phase2;

import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2 surface: all original Phase 2 capabilities (2.1 EDI 837P Export, 2.2 ERA 835 Remittance,
 * 2.3 Eligibility 270/271, 2.4 Denial Management & Appeals, 2.5 Document Attachments, and
 * 2.6 Revenue Cycle Analytics) are now fully implemented in their dedicated production controllers:
 *
 * - Edi837pExportController
 * - RemittanceController & PaymentPostingController & PaymentReversalController
 * - EligibilityController
 * - DenialController & AppealController
 * - ClaimAttachmentController
 * - RevenueCycleAnalyticsController & AccountsReceivableController
 */
@RestController
public class Phase2Controller {
    // All Phase 2 capabilities are implemented.
}
