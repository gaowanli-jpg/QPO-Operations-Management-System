package com.machining.quote.service.mes;

import java.util.ArrayList;
import java.util.List;

public final class MesCustomerRequirementsAssembler {
    private MesCustomerRequirementsAssembler() {
    }

    public static String assemble(String materialName, String surfaceTreatment) {
        List<String> lines = new ArrayList<>();
        String normalizedMaterialName = normalize(materialName);
        if (normalizedMaterialName != null) {
            lines.add("材料名称：" + normalizedMaterialName);
        }
        String normalizedSurfaceTreatment = normalize(surfaceTreatment);
        if (normalizedSurfaceTreatment != null) {
            lines.add("表面处理：" + normalizedSurfaceTreatment);
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
