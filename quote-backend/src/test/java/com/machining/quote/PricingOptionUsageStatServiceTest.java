package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.mapper.PricingOptionUsageStatMapper;
import com.machining.quote.service.PricingOptionUsageStatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class PricingOptionUsageStatServiceTest {

    @Test
    void recordsMaterialProcessCategoryAndExtraFeeUsageFromSuccessfulQuotePayload() {
        PricingOptionUsageStatMapper mapper = mock(PricingOptionUsageStatMapper.class);
        PricingOptionUsageStatService service = new PricingOptionUsageStatService();
        ReflectionTestUtils.setField(service, "usageStatMapper", mapper);

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", " mat-0007 ");
        QuoteSubmitReq.ProcessSubmitDTO process = new QuoteSubmitReq.ProcessSubmitDTO();
        process.setSourceType("process");
        process.setProcessCode(" cnc ");
        process.setProcessDetailId(101L);
        QuoteSubmitReq.ProcessSubmitDTO extra = new QuoteSubmitReq.ProcessSubmitDTO();
        extra.setSourceType("extra");
        extra.setFeeCode(" polish ");
        extra.setFeeItemId(9);

        service.recordQuoteOptionUsage(materialData, List.of(process, extra));

        verify(mapper).increment("MATERIAL", "MAT-0007");
        verify(mapper).increment("PROCESS_CATEGORY", "CNC");
        verify(mapper).increment("EXTRA_FEE", "POLISH");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void skipsBlankOrUnstableOptionKeys() {
        PricingOptionUsageStatMapper mapper = mock(PricingOptionUsageStatMapper.class);
        PricingOptionUsageStatService service = new PricingOptionUsageStatService();
        ReflectionTestUtils.setField(service, "usageStatMapper", mapper);

        QuoteSubmitReq.ProcessSubmitDTO legacyExtra = new QuoteSubmitReq.ProcessSubmitDTO();
        legacyExtra.setSourceType("extra");
        legacyExtra.setProcessName("只有名称不能作为稳定身份");
        QuoteSubmitReq.ProcessSubmitDTO missingProcessCode = new QuoteSubmitReq.ProcessSubmitDTO();
        missingProcessCode.setSourceType("process");
        missingProcessCode.setProcessDetailId(101L);

        service.recordQuoteOptionUsage(Map.of("materialCode", " "), List.of(legacyExtra, missingProcessCode));

        verifyNoMoreInteractions(mapper);
    }
}
