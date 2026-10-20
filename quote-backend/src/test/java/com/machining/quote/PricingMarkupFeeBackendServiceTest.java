package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.dto.PricingMarkupFeeSaveReq;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.PricingMarkupFeeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingMarkupFeeBackendServiceTest {

    @Test
    void listFeesFiltersByScopeAndCategoryOrderedBySortThenId() {
        DictPricingMarkupFeeMapper mapper = mock(DictPricingMarkupFeeMapper.class);
        PricingMarkupFeeService service = service(mapper, mock(AuditService.class));
        DictPricingMarkupFee fee = fee(10L, "NORMAL", "BASIC_PERCENT", "Freight", "2.50", 20, true, false);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(fee));

        List<DictPricingMarkupFee> rows = service.listFees(" normal ", " basic_percent ");

        assertThat(rows).containsExactly(fee);
        ArgumentCaptor<QueryWrapper<DictPricingMarkupFee>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        QueryWrapper<DictPricingMarkupFee> query = captor.getValue();
        assertThat(query.getSqlSegment()).contains("pricing_scope", "fee_category", "sort_no", "id");
        assertThat(query.getParamNameValuePairs().values()).contains("NORMAL", "BASIC_PERCENT");
    }

    @Test
    void createBasicPercentFeeNormalizesDefaultsAndAuditsUnifiedAction() {
        DictPricingMarkupFeeMapper mapper = mock(DictPricingMarkupFeeMapper.class);
        AuditService auditService = mock(AuditService.class);
        PricingMarkupFeeService service = service(mapper, auditService);
        doAnswer(invocation -> {
            DictPricingMarkupFee inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        }).when(mapper).insert(any(DictPricingMarkupFee.class));

        PricingMarkupFeeSaveReq req = req(" normal ", " basic_percent ", " Freight ", null, null, null);
        DictPricingMarkupFee saved = service.saveFee(null, req);

        assertThat(saved.getId()).isEqualTo(88L);
        assertThat(saved.getPricingScope()).isEqualTo("NORMAL");
        assertThat(saved.getFeeCategory()).isEqualTo("BASIC_PERCENT");
        assertThat(saved.getFeeName()).isEqualTo("Freight");
        assertThat(saved.getRatePercent()).isEqualByComparingTo("0");
        assertThat(saved.getSortNo()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSystemRequired()).isFalse();
        verify(auditService).record(eq("DICT_PRICING_MARKUP_FEE_CREATE"), eq("dict_pricing_markup_fee"), eq(88L), isNull(), any(Map.class));
    }

    @Test
    void createRejectsSystemFeeCategoriesBecauseDefaultsAreEditedOnly() {
        PricingMarkupFeeService service = service(mock(DictPricingMarkupFeeMapper.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.saveFee(null, req("NORMAL", "TAX", "Tax", "13", 1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system fee category");
        assertThatThrownBy(() -> service.saveFee(null, req("NORMAL", "CUSTOMER_WEIGHT", "Customer weight", "10", 1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system fee category");
    }

    @Test
    void updateSystemFeeAllowsOnlyRateAndEnabledWithoutChangingIdentity() {
        DictPricingMarkupFeeMapper mapper = mock(DictPricingMarkupFeeMapper.class);
        AuditService auditService = mock(AuditService.class);
        PricingMarkupFeeService service = service(mapper, auditService);
        DictPricingMarkupFee before = fee(7L, "NORMAL", "TAX", "Tax", "13.00", 10, true, true);
        when(mapper.selectById(7L)).thenReturn(before);

        PricingMarkupFeeSaveReq req = req("ASSEMBLY", "BASIC_PERCENT", "Changed", "9.00", 99, false);
        DictPricingMarkupFee saved = service.saveFee(7L, req);

        assertThat(saved.getPricingScope()).isEqualTo("NORMAL");
        assertThat(saved.getFeeCategory()).isEqualTo("TAX");
        assertThat(saved.getFeeName()).isEqualTo("Tax");
        assertThat(saved.getRatePercent()).isEqualByComparingTo("9.00");
        assertThat(saved.getSortNo()).isEqualTo(10);
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getSystemRequired()).isTrue();
        verify(mapper).updateById(any(DictPricingMarkupFee.class));
        verify(auditService).record(eq("DICT_PRICING_MARKUP_FEE_UPDATE"), eq("dict_pricing_markup_fee"), eq(7L), any(Map.class), any(Map.class));
    }

    @Test
    void deleteRejectsSystemRequiredRowsAndDeletesNormalRowsWithAudit() {
        DictPricingMarkupFeeMapper mapper = mock(DictPricingMarkupFeeMapper.class);
        AuditService auditService = mock(AuditService.class);
        PricingMarkupFeeService service = service(mapper, auditService);
        when(mapper.selectById(1L)).thenReturn(fee(1L, "NORMAL", "CUSTOMER_WEIGHT", "Customer weight", "0", 100, false, true));

        assertThatThrownBy(() -> service.deleteFee(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system item cannot be deleted");
        verify(mapper, never()).deleteById(1L);

        when(mapper.selectById(3L)).thenReturn(fee(3L, "ASSEMBLY", "TAX", "Tax", "13", 10, true, true));
        assertThatThrownBy(() -> service.deleteFee(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system item cannot be deleted");
        verify(mapper, never()).deleteById(3L);

        DictPricingMarkupFee normal = fee(2L, "ASSEMBLY", "BASIC_PERCENT", "Management", "5", 20, true, false);
        when(mapper.selectById(2L)).thenReturn(normal);

        service.deleteFee(2L);

        verify(mapper).deleteById(2L);
        verify(auditService).record(eq("DICT_PRICING_MARKUP_FEE_DELETE"), eq("dict_pricing_markup_fee"), eq(2L), any(Map.class), isNull());
    }

    @Test
    void dictControllerExposesUnifiedPricingMarkupFeeCrudWithExpectedPermissions() throws Exception {
        assertRoute("listPricingMarkupFees", new Class<?>[]{String.class, String.class}, GetMapping.class,
                "/pricing-markup-fees", "engineer:dict:view");
        assertRoute("createPricingMarkupFee", new Class<?>[]{PricingMarkupFeeSaveReq.class}, PostMapping.class,
                "/pricing-markup-fees", "engineer:dict:fee-manage");
        assertRoute("updatePricingMarkupFee", new Class<?>[]{Long.class, PricingMarkupFeeSaveReq.class}, PutMapping.class,
                "/pricing-markup-fees/{id}", "engineer:dict:fee-manage");
        assertRoute("deletePricingMarkupFee", new Class<?>[]{Long.class}, DeleteMapping.class,
                "/pricing-markup-fees/{id}", "engineer:dict:fee-manage");
    }

    private static PricingMarkupFeeService service(DictPricingMarkupFeeMapper mapper, AuditService auditService) {
        PricingMarkupFeeService service = new PricingMarkupFeeService();
        ReflectionTestUtils.setField(service, "pricingMarkupFeeMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private static PricingMarkupFeeSaveReq req(
            String pricingScope,
            String feeCategory,
            String feeName,
            String ratePercent,
            Integer sortNo,
            Boolean enabled) {
        PricingMarkupFeeSaveReq req = new PricingMarkupFeeSaveReq();
        req.setPricingScope(pricingScope);
        req.setFeeCategory(feeCategory);
        req.setFeeName(feeName);
        req.setRatePercent(ratePercent == null ? null : new BigDecimal(ratePercent));
        req.setSortNo(sortNo);
        req.setEnabled(enabled);
        return req;
    }

    private static DictPricingMarkupFee fee(
            Long id,
            String pricingScope,
            String feeCategory,
            String feeName,
            String ratePercent,
            Integer sortNo,
            Boolean enabled,
            Boolean systemRequired) {
        DictPricingMarkupFee fee = new DictPricingMarkupFee();
        fee.setId(id);
        fee.setPricingScope(pricingScope);
        fee.setFeeCategory(feeCategory);
        fee.setFeeName(feeName);
        fee.setRatePercent(new BigDecimal(ratePercent));
        fee.setSortNo(sortNo);
        fee.setEnabled(enabled);
        fee.setSystemRequired(systemRequired);
        return fee;
    }

    private void assertRoute(
            String methodName,
            Class<?>[] parameterTypes,
            Class<?> mappingType,
            String path,
            String permission) throws Exception {
        Method method = DictController.class.getDeclaredMethod(methodName, parameterTypes);
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        assertThat(requirePermission).as(methodName + " should have RequirePermission").isNotNull();
        assertThat(requirePermission.value()).containsExactly(permission);

        if (mappingType == GetMapping.class) {
            assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
        } else if (mappingType == PostMapping.class) {
            assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        } else if (mappingType == PutMapping.class) {
            assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly(path);
        } else if (mappingType == DeleteMapping.class) {
            assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly(path);
        } else {
            throw new IllegalArgumentException("Unsupported mapping type " + mappingType);
        }
    }
}
