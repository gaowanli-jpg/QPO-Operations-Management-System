package com.machining.quote.service;

import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderRepeatEnrichmentResult;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderRepeatEnrichmentService {
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 200;
    private static final String MATCHED = "\u5df2\u5339\u914d";
    private static final String UNMATCHED = "\u672a\u5339\u914d";
    private static final String WAITING_SNAPSHOT_MESSAGE = "\u5df2\u5bfc\u5165\u4ef7\u683c\uff0c\u672a\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";

    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    public SalesOrderRepeatEnrichmentResult enrich(List<SalesOrderItem> currentItems) {
        SalesOrderRepeatEnrichmentResult result = new SalesOrderRepeatEnrichmentResult();
        if (currentItems == null || currentItems.isEmpty()) {
            return result;
        }

        List<String> keys = salesOrderItemKeys(currentItems);
        Map<String, ItemQuoteVersion> sourceByKey = releasedProductionSnapshotsByKey(keys);
        int matchedCount = 0;
        int unmatchedCount = 0;
        for (SalesOrderItem current : currentItems) {
            if (current == null) {
                continue;
            }
            ItemQuoteVersion source = null;
            if (!isBlank(current.getPartNo()) && !isBlank(current.getVersion())) {
                source = sourceByKey.get(itemKey(current.getPartNo(), current.getVersion()));
            }
            if (source == null) {
                markUnmatched(current);
                updateSalesOrderItem(current);
                unmatchedCount++;
                continue;
            }
            enrichSalesOrderItem(current, source);
            updateSalesOrderItem(current);
            matchedCount++;
        }
        result.setMatchedItemCount(matchedCount);
        result.setUnmatchedItemCount(unmatchedCount);
        result.setWarningMessage(warningMessage(unmatchedCount));
        return result;
    }

    private List<String> salesOrderItemKeys(List<SalesOrderItem> items) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (SalesOrderItem item : items) {
            if (item != null && !isBlank(item.getPartNo()) && !isBlank(item.getVersion())) {
                keys.add(itemKey(item.getPartNo(), item.getVersion()));
            }
        }
        return new ArrayList<>(keys);
    }

    private Map<String, ItemQuoteVersion> releasedProductionSnapshotsByKey(List<String> keys) {
        Map<String, ItemQuoteVersion> result = new LinkedHashMap<>();
        List<PartVersionKey> partVersionKeys = keys.stream()
                .map(this::toPartVersionKey)
                .toList();
        for (int start = 0; start < partVersionKeys.size(); start += REPEAT_SOURCE_KEY_BATCH_SIZE) {
            int end = Math.min(start + REPEAT_SOURCE_KEY_BATCH_SIZE, partVersionKeys.size());
            List<ItemQuoteVersion> sources = itemMapper.selectLatestReleasedRepeatSources(null, partVersionKeys.subList(start, end));
            for (ItemQuoteVersion source : sources) {
                if (source != null && !isBlank(source.getPartNo()) && !isBlank(source.getVersion())) {
                    result.putIfAbsent(itemKey(source.getPartNo(), source.getVersion()), source);
                }
            }
        }
        return result;
    }

    private PartVersionKey toPartVersionKey(String key) {
        String[] parts = key.split("\\|\\|", 2);
        return new PartVersionKey(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private void enrichSalesOrderItem(SalesOrderItem current, ItemQuoteVersion source) {
        String uploadedDrawingUrl = current.getDrawingUrl();
        boolean hasUploadedDrawing = !isBlank(uploadedDrawingUrl);
        current.setMaterialName(source.getMaterialName());
        current.setEngineeringMaterialName(source.getEngineeringMaterialName());
        current.setSurfaceTreatment(source.getSurfaceTreatment());
        current.setMaterialGrossWeight(source.getMaterialGrossWeight());
        current.setPartNetWeight(source.getPartNetWeight());
        current.setQuoteMode(source.getQuoteMode());
        current.setAssemblyFee(toBigDecimal(source.getAssemblyFee()));
        current.setBaseCost(toBigDecimal(source.getBaseCost()));
        current.setMaterialData(source.getMaterialData());
        current.setProcessRoute(source.getProcessRoute());
        current.setFeeData(source.getFeeData());
        if (hasUploadedDrawing) {
            current.setDrawingUrl(uploadedDrawingUrl);
            current.setHasDrawing(true);
        } else {
            current.setDrawingUrl(source.getDrawingUrl());
            current.setHasDrawing(!isBlank(source.getDrawingUrl()));
        }
        current.setItemType(QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? "\u7ec4\u5408\u4ef6" : "\u666e\u901a\u4ef6");
        current.setMatchedRfqId(source.getRfqId());
        current.setMatchedItemId(source.getId());
        current.setMatchStatus(MATCHED);
        current.setMatchMessage("\u5df2\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167\uff1aRFQ " + source.getRfqId()
                + " / item " + source.getId());
        current.setUpdatedAt(LocalDateTime.now());
    }

    private void markUnmatched(SalesOrderItem current) {
        current.setMatchedRfqId(null);
        current.setMatchedItemId(null);
        current.setMatchStatus(UNMATCHED);
        current.setMatchMessage(WAITING_SNAPSHOT_MESSAGE);
        current.setUpdatedAt(LocalDateTime.now());
    }

    private void updateSalesOrderItem(SalesOrderItem item) {
        if (item.getId() != null) {
            salesOrderItemMapper.updateById(item);
        }
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String warningMessage(int unmatchedCount) {
        if (unmatchedCount <= 0) {
            return "";
        }
        return "\u672a\u5339\u914d\u5386\u53f2\u751f\u4ea7\u5feb\u7167\u7684\u9500\u552e\u8ba2\u5355\u96f6\u4ef6\u6570\uff1a" + unmatchedCount;
    }

    private String itemKey(String partNo, String version) {
        return partNo.trim() + "||" + version.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
