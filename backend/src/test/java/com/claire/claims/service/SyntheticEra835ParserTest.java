package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.AdjustmentGroupCode;
import com.claire.claims.dto.RemittanceDtos.ImportRemittanceRequest;
import com.claire.claims.service.remittance.SyntheticEra835Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticEra835ParserTest {

    private SyntheticEra835Parser parser;

    @BeforeEach
    void setUp() {
        parser = new SyntheticEra835Parser();
    }

    @Test
    @DisplayName("Parse valid synthetic X12 835 text content")
    void testParseValidX12Text() {
        String x12 = """
                ISA*00*          *00*          *ZZ*SUBMITTER      *ZZ*RECEIVER       *261120*1015*^*00501*000000001*0*T*:~
                GS*HP*SUBMITTER*RECEIVER*20261120*1015*1*X*005010X221A1~
                ST*835*0001~
                BPR*I*1240.00*C*ACH*CCP*01*999999999*DA*111111111*1234567890**01*999999999*DA*222222222*20261120~
                TRN*1*EFT-2026-99042*1999999999~
                N1*PR*Aetna Health Plans*XX*ETIN123~
                CLP*CLM-2026-000004*1*1450.00*1240.00*0.00*12*ICN-2026-9912~
                CAS*CO*45*210.00~
                SVC*HC:99214*1450.00*1240.00~
                CAS*CO*45*210.00~
                SE*11*0001~
                GE*1*1~
                IEA*1*000000001~
                """;

        ImportRemittanceRequest req = parser.parseX12(x12);

        assertThat(req.checkNumber()).isEqualTo("EFT-2026-99042");
        assertThat(req.checkDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(req.totalPaid()).isEqualByComparingTo("1240.00");
        assertThat(req.paymentMethod()).isEqualTo("ACH");
        assertThat(req.payerCode()).isEqualTo("ETIN123");
        assertThat(req.claims()).hasSize(1);

        var claim = req.claims().get(0);
        assertThat(claim.claimNumber()).isEqualTo("CLM-2026-000004");
        assertThat(claim.payerIcn()).isEqualTo("ICN-2026-9912");
        assertThat(claim.billedAmount()).isEqualByComparingTo("1450.00");
        assertThat(claim.paidAmount()).isEqualByComparingTo("1240.00");
        assertThat(claim.patientResponsibility()).isEqualByComparingTo("0.00");
        assertThat(claim.adjustments()).hasSize(1);
        assertThat(claim.adjustments().get(0).groupCode()).isEqualTo(AdjustmentGroupCode.CO);
        assertThat(claim.adjustments().get(0).reasonCode()).isEqualTo("45");
        assertThat(claim.adjustments().get(0).amount()).isEqualByComparingTo("210.00");
    }

    @Test
    @DisplayName("Reject malformed 835 text with missing TRN")
    void testMissingTrnRejection() {
        String x12 = """
                ST*835*0001~
                BPR*I*500.00*C*ACH~
                CLP*CLM-1001*1*500.00*500.00*0.00*12~
                """;

        assertThatThrownBy(() -> parser.parseX12(x12))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("missing TRN check/EFT trace number");
    }

    @Test
    @DisplayName("Reject empty or blank payload")
    void testEmptyPayloadRejection() {
        assertThatThrownBy(() -> parser.parseX12("   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty or blank");
    }

    @Test
    @DisplayName("Reject malformed binary data")
    void testBinaryDataRejection() {
        String binaryContent = "ST*835\u0000\u0001\u0002*TRN";
        assertThatThrownBy(() -> parser.parseX12(binaryContent))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("binary or non-text control characters detected");
    }

    @Test
    @DisplayName("Parse CAS segment with multiple adjustment pairs")
    void testParseMultipleCasPairs() {
        String x12 = """
                ST*835*0001~
                BPR*I*600.00*C*ACH*CCP*01*999999999*DA*111111111*1234567890**01*999999999*DA*222222222*20261120~
                TRN*1*CHK-8841*1999999999~
                CLP*CLM-2026-000008*1*1000.00*600.00*150.00*12~
                CAS*PR*1*100.00*2*50.00~
                CAS*CO*45*250.00~
                """;

        ImportRemittanceRequest req = parser.parseX12(x12);
        assertThat(req.claims()).hasSize(1);
        var claim = req.claims().get(0);
        assertThat(claim.adjustments()).hasSize(3);
        assertThat(claim.adjustments().get(0).reasonCode()).isEqualTo("1");
        assertThat(claim.adjustments().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(claim.adjustments().get(1).reasonCode()).isEqualTo("2");
        assertThat(claim.adjustments().get(1).amount()).isEqualByComparingTo("50.00");
        assertThat(claim.adjustments().get(2).groupCode()).isEqualTo(AdjustmentGroupCode.CO);
        assertThat(claim.adjustments().get(2).amount()).isEqualByComparingTo("250.00");
    }
}

