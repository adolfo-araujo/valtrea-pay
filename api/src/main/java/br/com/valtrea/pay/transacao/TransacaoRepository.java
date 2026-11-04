package br.com.valtrea.pay.transacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransacaoRepository extends JpaRepository<Transacao, UUID> {

	Optional<Transacao> findByOrigemIdAndChaveIdempotencia(UUID contaOrigemId, String chaveIdempotencia);

	@EntityGraph(attributePaths = { "origem.usuario", "destino.usuario" })
	@Query("select t from Transacao t where t.origem.id = :contaId or t.destino.id = :contaId")
	Page<Transacao> extrato(@Param("contaId") UUID contaId, Pageable pagina);

	/** Todas as transações da conta, da mais antiga para a mais nova (para "Baixar meus dados"). */
	@EntityGraph(attributePaths = { "origem.usuario", "destino.usuario" })
	@Query("select t from Transacao t where t.origem.id = :contaId or t.destino.id = :contaId order by t.criadoEm asc")
	List<Transacao> todasDaConta(@Param("contaId") UUID contaId);

}
