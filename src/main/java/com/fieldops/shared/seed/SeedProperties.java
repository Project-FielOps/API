package com.fieldops.shared.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do seed de desenvolvimento.
 *
 * <p>A senha não tem valor padrão de propósito. Uma senha embutida no código
 * cria contas de administrador com credencial conhecida por qualquer pessoa com
 * acesso ao repositório — e basta o perfil "dev" ser habilitado por engano em um
 * ambiente exposto para que isso vire uma porta aberta. Sem senha configurada, o
 * seed não roda.
 */
@ConfigurationProperties(prefix = "fieldops.seed")
public record SeedProperties(boolean enabled, String password) {

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
