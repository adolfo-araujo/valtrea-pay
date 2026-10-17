package br.com.valtrea.pay.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

	/** Trava a linha para duas renovações simultâneas com o mesmo token não gerarem duas sessões. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Sessao s join fetch s.usuario where s.tokenHash = :hash")
	Optional<Sessao> travarPorTokenHash(@Param("hash") String tokenHash);

	@Modifying
	@Query("update Sessao s set s.revogadoEm = :agora where s.usuario.id = :usuarioId and s.revogadoEm is null")
	int revogarTodas(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

}
