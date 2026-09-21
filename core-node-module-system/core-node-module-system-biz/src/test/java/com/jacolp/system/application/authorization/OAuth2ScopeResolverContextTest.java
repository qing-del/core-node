package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ScopeResolverContextTest {

    @Test
    void registersOneBeanAndResolvesAnIntersectedScope() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(OAuth2ScopeResolver.class);
            context.refresh();

            OAuth2ScopeResolver resolver = context.getBean(OAuth2ScopeResolver.class);
            assertThat(context.getBeansOfType(OAuth2ScopeResolver.class)).hasSize(1);
            assertThat(resolver.resolve(new EffectiveRolePermissions(1L, "USER", 3, List.of("note:read")),
                    List.of("note:read"), List.of("note:read"), List.of("note:read")))
                    .containsExactly("note:read");
        }
    }
}
