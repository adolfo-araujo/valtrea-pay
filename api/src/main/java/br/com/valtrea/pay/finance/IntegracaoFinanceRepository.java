package br.com.valtrea.pay.finance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegracaoFinanceRepository extends JpaRepository<IntegracaoFinance, UUID> {
}
