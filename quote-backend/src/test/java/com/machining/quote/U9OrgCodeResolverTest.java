package com.machining.quote;

import com.machining.quote.service.u9.U9OrgCodeResolver;
import com.machining.quote.service.u9.U9RuntimeConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class U9OrgCodeResolverTest {

    @Test
    void runtimeConfigOrgCodeOverridesFallbacks() {
        U9RuntimeConfigService configService = mock(U9RuntimeConfigService.class);
        when(configService.effectiveOrgCode()).thenReturn(" 106 ");
        U9OrgCodeResolver resolver = new U9OrgCodeResolver(configService);

        assertThat(resolver.effectiveOrgCode()).isEqualTo("106");
    }

    @Test
    void defaultOrgCodeRemains101WhenRuntimeConfigIsBlank() {
        U9RuntimeConfigService configService = mock(U9RuntimeConfigService.class);
        when(configService.effectiveOrgCode()).thenReturn(" ");
        U9OrgCodeResolver resolver = new U9OrgCodeResolver(configService);

        assertThat(resolver.effectiveOrgCode()).isEqualTo("101");
    }

    @Test
    void defaultOrgCodeRemains101WhenRuntimeConfigServiceIsUnavailable() {
        U9OrgCodeResolver resolver = new U9OrgCodeResolver(null);

        assertThat(resolver.effectiveOrgCode()).isEqualTo("101");
    }

}
