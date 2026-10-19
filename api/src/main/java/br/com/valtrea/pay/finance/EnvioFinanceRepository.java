package br.com.valtrea.pay.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvioFinanceRepository extends JpaRepository<EnvioFinance, UUID> {

	/** Próximos pendentes, travados; SKIP LOCKED deixa duas instâncias da API dividirem a fila sem repetir. */
	@NativeQuery("""
			select * from envios_finance
			where enviado_em is null and proxima_tentativa <= now()
			order by proxima_tentativa
			limit :limite
			for update skip locked
			""")
	List<EnvioFinance> travarPendentes(@Param("limite") int limite);

	@Modifying
	@Query("delete from EnvioFinance e where e.usuarioId = :usuarioId and e.enviadoEm is null")
	void apagarPendentes(@Param("usuarioId") UUID usuarioId);

	List<EnvioFinance> findByUsuarioIdOrderByCriadoEm(UUID usuarioId);

}
