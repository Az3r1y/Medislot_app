package fr.medislot.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ClinicTenantResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {
    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long id = TenantContext.current();
        return id == null ? 0L : id;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> properties) {
        properties.put("hibernate.tenant_identifier_resolver", this);
    }
}
