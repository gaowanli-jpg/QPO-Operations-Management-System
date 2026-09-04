package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.DictController;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictExtraFeeItemPaginationTest {

    @Test
    void controllerExposesExtraFeeBackendPaginationWithExistingDictViewPermission() throws Exception {
        Method method = DictController.class.getMethod(
                "pageExtraFeeItems", Boolean.class, String.class, Integer.class, Integer.class);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/extra-fee-items/page");
        assertThat(method.getAnnotation(RequirePermission.class).value()).containsExactly("engineer:dict:view");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageExtraFeeItemsSupportsKeywordSearchAndStableServerSidePagination() {
        DictExtraFeeItemMapper mapper = mock(DictExtraFeeItemMapper.class);
        DictController controller = new DictController();
        ReflectionTestUtils.setField(controller, "extraFeeItemMapper", mapper);

        DictExtraFeeItem fee = new DictExtraFeeItem();
        fee.setId(7);
        fee.setFeeCode("EDM");
        fee.setFeeName("电火花");
        fee.setSortNo(20);
        fee.setEnabled(true);
        fee.setRemarks("精加工");
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(37L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(fee));

        PageResult<DictExtraFeeItem> page = controller.pageExtraFeeItems(true, "edm", 3, 20);

        assertThat(page.getTotal()).isEqualTo(37);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getFeeCode()).isEqualTo("EDM");
            assertThat(row.getFeeName()).isEqualTo("电火花");
        });
        ArgumentCaptor<QueryWrapper<DictExtraFeeItem>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<DictExtraFeeItem>> listCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        org.mockito.Mockito.verify(mapper).selectCount(countCaptor.capture());
        org.mockito.Mockito.verify(mapper).selectList(listCaptor.capture());
        assertThat(countCaptor.getValue().getTargetSql().toLowerCase()).contains("fee_code", "fee_name", "remarks");
        assertThat(listCaptor.getValue().getTargetSql()).contains("LIMIT 20 OFFSET 40");
    }
}
