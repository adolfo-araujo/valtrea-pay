package br.com.valtrea.pay.conta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ContaRepository extends JpaRepository<Conta, UUID> {

	Optional<Conta> findByUsuarioId(UUID usuarioId);

	/**
	 * Trava as contas dos usuários informados (SELECT ... FOR UPDATE). A ordenação por id faz duas transferências
	 * cruzadas entre os mesmos usuários travarem as contas na mesma ordem, evitando deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Conta c join fetch c.usuario u where u.id in :usuarioIds order by c.id")
	List<Conta> travarPorUsuarioIds(@Param("usuarioIds") Collection<UUID> usuarioIds);

}
