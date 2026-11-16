package fr.medislot.tenant;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration
public class TenantTransactionManager {
    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
        var manager =
                new JpaTransactionManager(factory) {
                    @Override
                    protected void doBegin(Object transaction, TransactionDefinition definition) {
                        super.doBegin(transaction, definition);
                        try {
                            var holder =
                                    (EntityManagerHolder)
                                            TransactionSynchronizationManager.getResource(factory);
                            holder.getEntityManager()
                                    .unwrap(Session.class)
                                    .doWork(
                                            connection -> {
                                                try (var statement =
                                                        connection.prepareStatement(
                                                                "select set_config('app.clinic_id', ?, true)")) {
                                                    statement.setString(
                                                            1,
                                                            TenantContext.current() == null
                                                                    ? ""
                                                                    : TenantContext.current()
                                                                            .toString());
                                                    statement.execute();
                                                }
                                            });
                        } catch (RuntimeException failure) {
                            // Ne pas laisser une connexion transactionnelle liée au thread si SET
                            // LOCAL échoue.
                            super.doCleanupAfterCompletion(transaction);
                            throw failure;
                        }
                    }
                };
        return manager;
    }
}
