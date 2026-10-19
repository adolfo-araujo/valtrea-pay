package br.com.valtrea.pay.usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByCpf(String cpf);

	/**
	 * Busca contatos pelo e-mail exato ou por parte do nome, sem incluir quem está buscando. O nome é comparado sem
	 * acentos e sem diferenciar maiúsculas: {@code nomeBusca} já vem de {@link Texto#paraBusca}.
	 */
	@Query("""
			select u from Usuario u
			where u.id <> :solicitante
			  and (lower(u.email) = lower(:email) or u.nomeBusca like concat('%', :nomeBusca, '%'))
			order by u.nome
			""")
	List<Usuario> buscarContatos(@Param("solicitante") UUID solicitante, @Param("email") String email,
			@Param("nomeBusca") String nomeBusca, Pageable limite);

}
