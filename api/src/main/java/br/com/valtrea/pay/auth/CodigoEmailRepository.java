package br.com.valtrea.pay.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import br.com.valtrea.pay.auth.CodigoEmail.Finalidade;
import jakarta.persistence.LockModeType;

public interface CodigoEmailRepository extends JpaRepository<CodigoEmail, UUID> {

	/** Só o código mais recente de cada finalidade vale; pedir outro invalida os anteriores na prática. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CodigoEmail> findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(UUID usuarioId, Finalidade finalidade);

}
