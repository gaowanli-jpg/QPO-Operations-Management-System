package com.machining.quote.service.mes;

import java.util.ArrayList;
import java.util.List;

public final class MesCustomerRequirementsAssembler {
    private MesCustomerRequirementsAssembler() {
    }

    public static String assemble(String description, String surfaceTreatment) {
        List<String> lines = new ArrayList<>();
        String normalizedDescription = normalize(description);
        if (normalizedDescription != null) {
            lines.add("物料描述：" + normalizedDescription);
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
